#!/usr/bin/env bash
set -u -o pipefail

# Non-destructive P0/P0.2 qualification harness.
# It never connects to an ADB address, clears data, or touches the production packages.

EXPECTED_SHA="${P02_EXPECTED_SHA:-}"
RUNS="${P02_RUNS:-10}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="${P02_REPORT_DIR:-$ROOT/build/qualification/p02-$(date -u +%Y%m%dT%H%M%SZ)}"
GRADLE="${P02_GRADLE:-$ROOT/../gradle-9.8/gradle-9.8.0/bin/gradle}"
[ -x "$GRADLE" ] || GRADLE="$ROOT/gradlew"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ADB="${P02_ADB:-${ADB:-${SDK:+$SDK/platform-tools/adb}}}"
APKSIGNER="${APKSIGNER:-}"
AAPT2="${AAPT2:-}"
RESULTS_ROOT="${P02_RESULTS_ROOT:-$ROOT/app/build/outputs/androidTest-results/connected/p02Test}"

REQUIRED_COMMITS=(
  92d1c916ebbdf393166fcf2fd904894ebee15661
  51b62dbc95409acf5bee92e441942e37b691473b
  ba4b2b4a2cbdabe33675dba7de0ff20f0cdeddb7
  a0a6a9b0728b112397b855796e63fc912f187980
  87d17a67050404834863c8cff9c89337e8436618
  5a78620ee28dc959bf5871734a7ac5a4744edec7
  cbda5481203f35616bb3c617f3943ff205df3fd7
  5815594d5a30182892db9649498f4a951634dcdf
)

EXPECTED_DEBUG_SHA="${P02_DEBUG_APK_SHA256:-ad2c1cb6ddde1780900ca772eac27f9d50c99e13c146726eadbbb088d5daf9b4}"
EXPECTED_P02_SHA="${P02_APP_APK_SHA256:-320c03d697a534681c128844c29deed820204045b382c972336de3e5cc9b2a75}"
EXPECTED_TEST_SHA="${P02_TEST_APK_SHA256:-ad256269b817f85ebc52fb828f68b560856a34ff3c08e5947603c9ea5cb731da}"

mkdir -p "$REPORT_DIR/logs"
exec > >(tee "$REPORT_DIR/qualification.log") 2>&1

fail() { echo "QUALIFICATION_FAIL: $*"; exit 1; }
blocked() { echo "PHYSICAL_TEST_BLOCKED: $*"; exit 2; }

echo "P02 qualification harness"
echo "expected_sha=${EXPECTED_SHA:-approved-lineage}"
echo "report_dir=$REPORT_DIR"

cd "$ROOT" || fail "cannot enter Android project"
ACTUAL_SHA="$(git rev-parse HEAD 2>/dev/null)" || fail "not a Git worktree"
echo "actual_sha=$ACTUAL_SHA"
if [ -n "$EXPECTED_SHA" ] && [ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]; then
  fail "unexpected Git SHA"
fi
for required_commit in "${REQUIRED_COMMITS[@]}"; do
  git merge-base --is-ancestor "$required_commit" HEAD || \
    fail "required P0/P0.2 commit is not an ancestor: $required_commit"
done

for tool in git sha256sum unzip strings rg python3; do command -v "$tool" >/dev/null || fail "missing tool: $tool"; done

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
  local apk="$1" expected_package="$2" expected_sha="$3"
  [ -f "$apk" ] || fail "missing APK: $apk"
  local actual_sha
  actual_sha="$(sha256sum "$apk" | awk '{print $1}')"
  echo "sha256 $actual_sha $apk"
  [ "$actual_sha" = "$expected_sha" ] || fail "unexpected APK SHA-256: $apk"
  "$APKSIGNER" verify --verbose "$apk" > "$REPORT_DIR/$(basename "$apk").signature.txt" || fail "signature verification failed: $apk"
  rg -q 'Verified using v2 scheme .*true' "$REPORT_DIR/$(basename "$apk").signature.txt" || fail "APK v2 signature absent: $apk"
  "$AAPT2" dump badging "$apk" > "$REPORT_DIR/$(basename "$apk").badging.txt" || fail "cannot inspect APK: $apk"
  rg -q "package: name='$expected_package'" "$REPORT_DIR/$(basename "$apk").badging.txt" || fail "unexpected package in $apk"
}

check_apk "$APP_DEBUG" com.sentinel.quantum "$EXPECTED_DEBUG_SHA"
check_apk "$APP_P02" com.sentinel.quantum.p02test "$EXPECTED_P02_SHA"
check_apk "$TEST_P02" com.sentinel.quantum.p02test.test "$EXPECTED_TEST_SHA"

