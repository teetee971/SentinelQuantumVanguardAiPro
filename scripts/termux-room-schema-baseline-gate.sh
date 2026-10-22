#!/usr/bin/env bash
# Read-only Room schema baseline gate. Works in Termux or Linux.
set -euo pipefail
ROOT="$(git rev-parse --show-toplevel)"
APP="$ROOT/native-android-app/app"
SCHEMAS="$APP/schemas"
echo "SOURCE_SHA=$(git -C "$ROOT" rev-parse HEAD)"
echo "=== Room schema baseline audit ==="
if [[ ! -d "$SCHEMAS" ]]; then
  echo "ROOM_SCHEMA_GATE=BLOCKED_MISSING_DIRECTORY"
  exit 2
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "ROOM_SCHEMA_GATE=BLOCKED_PYTHON3_REQUIRED"
  exit 2
fi
python3 - "$SCHEMAS" <<'PY'
import json
import pathlib
import subprocess
import sys
import hashlib

base = pathlib.Path(sys.argv[1])
root = pathlib.Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
files = sorted(base.rglob("*.json"))
if not files:
    print("ROOM_SCHEMA_GATE=BLOCKED_NO_JSON")
    sys.exit(2)

errors = []
for p in files:
    relative = p.relative_to(root).as_posix()
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError) as exc:
        errors.append(f"{relative}: INVALID_JSON ({type(exc).__name__})")
        continue
    version = data.get("database", {}).get("version")
    identity = data.get("database", {}).get("identityHash")
    if not isinstance(version, int) or version < 1 or not isinstance(identity, str) or not identity:
        errors.append(f"{relative}: INVALID_ROOM_EXPORT")
    if p.stem.isdigit() and isinstance(version, int) and int(p.stem) != version:
        errors.append(f"{relative}: FILENAME_VERSION_MISMATCH")
    tracked = subprocess.run(["git", "-C", str(root), "ls-files", "--error-unmatch", "--", relative],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    digest = hashlib.sha256(p.read_bytes()).hexdigest()
    print(f"SCHEMA={relative} VERSION={version} TRACKED={str(tracked).lower()} SHA256={digest}")
    if not tracked:
        errors.append(f"{relative}: UNTRACKED_BASELINE")
if errors:
    for error in errors:
        print(f"ROOM_SCHEMA_FINDING={error}")
    print("ROOM_SCHEMA_GATE=BLOCKED")
    sys.exit(1)
print("ROOM_SCHEMA_GATE=PASS_TRACKED_JSON_BASELINES")
print("NOTE=Migrations and runtime database behavior require separate tests")
PY
