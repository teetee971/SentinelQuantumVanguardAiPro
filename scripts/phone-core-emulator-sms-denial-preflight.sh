#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:?Output directory required}"
PACKAGE="com.sentinel.quantum"
XML="$OUT_DIR/sms-denial-preflight-window.xml"
mkdir -p "$OUT_DIR"

role_held() {
  adb shell dumpsys role > "$OUT_DIR/sms-denial-preflight-role.txt" 2>&1
  python3 "$SCRIPT_DIR/phone-core-emulator-role-holders.py" android.app.role.SMS "$OUT_DIR/sms-denial-preflight-role.txt" |
    grep -Fxq "$PACKAGE"
}

permission_state() {
  local evidence="$1"
  if ! adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then printf 'unknown\n'; return; fi
  if grep -Eq 'android\.permission\.SEND_SMS: granted=true' "$OUT_DIR/$evidence"; then printf 'granted\n'; return; fi
  if grep -Eq 'android\.permission\.SEND_SMS: granted=false' "$OUT_DIR/$evidence"; then printf 'denied\n'; return; fi
  printf 'unknown\n'
}

appop_state() {
  local evidence="$1"
  if ! adb shell appops get "$PACKAGE" SEND_SMS > "$OUT_DIR/$evidence" 2>&1; then printf 'unknown\n'; return; fi
  if grep -Eiq 'Uid mode:.*SEND_SMS:\s*allow' "$OUT_DIR/$evidence"; then printf 'allow\n'; return; fi
  if grep -Eiq 'Uid mode:.*SEND_SMS:\s*(ignore|deny|errored)' "$OUT_DIR/$evidence"; then printf 'denied\n'; return; fi
  if grep -Eiq 'SEND_SMS:\s*(ignore|deny|errored)' "$OUT_DIR/$evidence"; then printf 'denied\n'; return; fi
  if grep -Eiq 'SEND_SMS:\s*allow' "$OUT_DIR/$evidence"; then printf 'allow\n'; return; fi
  printf 'unknown\n'
}

launch_sms() {
  adb shell am start -W -a android.intent.action.SENDTO -d sms:+15550123 \
    --es sms_body RevocationPreflight -n "$PACKAGE/.SmsComposeActivity" > "$OUT_DIR/$1" 2>&1
}

assert_send_disabled() {
  adb shell rm -f /sdcard/sentinel-sms-preflight.xml
  adb shell uiautomator dump --compressed /sdcard/sentinel-sms-preflight.xml >/dev/null 2>&1
  adb shell cat /sdcard/sentinel-sms-preflight.xml > "$XML"
  python3 - "$XML" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
parent = {child: node for node in root.iter() for child in node}
matched = False
for node in root.iter('node'):
    if node.get('package') != 'com.sentinel.quantum' or node.get('resource-id') != 'phone_core_sms_send':
        continue
    cur = node
    while cur is not None:
        if cur.get('clickable') == 'true':
            matched = True
            if cur.get('enabled') != 'false': sys.exit(2)
            break
        cur = parent.get(cur)
sys.exit(0 if matched else 1)
PY
}

role_held || { echo 'ROLE_SMS baseline is not proven.'; exit 1; }
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS > "$OUT_DIR/sms-denial-preflight-grant-command.txt" 2>&1 || true
[[ "$(permission_state sms-denial-preflight-before-launch.txt)" == granted ]] || { echo 'SEND_SMS grant baseline is not proven.'; exit 1; }
launch_sms sms-denial-preflight-launch.txt
role_held || { echo 'ROLE_SMS disappeared before denial probe.'; exit 1; }
[[ "$(permission_state sms-denial-preflight-after-launch.txt)" == granted ]] || { echo 'SEND_SMS was not granted after foreground launch.'; exit 1; }

set +e
adb shell pm revoke "$PACKAGE" android.permission.SEND_SMS > "$OUT_DIR/sms-denial-preflight-revoke-command.txt" 2>&1
REVOKE_STATUS=$?
set -e
sleep 1
STATE="$(permission_state sms-denial-preflight-after-revoke.txt)"
PROBE=''
if [[ "$STATE" == denied && "$REVOKE_STATUS" -eq 0 ]]; then
  PROBE='SEND_SMS_RUNTIME_PERMISSION_REVOKED'
elif [[ "$STATE" == granted ]]; then
  PROBE='SEND_SMS_APP_OP_DENIED'
  adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS ignore > "$OUT_DIR/sms-denial-preflight-appop-command.txt" 2>&1 || true
  sleep 1
  if [[ "$(appop_state sms-denial-preflight-appop-uid.txt)" != denied ]]; then
    adb shell appops set --user 0 "$PACKAGE" SEND_SMS ignore >> "$OUT_DIR/sms-denial-preflight-appop-command.txt" 2>&1
    sleep 1
  fi
  [[ "$(appop_state sms-denial-preflight-appop-state.txt)" == denied ]] || { echo 'Effective SEND_SMS AppOp denial is not proven.'; exit 1; }
else
  echo "SEND_SMS transition is not attributable to a successful revoke (state=$STATE status=$REVOKE_STATUS)."
  exit 1
fi

adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
launch_sms sms-denial-preflight-relaunch.txt
role_held || { echo 'ROLE_SMS disappeared after denial.'; exit 1; }
if [[ "$PROBE" == SEND_SMS_RUNTIME_PERMISSION_REVOKED ]]; then
  [[ "$(permission_state sms-denial-preflight-after-relaunch.txt)" == denied ]] || { echo 'Runtime denial did not survive foreground return.'; exit 1; }
else
  [[ "$(permission_state sms-denial-preflight-runtime-after-relaunch.txt)" == granted ]] || { echo 'Runtime permission changed during AppOp-only probe.'; exit 1; }
  [[ "$(appop_state sms-denial-preflight-appop-after-relaunch.txt)" == denied ]] || { echo 'AppOp denial did not survive foreground return.'; exit 1; }
fi
assert_send_disabled
adb exec-out screencap -p > "$OUT_DIR/08a-sms-denial-transition-preflight.png"
PROBE="$PROBE" OUT_DIR="$OUT_DIR" python3 <<'PY'
import json, os, pathlib
pathlib.Path(os.environ['OUT_DIR'], 'sms-denial-transition-proof.json').write_text(json.dumps({
  'schema_version': 1, 'baseline_granted': True, 'denial_observed': True,
  'post_foreground_denial_observed': True, 'ui_fail_closed': True,
  'effective_permission_probe': os.environ['PROBE'],
  'physical_modem_claim': False, 'commercial_release_claim': False
}, indent=2) + '\n')
PY
if [[ "$PROBE" == SEND_SMS_APP_OP_DENIED ]]; then
  adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS allow >/dev/null 2>&1 || true
  adb shell appops set --user 0 "$PACKAGE" SEND_SMS allow >/dev/null 2>&1 || true
fi
adb shell pm grant "$PACKAGE" android.permission.SEND_SMS >/dev/null 2>&1 || true
[[ "$(permission_state sms-denial-preflight-restored.txt)" == granted ]] || { echo 'SEND_SMS was not restored after preflight.'; exit 1; }
