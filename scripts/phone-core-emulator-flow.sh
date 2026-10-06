#!/usr/bin/env bash
# Synthetic communications on the isolated CI emulator only. No physical certification.
set -euo pipefail
FLOW_SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
FLOW_OUTPUT_DIR="${1:?Screenshot output directory required}"
mkdir -p "$FLOW_OUTPUT_DIR"
FLOW_PACKAGE="com.sentinel.quantum"
FLOW_NUMBER="5550100"
FLOW_SMS_NUMBER="+15550123"
FLOW_XML="$FLOW_OUTPUT_DIR/window.xml"
FLOW_API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

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

  # Android 10/API 29 can manage roles with `cmd role`, but its shell command does not expose
  # get-role-holders. Persist the dump for the shared, fail-closed user-0 parser.
  local role_dump="$FLOW_OUTPUT_DIR/role-state-current.txt"
  if ! adb shell dumpsys role > "$role_dump" 2>/dev/null; then
    return 1
  fi
  python3 "$(dirname "${BASH_SOURCE[0]}")/phone-core-emulator-role-holders.py" "$full_role" "$role_dump"
}

wait_role_held() {
  local full_role="$1"
  local evidence="$2"
  for _ in $(seq 1 20); do
    role_holders "$full_role" > "$FLOW_OUTPUT_DIR/$evidence" 2>&1 || true
    if grep -Fxq "$FLOW_PACKAGE" "$FLOW_OUTPUT_DIR/$evidence"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Android role $full_role was not stably held by $FLOW_PACKAGE."
  cat "$FLOW_OUTPUT_DIR/$evidence" 2>/dev/null || true
  adb shell dumpsys role > "$FLOW_OUTPUT_DIR/${evidence%.txt}-dumpsys.txt" 2>&1 || true
  return 1
}

for FLOW_ROLE in DIALER SMS; do
  adb shell cmd role add-role-holder --user 0 "android.app.role.$FLOW_ROLE" "$FLOW_PACKAGE"
  wait_role_held "android.app.role.$FLOW_ROLE" "role-${FLOW_ROLE,,}-held.txt"
done
# Android 10+ exposes ROLE_CALL_SCREENING. The runtime matrix starts at API 29 for Phone Core, so
# role ownership is a required precondition rather than an optional best-effort shell command.
adb shell cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$FLOW_PACKAGE"
wait_role_held android.app.role.CALL_SCREENING "role-call-screening-held.txt"

for FLOW_PERMISSION in CALL_PHONE READ_PHONE_STATE READ_CONTACTS READ_CALL_LOG SEND_SMS READ_SMS RECEIVE_SMS RECEIVE_MMS RECEIVE_WAP_PUSH; do
  adb shell pm grant "$FLOW_PACKAGE" "android.permission.$FLOW_PERMISSION"
done
if [[ "$FLOW_API" -ge 33 ]]; then
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
def matches(n, needle):
    if needle.startswith('phone_core_'):
        return n.get('resource-id') == needle and n.get('package') == 'com.sentinel.quantum'
    return needle in (n.get('text', '') + ' ' + n.get('content-desc', '') + ' ' + n.get('hint', ''))
