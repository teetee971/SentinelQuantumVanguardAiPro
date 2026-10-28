#!/usr/bin/env bash
# Run on a host or Termux already paired to the isolated Android test device.
set -euo pipefail
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the adb wireless connection serial}"
module=$(cd "$(dirname "$0")/.." && pwd)
adb_bin=${ADB:-adb}
run=${VAULT_RUN:-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')}
[[ $run =~ ^[a-f0-9]{32}$ ]] || { echo 'Invalid VAULT_RUN'; exit 2; }
output=${1:-"$module/qualification/device-$run"}
mkdir -p "$output"
pkg=com.sentinel.quantum.r1cryptotest
runner=$pkg.test/androidx.test.runner.AndroidJUnitRunner
class=com.sentinel.quantum.security.RecordingVaultPersistenceInstrumentedTest
adb_cmd() { "$adb_bin" -s "$ANDROID_SERIAL" "$@"; }
exec > >(tee "$output/protocol.log") 2>&1
printf 'UTC=%s RUN=%s SERIAL=%s\n' "$(date -u +%FT%TZ)" "$run" "$ANDROID_SERIAL"
adb_cmd get-state
for prop in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.fingerprint; do
  printf '%s=' "$prop"; adb_cmd shell getprop "$prop"
done
# Always install the exact hashed APKs while retaining data and the existing key.
# A signing mismatch fails; never recover by uninstalling or clearing application data.
app_apk=${APP_APK:-"$module/build/outputs/apk/debug/recording-vault-r1-test-debug.apk"}
test_apk=${TEST_APK:-"$module/build/outputs/apk/androidTest/debug/recording-vault-r1-test-debug-androidTest.apk"}
sha256sum "$app_apk" "$test_apk"
adb_cmd install -r -t "$app_apk"
adb_cmd install -r -t "$test_apk"
assert_junit() {
  local log=$1 count=$2
  if ! grep -Eq "OK \\($count tests?\\)" "$log" ||
     grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -[234]|Process crashed' "$log"; then
    echo "FAILED: instrumentation did not prove $count executed successful tests: $log"
    exit 1
  fi
}
phase() {
  local name=$1
  adb_cmd shell am instrument -w -r -e class "$class" -e vaultRun "$run" -e vaultPhase "$name" "$runner" | tee "$output/$name.log"
  assert_junit "$output/$name.log" 1
}
force_stop() {
  adb_cmd shell am force-stop "$pkg"
  local remaining
  remaining=$(adb_cmd shell "pidof $pkg || true" | tr -d '\r')
  printf 'After force-stop pidof=%s\n' "$remaining"
  [[ -z $remaining ]] || { echo 'Process still present'; exit 1; }
}
adb_cmd logcat -v threadtime -T 1 -s SentinelR3:I SentinelR1KeyAudit:I '*:S' > "$output/logcat.log" 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true' EXIT
classes='RecordingVaultCryptoInstrumentedTest,RecordingVaultKeySecurityInstrumentedTest,RecordingVaultStoreInstrumentedTest,RecordingVaultSecurityInstrumentedTest,RecordingVaultConcurrencyInstrumentedTest,RecordingVaultRecoveryInstrumentedTest,RecordingVaultR3InstrumentedTest,RecordingVaultProcessConcurrencyInstrumentedTest'
classes=$(printf '%s' "$classes" | sed 's/\(^\|,\)/\1com.sentinel.quantum.security./g')
adb_cmd shell am instrument -w -r -e class "$classes" "$runner" | tee "$output/regression.log"
assert_junit "$output/regression.log" 21
phase write
force_stop
adb_cmd shell am instrument -w -r -e class "$class" -e vaultRun "$run" -e vaultPhase stageInterruptedWrite "$runner" > "$output/interrupted.log" 2>&1 &
stage_pid=$!
ready=0
for ((i=0; i<120; i++)); do
  if grep -q "INTERRUPT_READY run=$run " "$output/logcat.log"; then ready=1; break; fi
  sleep 0.25
done
if [[ $ready != 1 ]]; then
  force_stop
  wait "$stage_pid" || true
  echo 'FAILED: no staged-write readiness evidence; logs/artifacts retained'
  exit 1
fi
force_stop
set +e
wait "$stage_pid"
stage_exit=$?
set -e
printf 'Interrupted instrumentation shell exit=%s (expected interruption, never counted PASS)\n' "$stage_exit"
phase verify
phase cleanup
printf 'PROTOCOL_COMPLETED run=%s; inspect logs and device identity before physical qualification\n' "$run"
