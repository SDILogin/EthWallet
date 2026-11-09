#!/bin/bash
set -euo pipefail

gh auth setup-git

if [ ! -d /workspace/EthWallet/.git ]; then
  git clone "https://github.com/$GITHUB_REPOSITORY.git" /workspace/EthWallet
  (cd /workspace/EthWallet && tools/setup.sh)
fi

exec "$@"
