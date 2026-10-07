#!/usr/bin/env bash
# Framework-level role/authorization denial is executed outside the target app process so
# Android is free to kill/restart Sentinel exactly as it would on a real device.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:?Output directory required}"
mkdir -p "$OUT_DIR"
PACKAGE="com.sentinel.quantum"
XML="$OUT_DIR/revocation-window.xml"
TIMELINE_XML="$OUT_DIR/revocation-phone-private-timeline.xml"
DIALER_PROBE_NUMBER="5550197"
SCREENING_PROBE_NUMBER="5550198"
ANDROID_API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
SEND_SMS_PM_REVOCATION_OBSERVABLE=false
SMS_AUTHORIZATION_DENIAL_PROVEN=false
SMS_AUTHORIZATION_PROBE="UNSET"
SMS_AUTHORIZATION_NOTE="No SEND_SMS authorization denial proof completed."
SMS_AUTHORIZATION_UI_NEEDLE="Envoi SMS : autorisation Android requise."
REVOCATION_SCHEMA_VERSION=3
ROLE_REVOCATION_PROVEN=false

capture() { adb exec-out screencap -p > "$OUT_DIR/$1.png" || true; }

dump_ui() {
  adb shell rm -f /sdcard/sentinel-revocation.xml
  adb shell uiautomator dump --compressed /sdcard/sentinel-revocation.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/sentinel-revocation.xml > "$XML"
  test -s "$XML"
}

ui_contains() {
  local needle="$1"
  dump_ui && python3 - "$XML" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
needle = sys.argv[2]
for node in ET.parse(sys.argv[1]).iter('node'):
    haystack = ' '.join([node.get('text',''), node.get('content-desc',''), node.get('hint','')])
    matched = (node.get('resource-id') == needle and node.get('package') == 'com.sentinel.quantum') if needle.startswith('phone_core_') else needle in haystack
    if matched:
        sys.exit(0)
sys.exit(1)
PY
}

wait_ui_contains() {
  local needle="$1"
  for _ in $(seq 1 20); do
    if ui_contains "$needle"; then return 0; fi
    sleep 1
  done
  capture revocation-failure
  echo "Expected UI evidence not found: $needle"
  return 1
}

scroll_until_ui_contains() {
  local needle="$1"
  for _ in $(seq 1 12); do
    if ui_contains "$needle"; then return 0; fi
    adb shell input swipe 160 560 160 220 280 >/dev/null 2>&1 || true
    sleep 0.4
  done
  capture revocation-failure
  echo "Expected UI evidence not found after scrolling: $needle"
  return 1
}

tap_ui_text() {
  local needle="$1"
  dump_ui
  local coordinates
  coordinates="$(python3 - "$XML" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[2]
for node in ET.parse(sys.argv[1]).iter('node'):
    haystack = ' '.join([node.get('text',''), node.get('content-desc',''), node.get('hint','')])
    matched = (node.get('resource-id') == needle and node.get('package') == 'com.sentinel.quantum') if needle.startswith('phone_core_') else needle in haystack
    if not matched:
        continue
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds',''))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        if x2 > x1 and y2 > y1:
            print((x1+x2)//2, (y1+y2)//2)
            sys.exit(0)
sys.exit(1)
PY
)"
  read -r x y <<< "$coordinates"
  adb shell input tap "$x" "$y"
}

assert_action_disabled() {
  local needle="$1"
  dump_ui
  python3 - "$XML" "$needle" <<'PYDISABLED'
import sys, xml.etree.ElementTree as ET
path, needle = sys.argv[1:]
root = ET.parse(path).getroot()
parent = {child: node for node in root.iter() for child in node}
matched = False
for node in root.iter('node'):
    values = (node.get('text',''), node.get('content-desc',''), node.get('hint',''))
    is_match = (node.get('resource-id') == needle and node.get('package') == 'com.sentinel.quantum') if needle.startswith('phone_core_') else needle in values
    if not is_match:
        continue
    cur = node
    while cur is not None:
        if cur.get('clickable') == 'true':
            matched = True
            if cur.get('enabled') != 'false':
                print(f'Protected action {needle!r} is still enabled: {cur.attrib}', file=sys.stderr)
                sys.exit(2)
            break
        cur = parent.get(cur)
if not matched:
    print(f'No clickable action container found for {needle!r}', file=sys.stderr)
    sys.exit(1)
sys.exit(0)
PYDISABLED
}

