#!/usr/bin/env bash
set -u

fail=0
need() {
  if command -v "$1" >/dev/null 2>&1; then
    printf 'FOUND %s: %s\n' "$1" "$(command -v "$1")"
  else
    printf 'MISSING %s\n' "$1" >&2
    fail=1
  fi
}

printf 'ARCH=%s\n' "$(uname -m)"
if [ "$(uname -m)" != "aarch64" ] && [ "$(uname -m)" != "arm64" ]; then
  printf 'UNSUPPORTED_ARCH_EXPECTED_ARM64\n' >&2
  fail=1
fi
need git
need cmake
need clang
need make
need sha256sum

if [ -n "${ANDROID_ROOT:-}" ]; then
  printf 'ANDROID_ROOT=%s\n' "$ANDROID_ROOT"
else
  printf 'ANDROID_ROOT=NOT_SET (not required for native Termux build)\n'
fi

if [ "$fail" -ne 0 ]; then
  exit 2
fi
printf 'TERMUX_HOST_CHECK_OK\n'
