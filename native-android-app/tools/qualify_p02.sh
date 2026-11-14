#!/usr/bin/env bash
set -u -o pipefail

# Non-destructive P0/P0.2 qualification harness.
# It never connects to an ADB address, clears data, or touches the production packages.

EXPECTED_SHA="${EXPECTED_SHA:-cdcd27027f4b50f58acb093ee35b9293dda297ba}"
RUNS="${P02_RUNS:-10}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${P02_REPORT_DIR:-$ROOT/build/qualification/p02-$(date -u +%Y%m%dT%H%M%SZ)}"
GRADLE="$ROOT/../gradle-9.8/gradle-9.8.0/bin/gradle"
[ -x "$GRADLE" ] || GRADLE="$ROOT/gradlew"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ADB="${ADB:-${SDK:+$SDK/platform-tools/adb}}"
APKSIGNER="${APKSIGNER:-}"
AAPT2="${AAPT2:-}"

mkdir -p "$REPORT_DIR/logs"
exec > >(tee "$REPORT_DIR/qualification.log") 2>&1

fail() { echo "QUALIFICATION_FAIL: $*"; exit 1; }
blocked() { echo "PHYSICAL_TEST_BLOCKED: $*"; exit 2; }

echo "P02 qualification harness"
echo "expected_sha=$EXPECTED_SHA"
echo "report_dir=$REPORT_DIR"

cd "$ROOT" || fail "cannot enter Android project"
ACTUAL_SHA="$(git rev-parse HEAD 2>/dev/null)" || fail "not a Git worktree"
echo "actual_sha=$ACTUAL_SHA"
[ "$ACTUAL_SHA" = "$EXPECTED_SHA" ] || fail "unexpected Git SHA"

for tool in git sha256sum unzip; do command -v "$tool" >/dev/null || fail "missing tool: $tool"; done

if [ -z "$APKSIGNER" ] && [ -n "$SDK" ]; then
  APKSIGNER="$(find "$SDK/build-tools" -type f -name apksigner -print | sort -V | tail -1)"
fi
if [ -z "$AAPT2" ] && [ -n "$SDK" ]; then
  AAPT2="$(find "$SDK/build-tools" -type f -name aapt2 -print | sort -V | tail -1)"
fi
[ -x "$APKSIGNER" ] || fail "apksigner unavailable"
[ -x "$AAPT2" ] || fail "aapt2 unavailable"

APP_DEBUG="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
APP_P02="$ROOT/app/build/outputs/apk/p02Test/app-p02Test.apk"
TEST_P02="$ROOT/app/build/outputs/apk/androidTest/p02Test/app-p02Test-androidTest.apk"

check_apk() {
  local apk="$1" expected_package="$2"
  [ -f "$apk" ] || fail "missing APK: $apk"
  echo "sha256 $(sha256sum "$apk")"
  "$APKSIGNER" verify --verbose "$apk" > "$REPORT_DIR/$(basename "$apk").signature.txt" || fail "signature verification failed: $apk"
  rg -q 'Verified using v2 scheme .*true' "$REPORT_DIR/$(basename "$apk").signature.txt" || fail "APK v2 signature absent: $apk"
  "$AAPT2" dump badging "$apk" > "$REPORT_DIR/$(basename "$apk").badging.txt" || fail "cannot inspect APK: $apk"
  rg -q "package: name='$expected_package'" "$REPORT_DIR/$(basename "$apk").badging.txt" || fail "unexpected package in $apk"
}

check_apk "$APP_DEBUG" com.sentinel.quantum
check_apk "$APP_P02" com.sentinel.quantum.p02test
check_apk "$TEST_P02" com.sentinel.quantum.p02test.test

MANIFEST="$ROOT/app/build/intermediates/packaged_manifests/p02TestAndroidTest/processP02TestAndroidTestManifest/AndroidManifest.xml"
[ -f "$MANIFEST" ] || fail "packaged instrumentation manifest missing"
rg -q 'android:targetPackage="com.sentinel.quantum.p02test"' "$MANIFEST" || fail "instrumentation target is not isolated"
rg -q 'android:name="androidx.test.runner.AndroidJUnitRunner"' "$MANIFEST" || fail "unexpected instrumentation runner"

echo "test_dex_classes="
for dex in $(unzip -Z1 "$TEST_P02" | rg '^classes([0-9]+)?\.dex$' | sort -V); do
  unzip -p "$TEST_P02" "$dex" | strings | rg 'CallBlocklistStoreConcurrencyInstrumentationTest;|P02TestIsolationInstrumentationTest;' && echo "  $dex"
done

echo "adb_devices="
if [ -z "$ADB" ] || [ ! -x "$ADB" ]; then
  blocked "adb unavailable"
fi
"$ADB" devices -l | tee "$REPORT_DIR/adb-devices.txt"
DEVICE_COUNT="$("$ADB" devices | awk '$2 == "device" {count++} END {print count+0}')"
[ "$DEVICE_COUNT" -gt 0 ] || blocked "no online device; no LAN connection attempted"

echo "physical_runs=$RUNS"
for iteration in $(seq 1 "$RUNS"); do
  log="$REPORT_DIR/logs/run-${iteration}.log"
  echo "--- instrumentation run $iteration/$RUNS ---"
  if command -v timeout >/dev/null; then
    timeout "${P02_RUN_TIMEOUT_SECONDS:-900}" "$GRADLE" --offline --no-daemon --max-workers=1 \
      :app:connectedP02TestAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest \
      --console=plain 2>&1 | tee "$log"
  else
    "$GRADLE" --offline --no-daemon --max-workers=1 \
      :app:connectedP02TestAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest \
      --console=plain 2>&1 | tee "$log"
  fi
  status=${PIPESTATUS[0]}
  [ "$status" -eq 0 ] || fail "instrumentation run $iteration failed with status $status"
  rg -q 'BUILD SUCCESSFUL' "$log" || fail "missing successful Gradle result in run $iteration"
done

echo "PHYSICAL_TEST_PASS: $RUNS runs of the seven-test concurrency class completed"
echo "qualification_report=$REPORT_DIR/qualification.log"
