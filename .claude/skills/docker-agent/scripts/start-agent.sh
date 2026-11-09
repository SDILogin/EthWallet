#!/bin/bash
# Runs on the host. Starts a Docker container for one task and launches an autonomous Claude agent in tmux.
set -euo pipefail

readonly TUI_START_TIMEOUT_S=3
readonly ENV_NOT_SET_EXIT_CODE=3

if [ $# -ne 3 ]; then
  echo "Usage: $0 <name> <task-reference> <task-description>" >&2
  exit 2
fi

readonly name="$1"
readonly task_reference="$2"
readonly task_description="$3"

if [[ ! "$name" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
  echo "Name must match [a-z0-9][a-z0-9-]*: $name" >&2
  exit 2
fi

readonly skill_dir="$(dirname "$(dirname "$(realpath "${BASH_SOURCE[0]}")")")"
readonly repo_root="$(dirname "$(git -C "$skill_dir" rev-parse --path-format=absolute --git-common-dir)")"
readonly env_file="$repo_root/.env"

missing_env_keys=()
for key in GH_TOKEN GIT_AUTHOR_NAME GIT_AUTHOR_EMAIL GIT_COMMITTER_NAME GIT_COMMITTER_EMAIL; do
  if [ ! -f "$env_file" ] || ! grep -Eq "^$key=[^[:space:]]" "$env_file"; then
    missing_env_keys+=("$key")
  fi
done

if [ "${#missing_env_keys[@]}" -ne 0 ]; then
  cat >&2 <<EOF
Agent not started. Missing settings in $env_file:
  ${missing_env_keys[*]}

Setup:
1. Create $env_file. Git ignores this file.
2. Add these lines with real values:
   GH_TOKEN=<token of the agent GitHub account, with Contents and Pull requests write access>
   GIT_AUTHOR_NAME=<commit author name>
   GIT_AUTHOR_EMAIL=<commit author email>
   GIT_COMMITTER_NAME=<commit committer name>
   GIT_COMMITTER_EMAIL=<commit committer email>
3. Run this command again.
EOF
  exit "$ENV_NOT_SET_EXIT_CODE"
fi

readonly compose_file="$skill_dir/docker/compose.yaml"
readonly project="ethwallet-$name"

compose() {
  docker compose --env-file "$env_file" -p "$project" -f "$compose_file" "$@"
}

compose up -d --wait

if compose exec -T agent tmux has-session -t agent 2> /dev/null; then
  echo "Agent is already running in $project" >&2
  exit 1
fi

printf '%s\n\n---\n\nTask reference: %s\n\n%s\n' \
  "$(cat "$skill_dir/template.md")" "$task_reference" "$task_description" \
  | compose exec -T agent sh -c 'cat > /tmp/agent-prompt.md'

compose exec -T agent tmux new-session -d -s agent -c /workspace/EthWallet \
  'claude --model claude-opus-5-5 --effort medium --permission-mode bypassPermissions \
    --settings '\''{"skipDangerousModePermissionPrompt": true, "advisorModel": "fable"}'\'''

sleep "$TUI_START_TIMEOUT_S"

# Bracketed paste keeps the prompt's newlines inside one message; Enter then submits it.
compose exec -T agent sh -c 'tmux load-buffer -b prompt /tmp/agent-prompt.md \
  && tmux paste-buffer -p -d -b prompt -t agent \
  && tmux send-keys -t agent Enter'

echo "Started $project"
echo "Attach: docker compose -p $project -f $compose_file exec -it agent tmux new-session -A -s agent"
