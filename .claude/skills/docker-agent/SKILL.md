---
name: docker-agent
description: Start an autonomous Claude Code agent in a Docker Desktop container for one EthWallet task. Idle containers are reused. The agent plans, implements, opens a PR, and answers PR comments. Use on the host machine (not inside Docker) when the user wants to delegate tasks to Docker agents, swarm several in parallel, or check the status of Docker agents. Takes a task reference and the task description.
---

# Docker Agent

Each agent container works on one task at a time. A container runs two Claude Code agents in tmux:

- The coder, in the tmux session `agent`: Opus 5.5 (medium effort) with Fable as the advisor. It does the task.
- The container manager, in the tmux session `manager`: Haiku 5.5. It only answers the start script on the host. For a
  status request, it runs `agent-status` in the container and replies with one line.

Both agents run in bypass-permissions mode. The container manager prompt forbids changes, but Claude Code does not
enforce this.

## Usage

```bash
.claude/skills/docker-agent/scripts/start-agent.py start "<task-reference>" "<task-description>"
.claude/skills/docker-agent/scripts/start-agent.py status
```

- `task-reference`: where the task comes from, for example `#304 Prevent keyboard caching of the recovery phrase` or
  a spec path. For an Agent Board task, read it through the MCP first and use the exact `#id title` format.
- `task-description`: the full task text. The agent sees only this text and the repository.

To swarm, call `start` once per task, one call after another. Do not run calls in parallel: two calls can select the
same idle container. `start` returns after the new coder starts to work. Report the attach command that the script
prints.

Do not claim an Agent Board task before starting the agent. The agent claims it, and completes it when the PR is
merged.

## Container selection

Agent containers are compose projects whose name starts with `ethwallet-`. `start` selects a container in this order:

1. The first running container whose container manager reports idle.
2. A stopped container. The script starts it and asks its container manager. If the container is busy, the script
   stops it again.
3. A new container `ethwallet-agent-<n>`, while fewer than three agent containers exist.

If all three containers are busy, the script exits with code 4. Then tell the user to merge or close a PR first.

Status rules in `agent-status`:

| State of the checkout | Status |
|---|---|
| The PR of the current branch is open | busy |
| The PR of the current branch is merged or closed | idle |
| The current branch has no PR | busy, the user decides |
| On `master`, the coder works | busy |
| On `master`, the coder does not work | idle |

A Docker restart starts both agents again, and the coder starts without a task. If the branch PR is still open, the
container stays busy, but nobody answers the PR comments. Start the work again by hand in that container.

Each running container uses about 1.2 GB of memory for the two Claude Code processes, also when it is idle.

A container without a container manager (built from an older image) counts as busy. A container manager that does not
answer in 3 minutes also counts as busy.

For a selected container, the script resets the checkout to the newest `origin/master`, stops the old coder session,
and starts a new coder session with the prompt: `template.md`, a `---` separator, then the task reference and
description. The reset keeps ignored files, such as Gradle build output.

## Prerequisites

- Docker Desktop is running. `start` builds the `ethwallet-agent` image from this skill's `docker/` when it starts or
  creates a container.
- The `.env` file in the root of the main checkout sets `GH_TOKEN`, `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`,
  `GIT_COMMITTER_NAME` and `GIT_COMMITTER_EMAIL`. `GH_TOKEN` is the agent GitHub account token, with Contents and Pull
  requests write access.
- The external volume `claude-box_claude-config` holds a logged-in Claude config. In that config,
  `/workspace/EthWallet` is a trusted folder and the Agent Board MCP is set up. The external volume
  `claude-box_gradle-cache` is mounted as a read-only Gradle dependency cache. Override the volume names with
  `CLAUDE_CONFIG_VOLUME` and `GRADLE_SHARED_VOLUME`.

## Environment check

`start` checks `.env` before it starts Docker. If a setting is missing, the script exits with code 3 and prints the
setup instructions. Then stop. Show these instructions to the user, and do not start more agents. Do not get the values
from other containers or files.

## Cleanup

Containers are reused, so delete one only when the user asks:

```bash
docker compose -p <project> -f .claude/skills/docker-agent/docker/compose.yaml down -v
```

`-v` deletes the container's checkout and Gradle home. It does not delete the shared external volumes.

A rebuild of the image leaves the previous agent image without a name. This command deletes only such old agent
images. Docker keeps an image while a container uses it.

```bash
docker image prune -f --filter label=ethwallet.agent=true
```