assert_no_crash() {
  local brief_output=""
  local brief_status=0
  set +e
  brief_output="$(adb logcat -d -v brief)"
  brief_status=$?
  set -e
  if [[ "$brief_status" -ne 0 ]]; then
    echo "Crash oracle is unreadable; cannot qualify crash-free state." >&2
    return 1
  fi
  if ! python3 "$SCRIPT_DIR/phone-core-logcat-crash-oracle.py" /dev/stdin <<< "$brief_output"; then
    adb logcat -d -v time | tail -n 400 || true
    return 1
  fi
  return 0
}

role_holders() {
  local full_role="$1"
  local direct_output=""
  local direct_status=0
  set +e
  direct_output="$(adb shell cmd role get-role-holders --user 0 "$full_role" 2>&1 | tr -d '\r')"
  direct_status=$?
  set -e
  if [[ "$direct_status" -eq 0 && "$direct_output" != *"Unknown command"* ]]; then
    if ! grep -Evq '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$|^$' <<< "$direct_output"; then
      printf '%s\n' "$direct_output"
      return 0
    fi
  fi

  local role_dump="$OUT_DIR/role-state-current.txt"
  if ! adb shell dumpsys role > "$role_dump" 2>/dev/null; then
    return 1
  fi
  python3 "$(dirname "${BASH_SOURCE[0]}")/phone-core-emulator-role-holders.py" "$full_role" "$role_dump"
}

wait_role_held() {
  local full_role="$1"
  for _ in $(seq 1 20); do
    if role_holders "$full_role" | grep -Fxq "$PACKAGE"; then return 0; fi
    sleep 0.5
  done
  echo "$full_role was not restored to $PACKAGE."
  adb shell dumpsys role > "$OUT_DIR/role-wait-held-failure.txt" 2>&1 || true
  return 1
}

wait_role_absent() {
  local full_role="$1"
  local evidence="${2:-}"
  local holders
  local observation="unknown"
  for _ in $(seq 1 20); do
    if holders="$(role_holders "$full_role")"; then
      if ! grep -Fxq "$PACKAGE" <<< "$holders"; then
        if [[ -n "$evidence" ]]; then
          {
            printf 'role=%s\n' "$full_role"
            printf 'package=%s\n' "$PACKAGE"
            printf 'absent=true\n'
          } > "$OUT_DIR/$evidence"
        fi
        return 0
      fi
      observation="held"
    else
      observation="unknown"
    fi
    sleep 0.5
  done
  echo "$full_role absence could not be proven after explicit removal (last observation: $observation)."
  adb shell dumpsys role > "$OUT_DIR/role-wait-absent-failure.txt" 2>&1 || true
  return 1
}

remove_role_holder() {
  local full_role="$1"
  local slug="${full_role##*.}"
  for attempt in 1 2 3 4 5; do
    local evidence="$OUT_DIR/role-remove-${slug,,}-${attempt}.txt"
    local status=0
    set +e
    adb shell cmd role remove-role-holder --user 0 "$full_role" "$PACKAGE" > "$evidence" 2>&1
    status=$?
    set -e
    printf 'remove_status=%s\n' "$status" >> "$evidence"
    if wait_role_absent "$full_role"; then
      return 0
    fi
    sleep "$attempt"
  done
  echo "Unable to remove Android role $full_role from $PACKAGE."
  cat "$OUT_DIR"/role-remove-${slug,,}-*.txt 2>/dev/null || true
  adb shell dumpsys role > "$OUT_DIR/role-remove-${slug,,}-failure-dumpsys.txt" 2>&1 || true
  return 1
}

ensure_role_held() {
  local full_role="$1"
  local slug="${full_role##*.}"
  if role_holders "$full_role" | grep -Fxq "$PACKAGE"; then return 0; fi
  for attempt in 1 2 3 4 5; do
    set +e
    adb shell cmd role add-role-holder --user 0 "$full_role" "$PACKAGE" \
      > "$OUT_DIR/role-add-${slug,,}-${attempt}.txt" 2>&1
    local status=$?
    set -e
    if [[ "$status" -eq 0 ]] && wait_role_held "$full_role"; then return 0; fi
    sleep "$attempt"
  done
  echo "Unable to establish Android role $full_role for $PACKAGE."
  cat "$OUT_DIR"/role-add-${slug,,}-*.txt 2>/dev/null || true
  adb shell dumpsys role > "$OUT_DIR/role-add-${slug,,}-failure-dumpsys.txt" 2>&1 || true
  return 1
}

