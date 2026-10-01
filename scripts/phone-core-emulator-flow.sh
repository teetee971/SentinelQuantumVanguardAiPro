#!/usr/bin/env bash
# Synthetic communications on the isolated CI emulator only. No physical certification.
set -euo pipefail
FLOW_OUTPUT_DIR="${1:?Screenshot output directory required}"
mkdir -p "$FLOW_OUTPUT_DIR"
FLOW_PACKAGE="com.sentinel.quantum"
FLOW_NUMBER="5550100"
FLOW_SMS_NUMBER="+15550123"
FLOW_XML="$FLOW_OUTPUT_DIR/window.xml"

for FLOW_ROLE in DIALER SMS; do
  adb shell cmd role add-role-holder --user 0 "android.app.role.$FLOW_ROLE" "$FLOW_PACKAGE"
done
for FLOW_PERMISSION in CALL_PHONE READ_PHONE_STATE READ_CONTACTS READ_CALL_LOG SEND_SMS READ_SMS RECEIVE_SMS RECEIVE_MMS RECEIVE_WAP_PUSH; do
  adb shell pm grant "$FLOW_PACKAGE" "android.permission.$FLOW_PERMISSION"
done
if [[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]]; then
  adb shell pm grant "$FLOW_PACKAGE" android.permission.POST_NOTIFICATIONS
fi

fresh_ui() {
  adb shell rm -f /sdcard/sentinel-flow.xml
  adb shell uiautomator dump --compressed /sdcard/sentinel-flow.xml >/dev/null 2>&1 || return 1
  adb shell cat /sdcard/sentinel-flow.xml > "$FLOW_XML"
  test -s "$FLOW_XML"
}
wait_text() {
  local expected="$1"
  for _ in $(seq 1 15); do
    if fresh_ui && python3 - "$FLOW_XML" "$expected" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = ET.parse(sys.argv[1]).iter('node')
needle = sys.argv[2]
sys.exit(0 if any(needle in (n.get('text', '') + ' ' + n.get('content-desc', '')) for n in nodes) else 1)
PY
    then return 0; fi
    sleep 1
  done
  echo "Phone Core flow did not expose expected UI: $expected"
  return 1
}
tap_text() {
  fresh_ui
  local coordinates
  coordinates="$(python3 - "$FLOW_XML" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter('node'):
    if sys.argv[2] in (node.get('text', '') + ' ' + node.get('content-desc', '')):
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
capture() { adb exec-out screencap -p > "$FLOW_OUTPUT_DIR/$1.png"; }

adb shell am force-stop "$FLOW_PACKAGE"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "Clavier"
capture 01-dialer

# Locked incoming call exercises the real Telecom -> InCallService -> call UI path.
adb shell input keyevent KEYCODE_SLEEP
adb emu gsm call "$FLOW_NUMBER"
wait_text "Décrocher"
capture 02-incoming-call
tap_text "Décrocher"
wait_text "En communication"
if python3 - "$FLOW_XML" <<'PY'
import sys, xml.etree.ElementTree as ET
bad = ('Aucun appel actif', 'Aucun appel détecté', 'liaison indisponible')
sys.exit(0 if any(any(w in (n.get('text','')+' '+n.get('content-desc','')) for w in bad)
                  for n in ET.parse(sys.argv[1]).iter('node')) else 1)
PY
then
  echo "Active call was represented as missing/idle."
  exit 1
fi
capture 03-active-call
adb emu gsm cancel "$FLOW_NUMBER"
wait_text "Appel terminé"
capture 04-ended-call

# Outgoing call originates from Sentinel's own button, matching the reported S24 scenario.
adb shell am start -W -a android.intent.action.DIAL -d tel:5550101 -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "Appeler"
tap_text "Appeler"
wait_text "Composition"
adb emu gsm accept 5550101
wait_text "En communication"
capture 05-outgoing-call
adb emu gsm cancel 5550101
wait_text "Appel terminé"

# Receive one synthetic SMS, open its conversation, and send an inline reply.
adb shell am start -W -a android.intent.action.MAIN -n "$FLOW_PACKAGE/.SmsComposeActivity"
adb emu sms send "$FLOW_SMS_NUMBER" "Sentinel emulator reply test"
wait_text "Sentinel emulator reply test"
tap_text "Sentinel emulator reply test"
wait_text "Répondre"
capture 06-thread
tap_text "Répondre"
adb shell input text ReplyFromSentinel
wait_text "ReplyFromSentinel"
tap_text "Envoyer"
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
test "$FLOW_REPLY_STORED" = "1"
capture 07-inline-reply
echo "Synthetic Telecom call and inline SMS reply UI verified; physical validation remains pending."
