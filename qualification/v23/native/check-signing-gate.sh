#!/usr/bin/env bash
# Actual Gradle graph regression: never signs, builds, installs or reads a signing key.
set -uo pipefail
out=${1:?Outside log directory required}; shift
repo=$(git rev-parse --show-toplevel)
mkdir -p "$out"
gradle=(bash "$repo/native-android-app/gradlew" -p "$repo/native-android-app" "$@")
env -u KEYSTORE_FILE -u KEYSTORE_PASSWORD -u KEY_ALIAS -u KEY_PASSWORD "${gradle[@]}" :app:bundleReleaseUnsigned --dry-run > "$out/unsigned.log" 2>&1
unsigned=$?
env -u KEYSTORE_FILE -u KEYSTORE_PASSWORD -u KEY_ALIAS -u KEY_PASSWORD "${gradle[@]}" :app:bundleRelease --dry-run > "$out/signed.log" 2>&1
signed=$?
python3 - "$out" "$unsigned" "$signed" <<'PY'
import json,sys
from pathlib import Path
p=Path(sys.argv[1]);u,s=map(int,sys.argv[2:]);protected=s!=0 and 'Release signing requires KEYSTORE_FILE' in (p/'signed.log').read_text()
r={'unsigned_graph_exit':u,'signed_graph_exit':s,'signed_graph_rejected_for_missing_credentials':protected,'actual_build_or_signing':False}
(p/'RESULT.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r));sys.exit(0 if u==0 and protected else 1)
PY