assert_sms_role_held() {
  role_holders android.app.role.SMS | grep -Fxq "$PACKAGE"
}

permission_granted() {
  local permission="$1"
  adb shell dumpsys package "$PACKAGE" 2>/dev/null |
    grep -E "${permission}: granted=true" >/dev/null
}

assert_send_sms_runtime_permission_granted() {
  local evidence="$1"
  if ! adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then
    echo "SEND_SMS runtime grant baseline is unreadable."
    return 1
  fi
  if ! grep -Eq 'android\.permission\.SEND_SMS: granted=true' "$OUT_DIR/$evidence"; then
    echo "SEND_SMS runtime grant baseline is not proven."
    grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
    return 1
  fi
}

archive_send_sms_runtime_permission_state() {
  local evidence="$1"
  if ! adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then
    echo "SEND_SMS runtime permission state is unreadable."
    return 1
  fi
  if ! grep -Eq 'android\.permission\.SEND_SMS: granted=(true|false)' "$OUT_DIR/$evidence"; then
    echo "SEND_SMS runtime permission state is ambiguous."
    grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
    return 1
  fi
}

probe_pm_revoke_send_sms() {
  local output="$OUT_DIR/send-sms-pm-revoke-observation.txt"
  set +e
  adb shell pm revoke "$PACKAGE" android.permission.SEND_SMS > "$output" 2>&1
  local status=$?
  set -e
  if [[ "$status" -ne 0 ]]; then
    printf 'pm_revoke_status=%s\nobservable=false\nreason=pm_revoke_command_failed\n' "$status" >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=false
    echo "SEND_SMS pm revoke failed; runtime revocation is non-observable, continuing to the supported fallback."
    return 0
  fi

  local denied_observations=0
  local required_stability_observations=6
  for observation in $(seq 1 "$required_stability_observations"); do
    sleep 1
    if permission_granted android.permission.SEND_SMS; then
      printf 'pm_revoke_status=%s\ndenial_stability_observations=%s\nobservable=false\nreason=role_controller_restored_runtime_permission\n' "$status" "$denied_observations" >> "$output"
      SEND_SMS_PM_REVOCATION_OBSERVABLE=false
      return 0
    fi
    denied_observations=$observation
  done
  printf 'pm_revoke_status=%s\ndenial_stability_observations=%s\nobservable=true\n' "$status" "$denied_observations" >> "$output"
  SEND_SMS_PM_REVOCATION_OBSERVABLE=true
}

assert_send_sms_runtime_permission_denied() {
  local evidence="$1"
  adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1
  if permission_granted android.permission.SEND_SMS; then
    echo "SEND_SMS runtime permission was not observably revoked."
    grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
    return 1
  fi
  if ! grep -Eq 'android\.permission\.SEND_SMS: granted=false' "$OUT_DIR/$evidence"; then
    echo "SEND_SMS runtime permission denial could not be proven from package state."
    return 1
  fi
}

wait_send_sms_runtime_permission_denied() {
  local evidence="$1"
  for _ in $(seq 1 20); do
    adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1 || true
    if grep -Eq 'android\.permission\.SEND_SMS: granted=false' "$OUT_DIR/$evidence"; then
      return 0
    fi
    sleep 0.5
  done
  echo "SEND_SMS runtime permission did not become observably denied after role removal."
  grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
  return 1
}

