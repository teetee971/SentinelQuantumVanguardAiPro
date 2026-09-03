#!/usr/bin/env bash
set -euo pipefail

# Sentinel runtime, build and deployment code must remain isolated from the
# separate external price-comparison project. Documentation is allowed to describe the
# isolation policy; executable/configuration surfaces are not.
external_project=$(printf '\x61\x6b\x69\x70\x72\x69\x73\x61\x79\x65')
external_project_hyphenated=$(printf '\x61\x2d\x6b\x69\x2d\x70\x72\x69\x2d\x73\x61\x2d\x79\x65')
forbidden=(
  "$external_project"
  "$external_project_hyphenated"
  "${external_project}-v4"
  "${external_project}.pages.dev"
  "com.${external_project}"
)

failed=0

# Check tracked filenames as well as file contents. A contaminated filename can
# be just as dangerous as a contaminated configuration value.
for term in "${forbidden[@]}"; do
  while IFS= read -r -d '' path; do
    case "$path" in
      docs/*|*.md|scripts/check-project-isolation.sh|scripts/security/check-project-isolation.sh) continue ;;
    esac
    if printf '%s\n' "$path" | grep -Fqi -- "$term"; then
      echo "[ISOLATION-FAIL] Forbidden cross-project identifier in tracked path: $path"
      failed=1
    fi
  done < <(git ls-files -z)
done

for term in "${forbidden[@]}"; do
  if matches=$(git grep -n -I -i -F -- "$term" -- \
      ':(exclude)docs/**' \
      ':(exclude)**/*.md' \
      ':(exclude)scripts/check-project-isolation.sh' \
      ':(exclude)scripts/security/check-project-isolation.sh' 2>/dev/null); then
    echo "[ISOLATION-FAIL] Forbidden cross-project reference detected"
    echo "$matches"
    failed=1
  fi
done

if [[ "$failed" -ne 0 ]]; then
  echo
  echo 'Sentinel project-isolation check failed.'
  exit 1
fi

echo 'Sentinel project-isolation check: PASS'
