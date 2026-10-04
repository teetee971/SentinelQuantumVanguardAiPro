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

# Establish a known-good SMS role before exercising revocation.
adb shell cmd role add-role-holder --user 0 android.app.role.SMS "$PACKAGE"
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission"
done
adb shell am force-stop "$PACKAGE"

# Remove role and critical permissions outside the app process. A correct app must relaunch in a
# fail-closed state rather than preserving a stale READY state.
adb logcat -c >/dev/null 2>&1 || true
adb shell cmd role remove-role-holder --user 0 android.app.role.SMS "$PACKAGE"
adb shell pm revoke "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true
adb shell pm revoke "$PACKAGE" android.permission.READ_PHONE_STATE >/dev/null 2>&1 || true
adb shell am force-stop "$PACKAGE"
adb shell am start -W -a android.intent.action.SENDTO -d sms:+15550123 -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/sms-revoked-launch.txt"
wait_ui_contains "rôle SMS disponible mais non accordé"
capture 08-sms-role-revoked
assert_no_crash

# The framework itself must agree that the app no longer owns the role/permission.
if adb shell cmd role get-role-holders --user 0 android.app.role.SMS | grep -q "$PACKAGE"; then
  echo "SMS role remained held after explicit removal."
  exit 1
fi
if adb shell dumpsys package "$PACKAGE" | grep -E 'android.permission.SEND_SMS: granted=true' >/dev/null; then
  echo "SEND_SMS remained granted after explicit revocation."
  exit 1
fi

# Restore the SMS path and prove the app survives a real framework-state transition.
adb shell cmd role add-role-holder --user 0 android.app.role.SMS "$PACKAGE"
for permission in SEND_SMS READ_SMS RECEIVE_SMS READ_PHONE_STATE; do
  adb shell pm grant "$PACKAGE" "android.permission.$permission"
done
adb shell am force-stop "$PACKAGE"
adb shell am start -W -a android.intent.action.SENDTO -d sms:+15550123 -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/sms-restored-launch.txt"
assert_no_crash
adb shell cmd role get-role-holders --user 0 android.app.role.SMS | grep -q "$PACKAGE"

# Repeat role withdrawal for dialer/call-screening without requiring a personal dual-SIM device.
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

echo "Role/permission revocation and restoration verified on Android Emulator."
