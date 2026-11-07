#!/usr/bin/env bash
# Ubuntu/proot preparation only. No adb, installation or Android test execution.
set -euo pipefail
SENTINEL_KIT_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec python3 "$SENTINEL_KIT_ROOT/sign-tests-only.py"
