#!/usr/bin/env bash
set -euo pipefail

# Sentinel must never contain runtime/build/config references belonging to A KI PRI SA YÉ.
# Keep this guard deliberately scoped to tracked files and exclude this guard itself.
forbidden=(
  'akiprisaye'
  'a-ki-pri-sa-ye'
  'akiprisaye-v4'
  'akiprisaye.pages.dev'
  'com.akiprisaye'
)

failed=0
for term in "${forbidden[@]}"; do
  if matches=$(git grep -n -I -i -F -- "$term" -- ':!scripts/check-project-isolation.sh' 2>/dev/null); then
    echo "[ISOLATION-FAIL] Forbidden cross-project reference: $term"
    echo "$matches"
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo
  echo 'Sentinel/A KI PRI SA YÉ isolation check failed.'
  exit 1
fi

echo 'Sentinel/A KI PRI SA YÉ isolation check: PASS'