set_send_sms_appop() {
  local mode="$1"
  local evidence="$2"
  local uid_status=0
  local package_status=0
  : > "$OUT_DIR/$evidence"

  set +e
  adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS "$mode" >> "$OUT_DIR/$evidence" 2>&1
  uid_status=$?
  adb shell appops set --user 0 "$PACKAGE" SEND_SMS "$mode" >> "$OUT_DIR/$evidence" 2>&1
  package_status=$?
  set -e

  printf 'uid_set_status=%s\npackage_set_status=%s\n' "$uid_status" "$package_status" >> "$OUT_DIR/$evidence"
  if [[ "$uid_status" -ne 0 ]]; then
    echo "UID AppOp command failed; package boundary is the persistence fallback." >> "$OUT_DIR/$evidence"
  fi
  if [[ "$package_status" -ne 0 ]]; then
    echo "Package AppOp command failed; persistent SEND_SMS AppOp state cannot be established." >> "$OUT_DIR/$evidence"
    return 1
  fi

  sleep 1
  adb shell appops get "$PACKAGE" SEND_SMS >> "$OUT_DIR/$evidence" 2>&1 || true
}

assert_send_sms_appop_denied() {
  local evidence="$1"
  adb shell appops get "$PACKAGE" SEND_SMS > "$OUT_DIR/$evidence" 2>&1

  if grep -Eiq 'Uid mode:.*SEND_SMS: *(allow|foreground)' "$OUT_DIR/$evidence"; then
    echo "SEND_SMS AppOp has an explicit UID-level allow; package denial is not effective."
    cat "$OUT_DIR/$evidence"
    return 1
  fi
  if grep -Eiq 'Uid mode:.*SEND_SMS: *(ignore|deny|errored)' "$OUT_DIR/$evidence"; then
    return 0
  fi
  if grep -Eiq 'SEND_SMS: *(ignore|deny|errored)' "$OUT_DIR/$evidence"; then
    return 0
  fi
  echo "SEND_SMS AppOp was not observably denied."
  cat "$OUT_DIR/$evidence"
  return 1
}

launch_sms_surface() {
  local output="$1"
  local restart="${2:-cold}"
  if [[ "$restart" == "cold" ]]; then adb shell am force-stop "$PACKAGE"; fi
  adb shell am start -W -a android.intent.action.SENDTO -d sms:+15550123 \
    --es sms_body RevocationProbe \
    -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/$output"
}

wait_app_backgrounded() {
  local evidence="$OUT_DIR/sms-background-activities.txt"
  local error_evidence="$OUT_DIR/sms-background-activities.err"
  local status=0
  for attempt in $(seq 1 25); do
    set +e
    adb shell dumpsys activity activities > "$evidence" 2> "$error_evidence"
    status=$?
    set -e
    printf 'attempt=%s\nadb_dumpsys_status=%s\n' "$attempt" "$status" >> "$error_evidence"
    if [[ "$status" -ne 0 ]]; then
      sleep 0.2
      continue
    fi
    if ! grep -E 'mResumedActivity:.*com\.sentinel\.quantum' "$evidence" >/dev/null; then
      return 0
    fi
    sleep 0.2
  done
  echo "Sentinel background state could not be proven before the foreground-return probe."
  cp "$evidence" "$OUT_DIR/sms-background-wait-failure.txt" 2>/dev/null || true
  cp "$error_evidence" "$OUT_DIR/sms-background-wait-failure.err" 2>/dev/null || true
  return 1
}

timeline_signal_prefix_count() {
  local prefix="$1"
  if ! adb shell run-as "$PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$TIMELINE_XML" 2>/dev/null; then
    echo "Phone Core private timeline is unreadable; screening revocation evidence cannot be qualified." >&2
    return 2
  fi
  python3 - "$TIMELINE_XML" "$prefix" <<'PY'
import json, sys, xml.etree.ElementTree as ET
path, prefix = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
    node = next((n for n in root.findall('string') if n.get('name') == 'events'), None)
    if node is None:
        raise ValueError('events node missing')
    events = json.loads((node.text or '[]'))
    if not isinstance(events, list):
        raise ValueError('events payload is not a list')
except Exception as exc:
    print(f'Invalid Phone Core private timeline evidence: {exc}', file=sys.stderr)
    sys.exit(2)
print(sum(1 for event in events if str(event.get('signal') or '').startswith(prefix)))
PY
}

