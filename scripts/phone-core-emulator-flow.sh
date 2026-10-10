#!/usr/bin/env bash
# Synthetic communications on the isolated CI emulator only. No physical certification.
set -Eeuo pipefail
FLOW_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
FLOW_OUTPUT_DIR="${1:?Screenshot output directory required}"
mkdir -p "$FLOW_OUTPUT_DIR"
FLOW_PACKAGE="com.sentinel.quantum"
FLOW_NUMBER="5550100"
FLOW_SMS_NUMBER="+15550123"
FLOW_XML="$FLOW_OUTPUT_DIR/window.xml"
ADB_COMMAND_TIMEOUT_SECONDS="${ADB_COMMAND_TIMEOUT_SECONDS:-30}"
ADB_COMMAND_KILL_GRACE_SECONDS="${ADB_COMMAND_KILL_GRACE_SECONDS:-5}"
FLOW_DEVICE_STATE_MUTATED=false
ORIGINAL_USER_ROTATION=""
ORIGINAL_ACCELEROMETER_ROTATION=""
ORIGINAL_WIFI_ON=""
ORIGINAL_MOBILE_DATA=""
MOBILE_DATA_ORACLE_SOURCE=""
RESTORE_FAILED=false
FLOW_FAILURE_TRAP_ACTIVE=false
FLOW_FAILURE_REPORTED=false
FLOW_FAILURE_TRAP_SUSPENDED=false
FLOW_LAST_ADB_ARGS=""
if [[ ! "$ADB_COMMAND_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] ||
  [[ ! "$ADB_COMMAND_KILL_GRACE_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
  echo "ADB command watchdog values must be positive integer seconds." >&2
  exit 2
fi
adb() {
  FLOW_LAST_ADB_ARGS="$(printf '%q ' "$@")"
  command timeout \
    --signal=INT \
    --kill-after="${ADB_COMMAND_KILL_GRACE_SECONDS}s" \
    "${ADB_COMMAND_TIMEOUT_SECONDS}s" \
    adb "$@"
}
flow_failure_diagnostics() {
  local status="$1"
  local failed_command="$2"
  local failed_line="$3"
  local failed_adb_args="$4"
  if [[ "$FLOW_FAILURE_REPORTED" == true ]]; then
    return 0
  fi
  FLOW_FAILURE_REPORTED=true
  # Diagnostics must never replace the original failure or recurse through ERR.
  trap - ERR
  set +e
  {
    printf 'status=%s\nfailed_line=%s\nfailed_command=%s\nadb_args=%s\n' \
      "$status" "$failed_line" "$failed_command" "$failed_adb_args"
    printf '\nadb_get_state_status='; adb get-state
    printf '\nadb_devices_status='; adb devices -l
    printf '\nlogcat_brief_status='; adb logcat -d -v brief
    printf '\nactivity_status='; adb shell dumpsys activity activities
    printf '\ntelecom_status='; adb shell dumpsys telecom
    printf '\nrole_status='; adb shell dumpsys role
  } > "$FLOW_OUTPUT_DIR/flow-failure.txt" 2>&1
  printf 'Phone Core flow unexpected shell failure: status=%s line=%s command=%s adb_args=%s; full diagnostics=%s\n' \
    "$status" "$failed_line" "$failed_command" "$failed_adb_args" \
    "$FLOW_OUTPUT_DIR/flow-failure.txt" >&2
  set -e
  trap 'flow_err_trap "$?" "$BASH_COMMAND" "${BASH_LINENO[0]:-unknown}"' ERR
  return 0
}
flow_err_trap() {
  local status="$1"
  local failed_command="$2"
  local failed_line="$3"
  local failed_adb_args="$FLOW_LAST_ADB_ARGS"
  if [[ "$FLOW_FAILURE_TRAP_ACTIVE" == true && "$FLOW_FAILURE_TRAP_SUSPENDED" != true ]]; then
    flow_failure_diagnostics "$status" "$failed_command" "$failed_line" "$failed_adb_args"
  fi
  return "$status"
}
trap 'flow_err_trap "$?" "$BASH_COMMAND" "${BASH_LINENO[0]:-unknown}"' ERR
FLOW_API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

wait_for_online_adb() {
  local reason="$1"
  local state=""
  local status=0
  for _ in $(seq 1 20); do
    FLOW_FAILURE_TRAP_SUSPENDED=true
    set +e
    state="$(adb get-state 2>&1)"
    status=$?
    set -e
    FLOW_FAILURE_TRAP_SUSPENDED=false
    if [[ "$status" -eq 0 && "$state" == device ]]; then
      return 0
    fi
    sleep 1
  done
  echo "ADB device did not return online after ${reason}." >&2
  FLOW_FAILURE_TRAP_SUSPENDED=true
  set +e
  adb get-state >&2
  adb devices -l >&2
  set -e
  FLOW_FAILURE_TRAP_SUSPENDED=false
  return 1
}

read_mobile_data_state() {
  local mobile_data_oracle=""
  if mobile_data_oracle="$(adb shell cmd phone get-data-enabled 2>/dev/null | tr -d '\r')"; then
    case "$mobile_data_oracle" in
      true|1)
        printf '1 cmd_phone\n'
        return 0
        ;;
      false|0)
        printf '0 cmd_phone\n'
        return 0
        ;;
    esac
  fi
  if mobile_data_oracle="$(adb shell settings get global mobile_data 2>/dev/null | tr -d '\r')"; then
    case "$mobile_data_oracle" in
      0|1)
        printf '%s settings_global\n' "$mobile_data_oracle"
        return 0
        ;;
    esac
  fi
  # Some API 36/37 emulator images have no subscription-backed data state. This is not
  # permission to guess: report the capability as LIMITED and leave mobile data untouched.
  printf 'UNAVAILABLE unavailable\n'
  return 2
}

capture_original_device_state() {
  local mobile_data_reading=""
  if ! ORIGINAL_USER_ROTATION="$(adb shell settings get system user_rotation | tr -d '\r')"; then
    echo "Failed to read original user rotation state." >&2
    return 1
  fi
  if ! ORIGINAL_ACCELEROMETER_ROTATION="$(adb shell settings get system accelerometer_rotation | tr -d '\r')"; then
    echo "Failed to read original accelerometer rotation state." >&2
    return 1
  fi
  if ! ORIGINAL_WIFI_ON="$(adb shell settings get global wifi_on | tr -d '\r')"; then
    echo "Failed to read original Wi-Fi state." >&2
    return 1
  fi
  if mobile_data_reading="$(read_mobile_data_state)"; then
    :
  elif [[ "$mobile_data_reading" != "UNAVAILABLE unavailable" ]]; then
    echo "Unable to read Android mobile-data state from supported fail-closed oracles." >&2
    return 1
  fi
  read -r ORIGINAL_MOBILE_DATA MOBILE_DATA_ORACLE_SOURCE <<< "$mobile_data_reading"
  if ! [[ "$ORIGINAL_USER_ROTATION" =~ ^[0-9]+$ ]]; then
    echo "Invalid original user rotation state: $ORIGINAL_USER_ROTATION" >&2
    return 1
  fi
  if ! [[ "$ORIGINAL_ACCELEROMETER_ROTATION" =~ ^[01]$ ]]; then
    echo "Invalid original accelerometer rotation state: $ORIGINAL_ACCELEROMETER_ROTATION" >&2
    return 1
  fi
  if ! [[ "$ORIGINAL_WIFI_ON" =~ ^[01]$ ]]; then
    echo "Invalid original Wi-Fi state: $ORIGINAL_WIFI_ON" >&2
    return 1
  fi
  if ! [[ "$ORIGINAL_MOBILE_DATA" == UNAVAILABLE || "$ORIGINAL_MOBILE_DATA" =~ ^[01]$ ]]; then
    echo "Invalid original mobile-data state: $ORIGINAL_MOBILE_DATA" >&2
    return 1
  fi
}

restore_device_state() {
  if [[ "$FLOW_DEVICE_STATE_MUTATED" != true ]]; then
    return 0
  fi
  RESTORE_FAILED=false
  wait_for_restore_device() {
    local state=""
    local status=0
    for _ in $(seq 1 20); do
      FLOW_FAILURE_TRAP_SUSPENDED=true
      set +e
      state="$(adb get-state 2>&1)"
      status=$?
      set -e
      FLOW_FAILURE_TRAP_SUSPENDED=false
      if [[ "$status" -eq 0 && "$state" == device ]]; then
        return 0
      fi
      sleep 1
    done
    return 1
  }
  restore_adb_diagnostics() {
    local device_state=""
    local devices=""
    local device_state_status=0
    local devices_status=0
    FLOW_FAILURE_TRAP_SUSPENDED=true
    set +e
    device_state="$(adb get-state 2>&1)"
    device_state_status=$?
    devices="$(adb devices -l 2>&1)"
    devices_status=$?
    set -e
    FLOW_FAILURE_TRAP_SUSPENDED=false
    printf 'restore_adb_get_state_status=%s\nrestore_adb_get_state=%s\nrestore_adb_devices_status=%s\nrestore_adb_devices=%s\n' \
      "$device_state_status" "$device_state" "$devices_status" "$devices" >&2
  }
  mark_restore_failure() {
    RESTORE_FAILED=true
    echo "$1" >&2
    if [[ "$RESTORE_FAILED" == true && "${RESTORE_DIAGNOSTICS_WRITTEN:-false}" != true ]]; then
      RESTORE_DIAGNOSTICS_WRITTEN=true
      restore_adb_diagnostics
    fi
  }
  if ! wait_for_restore_device; then
    RESTORE_DIAGNOSTICS_WRITTEN=true
    restore_adb_diagnostics
    echo "Emulator did not return to an online ADB device state before restoration." >&2
    return 1
  fi
  if ! adb shell settings put system user_rotation "$ORIGINAL_USER_ROTATION" >/dev/null 2>&1; then
    mark_restore_failure "Failed to restore user rotation state."
  fi
  if ! adb shell settings put system accelerometer_rotation "$ORIGINAL_ACCELEROMETER_ROTATION" >/dev/null 2>&1; then
    mark_restore_failure "Failed to restore accelerometer rotation state."
  fi
  if [[ "$ORIGINAL_WIFI_ON" == 1 ]]; then
    if ! adb shell svc wifi enable >/dev/null 2>&1; then
      mark_restore_failure "Failed to restore Wi-Fi state."
    fi
  else
    if ! adb shell svc wifi disable >/dev/null 2>&1; then
      mark_restore_failure "Failed to restore Wi-Fi state."
    fi
  fi
  if [[ "$ORIGINAL_MOBILE_DATA" != UNAVAILABLE ]]; then
    if [[ "$ORIGINAL_MOBILE_DATA" == 1 ]]; then
      if ! adb shell svc data enable >/dev/null 2>&1; then
        mark_restore_failure "Failed to restore mobile-data state."
      fi
    else
      if ! adb shell svc data disable >/dev/null 2>&1; then
        mark_restore_failure "Failed to restore mobile-data state."
      fi
    fi
  fi
  if [[ "$RESTORE_FAILED" == true ]]; then
    echo "Failed to restore one or more emulator settings." >&2
    return 1
  fi
}
on_exit() {
  local status=$?
  if ! restore_device_state; then
    status=1
  fi
  exit "$status"
}
trap on_exit EXIT

role_holders() {
  local full_role="$1"
  local direct_output=""
  local direct_status=0
  FLOW_FAILURE_TRAP_SUSPENDED=true
  set +e
  direct_output="$(adb shell cmd role get-role-holders --user 0 "$full_role" 2>&1 | tr -d '\r')"
  direct_status=$?
  set -e
  FLOW_FAILURE_TRAP_SUSPENDED=false
  if [[ "$direct_status" -eq 0 && "$direct_output" != *"Unknown command"* ]]; then
    # Accept only a holder list, never shell diagnostics containing the package name.
    if ! grep -Evq '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$|^$' <<< "$direct_output"; then
      printf '%s\n' "$direct_output"
      return 0
    fi
  fi

  # Android 10/API 29 can manage roles with `cmd role`, but its shell command does not expose
  # get-role-holders. Persist the dump for the shared, fail-closed user-0 parser.
  local role_dump="$FLOW_OUTPUT_DIR/role-state-current.txt"
  if ! adb shell dumpsys role > "$role_dump" 2>/dev/null; then
    return 1
  fi
  python3 "$(dirname "${BASH_SOURCE[0]}")/phone-core-emulator-role-holders.py" "$full_role" "$role_dump"
}

wait_role_held() {
  local full_role="$1"
  local evidence="$2"
  for _ in $(seq 1 20); do
    role_holders "$full_role" > "$FLOW_OUTPUT_DIR/$evidence" 2>&1 || true
    if grep -Fxq "$FLOW_PACKAGE" "$FLOW_OUTPUT_DIR/$evidence"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Android role $full_role was not stably held by $FLOW_PACKAGE."
  cat "$FLOW_OUTPUT_DIR/$evidence" 2>/dev/null || true
  adb shell dumpsys role > "$FLOW_OUTPUT_DIR/${evidence%.txt}-dumpsys.txt" 2>&1 || true
  return 1
}

for FLOW_ROLE in DIALER SMS; do
  adb shell cmd role add-role-holder --user 0 "android.app.role.$FLOW_ROLE" "$FLOW_PACKAGE"
  wait_role_held "android.app.role.$FLOW_ROLE" "role-${FLOW_ROLE,,}-held.txt"
done
# Android 10+ exposes ROLE_CALL_SCREENING. The runtime matrix starts at API 29 for Phone Core, so
# role ownership is a required precondition rather than an optional best-effort shell command.
adb shell cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$FLOW_PACKAGE"
wait_role_held android.app.role.CALL_SCREENING "role-call-screening-held.txt"

for FLOW_PERMISSION in CALL_PHONE READ_PHONE_STATE READ_CONTACTS READ_CALL_LOG SEND_SMS READ_SMS RECEIVE_SMS RECEIVE_MMS RECEIVE_WAP_PUSH; do
  adb shell pm grant "$FLOW_PACKAGE" "android.permission.$FLOW_PERMISSION"
done
if [[ "$FLOW_API" -ge 33 ]]; then
  adb shell pm grant "$FLOW_PACKAGE" android.permission.POST_NOTIFICATIONS
fi

fresh_ui() {
  adb shell rm -f /sdcard/sentinel-flow.xml
  adb shell uiautomator dump --compressed /sdcard/sentinel-flow.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/sentinel-flow.xml > "$FLOW_XML"
  test -s "$FLOW_XML"
}
wait_text() {
  local expected="$*"
  for _ in $(seq 1 15); do
    if fresh_ui && python3 - "$FLOW_XML" "$@" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = ET.parse(sys.argv[1]).iter('node')
needles = sys.argv[2:]
def matches(n, needle):
    if needle.startswith('phone_core_'):
        return n.get('resource-id') == needle and n.get('package') == 'com.sentinel.quantum'
    return needle in (n.get('text', '') + ' ' + n.get('content-desc', '') + ' ' + n.get('hint', ''))
sys.exit(0 if any(any(matches(n, needle) for needle in needles) for n in nodes) else 1)
PY
    then return 0; fi
    sleep 1
  done
  adb exec-out screencap -p > "$FLOW_OUTPUT_DIR/failure.png" || true
  echo "Phone Core flow did not expose expected UI: $expected"
  return 1
}
incoming_surface_visible() {
  python3 - "$FLOW_XML" "$FLOW_PACKAGE" <<'PYINCOMING'
import sys, xml.etree.ElementTree as ET
path, package_name = sys.argv[1:]
try:
    nodes = list(ET.parse(path).iter('node'))
except Exception:
    sys.exit(1)
owned = [n for n in nodes if n.get('package') == package_name]
text = ' '.join(
    (n.get('text', '') + ' ' + n.get('content-desc', '') + ' ' + n.get('hint', '')).strip()
    for n in owned
)
# API 29 can render the caller identity in a separate system surface and omit the
# synthetic number from Sentinel's app-owned InCall UI. Ownership and call identity
# are already independently established by this app's timeline and Answer control.
sentinel_surface = (
    'Appel autorisé' in text or
    ('Appel entrant' in text and 'Sonnerie' in text)
)
sys.exit(0 if sentinel_surface else 1)
PYINCOMING
}

wait_incoming_sentinel_surface() {
  for _ in $(seq 1 20); do
    if fresh_ui && incoming_surface_visible; then return 0; fi
    sleep 1
  done
  capture failure
  echo "Sentinel did not expose an app-owned incoming-call surface for $FLOW_NUMBER."
  return 1
}
wait_logcat_marker() {
  local marker="$1"
  local evidence="$2"
  for _ in $(seq 1 20); do
    adb logcat -d -v brief > "$FLOW_OUTPUT_DIR/$evidence" 2>/dev/null || true
    if grep -Fq "$marker" "$FLOW_OUTPUT_DIR/$evidence"; then return 0; fi
    sleep 0.5
  done
  echo "Expected PII-free Android lifecycle marker was not observed: $marker"
  return 1
}
wait_emulator_call_absent() {
  local number="$1"
  local evidence="$FLOW_OUTPUT_DIR/gsm-list-${number}.txt"
  local modem_status=0
  local telecom_evidence="$FLOW_OUTPUT_DIR/telecom-after-${number}.txt"
  for _ in $(seq 1 30); do
    FLOW_FAILURE_TRAP_SUSPENDED=true
    set +e
    adb emu gsm list > "$evidence" 2>&1
    modem_status=$?
    set -e
    FLOW_FAILURE_TRAP_SUSPENDED=false
    if [[ "$modem_status" -eq 0 ]] && ! grep -Fq "$number" "$evidence" &&
      adb shell dumpsys telecom > "$telecom_evidence" 2>&1 &&
      python3 "$FLOW_SCRIPT_DIR/phone-core-emulator-telecom-calls.py" "$telecom_evidence"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Emulator modem could not prove call $number absent after bounded teardown wait (last gsm-list status: $modem_status)."
  cat "$evidence" 2>/dev/null || true
  return 1
}
tap_text() {
  fresh_ui
  local coordinates
  coordinates="$(python3 - "$FLOW_XML" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter('node'):
    needle = sys.argv[2]
    matched = (node.get('resource-id') == needle and node.get('package') == 'com.sentinel.quantum') if needle.startswith('phone_core_') else needle in (node.get('text', '') + ' ' + node.get('content-desc', '') + ' ' + node.get('hint', ''))
    if matched:
        match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            if x2 > x1 and y2 > y1:
                print((x1+x2)//2, (y1+y2)//2)
                sys.exit(0)
sys.exit(1)
PY
)"
  read -r FLOW_X FLOW_Y <<< "$coordinates"
  adb shell input tap "$FLOW_X" "$FLOW_Y"
}
open_incoming_call_notification() {
  # The InCall activity is intentionally non-exported. Open its real notification
  # PendingIntent as a user would; never relax the manifest or launch it as shell UID.
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard
  adb shell cmd statusbar expand-notifications
  for _ in $(seq 1 20); do
    local coordinates=""
    if fresh_ui && coordinates="$(python3 - "$FLOW_XML" "$FLOW_NUMBER" <<'PYNOTIFICATION'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
parents = {child: parent for parent in root.iter() for child in parent}
rows = {}
for node in root.iter('node'):
    if node.get('package') != 'com.android.systemui' or node.get('resource-id') not in ('android:id/text', 'android:id/title'):
        continue
    row = parents.get(node)
    while row is not None and row.get('clickable') != 'true':
        row = parents.get(row)
    if row is None or row.get('package') != 'com.android.systemui':
        continue
    names = [n.get('text') for n in row.iter('node') if n.get('package') == 'com.android.systemui' and n.get('resource-id') == 'android:id/app_name_text']
    titles = [n.get('text', '') for n in row.iter('node') if n.get('package') == 'com.android.systemui' and n.get('resource-id') == 'android:id/title']
    # API 29 omits the row resource ID; API 37's compact CallStyle omits app_name.
    # Use the nearest clickable SystemUI notification and require a unique match.
    # A visible app name must match Sentinel; when absent the exact synthetic caller
    # identifies only a navigation candidate. Sentinel's own answer resource ID and
    # InCall ACTIVE are still mandatory afterwards: this never proves answer/ownership.
    if names:
        if names != ['Sentinel Quantum Vanguard']:
            continue
    elif not any(t == sys.argv[2] or t.startswith(sys.argv[2] + ' · ') for t in titles):
        continue
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    row_match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', row.get('bounds', ''))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        if x2 > x1 and y2 > y1:
            # Prefer the body text when CallStyle exposes it. API 36 may expose only
            # app_name_text; tapping that header can expand the notification without
            # invoking its content PendingIntent, so use the clickable row center as
            # the fallback target for that compact layout.
            if row_match:
                rx1, ry1, rx2, ry2 = map(int, row_match.groups())
                row_center = ((rx1+rx2)//2, (ry1+ry2)//2)
            else:
                row_center = ((x1+x2)//2, (y1+y2)//2)
            candidate = ((x1+x2)//2, (y1+y2)//2) if node.get('resource-id') == 'android:id/text' else row_center
            if row not in rows or node.get('resource-id') == 'android:id/text':
                rows[row] = candidate
if len(rows) == 1:
    print(*next(iter(rows.values())))
    sys.exit(0)
sys.exit(1)
PYNOTIFICATION
)"; then
      local x y
      read -r x y <<< "$coordinates"
      adb shell input tap "$x" "$y"
      printf 'mode=SYSTEMUI_NOTIFICATION_PENDING_INTENT\n' > "$FLOW_OUTPUT_DIR/incoming-call-entrypoint.txt"
      return 0
    fi
    if [[ "$FLOW_API" -ge 37 ]] && fresh_ui && incoming_surface_visible; then
      {
        printf 'mode=FULL_SCREEN_CALLSTYLE\n'
        printf 'notification_event=CALL_NOTIFICATION_POSTED\n'
        printf 'uiautomator_systemui_row=not_exposed_while_full_screen_surface_visible\n'
      } > "$FLOW_OUTPUT_DIR/incoming-call-entrypoint.txt"
      return 0
    fi
    sleep 1
  done
  capture failure
  echo "Sentinel incoming-call notification could not be observed and opened."
  return 1
}

wait_reply_focus() {
  for _ in $(seq 1 15); do
    if fresh_ui && python3 - "$FLOW_XML" <<'PYFOCUS'
import sys, xml.etree.ElementTree as ET
sys.exit(0 if any(n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true'
                  for n in ET.parse(sys.argv[1]).iter('node')) else 1)
PYFOCUS
    then return 0; fi
    sleep 1
  done
  capture failure
  echo "Inline reply input did not receive focus."
  return 1
}
private_timeline_has_event() {
  local direction="$1"
  local signal="$2"
  local kind="${3:-CALL}"
  local evidence="${4:-$FLOW_OUTPUT_DIR/phone-private-timeline-${direction,,}-${signal,,}.xml}"
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$evidence" 2>/dev/null && \
    python3 - "$evidence" "$direction" "$signal" "$kind" <<'PYTIMELINE'
import json, sys, xml.etree.ElementTree as ET
path, direction, signal, kind = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
    node = next((n for n in root.findall('string') if n.get('name') == 'events'), None)
    events = json.loads((node.text if node is not None else '') or '[]')
except Exception:
    sys.exit(1)
matched = any(
    event.get('kind') == kind and
    event.get('direction') == direction and
    event.get('signal') == signal
    for event in events
)
sys.exit(0 if matched else 1)
PYTIMELINE
}
wait_private_timeline_event() {
  local direction="$1"
  local signal="$2"
  local kind="${3:-CALL}"
  local evidence="$FLOW_OUTPUT_DIR/phone-private-timeline-${direction,,}-${signal,,}.xml"
  for _ in $(seq 1 20); do
    if private_timeline_has_event "$direction" "$signal" "$kind" "$evidence"; then
      return 0
    fi
    sleep 1
  done
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$evidence" 2>/dev/null || true
  echo "Phone Core private timeline did not record $direction/$signal."
  return 1
}
wait_private_timeline_signal_prefix() {
  local prefix="$1"
  local evidence="$FLOW_OUTPUT_DIR/phone-private-timeline-prefix.xml"
  for _ in $(seq 1 20); do
    if adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$evidence" 2>/dev/null && \
      python3 - "$evidence" "$prefix" <<'PYPREFIX'
import json, sys, xml.etree.ElementTree as ET
path, prefix = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
    node = next((n for n in root.findall('string') if n.get('name') == 'events'), None)
    events = json.loads((node.text if node is not None else '') or '[]')
except Exception:
    sys.exit(1)
matched = any(
    event.get('kind') == 'CALL' and
    event.get('direction') == 'INCOMING' and
    str(event.get('signal') or '').startswith(prefix)
    for event in events
)
sys.exit(0 if matched else 1)
PYPREFIX
    then return 0; fi
    sleep 1
  done
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$evidence" 2>/dev/null || true
  echo "Phone Core private timeline did not record incoming signal prefix $prefix."
  return 1
}
capture() { adb exec-out screencap -p > "$FLOW_OUTPUT_DIR/$1.png"; }

assert_no_crash_or_anr() {
  local evidence="$1"
  local logcat_status=1
  for _ in $(seq 1 5); do
    wait_for_online_adb "the crash and ANR logcat capture"
    FLOW_FAILURE_TRAP_SUSPENDED=true
    set +e
    adb logcat -d -v brief > "$FLOW_OUTPUT_DIR/$evidence"
    logcat_status=$?
    set -e
    FLOW_FAILURE_TRAP_SUSPENDED=false
    if [[ "$logcat_status" -eq 0 ]]; then
      break
    fi
    sleep 1
  done
  if [[ "$logcat_status" -ne 0 ]]; then
    echo "ADB logcat could not be read after bounded online-device retries (status: $logcat_status)." >&2
    return "$logcat_status"
  fi
  if grep -Eq 'FATAL EXCEPTION:|ANR in com\.sentinel\.quantum' "$FLOW_OUTPUT_DIR/$evidence"; then
    echo "Crash/ANR detected during emulator stability qualification; see $evidence." >&2
    return 1
  fi
}

read_process_ids() {
  local process_ids=""
  local pid_status=0
  local device_state=""
  local device_status=0
  FLOW_FAILURE_TRAP_SUSPENDED=true
  set +e
  process_ids="$(adb shell pidof "$FLOW_PACKAGE" | tr -d '\r')"
  pid_status=$?
  set -e
  FLOW_FAILURE_TRAP_SUSPENDED=false
  if [[ "$pid_status" -eq 0 ]]; then
    printf '%s\n' "$process_ids"
    return 0
  fi
  if [[ "$pid_status" -eq 1 && -z "$process_ids" ]]; then
    FLOW_FAILURE_TRAP_SUSPENDED=true
    set +e
    device_state="$(adb get-state 2>/dev/null | tr -d '\r')"
    device_status=$?
    set -e
    FLOW_FAILURE_TRAP_SUSPENDED=false
    if [[ "$device_status" -eq 0 && "$device_state" == device ]]; then
      return 0
    fi
  fi
  echo "Unable to read process state (pidof status: $pid_status, device status: $device_status)." >&2
  return 1
}

run_stability_qualification() {
  local pid_before=""
  local pid_after=""
  local wifi_state=""
  local mobile_state=""
  local mobile_data_reading=""
  local stability_verdict="AUTOMATED"
  local mobile_data_disable_observed="true"
  local mobile_data_limitation="none"

  capture_original_device_state
  FLOW_DEVICE_STATE_MUTATED=true

  # Offline is an exercised runtime state, not a label. Both transport controls must accept the
  # transition and their resulting platform settings are archived before the app is relaunched.
  if ! adb shell svc wifi disable; then
    echo "Failed to disable Wi-Fi for offline qualification." >&2
    return 1
  fi
  if ! wifi_state="$(adb shell settings get global wifi_on | tr -d '\r')"; then
    echo "Failed to read Wi-Fi state after offline transition." >&2
    return 1
  fi
  if [[ "$ORIGINAL_MOBILE_DATA" != UNAVAILABLE ]]; then
    if ! adb shell svc data disable; then
      echo "Failed to disable mobile data for offline qualification." >&2
      return 1
    fi
    mobile_data_reading="$(read_mobile_data_state)" || {
      echo "Mobile-data state became unreadable after the controlled disable; refusing an incomplete offline proof." >&2
      return 1
    }
    read -r mobile_state MOBILE_DATA_ORACLE_SOURCE <<< "$mobile_data_reading"
  else
    mobile_state="UNAVAILABLE"
    MOBILE_DATA_ORACLE_SOURCE="unavailable"
    stability_verdict="LIMITED"
    mobile_data_disable_observed="false"
    mobile_data_limitation="oracle_unavailable"
  fi
  if [[ "$mobile_state" != UNAVAILABLE && "$mobile_state" != "0" ]]; then
    stability_verdict="LIMITED"
    mobile_data_disable_observed="false"
    mobile_data_limitation="disable_not_observed"
  fi
  printf 'wifi_on=%s\nmobile_data=%s\nmobile_data_oracle=%s\nmobile_data_disable_observed=%s\nmobile_data_limitation=%s\nverdict=%s\n' \
    "$wifi_state" "$mobile_state" "$MOBILE_DATA_ORACLE_SOURCE" \
    "$mobile_data_disable_observed" "$mobile_data_limitation" "$stability_verdict" \
    > "$FLOW_OUTPUT_DIR/stability-offline-state.txt"
  if [[ "$wifi_state" != "0" ]]; then
    echo "Wi-Fi did not reach the disabled state: $wifi_state" >&2
    return 1
  fi
  adb shell am force-stop "$FLOW_PACKAGE"
  adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity" > "$FLOW_OUTPUT_DIR/stability-offline-launch.txt"
  wait_for_online_adb "the offline qualification relaunch"
  wait_text "phone_core_tab_0"
  capture stability-offline
  assert_no_crash_or_anr stability-offline-logcat.txt

  # Lock a real display rotation, observe the platform state, and re-read the app UI after the
  # configuration change. The original orientation is restored before Telecom probes begin.
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  local rotation_state="$(adb shell settings get system user_rotation | tr -d '\r')"
  printf 'user_rotation=%s\n' "$rotation_state" > "$FLOW_OUTPUT_DIR/stability-rotation-state.txt"
  [[ "$rotation_state" == "1" ]]
  wait_for_online_adb "the rotation transition"
  wait_text "phone_core_tab_0"
  capture stability-rotation
  assert_no_crash_or_anr stability-rotation-logcat.txt

  # Force process death outside the app and require a fresh process to render the same surface.
  pid_before="$(read_process_ids)"
  if ! [[ "$pid_before" =~ ^[0-9]+([[:space:]][0-9]+)*$ ]]; then
    echo "Unable to identify running process before restart: $pid_before" >&2
    return 1
  fi
  # The host drives process death through ActivityManager. Direct shell signals are rejected
  # by newer Android images even for this app's own UID.
  adb shell am force-stop "$FLOW_PACKAGE"
  wait_for_online_adb "the process-death transition"
  for _ in $(seq 1 15); do
    pid_after="$(read_process_ids)"
    [[ -z "$pid_after" ]] && break
    sleep 0.5
  done
  if [[ -n "$pid_after" ]]; then
    echo "Process remained alive after host force-stop: $pid_after" >&2
    return 1
  fi
  adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity" > "$FLOW_OUTPUT_DIR/stability-kill-restart-launch.txt"
  wait_for_online_adb "the process restart"
  wait_text "phone_core_tab_0"
  pid_after="$(read_process_ids)"
  if ! [[ "$pid_after" =~ ^[0-9]+([[:space:]][0-9]+)*$ ]]; then
    echo "Process did not restart after host force-stop: $pid_after" >&2
    return 1
  fi
  printf 'pid_before=%s\npid_after=%s\n' "$pid_before" "$pid_after" > "$FLOW_OUTPUT_DIR/stability-kill-restart.txt"
  capture stability-kill-restart
  assert_no_crash_or_anr stability-kill-restart-logcat.txt

  # Telecom/GSM probes must run in the original connectivity state. Keeping Wi-Fi/mobile data
  # disabled until the EXIT trap made the emulator modem reject the Android 16 synthetic call
  # before any app-owned Phone Core evidence could be produced. Restoration is still fail-closed.
  if ! restore_device_state; then
    echo "Failed to restore emulator state before Phone Core telecom probes." >&2
    return 1
  fi
  FLOW_DEVICE_STATE_MUTATED=false
}

# This is the first application launch after the workflow's fresh APK install. Exercise a second
# process launch as well so cold_install_and_relaunch is a real per-lane proof, not report metadata.
FLOW_FAILURE_TRAP_ACTIVE=true
adb shell am force-stop "$FLOW_PACKAGE"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "phone_core_tab_0"
capture 01-dialer-first-launch
adb shell am force-stop "$FLOW_PACKAGE"
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "phone_core_tab_0"
capture 01b-dialer-relaunch
run_stability_qualification

if ! adb emu gsm call "$FLOW_NUMBER"; then
  echo "The emulator rejected the synthetic incoming-call command." >&2
  adb get-state >&2 || true
  adb devices -l >&2 || true
  exit 1
fi
# First prove Telecom actually bound Sentinel's screening service. This marker contains no number or
# identity. Android 10 can fail emergency-number classification on an emulator even after callback
# invocation, so CALL_SCREENED:* remains a stricter, separate rule-engine-decision proof.
wait_logcat_marker "CallScreeningService:onScreenCall" "call-screening-callback-logcat.txt"
wait_logcat_marker "CallScreeningService:response_elapsed_ms=" "call-screening-latency-logcat.txt"
wait_logcat_marker "CallScreeningService:response_sent=true" "call-screening-response-sent-logcat.txt"
if [[ "$FLOW_API" -ge 36 ]]; then
  wait_private_timeline_signal_prefix "CALL_SCREENED:"
else
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml \
    > "$FLOW_OUTPUT_DIR/phone-private-timeline-api${FLOW_API}-screening.xml" 2>/dev/null || true
fi
capture 02-incoming-call
# CallerIdActivity can appear before Telecom finishes both screening callbacks on API 29.
# Accepting at that point races Telecom's later SET_RINGING and loses the ACTIVE transition.
# This event is recorded by InCallService only after it receives the ringing call and posts
# its notification; keep the later INCALL_ACTIVE assertion as the independent answer proof.
wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"
# Exercise the notification path while the device display is asleep. The emulator accepts the GSM
# command reliably while awake, but the app-owned notification must still survive the sleep boundary.
if ! adb shell input keyevent KEYCODE_SLEEP; then
  echo "Failed to put the emulator to sleep before opening the incoming-call notification." >&2
  exit 1
fi
wait_for_online_adb "the sleep transition before opening the incoming-call notification"
# Exercise Sentinel's answer path, not a modem-side answer on behalf of the application.
# An app-owned stable control plus the independent ACTIVE timeline event proves the effect.
open_incoming_call_notification
wait_incoming_sentinel_surface
wait_text "phone_core_answer"
tap_text "phone_core_answer"
wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"
capture 03-incoming-active-evidence
adb emu gsm cancel "$FLOW_NUMBER"
# The emulator modem is the lifecycle authority for this synthetic call. A localized UI string is
# not a teardown oracle and can disappear before or after Telecom finishes releasing the account.
wait_emulator_call_absent "$FLOW_NUMBER"
capture 04-ended-call

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

# Place exactly once. Timeline persistence is asynchronous: retrying placement while waiting for
# INCALL_ACTIVE can create a second call and leave the first ACTIVE after the UI hangs up the second.
# Wait within the existing bounded observation window; never change production SIM authorization.
FLOW_OUTGOING_ACTIVE=0
FLOW_OUTGOING_EVIDENCE="$FLOW_OUTPUT_DIR/phone-private-timeline-outgoing-incall_active.xml"
adb shell am start -W -a android.intent.action.DIAL -d tel:5550101 -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "phone_core_call"
tap_text "phone_core_call"
for FLOW_ATTEMPT in $(seq 1 12); do
  for _ in $(seq 1 3); do
    # The emulator modem may require an explicit transition from dialing to active.
    adb emu gsm accept 5550101 >/dev/null 2>&1 || true
    if private_timeline_has_event "OUTGOING" "INCALL_ACTIVE" "CALL" "$FLOW_OUTGOING_EVIDENCE"; then
      FLOW_OUTGOING_ACTIVE=1
      break
    fi
    sleep 0.5
  done
  if [[ "$FLOW_OUTGOING_ACTIVE" == "1" ]]; then
    break
  fi
  sleep 0.5
done
if [[ "$FLOW_OUTGOING_ACTIVE" != "1" ]]; then
  capture failure
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$FLOW_OUTGOING_EVIDENCE" 2>/dev/null || true
  echo "Sentinel did not establish the outgoing emulator call within the bounded Telecom recovery window."
  exit 1
fi
# Preserve the canonical contract marker as a final assertion after the bounded recovery probe.
wait_private_timeline_event "OUTGOING" "INCALL_ACTIVE"
# Connected-state and app-owned in-call-surface proofs are separate: require both.
wait_private_timeline_event "LOCAL" "INCALL_UI_SHOWN"
wait_text "phone_core_hangup"
capture 05-outgoing-call
tap_text "phone_core_hangup"
wait_emulator_call_absent 5550101

adb shell am start -W -a android.intent.action.MAIN -n "$FLOW_PACKAGE/.SmsComposeActivity"
adb emu sms send "$FLOW_SMS_NUMBER" "Sentinel emulator reply test"
wait_text "Sentinel emulator reply test"
tap_text "Sentinel emulator reply test"
wait_text "phone_core_sms_reply"
capture 06-thread
tap_text "phone_core_sms_reply"
wait_reply_focus
adb shell input text ReplyFromSentinel
wait_text "ReplyFromSentinel"
tap_text "phone_core_sms_reply_send"
adb shell input keyevent KEYCODE_BACK
FLOW_REPLY_STORED=0
for _ in $(seq 1 15); do
  if fresh_ui && python3 - "$FLOW_XML" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = list(ET.parse(sys.argv[1]).iter('node'))
stored = any(n.get('text') == 'ReplyFromSentinel' and n.get('class') != 'android.widget.EditText' for n in nodes)
draft_remaining = any('ReplyFromSentinel' in n.get('text', '') and n.get('class') == 'android.widget.EditText' for n in nodes)
sys.exit(0 if stored and not draft_remaining else 1)
PY
  then FLOW_REPLY_STORED=1; break; fi
  sleep 1
done
if [[ "$FLOW_REPLY_STORED" != "1" ]]; then
  capture failure
  echo "Inline reply was not observed in the provider-backed conversation."
  exit 1
fi
capture 07-inline-reply
# Provider visibility alone is insufficient: require the real Android SENT callback.
# Recent emulator images also expose DELIVERED; this remains synthetic modem evidence.
wait_private_timeline_event "OUTGOING" "SMS_ALL_PARTS_SENT" "SMS"
if [[ "$FLOW_API" -ge 36 ]]; then
  wait_private_timeline_event "OUTGOING" "SMS_ALL_PARTS_DELIVERED" "SMS"
fi

for FLOW_ROLE in DIALER SMS CALL_SCREENING; do
  role_holders "android.app.role.$FLOW_ROLE" > "$FLOW_OUTPUT_DIR/role-${FLOW_ROLE,,}-final.txt" 2>&1 || true
  grep -Fxq "$FLOW_PACKAGE" "$FLOW_OUTPUT_DIR/role-${FLOW_ROLE,,}-final.txt"
done

echo "Synthetic Telecom callback/calls, cold relaunch, and inline SMS reply verified; physical validation remains pending."
