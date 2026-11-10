#!/usr/bin/env python3
"""Prints `idle <reason>` or `busy <reason>` for the task in the container checkout.

Rules: an open PR of the current branch is busy, a merged or closed PR is idle, a branch without a PR is busy.
On master, the container is busy while the coder works and idle otherwise.
"""

import json
import os
import subprocess

REPO_DIR = "/workspace/EthWallet"
MAIN_BRANCH = "master"
BUSY_MARK = "esc to interrupt"


def run(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=REPO_DIR, capture_output=True, text=True)


def coder_is_working() -> bool:
    screen = run(["tmux", "capture-pane", "-p", "-t", "agent"])
    return screen.returncode == 0 and BUSY_MARK in screen.stdout


def branch_pull_requests(branch: str) -> list[dict]:
    repository = os.environ["GITHUB_REPOSITORY"]
    owner = repository.split("/")[0]
    response = run(["gh", "api", f"repos/{repository}/pulls?head={owner}:{branch}&state=all"])
    response.check_returncode()
    return json.loads(response.stdout)


def status() -> tuple[str, str]:
    branch = run(["git", "branch", "--show-current"]).stdout.strip()
    if branch == MAIN_BRANCH:
        if coder_is_working():
            return "busy", "the coder works on master"
        return "idle", "no task on master"
    if branch == "":
        return "busy", "the checkout has no current branch"

    pull_requests = branch_pull_requests(branch)
    if len(pull_requests) == 0:
        return "busy", f"branch {branch} has no PR"

    latest = max(pull_requests, key=lambda pull_request: pull_request["number"])
    if latest["state"] == "open":
        return "busy", f"PR #{latest['number']} of branch {branch} is open"
    return "idle", f"PR #{latest['number']} of branch {branch} is merged or closed"


def main() -> None:
    try:
        state, reason = status()
    except Exception as error:
        state, reason = "busy", f"status check failed: {error}"
    print(f"{state} {reason}")


if __name__ == "__main__":
    main()