screening_callback_count() {
  local phase="${1:-snapshot}"
  local evidence="$OUT_DIR/screening-callback-count-${phase}-logcat.txt"
  local count
  local attempt
  local attempt_evidence
  local attempt_error
  local status
  rm -f "$evidence"
  for attempt in 1 2 3 4 5; do
    attempt_evidence="$OUT_DIR/screening-callback-count-${phase}-attempt-${attempt}.txt"
    attempt_error="$OUT_DIR/screening-callback-count-${phase}-attempt-${attempt}.err"
    if adb logcat -d -v brief > "$attempt_evidence" 2> "$attempt_error"; then
      cp "$attempt_evidence" "$evidence"
      if count="$(grep -F -c 'CallScreeningService:onScreenCall' "$evidence")"; then
        printf '%s\n' "$count"
        return 0
      fi
      if [[ "$count" == "0" ]]; then
        printf '0\n'
        return 0
      fi
      echo "Screening callback oracle count is unreadable; absence cannot be qualified." >&2
      return 2
    else
      status=$?
      printf 'adb_logcat_status=%s\n' "$status" >> "$attempt_error"
    fi
    sleep 0.5
  done
  echo "Screening callback oracle is unreadable after bounded ADB retries; absence cannot be qualified." >&2
  return 2
}

assert_modem_call_absent() {
  local number="$1"
  local evidence="$2"
  adb emu gsm list > "$OUT_DIR/$evidence"
  if grep -Fq "$number" "$OUT_DIR/$evidence"; then
    echo "Protected dial action created modem call $number while DIALER role was revoked."
    exit 1
  fi
  local telecom_evidence="$OUT_DIR/${evidence%.txt}-telecom.txt"
  adb shell dumpsys telecom > "$telecom_evidence"
  if ! python3 "$SCRIPT_DIR/phone-core-emulator-telecom-calls.py" "$telecom_evidence"; then
    echo "Protected dial action has live or UNKNOWN Telecom state while DIALER role is revoked."
    exit 1
  fi
}

wait_incoming_call_observed() {
  local number="$1"
  local evidence="$2"
  for _ in $(seq 1 15); do
    adb emu gsm list > "$OUT_DIR/$evidence"
    if grep -Fq "$number" "$OUT_DIR/$evidence"; then return 0; fi
    local notifications
    notifications="$(timeline_signal_prefix_count 'CALL_NOTIFICATION_POSTED')" || return 2
    if [[ "$notifications" -gt "$INCOMING_NOTIFICATION_BEFORE" ]]; then return 0; fi
    sleep 1
  done
  echo "Synthetic screening probe call $number has no modem or new InCallService notification evidence."
  return 1
}

write_summary() {
  SEND_SMS_PM_REVOCATION_OBSERVABLE="$SEND_SMS_PM_REVOCATION_OBSERVABLE" \
  SMS_AUTHORIZATION_DENIAL_PROVEN="$SMS_AUTHORIZATION_DENIAL_PROVEN" \
  SMS_AUTHORIZATION_PROBE="$SMS_AUTHORIZATION_PROBE" \
  SMS_AUTHORIZATION_NOTE="$SMS_AUTHORIZATION_NOTE" \
  REVOCATION_SCHEMA_VERSION="$REVOCATION_SCHEMA_VERSION" \
  ROLE_REVOCATION_PROVEN="$ROLE_REVOCATION_PROVEN" \
  OUT_DIR="$OUT_DIR" python3 <<'PY'
import json, os, pathlib
proven = os.environ['SMS_AUTHORIZATION_DENIAL_PROVEN'] == 'true'
probe = os.environ['SMS_AUTHORIZATION_PROBE']
payload = {
    'schema_version': int(os.environ['REVOCATION_SCHEMA_VERSION']),
    'send_sms_pm_revocation_observable': os.environ['SEND_SMS_PM_REVOCATION_OBSERVABLE'] == 'true',
    'sms_authorization_denial_fail_closed': proven,
    'role_revocation_fail_closed': os.environ['ROLE_REVOCATION_PROVEN'] == 'true',
    'sms_authorization_probe': probe,
    'effective_permission_denial_fail_closed': proven,
    'effective_permission_probe': probe,
    'note': os.environ['SMS_AUTHORIZATION_NOTE']
}
pathlib.Path(os.environ['OUT_DIR'], 'revocation-summary.json').write_text(json.dumps(payload, indent=2) + '\n')
PY
}
trap write_summary EXIT

