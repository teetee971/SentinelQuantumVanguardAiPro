#!/usr/bin/env bash
set -euo pipefail

MODULE="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$MODULE/src/main/cpp/rename_noreplace.c"
OUTPUT="$MODULE/src/main/jniLibs/arm64-v8a/librecordingvault_r3.so"

command -v clang >/dev/null
command -v readelf >/dev/null
test -f "$SOURCE"
test -f "$PREFIX/include/jni.h"

mkdir -p "$(dirname "$OUTPUT")"

TEMP="$(mktemp)"
trap 'rm -f "$TEMP"' EXIT

clang \
  -std=c11 \
  -D_GNU_SOURCE \
  -Wall -Wextra -Werror \
  -O2 -fPIC -shared \
  -fvisibility=hidden \
  -I"$PREFIX/include" \
  "$SOURCE" \
  -o "$TEMP"

readelf -Ws "$TEMP" |
  grep -q 'Java_com_sentinel_quantum_security_RecordingVaultNative_renameNoReplace'

if cmp -s "$TEMP" "$OUTPUT"; then
  echo "BINAIRE IDENTIQUE"
else
  echo "BINAIRE DIFFERENT : contrôle nécessaire"
  sha256sum "$TEMP" "$OUTPUT"
  exit 1
fi

echo "=== SHA256 ==="
sha256sum "$OUTPUT"

echo "COMPILATION JNI REPRODUCTIBLE : PASS"
