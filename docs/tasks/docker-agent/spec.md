# Task

Let the orchestrator Claude session on the host machine start autonomous Claude Code agents in Docker Desktop
containers. Each container runs one task, so the orchestrator can run several agents in parallel.

## Scope

- `.claude/skills/docker-agent/SKILL.md`: a project skill for the host orchestrator. It takes a task reference and
  a task description.
- `.claude/skills/docker-agent/scripts/start-agent.sh`: starts the compose project `ethwallet-<name>` and writes
  the prompt (template, `---`, then the task) into the container. It launches `claude` in a detached tmux session
  with Opus 5.5, medium effort, bypassed permissions, and Fable as the advisor. After 3 seconds it pastes the
  prompt with tmux and presses Enter.
- `.claude/skills/docker-agent/template.md`: rules shared by all Docker agents.
- `.claude/skills/docker-agent/scripts/pr-comments.sh`: runs in the container. It prints the PR state and new
  comments from other users, because GitHub cannot push events to a container without a public IP.
- `.claude/skills/docker-agent/docker/` (`Dockerfile`, `compose.yaml`, `entrypoint.sh`): the agent image and one
  compose project per task. Each project has its own workspace volume, cloned over HTTPS with `GH_TOKEN`, and its own Gradle home.
  Claude config comes from the shared external volume. The shared Gradle cache is mounted read-only.

## Template rules

- The agent runs in Docker in "allow all" mode. This is safe because of container isolation, so the agent never
  asks for confirmation.
- The agent is an autonomous actor in a Software Factory. It makes its own decisions and lists them in the PR
  description. The human gives feedback through PR comments.
- The agent plans before changing code. Two independent general-purpose subagents (Opus and Fable) review and
  approve the plan. Fable is also the advisor.
- Two new independent subagents review the diff against `REVIEW.md` and `CLAUDE.md`. Reviewers get the task and the
  plan or diff, never the agent's reasoning.
- The change is full and minimal.
- The PR description lists the most critical changes and the places where the agent struggled.
- After the PR is created, the agent polls comments with the built-in `loop` skill and replies to every human
  comment.

## Definition of done

- The orchestrator can run `start-agent.sh` once per task. Each call starts an isolated container whose agent works
  without any terminal interaction.
- The prompt starts claude without blocking dialogs.
- `README.md` is unchanged because it does not mention agent tooling.
