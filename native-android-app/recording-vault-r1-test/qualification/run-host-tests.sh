#!/usr/bin/env bash
set -euo pipefail
module=$(cd "$(dirname "$0")/.." && pwd)
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
"${KOTLINC:-kotlinc}" -version
java -version
"${KOTLINC:-kotlinc}" "${VAULT_SOURCE_DIR:-$module/src/main/java/com/sentinel/quantum/security}"/*.kt \
  "$module"/qualification/host/*.kt -include-runtime -d "$build/host-tests.jar"
test -n "${JAVA_HOME:-}" || { echo 'Set JAVA_HOME to a JDK with JNI headers'; exit 2; }
"${CC:-cc}" -std=c11 -D_GNU_SOURCE -Wall -Wextra -Werror -O2 -fPIC -shared \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$module/src/main/cpp/rename_noreplace.c" -o "$build/librecordingvault_r3.so"
export LD_LIBRARY_PATH="$build${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
java -Xmx512m -cp "$build/host-tests.jar" com.sentinel.quantum.security.HostTestsKt
