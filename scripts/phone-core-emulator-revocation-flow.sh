#!/usr/bin/env bash
# Framework-level role/permission revocation is executed outside the target app process so Android
# is free to kill/restart Sentinel exactly as it would on a real device.
set -euo pipefail
OUT_DIR="${1:?Output directory required}"
mkdir -p "$OUT_DIR"
PACKAGE="com.sentinel.quantum"
XML="$OUT_DIR/revocation-window.xml"

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
    -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/$output"
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
capture 10-sms-role-revoked
assert_no_crash

adb shell cmd role add-role-holder --user 0 android.app.role.SMS "$PACKAGE"
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission"
done
launch_sms_surface "sms-restored-launch.txt"
assert_no_crash
assert_sms_role_held

for role in DIALER CALL_SCREENING; do
  full_role="android.app.role.$role"
  if adb shell cmd role get-role-holders --user 0 "$full_role" >/dev/null 2>&1; then
    adb shell cmd role add-role-holder --user 0 "$full_role" "$PACKAGE"
    adb shell cmd role remove-role-holder --user 0 "$full_role" "$PACKAGE"
    if adb shell cmd role get-role-holders --user 0 "$full_role" | grep -q "$PACKAGE"; then
      echo "$role remained held after explicit removal."
      exit 1
    fi
    adb shell am force-stop "$PACKAGE"
    adb shell am start -W -n "$PACKAGE/.SentinelDialerActivity" > "$OUT_DIR/${role,,}-revoked-launch.txt"
    sleep 1
    assert_no_crash
    adb shell cmd role add-role-holder --user 0 "$full_role" "$PACKAGE"
  fi
done

adb shell cmd role get-role-holders --user 0 android.app.role.SMS > "$OUT_DIR/sms-role-restored.txt"
adb shell cmd role get-role-holders --user 0 android.app.role.DIALER > "$OUT_DIR/dialer-role-restored.txt" 2>/dev/null || true
adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING > "$OUT_DIR/call-screening-role-restored.txt" 2>/dev/null || true

echo "Independent permission and role revocation/restoration verified on Android Emulator."
