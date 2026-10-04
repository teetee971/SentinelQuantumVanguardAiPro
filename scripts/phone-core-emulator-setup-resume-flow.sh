#!/usr/bin/env bash
# Verifies that an interrupted first-run Phone Core setup resumes from persisted/runtime truth.
set -euo pipefail
OUT_DIR="${1:?Output directory required}"
mkdir -p "$OUT_DIR"
PACKAGE="com.sentinel.quantum"
ACTIVITY="$PACKAGE/.PhoneCoreActivationActivity"
FIRST_RUN_EXTRA="com.sentinel.quantum.extra.FIRST_RUN_PHONE_CORE_SETUP"
XML="$OUT_DIR/setup-resume-window.xml"

capture() { adb exec-out screencap -p > "$OUT_DIR/$1.png" || true; }
dump_ui() {
  adb shell rm -f /sdcard/sentinel-setup-resume.xml
  adb shell uiautomator dump --compressed /sdcard/sentinel-setup-resume.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/sentinel-setup-resume.xml > "$XML"
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
  capture setup-resume-failure
  echo "Expected first-run setup evidence not found: $needle"
  return 1
}
assert_no_crash() {
  if adb logcat -d -v brief | grep -Eq 'FATAL EXCEPTION: main|ANR in com\.sentinel\.quantum'; then
    adb logcat -d -v time | tail -n 400
    return 1
  fi
}

# Begin from incomplete Android truth. Role commands are conditional because the emulator's role
# service may expose a different subset across supported API levels.
for role in SMS DIALER CALL_SCREENING; do
  full_role="android.app.role.$role"
  if adb shell cmd role get-role-holders --user 0 "$full_role" >/dev/null 2>&1; then
    adb shell cmd role remove-role-holder --user 0 "$full_role" "$PACKAGE" >/dev/null 2>&1 || true
  fi
done
for permission in CALL_PHONE READ_PHONE_STATE READ_CALL_LOG SEND_SMS READ_SMS RECEIVE_SMS RECEIVE_MMS RECEIVE_WAP_PUSH; do
  adb shell pm revoke "$PACKAGE" "android.permission.$permission" >/dev/null 2>&1 || true
done

adb logcat -c >/dev/null 2>&1 || true
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" --ez "$FIRST_RUN_EXTRA" true > "$OUT_DIR/setup-first-launch.txt"

# The assistant intentionally launches one Android request at a time. Dismiss the first system
# request without granting it, simulating an interrupted/deferred setup rather than success.
sleep 2
capture 01-setup-first-request
adb shell input keyevent KEYCODE_BACK || true
sleep 1
wait_ui_contains "Configuration initiale"
wait_ui_contains "Assistant séquentiel"
capture 02-setup-after-interruption

# The attempted target must have been persisted before Android was invoked.
adb shell run-as "$PACKAGE" cat shared_prefs/phone_core_setup_wizard_v2.xml \
  > "$OUT_DIR/setup-prefs-before-restart.xml"
grep -q 'attempted_target' "$OUT_DIR/setup-prefs-before-restart.xml"
if grep -q 'name="completed" value="true"' "$OUT_DIR/setup-prefs-before-restart.xml"; then
  echo "Interrupted setup was incorrectly persisted as completed."
  exit 1
fi

# Kill the process and relaunch exactly as a resumed first-run. Since the same missing target was
# already attempted, Sentinel must show the assistant again without converting refusal/interruption
# into a successful prerequisite.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$ACTIVITY" --ez "$FIRST_RUN_EXTRA" true > "$OUT_DIR/setup-resume-launch.txt"
wait_ui_contains "Configuration initiale"
wait_ui_contains "Assistant séquentiel"
wait_ui_contains "Configuration Android incomplète"
capture 03-setup-resumed
assert_no_crash

adb shell run-as "$PACKAGE" cat shared_prefs/phone_core_setup_wizard_v2.xml \
  > "$OUT_DIR/setup-prefs-after-restart.xml"
grep -q 'attempted_target' "$OUT_DIR/setup-prefs-after-restart.xml"
if grep -q 'name="completed" value="true"' "$OUT_DIR/setup-prefs-after-restart.xml"; then
  echo "Resumed incomplete setup was incorrectly persisted as completed."
  exit 1
fi

echo "Interrupted Phone Core first-run setup resumed without a false READY/completed state."
