#!/bin/bash
# Prints the PR state, then unseen comments from other users as JSON lines, and marks them as seen.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <pr-number>" >&2
  exit 2
fi

readonly pr="$1"
readonly seen_file="$(git rev-parse --absolute-git-dir)/pr-$pr-seen-comments"
readonly me="$(gh api user --jq .login)"
touch "$seen_file"

gh pr view "$pr" --json state --jq .state

{
  gh api --paginate "repos/{owner}/{repo}/issues/$pr/comments" \
    --jq '.[] | {kind: "issue", id, author: .user.login, url: .html_url, body}'
  gh api --paginate "repos/{owner}/{repo}/pulls/$pr/reviews" \
    --jq '.[] | select(.body != "") | {kind: "review", id, author: .user.login, state, url: .html_url, body}'
  gh api --paginate "repos/{owner}/{repo}/pulls/$pr/comments" \
    --jq '.[] | {kind: "review_comment", id, author: .user.login, path, line, in_reply_to: .in_reply_to_id, url: .html_url, body}'
} | jq -c --arg me "$me" 'select(.author != $me)' | while IFS= read -r comment; do
  key="$(jq -r '"\(.kind):\(.id)"' <<< "$comment")"
  if ! grep -qxF "$key" "$seen_file"; then
    echo "$comment"
    echo "$key" >> "$seen_file"
  fi
done
