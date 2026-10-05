#!/usr/bin/env bash
# Framework-level role/effective-permission denial is executed outside the target app process so
# Android is free to kill/restart Sentinel exactly as it would on a real device.
set -euo pipefail
OUT_DIR="${1:?Output directory required}"
mkdir -p "$OUT_DIR"
PACKAGE="com.sentinel.quantum"
XML="$OUT_DIR/revocation-window.xml"
TIMELINE_XML="$OUT_DIR/revocation-phone-private-timeline.xml"
DIALER_PROBE_NUMBER="5550197"
SCREENING_PROBE_NUMBER="5550198"
SEND_SMS_PM_REVOCATION_OBSERVABLE=false
EFFECTIVE_PERMISSION_DENIAL_PROVEN=false
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
    if needle in haystack:
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
    if needle not in haystack:
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
    if needle not in values:
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
  if adb logcat -d -v brief | grep -Eq 'FATAL EXCEPTION:|ANR in com\.sentinel\.quantum'; then
    adb logcat -d -v time | tail -n 400
    return 1
  fi
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
    # Accept only a holder list, never shell diagnostics containing the package name.
    if ! grep -Evq '^[A-Za-z0-9_]+(\.[A-Za-z0-9_]+)+$|^$' <<< "$direct_output"; then
      printf '%s\n' "$direct_output"
      return 0
    fi
  fi

  # Android 10/API 29 has add/remove role shell commands but no get-role-holders command.
  # Persist the dump so the shared parser can distinguish empty, incomplete and unknown roles.
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
  local holders
  for _ in $(seq 1 20); do
    # A failed oracle is UNKNOWN, never proof that Android removed the role.
    if holders="$(role_holders "$full_role")"; then
      if ! grep -Fxq "$PACKAGE" <<< "$holders"; then return 0; fi
    fi
    sleep 0.5
  done
  echo "$full_role remained held after explicit removal."
  adb shell dumpsys role > "$OUT_DIR/role-wait-absent-failure.txt" 2>&1 || true
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

probe_pm_revoke_send_sms() {
  local output="$OUT_DIR/send-sms-pm-revoke-observation.txt"
  set +e
  adb shell pm revoke "$PACKAGE" android.permission.SEND_SMS > "$output" 2>&1
  local status=$?
  set -e
  sleep 1
  if permission_granted android.permission.SEND_SMS; then
    printf 'pm_revoke_status=%s\nobservable=false\nreason=role_controller_restored_runtime_permission\n' "$status" >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=false
  else
    printf 'pm_revoke_status=%s\nobservable=true\n' "$status" >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=true
  fi
  # Stabilize the role-managed baseline before the independent AppOp denial proof.
  adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true
}

set_send_sms_appop() {
  local mode="$1"
  local evidence="$2"
  # Recent RoleController versions grant a UID-level mode. Android 10 can instead
  # retain only a package mode. Try UID first, then the package boundary if the UID
  # denial did not become observable. The caller must still prove denial after launch.
  if ! adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS "$mode" > "$OUT_DIR/$evidence" 2>&1; then
    echo "UID AppOp command failed; checking effective mode." >> "$OUT_DIR/$evidence"
  fi
  sleep 1
  adb shell appops get "$PACKAGE" SEND_SMS >> "$OUT_DIR/$evidence" 2>&1 || true
  if [[ "$mode" == "allow" ]] || ! grep -Eiq 'SEND_SMS: *(ignore|deny|errored)' "$OUT_DIR/$evidence"; then
    adb shell appops set --user 0 "$PACKAGE" SEND_SMS "$mode" >> "$OUT_DIR/$evidence" 2>&1
    sleep 1
    adb shell appops get "$PACKAGE" SEND_SMS >> "$OUT_DIR/$evidence" 2>&1 || true
  fi
}

assert_send_sms_appop_denied() {
  local evidence="$1"
  adb shell appops get "$PACKAGE" SEND_SMS > "$OUT_DIR/$evidence" 2>&1
  if ! grep -Eiq 'SEND_SMS: *(ignore|deny|errored)' "$OUT_DIR/$evidence"; then
    echo "SEND_SMS AppOp was not observably denied."
    cat "$OUT_DIR/$evidence"
    return 1
  fi
}

launch_sms_surface() {
  local output="$1"
  local restart="${2:-cold}"
  if [[ "$restart" == "cold" ]]; then adb shell am force-stop "$PACKAGE"; fi
  adb shell am start -W -a android.intent.action.SENDTO -d sms:+15550123 \
    --es sms_body RevocationProbe \
    -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/$output"
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
  local evidence="$OUT_DIR/screening-callback-count-logcat.txt"
  local count
  if ! adb logcat -d -v brief > "$evidence"; then
    echo "Screening callback oracle is unreadable; absence cannot be qualified." >&2
    return 2
  fi
  if count="$(grep -F -c 'CallScreeningService:onScreenCall' "$evidence")"; then
    printf '%s\n' "$count"
  elif [[ "$count" == "0" ]]; then
    printf '0\n'
  else
    return 2
  fi
}

assert_modem_call_absent() {
  local number="$1"
  local evidence="$2"
  adb emu gsm list > "$OUT_DIR/$evidence"
  if grep -Fq "$number" "$OUT_DIR/$evidence"; then
    echo "Protected dial action created modem call $number while DIALER role was revoked."
    exit 1
  fi
}

