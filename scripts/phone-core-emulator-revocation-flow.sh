#!/usr/bin/env bash
# Framework-level role/permission revocation is executed outside the target app process so Android
# is free to kill/restart Sentinel exactly as it would on a real device.
set -euo pipefail
OUT_DIR="${1:?Output directory required}"
mkdir -p "$OUT_DIR"
PACKAGE="com.sentinel.quantum"
XML="$OUT_DIR/revocation-window.xml"
TIMELINE_XML="$OUT_DIR/revocation-phone-private-timeline.xml"
DIALER_PROBE_NUMBER="5550197"
SCREENING_PROBE_NUMBER="5550198"

capture() { adb exec-out screencap -p > "$OUT_DIR/$1.png" || true; }
dump_ui() {
  adb shell rm -f /sdcard/sentinel-revocation.xml
  adb shell uiautomator dump --compressed /sdcard/sentinel-revocation.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/sentinel-revocation.xml > "$XML"
  test -s "$XML"
}
wait_ui_contains() {
  local needle="$1"
  for _ in $(seq 1 20); do
    if dump_ui && python3 - "$XML" "$needle" <<'PY'
import sys, xml.etree.ElementTree as ET
needle = sys.argv[2]
for node in ET.parse(sys.argv[1]).iter('node'):
    haystack = ' '.join([node.get('text',''), node.get('content-desc',''), node.get('hint','')])
    if needle in haystack:
        sys.exit(0)
sys.exit(1)
PY
    then return 0; fi
    sleep 1
  done
  capture revocation-failure
  echo "Expected UI evidence not found: $needle"
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
assert_no_crash() {
  if adb logcat -d -v brief | grep -Eq 'FATAL EXCEPTION: main|ANR in com\.sentinel\.quantum'; then
    adb logcat -d -v time | tail -n 400
    return 1
  fi
}
assert_sms_role_held() {
  adb shell cmd role get-role-holders --user 0 android.app.role.SMS | grep -q "$PACKAGE"
}
launch_sms_surface() {
  local output="$1"
  adb shell am force-stop "$PACKAGE"
  adb shell am start -W -a android.intent.action.SENDTO -d sms:+15550123 \
    --es sms_body RevocationProbe \
    -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/$output"
}
timeline_signal_prefix_count() {
  local prefix="$1"
  if ! adb shell run-as "$PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$TIMELINE_XML" 2>/dev/null; then
    printf '0\n'
    return 0
  fi
  python3 - "$TIMELINE_XML" "$prefix" <<'PY'
import json, sys, xml.etree.ElementTree as ET
path, prefix = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
    node = next((n for n in root.findall('string') if n.get('name') == 'events'), None)
    events = json.loads((node.text if node is not None else '') or '[]')
except Exception:
    print(0)
    sys.exit(0)
print(sum(1 for event in events if str(event.get('signal') or '').startswith(prefix)))
PY
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
wait_modem_call_present() {
  local number="$1"
  local evidence="$2"
  for _ in $(seq 1 15); do
    adb emu gsm list > "$OUT_DIR/$evidence"
    if grep -Fq "$number" "$OUT_DIR/$evidence"; then return 0; fi
    sleep 1
  done
  echo "Synthetic screening probe call $number never reached emulator modem state."
  return 1
}

adb shell cmd role add-role-holder --user 0 android.app.role.SMS "$PACKAGE"
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission"
done
assert_sms_role_held

adb logcat -c >/dev/null 2>&1 || true
adb shell pm revoke "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true
launch_sms_surface "sms-send-permission-revoked-launch.txt"
assert_sms_role_held
wait_ui_contains "Envoi SMS : autorisation Android requise."
capture 08-sms-send-permission-revoked
if adb shell dumpsys package "$PACKAGE" | grep -E 'android.permission.SEND_SMS: granted=true' >/dev/null; then
  echo "SEND_SMS remained granted after explicit permission-only revocation."
  exit 1
fi
assert_no_crash
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS

adb shell pm revoke "$PACKAGE" android.permission.READ_PHONE_STATE >/dev/null 2>&1 || true
launch_sms_surface "sms-phone-state-permission-revoked-launch.txt"
assert_sms_role_held
wait_ui_contains "Détection SIM : accès à l’état téléphonique requis."
capture 09-sms-phone-state-permission-revoked
if adb shell dumpsys package "$PACKAGE" | grep -E 'android.permission.READ_PHONE_STATE: granted=true' >/dev/null; then
  echo "READ_PHONE_STATE remained granted after explicit permission-only revocation."
  exit 1
fi
assert_no_crash
adb shell pm grant "$PACKAGE" android.permission.READ_PHONE_STATE

for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission"
done
adb shell cmd role remove-role-holder --user 0 android.app.role.SMS "$PACKAGE"
launch_sms_surface "sms-role-revoked-launch.txt"
if adb shell cmd role get-role-holders --user 0 android.app.role.SMS | grep -q "$PACKAGE"; then
  echo "SMS role remained held after explicit removal."
  exit 1
fi
wait_ui_contains "rôle SMS disponible mais non accordé"
wait_ui_contains "Envoyer"
SMS_SIGNAL_BEFORE="$(timeline_signal_prefix_count 'SMS_ALL_PARTS_SENT')"
tap_ui_text "Envoyer"
sleep 2
SMS_SIGNAL_AFTER="$(timeline_signal_prefix_count 'SMS_ALL_PARTS_SENT')"
if [[ "$SMS_SIGNAL_AFTER" != "$SMS_SIGNAL_BEFORE" ]]; then
  echo "SMS submission evidence advanced while SMS role was revoked."
  exit 1
fi
if adb shell cmd role get-role-holders --user 0 android.app.role.SMS | grep -q "$PACKAGE"; then
  echo "SMS role was unexpectedly reacquired by the protected send attempt."
  exit 1
fi
capture 10-sms-role-revoked
assert_no_crash

adb shell cmd role add-role-holder --user 0 android.app.role.SMS "$PACKAGE"
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission"
done
launch_sms_surface "sms-restored-launch.txt"
assert_no_crash
assert_sms_role_held

# DIALER role: a real tap on Appeler must not create any emulator-modem call while the role is absent.
adb shell cmd role add-role-holder --user 0 android.app.role.DIALER "$PACKAGE"
adb shell cmd role remove-role-holder --user 0 android.app.role.DIALER "$PACKAGE"
if adb shell cmd role get-role-holders --user 0 android.app.role.DIALER | grep -q "$PACKAGE"; then
  echo "DIALER remained held after explicit removal."
  exit 1
fi
adb shell am force-stop "$PACKAGE"
adb shell am start -W -a android.intent.action.DIAL -d "tel:$DIALER_PROBE_NUMBER" \
  -n "$PACKAGE/.SentinelDialerActivity" > "$OUT_DIR/dialer-role-revoked-launch.txt"
wait_ui_contains "Appeler"
assert_modem_call_absent "$DIALER_PROBE_NUMBER" "dialer-revoked-modem-before.txt"
tap_ui_text "Appeler"
sleep 2
assert_modem_call_absent "$DIALER_PROBE_NUMBER" "dialer-revoked-modem-after.txt"
if adb shell cmd role get-role-holders --user 0 android.app.role.DIALER | grep -q "$PACKAGE"; then
  echo "DIALER was unexpectedly reacquired without user approval."
  exit 1
fi
capture 11-dialer-role-revoked
assert_no_crash
adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
adb shell cmd role add-role-holder --user 0 android.app.role.DIALER "$PACKAGE"

# CALL_SCREENING role: inject an actual incoming call while the role is absent. The call must reach
# the emulator modem, but Sentinel must not manufacture a new CALL_SCREENED:* evidence event.
if adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING >/dev/null 2>&1; then
  adb shell cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$PACKAGE"
  adb shell cmd role remove-role-holder --user 0 android.app.role.CALL_SCREENING "$PACKAGE"
  if adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING | grep -q "$PACKAGE"; then
    echo "CALL_SCREENING remained held after explicit removal."
    exit 1
  fi
  SCREENING_BEFORE="$(timeline_signal_prefix_count 'CALL_SCREENED:')"
  adb shell input keyevent KEYCODE_SLEEP >/dev/null 2>&1 || true
  adb emu gsm call "$SCREENING_PROBE_NUMBER"
  wait_modem_call_present "$SCREENING_PROBE_NUMBER" "call-screening-revoked-modem.txt"
  sleep 3
  SCREENING_AFTER="$(timeline_signal_prefix_count 'CALL_SCREENED:')"
  if [[ "$SCREENING_AFTER" != "$SCREENING_BEFORE" ]]; then
    adb emu gsm cancel "$SCREENING_PROBE_NUMBER" >/dev/null 2>&1 || true
    echo "Call-screening evidence advanced while CALL_SCREENING role was revoked."
    exit 1
  fi
  if adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING | grep -q "$PACKAGE"; then
    adb emu gsm cancel "$SCREENING_PROBE_NUMBER" >/dev/null 2>&1 || true
    echo "CALL_SCREENING was unexpectedly reacquired during incoming call."
    exit 1
  fi
  capture 12-call-screening-role-revoked
  adb emu gsm cancel "$SCREENING_PROBE_NUMBER"
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  assert_no_crash
  adb shell cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$PACKAGE"
fi

adb shell cmd role get-role-holders --user 0 android.app.role.SMS > "$OUT_DIR/sms-role-restored.txt"
adb shell cmd role get-role-holders --user 0 android.app.role.DIALER > "$OUT_DIR/dialer-role-restored.txt" 2>/dev/null || true
adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING > "$OUT_DIR/call-screening-role-restored.txt" 2>/dev/null || true

echo "Independent permission revocation and protected-action role fail-closed behavior verified on Android Emulator."