sys.exit(0 if any(any(matches(n, needle) for needle in needles) for n in nodes) else 1)
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
wait_logcat_marker() {
  local marker="$1"
  local evidence="$2"
  for _ in $(seq 1 20); do
    adb logcat -d -v brief > "$FLOW_OUTPUT_DIR/$evidence" 2>/dev/null || true
    if grep -Fq "$marker" "$FLOW_OUTPUT_DIR/$evidence"; then return 0; fi
    sleep 0.5
  done
  echo "Expected PII-free Android lifecycle marker was not observed: $marker"
  return 1
}
wait_emulator_call_absent() {
  local number="$1"
  local evidence="$FLOW_OUTPUT_DIR/gsm-list-${number}.txt"
  local modem_status=0
  local telecom_evidence="$FLOW_OUTPUT_DIR/telecom-after-${number}.txt"
  for _ in $(seq 1 30); do
    set +e
    adb emu gsm list > "$evidence" 2>&1
    modem_status=$?
    set -e
    if [[ "$modem_status" -eq 0 ]] && ! grep -Fq "$number" "$evidence" &&
      adb shell dumpsys telecom > "$telecom_evidence" 2>&1 &&
      python3 "$FLOW_SCRIPT_DIR/phone-core-emulator-telecom-calls.py" "$telecom_evidence"; then
      return 0
    fi
    sleep 0.5
  done
  echo "Emulator modem could not prove call $number absent after bounded teardown wait (last gsm-list status: $modem_status)."
  cat "$evidence" 2>/dev/null || true
  return 1
}
tap_text() {
  fresh_ui
  local coordinates
  coordinates="$(python3 - "$FLOW_XML" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for node in ET.parse(sys.argv[1]).iter('node'):
    needle = sys.argv[2]
    matched = (node.get('resource-id') == needle and node.get('package') == 'com.sentinel.quantum') if needle.startswith('phone_core_') else needle in (node.get('text', '') + ' ' + node.get('content-desc', '') + ' ' + node.get('hint', ''))
    if matched:
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
private_timeline_has_event() {
  local direction="$1"
  local signal="$2"
  local kind="${3:-CALL}"
  local evidence="${4:-$FLOW_OUTPUT_DIR/phone-private-timeline-${direction,,}-${signal,,}.xml}"
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$evidence" 2>/dev/null && \
    python3 - "$evidence" "$direction" "$signal" "$kind" <<'PYTIMELINE'
import json, sys, xml.etree.ElementTree as ET
path, direction, signal, kind = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
    node = next((n for n in root.findall('string') if n.get('name') == 'events'), None)
    events = json.loads((node.text if node is not None else '') or '[]')
except Exception:
    sys.exit(1)
matched = any(
    event.get('kind') == kind and
    event.get('direction') == direction and
    event.get('signal') == signal
    for event in events
)
sys.exit(0 if matched else 1)
PYTIMELINE
}
wait_private_timeline_event() {
  local direction="$1"
  local signal="$2"
  local kind="${3:-CALL}"
  local evidence="$FLOW_OUTPUT_DIR/phone-private-timeline-${direction,,}-${signal,,}.xml"
  for _ in $(seq 1 20); do
    if private_timeline_has_event "$direction" "$signal" "$kind" "$evidence"; then
      return 0
    fi
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
wait_text "phone_core_tab_0"
capture 01-dialer-first-launch
adb shell am force-stop "$FLOW_PACKAGE"
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "phone_core_tab_0"
capture 01b-dialer-relaunch

adb shell input keyevent KEYCODE_SLEEP
adb emu gsm call "$FLOW_NUMBER"
# First prove Telecom actually bound Sentinel's screening service. This marker contains no number or
# identity. Android 10 can fail emergency-number classification on an emulator even after callback
# invocation, so CALL_SCREENED:* remains a stricter, separate rule-engine-decision proof.
wait_logcat_marker "CallScreeningService:onScreenCall" "call-screening-callback-logcat.txt"
wait_incoming_sentinel_surface
if [[ "$FLOW_API" -ge 36 ]]; then
  wait_private_timeline_signal_prefix "CALL_SCREENED:"
else
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml \
    > "$FLOW_OUTPUT_DIR/phone-private-timeline-api${FLOW_API}-screening.xml" 2>/dev/null || true
fi
capture 02-incoming-call
# CallerIdActivity can appear before Telecom finishes both screening callbacks on API 29.
# Accepting at that point races Telecom's later SET_RINGING and loses the ACTIVE transition.
# This event is recorded by InCallService only after it receives the ringing call and posts
# its notification; keep the later INCALL_ACTIVE assertion as the independent answer proof.
wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"
# Exercise Sentinel's answer path, not a modem-side answer on behalf of the application.
# An app-owned stable control plus the independent ACTIVE timeline event proves the effect.
adb shell am start -W -n "$FLOW_PACKAGE/.SentinelInCallActivity"
wait_text "phone_core_answer"
tap_text "phone_core_answer"
wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"
capture 03-incoming-active-evidence
adb emu gsm cancel "$FLOW_NUMBER"
# The emulator modem is the lifecycle authority for this synthetic call. A localized UI string is
# not a teardown oracle and can disappear before or after Telecom finishes releasing the account.
wait_emulator_call_absent "$FLOW_NUMBER"
capture 04-ended-call

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

# Place exactly once. Timeline persistence is asynchronous: retrying placement while waiting for
# INCALL_ACTIVE can create a second call and leave the first ACTIVE after the UI hangs up the second.
# Wait within the existing bounded observation window; never change production SIM authorization.
FLOW_OUTGOING_ACTIVE=0
FLOW_OUTGOING_EVIDENCE="$FLOW_OUTPUT_DIR/phone-private-timeline-outgoing-incall_active.xml"
adb shell am start -W -a android.intent.action.DIAL -d tel:5550101 -n "$FLOW_PACKAGE/.SentinelDialerActivity"
wait_text "phone_core_call"
tap_text "phone_core_call"
for FLOW_ATTEMPT in $(seq 1 12); do
  for _ in $(seq 1 3); do
    # The emulator modem may require an explicit transition from dialing to active.
    adb emu gsm accept 5550101 >/dev/null 2>&1 || true
    if private_timeline_has_event "OUTGOING" "INCALL_ACTIVE" "CALL" "$FLOW_OUTGOING_EVIDENCE"; then
      FLOW_OUTGOING_ACTIVE=1
      break
    fi
    sleep 0.5
  done
  if [[ "$FLOW_OUTGOING_ACTIVE" == "1" ]]; then
    break
  fi
  sleep 0.5
done
if [[ "$FLOW_OUTGOING_ACTIVE" != "1" ]]; then
  capture failure
  adb shell run-as "$FLOW_PACKAGE" cat shared_prefs/phone_private_timeline.xml > "$FLOW_OUTGOING_EVIDENCE" 2>/dev/null || true
  echo "Sentinel did not establish the outgoing emulator call within the bounded Telecom recovery window."
  exit 1
fi
# Preserve the canonical contract marker as a final assertion after the bounded recovery probe.
wait_private_timeline_event "OUTGOING" "INCALL_ACTIVE"
# Connected-state and app-owned in-call-surface proofs are separate: require both.
wait_private_timeline_event "LOCAL" "INCALL_UI_SHOWN"
wait_text "phone_core_hangup"
capture 05-outgoing-call
tap_text "phone_core_hangup"
wait_emulator_call_absent 5550101

adb shell am start -W -a android.intent.action.MAIN -n "$FLOW_PACKAGE/.SmsComposeActivity"
adb emu sms send "$FLOW_SMS_NUMBER" "Sentinel emulator reply test"
wait_text "Sentinel emulator reply test"
tap_text "Sentinel emulator reply test"
wait_text "phone_core_sms_reply"
capture 06-thread
tap_text "phone_core_sms_reply"
wait_reply_focus
adb shell input text ReplyFromSentinel
wait_text "ReplyFromSentinel"
tap_text "phone_core_sms_reply_send"
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
# Provider visibility alone is insufficient: require the real Android SENT callback.
# Recent emulator images also expose DELIVERED; this remains synthetic modem evidence.
wait_private_timeline_event "OUTGOING" "SMS_ALL_PARTS_SENT" "SMS"
if [[ "$FLOW_API" -ge 36 ]]; then
  wait_private_timeline_event "OUTGOING" "SMS_ALL_PARTS_DELIVERED" "SMS"
fi

for FLOW_ROLE in DIALER SMS CALL_SCREENING; do
  role_holders "android.app.role.$FLOW_ROLE" > "$FLOW_OUTPUT_DIR/role-${FLOW_ROLE,,}-final.txt" 2>&1 || true
  grep -Fxq "$FLOW_PACKAGE" "$FLOW_OUTPUT_DIR/role-${FLOW_ROLE,,}-final.txt"
done

echo "Synthetic Telecom callback/calls, cold relaunch, and inline SMS reply verified; physical validation remains pending."
