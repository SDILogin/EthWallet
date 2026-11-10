#!/usr/bin/env python3
"""Starts EthWallet tasks in Docker agent containers and reuses idle containers.

Runs on the host.

    start-agent.py start "<task-reference>" "<task-description>"
    start-agent.py status

The container manager agent in each running container reports whether the container is idle or busy. `start` uses
the first idle running container, then a stopped container that is idle, then a new container, up to
MAX_CONTAINERS containers in total.
"""

import argparse
import re
import secrets
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

MAX_CONTAINERS = 3
PROJECT_PREFIX = "ethwallet-"
NEW_PROJECT_PREFIX = "ethwallet-agent-"
REQUIRED_ENV_KEYS = ("GH_TOKEN", "GIT_AUTHOR_NAME", "GIT_AUTHOR_EMAIL", "GIT_COMMITTER_NAME", "GIT_COMMITTER_EMAIL")
USAGE_EXIT_CODE = 2
ENV_NOT_SET_EXIT_CODE = 3
NO_FREE_CONTAINER_EXIT_CODE = 4
STATUS_TIMEOUT_S = 180
STATUS_POLL_INTERVAL_S = 3
KEY_DELAY_S = 1
BUSY_MARK = "esc to interrupt"
INPUT_MARK = "❯"
REPLY_MARK = "●"
REPO_DIR = "/workspace/EthWallet"
RESET_CHECKOUT = "git fetch origin && git reset --hard && git clean -fd && git switch -C master origin/master"

SKILL_DIR = Path(__file__).resolve().parent.parent
COMPOSE_FILE = SKILL_DIR / "docker" / "compose.yaml"
TEMPLATE_FILE = SKILL_DIR / "template.md"


@dataclass(frozen=True)
class Container:
    project: str
    name: str
    is_running: bool


@dataclass(frozen=True)
class Status:
    is_idle: bool
    reason: str


def run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, capture_output=True, text=True)


def run_checked(args: list[str]) -> str:
    result = run(args)
    if result.returncode != 0:
        sys.exit(f"Command failed: {' '.join(args[:4])} ...\n{result.stderr.strip()}")
    return result.stdout


def env_file() -> Path:
    git_common_dir = run_checked(["git", "-C", str(SKILL_DIR), "rev-parse", "--path-format=absolute", "--git-common-dir"])
    return Path(git_common_dir.strip()).parent / ".env"


def missing_env_keys(path: Path) -> list[str]:
    text = path.read_text() if path.is_file() else ""
    return [key for key in REQUIRED_ENV_KEYS if re.search(rf"^{key}=\S", text, re.MULTILINE) is None]


def check_env(path: Path) -> None:
    missing = missing_env_keys(path)
    if len(missing) == 0:
        return
    print(
        f"""Agent not started. Missing settings in {path}:
  {' '.join(missing)}

Setup:
1. Create {path}. Git ignores this file.
2. Add these lines with real values:
   GH_TOKEN=<token of the agent GitHub account, with Contents and Pull requests write access>
   GIT_AUTHOR_NAME=<commit author name>
   GIT_AUTHOR_EMAIL=<commit author email>
   GIT_COMMITTER_NAME=<commit committer name>
   GIT_COMMITTER_EMAIL=<commit committer email>
3. Run this command again.""",
        file=sys.stderr,
    )
    sys.exit(ENV_NOT_SET_EXIT_CODE)


def compose(path: Path, project: str, *args: str) -> None:
    run_checked(["docker", "compose", "--env-file", str(path), "-p", project, "-f", str(COMPOSE_FILE), *args])


def parse_containers(listing: str) -> list[Container]:
    containers = []
    for row in listing.splitlines():
        project, name, state = row.split("|")
        if project.startswith(PROJECT_PREFIX):
            containers.append(Container(project=project, name=name, is_running=state == "running"))
    return containers


def list_containers() -> list[Container]:
    listing = run_checked(
        [
            "docker",
            "ps",
            "-a",
            "--filter",
            "label=com.docker.compose.service=agent",
            "--format",
            '{{.Label "com.docker.compose.project"}}|{{.Names}}|{{.State}}',
        ]
    )
    return parse_containers(listing)


def next_project(projects: set[str]) -> str:
    number = 1
    while f"{NEW_PROJECT_PREFIX}{number}" in projects:
        number += 1
    return f"{NEW_PROJECT_PREFIX}{number}"


def parse_status_reply(screen: str, request_id: str) -> Status | None:
    match = re.search(rf"STATUS-REPLY {request_id} (idle|busy)\b ?(.*)", screen)
    if match is None:
        return None
    return Status(is_idle=match.group(1) == "idle", reason=match.group(2).strip())


