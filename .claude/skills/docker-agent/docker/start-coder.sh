#!/bin/bash
set -euo pipefail

tmux kill-session -t agent 2> /dev/null || true
tmux new-session -d -s agent -c /workspace/EthWallet \
  claude --model claude-opus-5-5 --effort medium --permission-mode bypassPermissions \
  --settings '{"skipDangerousModePermissionPrompt": true, "advisorModel": "fable"}' "$@"
