#!/usr/bin/env bash
# Offline APK intake check for Termux or Linux; never grants Android runtime qualification.
set -euo pipefail
usage() { echo "Usage: bash check-apk.sh PATH_TO_APK [EXPECTED_FULL_SHA256]" >&2; exit 2; }
[[ $# -ge 1 && $# -le 2 ]] || usage
apk="$1"
[[ -f "$apk" && -s "$apk" ]] || { echo 'FAIL: APK missing or empty' >&2; exit 1; }
command -v sha256sum >/dev/null || { echo 'FAIL: sha256sum unavailable' >&2; exit 1; }
if [[ $# -eq 2 ]]; then
  expected="${2,,}"
  [[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo 'FAIL: expected SHA-256 must have 64 hexadecimal characters' >&2; exit 1; }
fi
actual="$(sha256sum "$apk" | cut -d ' ' -f 1)"
[[ "$actual" =~ ^[0-9a-f]{64}$ ]] || { echo 'FAIL: local SHA-256 calculation malformed' >&2; exit 1; }
bytes="$(wc -c < "$apk" | tr -d '[:space:]')"
magic="$(od -An -tx1 -N4 "$apk" | tr -d '[:space:]')"
[[ "$magic" == 504b0304 ]] || { echo 'FAIL: not a standard ZIP/APK header' >&2; exit 1; }
if command -v unzip >/dev/null; then
  unzip -tqq "$apk" >/dev/null || { echo 'FAIL: ZIP archive failed integrity test' >&2; exit 1; }
  unzip -Z1 "$apk" | grep -Fx 'AndroidManifest.xml' >/dev/null || { echo 'FAIL: AndroidManifest.xml not found' >&2; exit 1; }
else
  echo 'LIMITED: unzip unavailable; ZIP entries not validated' >&2
fi
if [[ $# -eq 2 && "$actual" != "$expected" ]]; then
  printf 'FAIL: SHA-256 mismatch\nExpected: %s\nActual:   %s\n' "$expected" "$actual" >&2
  exit 1
fi
if command -v apksigner >/dev/null; then
  apksigner verify --verbose --print-certs "$apk" || { echo 'FAIL: APK signature verification' >&2; exit 1; }
  signature_status=VERIFIED
else
  signature_status=NOT_CHECKED
fi
printf 'LOCAL_APK_INTAKE_OK (not Android runtime qualification)\nFile: %s\nBytes: %s\nSHA-256: %s\nSignature: %s\n' "$apk" "$bytes" "$actual" "$signature_status"
