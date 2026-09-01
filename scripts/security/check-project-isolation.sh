#!/usr/bin/env bash
set -euo pipefail

# Sentinel is intentionally isolated from A KI PRI SA YÉ. This guard scans the
# current tracked tree (not git history) and fails on known project identifiers.
# Security documentation is excluded because it must describe this control.

patterns=(
  'akiprisaye'
  'a-ki-pri-sa-ye'
  'com\.akiprisaye'
  'akiprisaye\.pages\.dev'
  'akiprisaye-v4'
)

forbidden_re='('"$(IFS='|'; echo "${patterns[*]}")"')'

matches=$(git grep -n -I -E -i "$forbidden_re" -- . \
  ':(exclude)docs/security/**' \
  ':(exclude).github/workflows/check-project-isolation.yml' \
  ':(exclude)scripts/security/check-project-isolation.sh' \
  || true)

if [[ -n "$matches" ]]; then
  echo "ERROR: Sentinel/A KI PRI SA YÉ isolation violation detected in tracked source/configuration:" >&2
  echo "$matches" >&2
  exit 1
fi

echo "Project isolation check: PASS"