def manager_screen(container: Container, *history: str) -> str:
    return run_checked(["docker", "exec", container.name, "tmux", "capture-pane", "-p", "-J", *history, "-t", "manager"])


def manager_is_ready(screen: str) -> bool:
    has_input_box = any(line.startswith(INPUT_MARK) for line in screen.splitlines())
    return has_input_box and BUSY_MARK not in screen


def wait_for_manager(container: Container, deadline: float) -> bool:
    while time.monotonic() < deadline:
        if manager_is_ready(manager_screen(container)):
            return True
        time.sleep(STATUS_POLL_INTERVAL_S)
    return False


def coder_has_started(screen: str) -> bool:
    has_reply = any(line.startswith(REPLY_MARK) for line in screen.splitlines())
    return has_reply or BUSY_MARK in screen


def wait_for_coder(container: Container, deadline: float) -> bool:
    while time.monotonic() < deadline:
        screen = run_checked(["docker", "exec", container.name, "tmux", "capture-pane", "-p", "-t", "agent"])
        if coder_has_started(screen):
            return True
        time.sleep(STATUS_POLL_INTERVAL_S)
    return False


def ask_status(container: Container) -> Status:
    if run(["docker", "exec", container.name, "tmux", "has-session", "-t", "manager"]).returncode != 0:
        return Status(is_idle=False, reason="no container manager (old image)")

    deadline = time.monotonic() + STATUS_TIMEOUT_S
    if wait_for_manager(container, deadline):
        request_id = secrets.token_hex(4)
        run_checked(["docker", "exec", container.name, "tmux", "send-keys", "-t", "manager", "-l", f"STATUS-REQUEST {request_id}"])
        time.sleep(KEY_DELAY_S)
        run_checked(["docker", "exec", container.name, "tmux", "send-keys", "-t", "manager", "Enter"])

        while time.monotonic() < deadline:
            time.sleep(STATUS_POLL_INTERVAL_S)
            status = parse_status_reply(manager_screen(container, "-S", "-300"), request_id)
            if status is not None:
                return status
    return Status(is_idle=False, reason="the container manager did not answer")


def report(container: Container, status: Status) -> None:
    state = "idle" if status.is_idle else "busy"
    print(f"{container.project}: {state} - {status.reason}")


def select_container(path: Path, containers: list[Container]) -> Container:
    for container in [container for container in containers if container.is_running]:
        status = ask_status(container)
        report(container, status)
        if status.is_idle:
            return container

    for container in [container for container in containers if not container.is_running]:
        compose(path, container.project, "up", "-d", "--build", "--wait")
        status = ask_status(container)
        report(container, status)
        if status.is_idle:
            return container
        compose(path, container.project, "stop")

    if len(containers) < MAX_CONTAINERS:
        project = next_project({container.project for container in containers})
        compose(path, project, "up", "-d", "--build", "--wait")
        print(f"{project}: created")
        return Container(project=project, name=f"{project}-agent-1", is_running=True)

    print(f"All {MAX_CONTAINERS} agent containers are busy. Merge or close a PR first.", file=sys.stderr)
    sys.exit(NO_FREE_CONTAINER_EXIT_CODE)


def start(task_reference: str, task_description: str) -> None:
    path = env_file()
    check_env(path)
    container = select_container(path, list_containers())

    prompt = f"{TEMPLATE_FILE.read_text()}\n\n---\n\nTask reference: {task_reference}\n\n{task_description}\n"
    run_checked(["docker", "exec", "-w", REPO_DIR, container.name, "sh", "-c", RESET_CHECKOUT])
    run_checked(["docker", "exec", container.name, "start-coder", prompt])

    if wait_for_coder(container, time.monotonic() + STATUS_TIMEOUT_S):
        print(f"Started {task_reference} in {container.project}")
    else:
        print(f"The coder in {container.project} did not start working. Attach and check its screen.", file=sys.stderr)
    print(f"Attach: docker exec -it {container.name} tmux new-session -A -s agent")


def status() -> None:
    for container in list_containers():
        if container.is_running:
            report(container, ask_status(container))
        else:
            print(f"{container.project}: stopped")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    start_parser = commands.add_parser("start", help="start a task in an idle, stopped or new container")
    start_parser.add_argument("task_reference")
    start_parser.add_argument("task_description")
    commands.add_parser("status", help="ask each running container manager for its status")
    arguments = parser.parse_args()

    if arguments.command == "start":
        start(arguments.task_reference, arguments.task_description)
    else:
        status()


if __name__ == "__main__":
    main()
