#!/usr/bin/env bash
set -euo pipefail

# Sentinel must never contain runtime/build/config references belonging to A KI PRI SA YÉ.
# Security policy documentation is intentionally excluded because it names the
# forbidden project in order to define and audit this control.
forbidden=(
  'akiprisaye'
  'a-ki-pri-sa-ye'
  'akiprisaye-v4'
  'akiprisaye.pages.dev'
  'com.akiprisaye'
)

failed=0
for term in "${forbidden[@]}"; do
  if matches=$(git grep -n -I -i -F -- "$term" -- \
      ':(exclude)docs/security/**' \
      ':(exclude)scripts/check-project-isolation.sh' \
      ':(exclude)scripts/security/check-project-isolation.sh' 2>/dev/null); then
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
