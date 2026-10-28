#!/usr/bin/env bash
set -euo pipefail
module=$(cd "$(dirname "$0")/.." && pwd)
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
"${KOTLINC:-kotlinc}" -version
java -version
"${KOTLINC:-kotlinc}" "${VAULT_SOURCE_DIR:-$module/src/main/java/com/sentinel/quantum/security}"/*.kt \
  "$module"/qualification/host/*.kt -include-runtime -d "$build/host-tests.jar"
java -Xmx512m -cp "$build/host-tests.jar" com.sentinel.quantum.security.HostTestsKt
