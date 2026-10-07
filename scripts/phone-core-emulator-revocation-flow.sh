#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.sentinel.quantum"
OUT_DIR="${OUT_DIR:-artifacts/phone-core-revocation}"
TIMELINE_XML="$OUT_DIR/phone-private-timeline.xml"
DIALER_PROBE_NUMBER="5550199"
SCREENING_PROBE_NUMBER="5550198"
SEND_SMS_PM_REVOCATION_OBSERVABLE=false
EFFECTIVE_PERMISSION_DENIAL_PROVEN=false
EFFECTIVE_PERMISSION_PROBE="UNKNOWN"
EFFECTIVE_PERMISSION_NOTE="Effective SEND_SMS denial not yet proven."
ROLE_REVOCATION_PROVEN=false

mkdir -p "$OUT_DIR"

capture() {
  local name="$1"
  adb exec-out screencap -p > "$OUT_DIR/$name.png"
}

assert_no_crash() {
  local evidence="$OUT_DIR/logcat-check.txt"
  set +e
  adb logcat -d -v threadtime > "$evidence" 2> "$evidence.stderr"
  local status=$?
  set -e
  printf '%s\n' "$status" > "$evidence.status"
  if [[ "$status" -ne 0 ]]; then
    echo "Unable to read complete logcat evidence; crash absence is UNKNOWN."
    cat "$evidence.stderr" 2>/dev/null || true
    return 1
  fi
  if grep -Eiq '(FATAL EXCEPTION|ANR in com\.sentinel\.quantum|Process: com\.sentinel\.quantum.*has died)' "$evidence"; then
    echo "Crash or ANR evidence detected for $PACKAGE."
    grep -Ein '(FATAL EXCEPTION|ANR in com\.sentinel\.quantum|Process: com\.sentinel\.quantum.*has died)' "$evidence" || true
    return 1
  fi
}

wait_ui_contains() {
  local needle="$1"
  local dump="$OUT_DIR/ui.xml"
  for _ in $(seq 1 20); do
    adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
    adb pull /sdcard/window.xml "$dump" >/dev/null 2>&1 || true
    if [[ -f "$dump" ]] && grep -Fq "$needle" "$dump"; then return 0; fi
    sleep 1
  done
  echo "UI text not found: $needle"
  cat "$dump" 2>/dev/null || true
  return 1
}

scroll_until_ui_contains() {
  local needle="$1"
  if wait_ui_contains "$needle"; then return 0; fi
  for _ in $(seq 1 8); do
    adb shell input swipe 540 1800 540 700 350 >/dev/null 2>&1 || true
    if wait_ui_contains "$needle"; then return 0; fi
  done
  return 1
}