MANIFEST="$ROOT/app/build/intermediates/packaged_manifests/p02TestAndroidTest/processP02TestAndroidTestManifest/AndroidManifest.xml"
[ -f "$MANIFEST" ] || fail "packaged instrumentation manifest missing"
rg -q 'android:targetPackage="com.sentinel.quantum.p02test"' "$MANIFEST" || fail "instrumentation target is not isolated"
rg -q 'android:name="androidx.test.runner.AndroidJUnitRunner"' "$MANIFEST" || fail "unexpected instrumentation runner"

echo "test_dex_classes="
found_concurrency=0
found_isolation=0
for dex in $(unzip -Z1 "$TEST_P02" | rg '^classes([0-9]+)?\.dex$' | sort -V); do
  if unzip -p "$TEST_P02" "$dex" | strings | rg 'CallBlocklistStoreConcurrencyInstrumentationTest;' >/dev/null; then
    found_concurrency=1
    echo "  $dex: CallBlocklistStoreConcurrencyInstrumentationTest"
  fi
  if unzip -p "$TEST_P02" "$dex" | strings | rg 'P02TestIsolationInstrumentationTest;' >/dev/null; then
    found_isolation=1
    echo "  $dex: P02TestIsolationInstrumentationTest"
  fi
done
[ "$found_concurrency" -eq 1 ] || fail "concurrency instrumentation class absent from DEX"
[ "$found_isolation" -eq 1 ] || fail "isolation instrumentation class absent from DEX"

echo "adb_devices="
if [ -z "$ADB" ] || [ ! -x "$ADB" ]; then
  blocked "adb unavailable"
fi
"$ADB" devices -l | tee "$REPORT_DIR/adb-devices.txt"
DEVICE_SERIAL="${P02_SERIAL:-}"
mapfile -t ONLINE_DEVICES < <("$ADB" devices | awk '$2 == "device" {print $1}')
if [ -z "$DEVICE_SERIAL" ]; then
  [ "${#ONLINE_DEVICES[@]}" -eq 1 ] || blocked "expected one online device or P02_SERIAL; no LAN connection attempted"
  DEVICE_SERIAL="${ONLINE_DEVICES[0]}"
else
  printf '%s\n' "${ONLINE_DEVICES[@]}" | rg -qxF "$DEVICE_SERIAL" || blocked "P02_SERIAL is not online"
fi
echo "selected_device=$DEVICE_SERIAL"

echo "physical_runs=$RUNS"
for iteration in $(seq 1 "$RUNS"); do
  log="$REPORT_DIR/logs/run-${iteration}.log"
  run_report="$REPORT_DIR/junit/run-${iteration}"
  mkdir -p "$run_report"
  marker="$run_report.started"
  touch "$marker"
  echo "--- instrumentation run $iteration/$RUNS ---"
  if command -v timeout >/dev/null; then
    ANDROID_SERIAL="$DEVICE_SERIAL" timeout "${P02_RUN_TIMEOUT_SECONDS:-900}" "$GRADLE" --offline --no-daemon --max-workers=1 --rerun-tasks \
      :app:connectedP02TestAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest \
      --console=plain 2>&1 | tee "$log"
  else
    ANDROID_SERIAL="$DEVICE_SERIAL" "$GRADLE" --offline --no-daemon --max-workers=1 --rerun-tasks \
      :app:connectedP02TestAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.sentinel.quantum.security.CallBlocklistStoreConcurrencyInstrumentationTest \
      --console=plain 2>&1 | tee "$log"
  fi
  status=${PIPESTATUS[0]}
  [ "$status" -eq 0 ] || fail "instrumentation run $iteration failed with status $status"
  rg -q 'BUILD SUCCESSFUL' "$log" || fail "missing successful Gradle result in run $iteration"
  # connectedAndroidTest may rebuild its dependencies; prove the installed inputs
  # still match the approved artifacts after Gradle returns.
  check_apk "$APP_DEBUG" com.sentinel.quantum "$EXPECTED_DEBUG_SHA"
  check_apk "$APP_P02" com.sentinel.quantum.p02test "$EXPECTED_P02_SHA"
  check_apk "$TEST_P02" com.sentinel.quantum.p02test.test "$EXPECTED_TEST_SHA"
  mapfile -t junit_files < <(find "$RESULTS_ROOT" \
    -type f -name '*.xml' -newer "$marker" 2>/dev/null | sort)
  [ "${#junit_files[@]}" -gt 0 ] || fail "no fresh JUnit report for run $iteration"
  report_args=()
  for junit_file in "${junit_files[@]}"; do
    cp "$junit_file" "$run_report/$(basename "$junit_file")"
    report_args+=("$run_report/$(basename "$junit_file")")
  done
  python3 "$ROOT/tools/p02_junit_validator.py" --expected-tests 7 "${report_args[@]}" \
    | tee "$run_report/summary.txt" || fail "JUnit validation failed for run $iteration"
done

echo "PHYSICAL_TEST_PASS: $RUNS runs of the seven-test concurrency class completed"
echo "qualification_report=$REPORT_DIR/qualification.log"
