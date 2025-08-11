#!/usr/bin/env bash
set -euo pipefail

BRANCH=${1:-main}
MSG=${2:-"deploy: $(date +'%F %T')"}

echo "▶ Build…"
npm run build

echo "▶ Stage…"
git add -A

if ! git diff --cached --quiet; then
  echo "▶ Commit…"
  git commit -m "$MSG"
else
  echo "✓ Aucun changement à committer"
fi

echo "▶ Push vers origin/$BRANCH…"
git push origin "$BRANCH"
