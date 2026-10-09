#!/usr/bin/env bash
# Emulator-only proof that ROLE_SMS loss may kill Sentinel while a proven pre-transport SMS
# provider mutation remains durably recoverable. This flow never invokes SmsManager.
set -euo pipefail

OUTPUT_DIR="${1:?ROLE_SMS process-death evidence directory required}"
mkdir -p "$OUTPUT_DIR"
PACKAGE="com.sentinel.quantum"
ROLE="android.app.role.SMS"
CLASS="com.sentinel.quantum.security.SmsRoleLossRecoveryInstrumentationTest"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

if [[ ! "$API" =~ ^[0-9]+$ || "$API" -lt 29 ]]; then
  echo "ROLE_SMS process-death proof requires Android 10 / API 29+."
  exit 2
fi

role_holders() {
  local direct=""
  local status=0
  set +e
  direct="$(adb shell cmd role get-role-holders --user 0 "$ROLE" 2>&1 | tr -d '\r')"
  status=$?
  set -e
  if [[ "$status" -eq 0 && "$direct" != *"Unknown command"* ]]; then
    if ! grep -Evq '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$|^$' <<< "$direct"; then
      printf '%s\n' "$direct"
      return 0
    fi
  fi
  local dump="$OUTPUT_DIR/role-state-current.txt"
  adb shell dumpsys role > "$dump"
  python3 "$SCRIPT_DIR/phone-core-emulator-role-holders.py" "$ROLE" "$dump"
}

wait_role_state() {
  local expected="$1"
  local evidence="$2"
  for _ in $(seq 1 30); do
    role_holders > "$OUTPUT_DIR/$evidence" 2>&1 || true
    if [[ "$expected" == "held" ]] && grep -Fxq "$PACKAGE" "$OUTPUT_DIR/$evidence"; then
      return 0
    fi
    if [[ "$expected" == "absent" ]] && ! grep -Fxq "$PACKAGE" "$OUTPUT_DIR/$evidence"; then
      return 0
    fi
    sleep 0.2
  done
  echo "ROLE_SMS did not reach expected state: $expected"
  cat "$OUTPUT_DIR/$evidence" || true
  return 1
}

RUNNER="$(
  adb shell pm list instrumentation | tr -d '\r' |
    awk '/^instrumentation:com\.sentinel\.quantum\.test\// {
      sub(/^instrumentation:/, "")
      sub(/ .*/, "")
      print
      exit
    }'
)"
if [[ -z "$RUNNER" ]]; then
  echo "Sentinel AndroidJUnitRunner is not installed."
  adb shell pm list instrumentation || true
  exit 1
fi
printf '%s\n' "$RUNNER" > "$OUTPUT_DIR/instrumentation-runner.txt"

run_phase() {
  local method="$1"
  local phase="$2"
  local evidence="$OUTPUT_DIR/role-sms-${phase}-instrumentation.txt"
  set +e
  adb shell am instrument -w -r \
    -e class "$CLASS#$method" \
    -e sentinel_role_phase "$phase" \
    "$RUNNER" | tr -d '\r' | tee "$evidence"
  local status=${PIPESTATUS[0]}
  set -e
  if [[ "$status" -ne 0 ]]; then
    echo "ROLE_SMS phase $phase instrumentation command failed with status $status."
    return "$status"
  fi
  if grep -Eq '^FAILURES!!!|^INSTRUMENTATION_FAILED:|Process crashed' "$evidence"; then
    echo "ROLE_SMS phase $phase reported a test failure or crash."
    return 1
  fi
  if ! grep -Eq '^OK \(1 test\)$' "$evidence"; then
    echo "ROLE_SMS phase $phase did not report exactly one passing test."
    return 1
  fi
}

# Establish an isolated authorized state. The fixture stops at PROVIDER_READY and never enters
# SmsManager, so this qualification cannot emit a carrier SMS.
adb shell cmd role add-role-holder --user 0 "$ROLE" "$PACKAGE"
wait_role_state held role-sms-initial-held.txt
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS
adb shell pm grant "$PACKAGE" android.permission.READ_PHONE_STATE
adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS allow
adb shell appops set --user 0 "$PACKAGE" SEND_SMS allow

run_phase prepareProviderReadyFixture prepare

# Keep a real target process alive so role loss semantics are observable independently of JUnit.
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$PACKAGE/.MainActivity" > "$OUTPUT_DIR/role-sms-process-before-removal-start.txt"
for _ in $(seq 1 30); do
  BEFORE_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | xargs || true)"
  [[ -n "$BEFORE_PID" ]] && break
  sleep 0.2
done
if [[ -z "${BEFORE_PID:-}" ]]; then
  echo "Sentinel process was not alive before ROLE_SMS removal."
  exit 1
fi
printf '%s\n' "$BEFORE_PID" > "$OUTPUT_DIR/role-sms-pid-before-removal.txt"

adb shell cmd role remove-role-holder --user 0 "$ROLE" "$PACKAGE"
wait_role_state absent role-sms-after-removal.txt

PROCESS_INVALIDATED=0
for _ in $(seq 1 40); do
  AFTER_PID="$(adb shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' | xargs || true)"
  if [[ -z "$AFTER_PID" || "$AFTER_PID" != "$BEFORE_PID" ]]; then
    PROCESS_INVALIDATED=1
    break
  fi
  sleep 0.2
done
printf '%s\n' "${AFTER_PID:-}" > "$OUTPUT_DIR/role-sms-pid-after-removal.txt"
if [[ "$PROCESS_INVALIDATED" != "1" ]]; then
  echo "ROLE_SMS removal did not invalidate the pre-removal Sentinel process."
  exit 1
fi
printf 'process_invalidated=1\n' > "$OUTPUT_DIR/role-sms-process-death-proof.txt"

# Fresh instrumentation while the role is absent proves that the pre-transport journal survived
# the killed process and was not falsely advanced to TRANSPORT_STARTED.
run_phase roleLossPreservesProviderReadyFixture role_absent
wait_role_state absent role-sms-still-absent-after-proof.txt

adb shell cmd role add-role-holder --user 0 "$ROLE" "$PACKAGE"
wait_role_state held role-sms-restored.txt
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS
adb shell pm grant "$PACKAGE" android.permission.READ_PHONE_STATE
adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS allow
adb shell appops set --user 0 "$PACKAGE" SEND_SMS allow

# A third process observes startup recovery after role restoration. The product worker must turn the
# pending provider row into FAILED and clear only the proven pre-transport journal record.
run_phase recoverAfterRoleRestoration recover

cat > "$OUTPUT_DIR/role-sms-process-death-summary.txt" <<EOF
api=$API
role_removed=true
process_invalidated=true
provider_ready_survived_role_loss=true
role_restored=true
provider_recovery_verified=true
transport_invoked=false
EOF
