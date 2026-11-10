#!/bin/bash
set -euo pipefail

gh auth setup-git

if [ ! -d /workspace/EthWallet/.git ]; then
  git clone "https://github.com/$GITHUB_REPOSITORY.git" /workspace/EthWallet
  (cd /workspace/EthWallet && tools/setup.sh)
fi

start-coder
tmux new-session -d -s manager -c /workspace/EthWallet \
  claude --model claude-haiku-5-5 --permission-mode bypassPermissions \
  --settings '{"skipDangerousModePermissionPrompt": true}' \
  "$(cat /usr/local/share/ethwallet-agent/manager.md)"

exec "$@"
