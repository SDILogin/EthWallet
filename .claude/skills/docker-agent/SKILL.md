---
name: docker-agent
description: Start an autonomous Claude Code agent in its own Docker Desktop container for one EthWallet task. The agent plans, implements, opens a PR, and answers PR comments. Use on the host machine (not inside Docker) when the user wants to delegate tasks to Docker agents or swarm several in parallel. Takes a task reference and the task description.
---

# Docker Agent

Each call starts one container that works on one task. To swarm, call the script once per task, each with a distinct
name.

## Usage

```bash
.claude/skills/docker-agent/scripts/start-agent.sh <name> "<task-reference>" "<task-description>"
```

- `name`: a short slug for the task, matching `[a-z0-9][a-z0-9-]*`. The compose project is `ethwallet-<name>`.
- `task-reference`: where the task comes from, for example `#304 Prevent keyboard caching of the recovery phrase` or
  a spec path. For an Agent Board task, read it through the MCP first and use the exact `#id title` format.
- `task-description`: the full task text. The agent sees only this text and the repository.

The script builds the prompt as `template.md`, a `---` separator, then the task reference and description. It starts
`claude` on Opus 5.5 (medium effort) in bypass-permissions mode, inside the tmux session `agent`, waits 3 seconds for
the TUI, then pastes the prompt with tmux and presses Enter. Fable is the advisor. Report the attach command the
script prints.

Do not claim an Agent Board task before starting the agent. The agent claims it, and completes it when the PR is
merged.

## Prerequisites

- Docker Desktop is running. The first start builds the `ethwallet-agent` image from this skill's `docker/`.
- `GH_TOKEN` is the agent GitHub account token, with Contents and Pull requests write access.
- `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`, `GIT_COMMITTER_NAME` and `GIT_COMMITTER_EMAIL` are set.
- The external volume `claude-box_claude-config` holds a logged-in Claude config. In that config,
  `/workspace/EthWallet` is a trusted folder and the Agent Board MCP is set up. The external volume
  `claude-box_gradle-cache` is mounted as a read-only Gradle dependency cache. Override the volume names with
  `CLAUDE_CONFIG_VOLUME` and `GRADLE_SHARED_VOLUME`.

## Cleanup

Only when the user asks, for a task whose PR is merged or closed:

```bash
docker compose -p ethwallet-<name> -f .claude/skills/docker-agent/docker/compose.yaml down -v
```

`-v` deletes the task's checkout and Gradle home. It does not delete the shared external volumes.