ensure_role_held android.app.role.SMS
ensure_role_held android.app.role.DIALER
ensure_role_held android.app.role.CALL_SCREENING
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission" >/dev/null 2>&1 || true
done
assert_sms_role_held
assert_send_sms_runtime_permission_granted "send-sms-runtime-permission-granted-before-launch.txt"

launch_sms_surface "sms-before-effective-denial-launch.txt"
assert_sms_role_held
assert_send_sms_runtime_permission_granted "send-sms-runtime-permission-granted-after-launch.txt"
probe_pm_revoke_send_sms
ensure_role_held android.app.role.SMS

if [[ "$SEND_SMS_PM_REVOCATION_OBSERVABLE" == "true" ]]; then
  SMS_AUTHORIZATION_PROBE="SEND_SMS_RUNTIME_PERMISSION_REVOKED"
  SMS_AUTHORIZATION_NOTE="ROLE_SMS remained held while Android exposed a real SEND_SMS runtime-permission revocation; Sentinel stayed fail-closed before and after a proven foreground return."
  assert_send_sms_runtime_permission_denied "send-sms-runtime-permission-denied-state.txt"
  adb shell input keyevent KEYCODE_HOME
  wait_app_backgrounded
  launch_sms_surface "sms-send-runtime-permission-denied-launch.txt" warm
  assert_sms_role_held
  assert_send_sms_runtime_permission_denied "send-sms-runtime-permission-denied-after-launch.txt"
elif [[ "$ANDROID_API" -ge 36 ]]; then
  REVOCATION_SCHEMA_VERSION=5
  SMS_AUTHORIZATION_PROBE="SEND_SMS_ROLE_AUTHORIZATION_REVOKED"
  SMS_AUTHORIZATION_NOTE="Android retained the raw SEND_SMS grant after default-SMS role removal. Qualification therefore proves the supported authorization boundary: ROLE_SMS is absent before and after relaunch, the raw permission state is archived without interpreting its boolean as capability, and Sentinel keeps the send action disabled. No AppOp mutation is credited."
  SMS_AUTHORIZATION_UI_NEEDLE="rôle SMS disponible mais non accordé"
  adb shell input keyevent KEYCODE_HOME
  wait_app_backgrounded
  remove_role_holder android.app.role.SMS
  wait_role_absent android.app.role.SMS "send-sms-role-managed-role-absent-state.txt"
  archive_send_sms_runtime_permission_state "send-sms-role-managed-permission-state.txt"
  launch_sms_surface "sms-send-role-managed-authorization-denied-launch.txt" warm
  wait_role_absent android.app.role.SMS "send-sms-role-managed-role-absent-after-launch.txt"
  archive_send_sms_runtime_permission_state "send-sms-role-managed-permission-state-after-launch.txt"
else
  adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true
  SMS_AUTHORIZATION_PROBE="SEND_SMS_APP_OP_DENIED"
  SMS_AUTHORIZATION_NOTE="Legacy emulator fallback: ROLE_SMS restored the runtime grant, so an observable SEND_SMS AppOp denial was required before Sentinel UI qualification."
  set_send_sms_appop ignore "send-sms-appop-deny.txt"
  assert_send_sms_appop_denied "send-sms-appop-denied-state.txt"
  if ! permission_granted android.permission.SEND_SMS; then
    echo "SEND_SMS runtime permission unexpectedly disappeared during AppOp-only denial proof."
    exit 1
  fi
  adb shell input keyevent KEYCODE_HOME
  wait_app_backgrounded
  launch_sms_surface "sms-send-appop-denied-launch.txt" warm
  assert_sms_role_held
  set_send_sms_appop ignore "send-sms-appop-reassert-after-launch.txt"
  assert_send_sms_appop_denied "send-sms-appop-denied-after-launch.txt"
fi

scroll_until_ui_contains "$SMS_AUTHORIZATION_UI_NEEDLE"
scroll_until_ui_contains "phone_core_sms_send"
assert_action_disabled "phone_core_sms_send"
if [[ "$REVOCATION_SCHEMA_VERSION" -eq 5 ]]; then
  capture 08-sms-authorization-denied
else
  capture 08-sms-effective-permission-denied
fi
assert_no_crash
SMS_AUTHORIZATION_DENIAL_PROVEN=true

