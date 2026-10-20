#!/usr/bin/env bash
# Portable, fail-closed build qualification on a supported Linux x86-64 host.
# This is NOT an emulator, physical-device, or production release certificate.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
LOGDIR="${SENTINEL_EVIDENCE_DIR:-$ROOT/.sentinel-local-evidence}"
mkdir -p "$LOGDIR"
LOGDIR="$(cd "$LOGDIR" && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN="$LOGDIR/$STAMP"
mkdir -p "$RUN"
printf 'FAIL\n' > "$RUN/verdict.txt"
echo "Repository: $ROOT"
echo "Evidence: $RUN"

fail() { echo "BLOCKED: $*" >&2; exit 1; }
[[ "$(uname -s)" == Linux ]] || fail "Linux required"
[[ "$(uname -m)" == x86_64 ]] || fail "Official Android build tools require Linux x86-64; refusing an unproven AAPT2 override"
command -v java >/dev/null || fail "Java missing"
command -v node >/dev/null || fail "Node.js missing"
command -v npm >/dev/null || fail "npm missing"
command -v sha256sum >/dev/null || fail "sha256sum missing"
[[ -d "${ANDROID_HOME:-}" ]] || fail "ANDROID_HOME must point to installed SDK"
[[ -f native-android-app/gradlew ]] || fail "Missing canonical Android Gradle wrapper"
[[ -d node_modules ]] || fail "Install locked root dependencies with npm ci first"

git rev-parse HEAD > "$RUN/commit.txt"
uname -a > "$RUN/host.txt"
java -version > "$RUN/java-version.txt" 2>&1
node --version > "$RUN/node-version.txt"
npm --version > "$RUN/npm-version.txt"

step() {
  local label="$1"; shift
  echo "== $label =="
  "$@" > "$RUN/$label.log" 2>&1 || {
    tail -n 80 "$RUN/$label.log" >&2
    fail "$label failed; see $RUN/$label.log"
  }
}

step manifest node scripts/check-android-manifest.js
step product-truth npm run test:android-product-truth
step ui-consistency node scripts/check-android-ui-consistency.js

cd native-android-app
step unit-tests ./gradlew testDebugUnitTest --no-daemon --console=plain
step lint ./gradlew lintDebug --no-daemon --console=plain
step inventory ./gradlew :app:exportReleaseDependencyInventory --no-daemon --console=plain
test -s app/build/reports/release-dependencies.json || fail "Missing release dependency inventory"
step debug-apk ./gradlew assembleDebug --no-daemon --console=plain
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
[[ -n "$APK" && -s "$APK" ]] || fail "Missing debug APK"
TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
[[ -n "$TOOLS" ]] || fail "Android build-tools missing"
for exe in aapt zipalign apksigner; do [[ -x "$TOOLS/$exe" ]] || fail "$exe missing"; done
PACKAGE="$("$TOOLS/aapt" dump badging "$APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n1)"
[[ "$PACKAGE" == com.sentinel.quantum ]] || fail "Unexpected APK package: $PACKAGE"
step alignment "$TOOLS/zipalign" -c -P 16 -v 4 "$APK"
step signature "$TOOLS/apksigner" verify --verbose --print-certs "$APK"
step apk-secrets node ../scripts/check-android-apk-secrets.js "$APK"
sha256sum "$APK" > "$RUN/apk-sha256.txt"
cp app/build/reports/release-dependencies.json "$RUN/release-dependencies.json"
printf 'HOST_BUILD_VERIFIED_ONLY\n' > "$RUN/verdict.txt"
echo "Host build verified. Emulator/device qualification NOT performed."
echo "Evidence directory: $RUN"
