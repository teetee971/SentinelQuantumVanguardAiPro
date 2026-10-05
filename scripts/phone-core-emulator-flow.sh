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
# Call screening is a distinct Android role on supported platform versions. Keep this conditional
# because some emulator/OEM role services may report it unavailable even when Telecom remains usable.
if adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING >/dev/null 2>&1; then
  adb shell cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$FLOW_PACKAGE"
fi
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
  local expected="$*"
  for _ in $(seq 1 15); do
    if fresh_ui && python3 - "$FLOW_XML" "$@" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = ET.parse(sys.argv[1]).iter('node')
needles = sys.argv[2:]
sys.exit(0 if any(any(needle in (n.get('text', '') + ' ' + n.get('content-desc', '') + ' ' + n.get('hint', '')) for needle in needles) for n in nodes) else 1)
PY
    then return 0; fi
    sleep 1
  done
  adb exec-out screencap -p > "$FLOW_OUTPUT_DIR/failure.png" || true
  echo "Phone Core flow did not expose expected UI: $expected"
  return 1
}
wait_incoming_sentinel_surface() {
  for _ in $(seq 1 20); do
    if fresh_ui && python3 - "$FLOW_XML" "$FLOW_PACKAGE" "$FLOW_NUMBER" <<'PYINCOMING'
import sys, xml.etree.ElementTree as ET
path, package_name, number = sys.argv[1:]
try:
    nodes = list(ET.parse(path).iter('node'))
except Exception:
    sys.exit(1)
owned = [n for n in nodes if n.get('package') == package_name]
text = ' '.join(
    (n.get('text', '') + ' ' + n.get('content-desc', '') + ' ' + n.get('hint', '')).strip()
    for n in owned
)
number_present = number in text
sentinel_surface = (
    'Appel autorisé' in text or
    ('Appel entrant' in text and 'Sonnerie' in text)
)
sys.exit(0 if number_present and sentinel_surface else 1)
PYINCOMING
    then return 0; fi
    sleep 1
  done
  capture failure
  echo "Sentinel did not expose an app-owned incoming-call surface for $FLOW_NUMBER."
  return 1
}
tap_text() {
  fresh_ui
  local coordinates
  coordinates="$(python3 - "$FLOW_XML" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter('node'):
    if sys.argv[2] in (node.get('text', '') + ' ' + node.get('content-desc', '') + ' ' + node.get('hint', '')):
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
wait_private_timeline_event() {
  local direction="$1"
  local signal="$2"
  local evidence="$FLOW_OUTPUT_DIR/phone-private-timeline-${direction,,}-${signal,,}.xml"
  for _ in $(seq 1 20); do
    if adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$evidence" 2>/dev/null && \
      python3 - "$evidence" "$direction" "$signal" <<'PYTIMELINE'
import json, sys, xml.etree.ElementTree as ET
path, direction, signal = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
    node = next((n for n in root.findall('string') if n.get('name') == 'events'), None)
    events = json.loads((node.text if node is not None else '') or '[]')
except Exception:
    sys.exit(1)
matched = any(
    event.get('kind') == 'CALL' and
    event.get('direction') == direction and
    event.get('signal') == signal
    for event in events
)
sys.exit(0 if matched else 1)
PYTIMELINE
    then return 0; fi
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

# This is the first application launch after the workflow's fresh APK install. Exercise a second
# process launch as well so cold_install_and_relaunch is a real per-lane proof, not report metadata.
adb shell am force-stop "$FLOW_PACKAGE"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "Clavier"
capture 01-dialer-first-launch
adb shell am force-stop "$FLOW_PACKAGE"
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "Clavier"
capture 01b-dialer-relaunch

adb shell input keyevent KEYCODE_SLEEP
adb emu gsm call "$FLOW_NUMBER"
# CallerIdActivity and SentinelInCallActivity are both Sentinel-owned and may race for foreground.
# Prove a coherent app-owned surface and wait for the post-response CallScreening evidence before
# continuing; this also stabilizes the later CALL_SCREENING revocation baseline.
wait_incoming_sentinel_surface
wait_private_timeline_signal_prefix "CALL_SCREENED:"
capture 02-incoming-call
adb emu gsm accept "$FLOW_NUMBER"
wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"
capture 03-incoming-active-evidence
adb emu gsm cancel "$FLOW_NUMBER"
sleep 1

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

# Outgoing call originates from Sentinel's own button and keeps the explicit InCall UI proof.
adb shell am start -W -a android.intent.action.DIAL -d tel:5550101 -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "Appeler"
tap_text "Appeler"
wait_text "Composition" "En communication"
if ! python3 - "$FLOW_XML" <<'PY'
import sys, xml.etree.ElementTree as ET
sys.exit(0 if any("En communication" in (n.get("text", "") + n.get("content-desc", "")) for n in ET.parse(sys.argv[1]).iter("node")) else 1)
PY
then adb emu gsm accept 5550101; fi
wait_text "En communication"
wait_private_timeline_event "OUTGOING" "INCALL_ACTIVE"
capture 05-outgoing-call
tap_text "Raccrocher"
wait_text "Appel terminé"

adb shell am start -W -a android.intent.action.MAIN -n "$FLOW_PACKAGE/.SmsComposeActivity"
adb emu sms send "$FLOW_SMS_NUMBER" "Sentinel emulator reply test"
wait_text "Sentinel emulator reply test"
tap_text "Sentinel emulator reply test"
wait_text "Répondre"
capture 06-thread
tap_text "Répondre"
wait_reply_focus
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
if [[ "$FLOW_REPLY_STORED" != "1" ]]; then
  capture failure
  echo "Inline reply was not observed in the provider-backed conversation."
  exit 1
fi
capture 07-inline-reply

for FLOW_ROLE in DIALER SMS; do
  adb shell cmd role get-role-holders --user 0 "android.app.role.$FLOW_ROLE" | grep -q "$FLOW_PACKAGE"
done
if adb shell cmd role get-role-holders --user 0 android.app.role.CALL_SCREENING > "$FLOW_OUTPUT_DIR/call-screening-role.txt" 2>/dev/null; then
  grep -q "$FLOW_PACKAGE" "$FLOW_OUTPUT_DIR/call-screening-role.txt"
fi

echo "Synthetic Telecom screening/calls, cold relaunch, and inline SMS reply verified; physical validation remains pending."
