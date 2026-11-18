#!/usr/bin/env bash
# Runs an explicit restart qualification against an installed, isolated C11 validation package.
set -euo pipefail
umask 077

usage() {
    printf 'Usage: %s ADB_SERIAL process|reboot NEW_OUTPUT_DIRECTORY\n' "$0" >&2
    printf 'process stops only the C11 app. reboot restarts the selected Galaxy S24+ and waits for its owner to unlock it.\n' >&2
    exit 2
}

[[ $# == 3 ]] || usage
serial=$1
kind=$2
output=$3
[[ $serial =~ ^[[:alnum:]._:][-[:alnum:]._:]*$ && ( $kind == process || $kind == reboot ) ]] || usage

package=com.sentinel.quantum.aivalidation.c11
runner="$package.test/androidx.test.runner.AndroidJUnitRunner"
test_class=com.sentinel.quantum.ai.SentinelAiPersistenceQualificationTest
test_method=journalPersistenceFixtureProtocol
mkdir -m 700 -- "$output"
trap 'rc=$?; printf "%s\n" "$rc" > "$output/script.rc"' EXIT

fail() {
    printf '%s\n' "$*" >&2
    printf '%s\n' "$*" > "$output/result.txt"
    exit 1
}

capture() {
    local label=$1 rc=0
    shift
    "$@" > "$output/$label.stdout" 2> "$output/$label.stderr" || rc=$?
    printf '%s\n' "$rc" > "$output/$label.rc"
    return "$rc"
}

adb_on_device() {
    timeout --kill-after=3s 12s adb -s "$serial" "$@"
}

# Polling evidence records each attempt without creating a new set of files on every iteration.
sample() {
    local label=$1 result rc=0
    shift
    result=$(adb_on_device "$@" 2>> "$output/$label.stderr") || rc=$?
    result=$(tr -d '\r' <<< "$result")
    printf '%s\t%s\t%s\n' "$SECONDS" "$rc" "$result" >> "$output/$label.samples"
    printf '%s\n' "$result"
    return "$rc"
}

value() {
    tr -d '\r' < "$output/$1.stdout"
}

status_value() {
    awk -v prefix="INSTRUMENTATION_STATUS: cycle11_$2=" '
        { sub(/\r$/, "") }
        index($0, prefix) == 1 { count++; value = substr($0, length(prefix) + 1) }
        END { if (count != 1) exit 1; print value }
    ' "$output/$1.stdout"
}

require_status() {
    local actual
    actual=$(status_value "$1" "$2") || fail "FAIL: missing or duplicate $1 receipt field cycle11_$2."
    [[ $actual == "$3" ]] || fail "FAIL: invalid $1 receipt field cycle11_$2."
}

prepare_receipt_finished() {
    [[ -f "$output/prepare.stdout" ]] || return 1
    awk '
        { sub(/\r$/, "") }
        $0 == "INSTRUMENTATION_STATUS: cycle11_prepare_committed=true" { marker = 1 }
        marker && $0 == "INSTRUMENTATION_STATUS_CODE: 0" { complete = 1 }
        END { exit !complete }
    ' "$output/prepare.stdout"
}

instrument() {
    local phase=$1 seconds=$2
    timeout --kill-after=3s "$seconds" adb -s "$serial" shell am instrument -w -r --user "$android_user" \
        -e class "$test_class#$test_method" \
        -e sentinel_ai_restart_phase "$phase" \
        -e sentinel_ai_restart_kind "$kind" \
        -e sentinel_ai_run_id "$run_id" "$runner"
}

command -v adb >/dev/null || fail "BLOCKED: adb is unavailable on the host."
command -v timeout >/dev/null || fail "BLOCKED: GNU timeout is unavailable on the host."
if [[ -r /proc/sys/kernel/random/uuid ]]; then
    read -r run_id < /proc/sys/kernel/random/uuid
else
    run_id=$(uuidgen | tr '[:upper:]' '[:lower:]') || fail "BLOCKED: no host UUID generator is available."
fi
[[ $run_id =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]] ||
    fail "BLOCKED: a canonical run UUID is unavailable."
printf '%s\n' "$run_id" > "$output/run-id.txt"
printf 'serial=%s\nkind=%s\npackage=%s\nrunner=%s\n' "$serial" "$kind" "$package" "$runner" > "$output/scope.txt"

capture device-state adb_on_device get-state && [[ $(value device-state) == device ]] ||
    fail "BLOCKED: the selected ADB device is unavailable or unauthorized."
capture model adb_on_device shell getprop ro.product.model &&
    [[ $(value model) =~ ^SM-S926[A-Z0-9]*$ ]] ||
    fail "BLOCKED: the selected device does not identify as a Galaxy S24+ (SM-S926 family)."
capture android-user adb_on_device shell am get-current-user || fail "BLOCKED: current Android user unavailable."
android_user=$(value android-user)
[[ $android_user =~ ^(0|[1-9][0-9]*)$ ]] || fail "BLOCKED: Android user ID is invalid."
capture pre-unlock adb_on_device shell am get-started-user-state "$android_user" &&
    [[ $(value pre-unlock) == RUNNING_UNLOCKED ]] || fail "BLOCKED: unlock the selected Android user."
capture package adb_on_device shell pm path --user "$android_user" "$package" &&
    grep -q '^package:' "$output/package.stdout" || fail "BLOCKED: isolated C11 app is not installed."
capture pre-boot adb_on_device shell settings get global boot_count || fail "BLOCKED: boot count unavailable."
old_boot=$(value pre-boot)
[[ $old_boot =~ ^(0|[1-9][0-9]{0,9})$ ]] || fail "BLOCKED: boot count is invalid."

printf 'C11 %s qualification, run %s on %s. Evidence: %s\n' "$kind" "$run_id" "$serial" "$output"
capture prepare instrument prepare 210s &
prepare_job=$!
deadline=$((SECONDS + 45))
until prepare_receipt_finished; do
    [[ ! -f "$output/prepare.rc" ]] || fail "FAIL: prepare exited before its committed receipt."
    (( SECONDS < deadline )) || fail "BLOCKED: prepare did not produce a committed receipt in time."
    sleep 0.2
done
require_status prepare phase prepare
require_status prepare run_id "$run_id"
require_status prepare restart_kind "$kind"
require_status prepare sequence 1
require_status prepare prepare_committed true
require_status prepare restart_verified false
require_status prepare reboot_verified false
require_status prepare boot_count "$old_boot"
old_pid=$(status_value prepare pid) || fail "FAIL: prepare PID is unavailable."
old_start=$(status_value prepare start_ticks) || fail "FAIL: prepare start ticks are unavailable."
[[ $old_pid =~ ^[1-9][0-9]*$ && $old_start =~ ^[1-9][0-9]*$ ]] ||
    fail "FAIL: prepare process identity is invalid."
capture prepared-active-pid adb_on_device shell pidof "$package" &&
    [[ $(value prepared-active-pid) == "$old_pid" && ! -f "$output/prepare.rc" ]] ||
    fail "FAIL: the committed prepare PID is not the active C11 process."

if [[ $kind == process ]]; then
    # Recheck the prepared PID on the device immediately before force-stop.
    printf -v stop_command 'test "$(pidof %s)" = "%s" && am force-stop --user %s %s' \
        "$package" "$old_pid" "$android_user" "$package"
    capture force-stop adb_on_device shell "$stop_command" || fail "FAIL: the C11 force-stop was not confirmed."
    deadline=$((SECONDS + 15))
    absent=false
    while (( SECONDS < deadline )); do
        if pid=$(sample stopped-pid shell "pidof $package || [ \"\$?\" -eq 1 ]") && [[ -z $pid ]]; then
            absent=true
            break
        fi
        sleep 0.3
    done
    [[ $absent == true ]] || fail "FAIL: the active C11 process did not disappear after force-stop."
else
    capture reboot adb_on_device reboot || fail "BLOCKED: the selected S24+ did not accept the reboot command."
    printf 'Unlock the selected Galaxy S24+ after it restarts. Waiting on ADB serial %s.\n' "$serial"
    deadline=$((SECONDS + 300))
    boot_ready=false
    while (( SECONDS < deadline )); do
        if state=$(sample reboot-device get-state) && [[ $state == device ]] &&
            complete=$(sample reboot-ready shell getprop sys.boot_completed) && [[ $complete == 1 ]] &&
            boot=$(sample reboot-boot-count shell settings get global boot_count) &&
            [[ $boot =~ ^(0|[1-9][0-9]{0,9})$ ]] && (( boot > old_boot )) &&
            user=$(sample reboot-user shell am get-current-user) && [[ $user == "$android_user" ]] &&
            unlock=$(sample reboot-unlock shell am get-started-user-state "$android_user") &&
            [[ $unlock == RUNNING_UNLOCKED ]]; then
            boot_ready=true
            break
        fi
        sleep 1
    done
    [[ $boot_ready == true ]] || fail "BLOCKED: reboot, return of the same ADB device and owner unlock were not all observed."
fi

# A force-stop or reboot is expected to interrupt the prepare instrumentation.
deadline=$((SECONDS + 15))
until [[ -f "$output/prepare.rc" ]]; do
    (( SECONDS < deadline )) || fail "BLOCKED: the prepare instrumentation did not stop."
    sleep 0.2
done
wait "$prepare_job" || true
if grep -Eq 'C11 prepare timed out|^OK \(' "$output/prepare.stdout"; then
    fail "FAIL: prepare ended without the requested explicit restart."
fi

capture post-boot adb_on_device shell settings get global boot_count || fail "BLOCKED: boot count unavailable after restart."
new_boot=$(value post-boot)
[[ $new_boot =~ ^(0|[1-9][0-9]{0,9})$ ]] || fail "FAIL: post-restart boot count is invalid."
if [[ $kind == process ]]; then
    [[ $new_boot == "$old_boot" ]] || fail "FAIL: boot count changed during process-only qualification."
else
    (( new_boot > old_boot )) || fail "FAIL: boot count did not advance after the reboot."
fi

capture verify instrument verify 120s || fail "FAIL: verify instrumentation command failed."
tr -d '\r' < "$output/verify.stdout" > "$output/verify-normalized.stdout"
grep -qx 'OK (1 test)' "$output/verify-normalized.stdout" &&
    grep -qx 'INSTRUMENTATION_CODE: -1' "$output/verify-normalized.stdout" &&
    grep -qx "INSTRUMENTATION_STATUS: class=$test_class" "$output/verify-normalized.stdout" &&
    grep -qx "INSTRUMENTATION_STATUS: test=$test_method" "$output/verify-normalized.stdout" ||
    fail "FAIL: expected one successful JUnit test from the C11 persistence scope."
if grep -Eq '^INSTRUMENTATION_(FAILED|ABORTED):|^INSTRUMENTATION_STATUS_CODE: -' "$output/verify-normalized.stdout"; then
    fail "FAIL: verify instrumentation reported a failed, skipped or aborted test."
fi
require_status verify phase verify
require_status verify run_id "$run_id"
require_status verify restart_kind "$kind"
require_status verify sequence 2
require_status verify prepare_committed false
require_status verify boot_count "$new_boot"
new_pid=$(status_value verify pid) || fail "FAIL: verify PID is unavailable."
new_start=$(status_value verify start_ticks) || fail "FAIL: verify start ticks are unavailable."
[[ $new_pid =~ ^[1-9][0-9]*$ && $new_start =~ ^[1-9][0-9]*$ ]] ||
    fail "FAIL: verify process identity is invalid."
if [[ $kind == process ]]; then
    require_status verify restart_verified true
    require_status verify reboot_verified false
    [[ $old_pid != "$new_pid" || $old_start != "$new_start" ]] ||
        fail "FAIL: the process identity did not change."
else
    require_status verify restart_verified false
    require_status verify reboot_verified true
fi

printf 'PASS: C11 %s restart, run %s, sequence 2.\n' "$kind" "$run_id" | tee "$output/result.txt"