wait_incoming_call_observed() {
  local number="$1"
  local evidence="$2"
  for _ in $(seq 1 15); do
    adb emu gsm list > "$OUT_DIR/$evidence"
    if grep -Fq "$number" "$OUT_DIR/$evidence"; then return 0; fi
    # Some emulator versions return only OK from gsm list even while Telecom rings.
    # A new InCallService notification event independently proves the incoming call.
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
  EFFECTIVE_PERMISSION_DENIAL_PROVEN="$EFFECTIVE_PERMISSION_DENIAL_PROVEN" \
  ROLE_REVOCATION_PROVEN="$ROLE_REVOCATION_PROVEN" \
  OUT_DIR="$OUT_DIR" python3 <<'PY'
import json, os, pathlib
payload = {
    'schema_version': 2,
    'send_sms_pm_revocation_observable': os.environ['SEND_SMS_PM_REVOCATION_OBSERVABLE'] == 'true',
    'effective_permission_denial_fail_closed': os.environ['EFFECTIVE_PERMISSION_DENIAL_PROVEN'] == 'true',
    'role_revocation_fail_closed': os.environ['ROLE_REVOCATION_PROVEN'] == 'true',
    'effective_permission_probe': 'SEND_SMS_APP_OP_DENIED',
    'note': 'ROLE_SMS can restore role-managed runtime permissions. The independent fail-closed proof therefore denies the SEND_SMS AppOp while keeping ROLE_SMS held and verifies the UI becomes non-actionable.'
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

# Record whether raw runtime revocation remains observable while ROLE_SMS is held. This is
# diagnostic evidence only because the role controller is allowed to restore role-managed grants.
probe_pm_revoke_send_sms
ensure_role_held android.app.role.SMS
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true

# Independent effective-permission denial: keep ROLE_SMS and the runtime grant, deny only the AppOp.
# Sentinel uses PermissionChecker at UI and transport boundaries, so the protected action must
# become unavailable even though PackageManager still reports SEND_SMS granted.
launch_sms_surface "sms-before-effective-denial-launch.txt"
set_send_sms_appop ignore "send-sms-appop-deny.txt"
assert_send_sms_appop_denied "send-sms-appop-denied-state.txt"
if ! permission_granted android.permission.SEND_SMS; then
  echo "SEND_SMS runtime permission unexpectedly disappeared during AppOp-only denial proof."
  exit 1
fi
# Force-stop can make RoleController regrant the UID AppOp on Android 37. Exercise
# a real background/foreground return while the independently denied mode remains set.
adb shell input keyevent KEYCODE_HOME
launch_sms_surface "sms-send-appop-denied-launch.txt" warm
assert_sms_role_held
assert_send_sms_appop_denied "send-sms-appop-denied-after-launch.txt"
scroll_until_ui_contains "Envoi SMS : autorisation Android requise."
scroll_until_ui_contains "Envoyer"
assert_action_disabled "Envoyer"
capture 08-sms-effective-permission-denied
assert_no_crash
EFFECTIVE_PERMISSION_DENIAL_PROVEN=true
set_send_sms_appop allow "send-sms-appop-restore.txt"
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true

# ROLE_SMS revocation: the protected send action must remain disabled and must not reacquire the role.
adb shell cmd role remove-role-holder --user 0 android.app.role.SMS "$PACKAGE"
wait_role_absent android.app.role.SMS
launch_sms_surface "sms-role-revoked-launch.txt"
scroll_until_ui_contains "rôle SMS disponible mais non accordé"
scroll_until_ui_contains "Envoyer"
assert_action_disabled "Envoyer"
tap_ui_text "Envoyer"
sleep 1
assert_action_disabled "Envoyer"
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

# DIALER role: a real tap on Appeler must not create any emulator-modem call while the role is absent.
adb shell cmd role remove-role-holder --user 0 android.app.role.DIALER "$PACKAGE"
wait_role_absent android.app.role.DIALER
adb shell am force-stop "$PACKAGE"
adb shell am start -W -a android.intent.action.DIAL -d "tel:$DIALER_PROBE_NUMBER" \
  -n "$PACKAGE/.SentinelDialerActivity" > "$OUT_DIR/dialer-role-revoked-launch.txt"
wait_ui_contains "Appeler"
assert_modem_call_absent "$DIALER_PROBE_NUMBER" "dialer-revoked-modem-before.txt"
tap_ui_text "Appeler"
sleep 2
assert_modem_call_absent "$DIALER_PROBE_NUMBER" "dialer-revoked-modem-after.txt"
wait_role_absent android.app.role.DIALER
capture 11-dialer-role-revoked
assert_no_crash
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
ensure_role_held android.app.role.DIALER

# CALL_SCREENING role: Telecom may still invoke the default dialer's service. A real incoming
# call must not create a CALL_SCREENED:* engine decision while the screening role is absent.
# Callback invocation is recorded separately. Timeline read/parse errors are fatal because
# an unreadable evidence source must never be interpreted as a zero-count proof.
adb shell cmd role remove-role-holder --user 0 android.app.role.CALL_SCREENING "$PACKAGE"
wait_role_absent android.app.role.CALL_SCREENING
SCREENING_CALLBACK_BEFORE="$(screening_callback_count)"
SCREENING_DECISION_BEFORE="$(timeline_signal_prefix_count 'CALL_SCREENED:')"
INCOMING_NOTIFICATION_BEFORE="$(timeline_signal_prefix_count 'CALL_NOTIFICATION_POSTED')"
adb shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true
adb emu gsm call "$SCREENING_PROBE_NUMBER"
wait_incoming_call_observed "$SCREENING_PROBE_NUMBER" "call-screening-revoked-modem.txt"
sleep 3
SCREENING_CALLBACK_AFTER="$(screening_callback_count)"
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

echo "Effective SEND_SMS AppOp denial and protected-action role fail-closed behavior verified on Android Emulator."
