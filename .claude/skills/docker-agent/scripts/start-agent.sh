#!/bin/bash
# Runs on the host. Starts a Docker container for one task and launches an autonomous Claude agent in tmux.
set -euo pipefail

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
: "${GH_TOKEN:?Set GH_TOKEN to the agent GitHub account token}"
: "${GIT_AUTHOR_NAME:?Set GIT_AUTHOR_NAME}" "${GIT_AUTHOR_EMAIL:?Set GIT_AUTHOR_EMAIL}"
: "${GIT_COMMITTER_NAME:?Set GIT_COMMITTER_NAME}" "${GIT_COMMITTER_EMAIL:?Set GIT_COMMITTER_EMAIL}"

readonly skill_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly compose_file="$skill_dir/../../../docker/compose.yaml"
readonly project="ethwallet-$name"

compose() {
  docker compose -p "$project" -f "$compose_file" "$@"
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
    --settings '\''{"skipDangerousModePermissionPrompt": true, "advisorModel": "fable"}'\'' \
    "$(cat /tmp/agent-prompt.md)"'

echo "Started $project"
echo "Attach: docker compose -p $project -f $compose_file exec -it agent tmux new-session -A -s agent"