if [[ "$SMS_AUTHORIZATION_PROBE" == "SEND_SMS_APP_OP_DENIED" ]]; then
  set_send_sms_appop allow "send-sms-appop-restore.txt"
elif [[ "$SMS_AUTHORIZATION_PROBE" == "SEND_SMS_ROLE_AUTHORIZATION_REVOKED" ]]; then
  ensure_role_held android.app.role.SMS
fi
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true

remove_role_holder android.app.role.SMS
wait_role_absent android.app.role.SMS
launch_sms_surface "sms-role-revoked-launch.txt"
scroll_until_ui_contains "rôle SMS disponible mais non accordé"
scroll_until_ui_contains "phone_core_sms_send"
assert_action_disabled "phone_core_sms_send"
tap_ui_text "phone_core_sms_send"
sleep 1
assert_action_disabled "phone_core_sms_send"
wait_role_absent android.app.role.SMS
capture 10-sms-role-revoked
assert_no_crash
ensure_role_held android.app.role.SMS
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission" >/dev/null 2>&1 || true
done
set_send_sms_appop allow "send-sms-appop-post-role-restore.txt"
launch_sms_surface "sms-restored-launch.txt"
assert_no_crash
assert_sms_role_held

remove_role_holder android.app.role.DIALER
wait_role_absent android.app.role.DIALER
adb shell am force-stop "$PACKAGE"
adb shell am start -W -a android.intent.action.DIAL -d "tel:$DIALER_PROBE_NUMBER" \
  -n "$PACKAGE/.SentinelDialerActivity" > "$OUT_DIR/dialer-role-revoked-launch.txt"
wait_ui_contains "phone_core_call"
assert_modem_call_absent "$DIALER_PROBE_NUMBER" "dialer-revoked-modem-before.txt"
tap_ui_text "phone_core_call"
sleep 2
assert_modem_call_absent "$DIALER_PROBE_NUMBER" "dialer-revoked-modem-after.txt"
wait_role_absent android.app.role.DIALER
capture 11-dialer-role-revoked
assert_no_crash
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
ensure_role_held android.app.role.DIALER

remove_role_holder android.app.role.CALL_SCREENING
wait_role_absent android.app.role.CALL_SCREENING
SCREENING_CALLBACK_BEFORE="$(screening_callback_count before)"
SCREENING_DECISION_BEFORE="$(timeline_signal_prefix_count 'CALL_SCREENED:')"
INCOMING_NOTIFICATION_BEFORE="$(timeline_signal_prefix_count 'CALL_NOTIFICATION_POSTED')"
adb shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true
adb emu gsm call "$SCREENING_PROBE_NUMBER"
wait_incoming_call_observed "$SCREENING_PROBE_NUMBER" "call-screening-revoked-modem.txt"
sleep 3
SCREENING_CALLBACK_AFTER="$(screening_callback_count after)"
SCREENING_DECISION_AFTER="$(timeline_signal_prefix_count 'CALL_SCREENED:')"
printf 'callback_before=%s\ncallback_after=%s\n' "$SCREENING_CALLBACK_BEFORE" "$SCREENING_CALLBACK_AFTER" \
  > "$OUT_DIR/call-screening-revoked-callback-observation.txt"
if [[ "$SCREENING_DECISION_AFTER" != "$SCREENING_DECISION_BEFORE" ]]; then
  adb emu gsm cancel "$SCREENING_PROBE_NUMBER" >/dev/null 2>&1 || true
  echo "Call-screening decision evidence advanced while CALL_SCREENING role was revoked."
  exit 1
fi
wait_role_absent android.app.role.CALL_SCREENING
capture 12-call-screening-role-revoked
adb emu gsm cancel "$SCREENING_PROBE_NUMBER"
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
assert_no_crash
ensure_role_held android.app.role.CALL_SCREENING

ROLE_REVOCATION_PROVEN=true
role_holders android.app.role.SMS > "$OUT_DIR/sms-role-restored.txt"
role_holders android.app.role.DIALER > "$OUT_DIR/dialer-role-restored.txt"
role_holders android.app.role.CALL_SCREENING > "$OUT_DIR/call-screening-role-restored.txt"

echo "SEND_SMS authorization denial and protected-action role fail-closed behavior verified on Android Emulator."