tap_ui_text() {
  local text="$1"
  local dump="$OUT_DIR/ui.xml"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  adb pull /sdcard/window.xml "$dump" >/dev/null 2>&1
  python3 - "$dump" "$text" <<'PY'
import re, subprocess, sys, xml.etree.ElementTree as ET
path, text = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter('node'):
    if node.get('text') == text or node.get('content-desc') == text:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        subprocess.run(['adb', 'shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)], check=True)
        sys.exit(0)
raise SystemExit(f'No exact UI node found for {text!r}')
PY
}

assert_action_disabled() {
  local text="$1"
  local dump="$OUT_DIR/ui.xml"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1
  adb pull /sdcard/window.xml "$dump" >/dev/null 2>&1
  python3 - "$dump" "$text" <<'PY'
import sys, xml.etree.ElementTree as ET
path, text = sys.argv[1:]
root = ET.parse(path).getroot()
for node in root.iter('node'):
    if node.get('text') == text or node.get('content-desc') == text:
        if node.get('enabled') == 'false':
            raise SystemExit(0)
        raise SystemExit(f'UI action {text!r} is enabled')
raise SystemExit(f'UI action {text!r} is missing')
PY
}

role_holders() {
  local full_role="$1"
  local output status
  set +e
  output="$(adb shell cmd role get-role-holders --user 0 "$full_role" 2>&1)"
  status=$?
  set -e
  if [[ "$status" -eq 0 ]] && ! grep -Eiq '(error|exception|unknown command|unsupported)' <<<"$output"; then
    printf '%s\n' "$output" | sed -e 's/\r$//' -e '/^[[:space:]]*$/d'
    return 0
  fi

  local dumpsys="$OUT_DIR/dumpsys-role-${full_role##*.}.txt"
  set +e
  adb shell dumpsys role > "$dumpsys" 2>&1
  status=$?
  set -e
  if [[ "$status" -ne 0 ]]; then
    echo "Role oracle failed for $full_role." >&2
    cat "$dumpsys" >&2 || true
    return 2
  fi
  python3 - "$dumpsys" "$full_role" <<'PY'
import re, sys
path, role = sys.argv[1:]
text = open(path, encoding='utf-8', errors='replace').read().replace('\r', '')
entries = []
patterns = [
    re.compile(rf'(?m)^\s*{re.escape(role)}\s*:\s*\[([^\]\n]*)\]\s*$'),
    re.compile(rf'(?m)^\s*{re.escape(role)}\s+holders\s*=\s*\[([^\]\n]*)\]\s*$'),
]
for pattern in patterns:
    entries.extend(pattern.findall(text))
if len(entries) != 1:
    raise SystemExit(2)
holders = [item.strip() for item in entries[0].split(',') if item.strip()]
if any(not re.fullmatch(r'[A-Za-z0-9_.]+', item) for item in holders):
    raise SystemExit(2)
print('\n'.join(holders))
PY
}

wait_role_absent() {
  local full_role="$1"
  for attempt in 1 2 3 4 5; do
    local holders
    set +e
    holders="$(role_holders "$full_role")"
    local status=$?
    set -e
    if [[ "$status" -eq 0 ]] && ! grep -Fxq "$PACKAGE" <<<"$holders"; then return 0; fi
    sleep "$attempt"
  done
  echo "Unable to prove role $full_role is absent for $PACKAGE."
  return 1
}

wait_role_held() {
  local full_role="$1"
  for attempt in 1 2 3 4 5; do
    local holders
    set +e
    holders="$(role_holders "$full_role")"
    local status=$?
    set -e
    if [[ "$status" -eq 0 ]] && grep -Fxq "$PACKAGE" <<<"$holders"; then return 0; fi
    sleep "$attempt"
  done
  echo "Unable to prove role $full_role is held by $PACKAGE."
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
  : > "$output"

  # This probe only answers whether a runtime-permission transition is observable.
  # Non-observable/unsupported states are expected and must return success so the
  # caller can safely fall back to the established AppOp denial proof.
  if ! permission_granted android.permission.SEND_SMS; then
    printf 'baseline_granted=false\nobservable=false\nreason=baseline_grant_not_proven\n' >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=false
    echo "SEND_SMS granted baseline could not be proven before runtime revocation; using fallback probe." >&2
    return 0
  fi
  printf 'baseline_granted=true\n' >> "$output"

  set +e
  adb shell pm revoke "$PACKAGE" android.permission.SEND_SMS >> "$output" 2>&1
  local status=$?
  set -e
  printf 'pm_revoke_status=%s\n' "$status" >> "$output"
  if [[ "$status" -ne 0 ]]; then
    printf 'observable=false\nreason=pm_revoke_failed\n' >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=false
    echo "SEND_SMS runtime revoke command failed with status $status; using fallback probe." >&2
    return 0
  fi

  sleep 1
  if permission_granted android.permission.SEND_SMS; then
    printf 'observable=false\nreason=role_controller_restored_runtime_permission\n' >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=false
  else
    printf 'observable=true\n' >> "$output"
    SEND_SMS_PM_REVOCATION_OBSERVABLE=true
  fi
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
    events = json.loads(node.text or '[]')
    if not isinstance(events, list):
        raise ValueError('events not a list')
    print(sum(1 for item in events if isinstance(item, str) and item.startswith(prefix)))
except Exception as exc:
    print(f'Unable to parse private timeline: {exc}', file=sys.stderr)
    raise SystemExit(2)
PY
}

screening_callback_count() {
  local output="$OUT_DIR/call-screening-callback-logcat.txt"
  set +e
  adb logcat -d -v brief 'SentinelLifecycle:I' '*:S' > "$output" 2> "$output.stderr"
  local status=$?
  set -e
  printf '%s\n' "$status" > "$output.status"
  if [[ "$status" -ne 0 ]]; then
    echo "Unable to read screening callback logcat evidence." >&2
    return 2
  fi
  grep -c 'CallScreeningService:onScreenCall' "$output" || true
}

assert_modem_call_absent() {
  local number="$1"
  local evidence="$2"
  adb emu gsm list > "$OUT_DIR/$evidence" 2>&1
  if grep -Fq "$number" "$OUT_DIR/$evidence"; then
    echo "Unexpected emulator modem call created for $number."
    cat "$OUT_DIR/$evidence"
    return 1
  fi
}

wait_incoming_call_observed() {
  local number="$1"
  local evidence="$2"
  for _ in $(seq 1 10); do
    adb emu gsm list > "$OUT_DIR/$evidence" 2>&1 || true
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
  EFFECTIVE_PERMISSION_PROBE="$EFFECTIVE_PERMISSION_PROBE" \
  EFFECTIVE_PERMISSION_NOTE="$EFFECTIVE_PERMISSION_NOTE" \
  ROLE_REVOCATION_PROVEN="$ROLE_REVOCATION_PROVEN" \
  OUT_DIR="$OUT_DIR" python3 <<'PY'
import json, os, pathlib
payload = {
    'schema_version': 3,
    'send_sms_pm_revocation_observable': os.environ['SEND_SMS_PM_REVOCATION_OBSERVABLE'] == 'true',
    'effective_permission_denial_fail_closed': os.environ['EFFECTIVE_PERMISSION_DENIAL_PROVEN'] == 'true',
    'role_revocation_fail_closed': os.environ['ROLE_REVOCATION_PROVEN'] == 'true',
    'effective_permission_probe': os.environ['EFFECTIVE_PERMISSION_PROBE'],
    'note': os.environ['EFFECTIVE_PERMISSION_NOTE']
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

# Start from an actionable SMS surface, then ask Android for the strongest effective-denial probe
# that the current framework actually exposes. Android 17 can keep the default-SMS SEND_SMS AppOp
# at allow while still permitting an observable runtime-permission revocation.
launch_sms_surface "sms-before-effective-denial-launch.txt"
probe_pm_revoke_send_sms
ensure_role_held android.app.role.SMS

if [[ "$SEND_SMS_PM_REVOCATION_OBSERVABLE" == "true" ]]; then
  EFFECTIVE_PERMISSION_PROBE="SEND_SMS_RUNTIME_PERMISSION_REVOKED"
  EFFECTIVE_PERMISSION_NOTE="ROLE_SMS remained held while Android exposed a real SEND_SMS runtime-permission revocation; Sentinel stayed fail-closed before and after foreground return."
  assert_send_sms_runtime_permission_denied "send-sms-runtime-permission-denied-state.txt"
  adb shell input keyevent KEYCODE_HOME
  launch_sms_surface "sms-send-runtime-permission-denied-launch.txt" warm
  assert_sms_role_held
  assert_send_sms_runtime_permission_denied "send-sms-runtime-permission-denied-after-launch.txt"
else
  # Some RoleController versions immediately restore the role-managed runtime grant. In that case,
  # retain the established AppOp probe used by older emulator lanes.
  adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true
  EFFECTIVE_PERMISSION_PROBE="SEND_SMS_APP_OP_DENIED"
  EFFECTIVE_PERMISSION_NOTE="ROLE_SMS restored the runtime grant, so the effective-denial proof used the SEND_SMS AppOp and verified the protected UI stayed non-actionable."
  set_send_sms_appop ignore "send-sms-appop-deny.txt"
  assert_send_sms_appop_denied "send-sms-appop-denied-state.txt"
  if ! permission_granted android.permission.SEND_SMS; then
    echo "SEND_SMS runtime permission unexpectedly disappeared during AppOp-only denial proof."
    exit 1
  fi
  adb shell input keyevent KEYCODE_HOME
  launch_sms_surface "sms-send-appop-denied-launch.txt" warm
  assert_sms_role_held
  assert_send_sms_appop_denied "send-sms-appop-denied-after-launch.txt"
fi

scroll_until_ui_contains "Envoi SMS : autorisation Android requise."
scroll_until_ui_contains "phone_core_sms_send"
assert_action_disabled "phone_core_sms_send"
capture 08-sms-effective-permission-denied
assert_no_crash
EFFECTIVE_PERMISSION_DENIAL_PROVEN=true

if [[ "$EFFECTIVE_PERMISSION_PROBE" == "SEND_SMS_APP_OP_DENIED" ]]; then
  set_send_sms_appop allow "send-sms-appop-restore.txt"
fi
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true

# ROLE_SMS revocation: the protected send action must remain disabled and must not reacquire the role.
adb shell cmd role remove-role-holder --user 0 android.app.role.SMS "$PACKAGE"
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

# DIALER role: a real tap on Appeler must not create any emulator-modem call while the role is absent.
adb shell cmd role remove-role-holder --user 0 android.app.role.DIALER "$PACKAGE"
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

echo "Effective SEND_SMS denial and protected-action role fail-closed behavior verified on Android Emulator."
