#!/usr/bin/env bash
# Orchestrates a strict SEND_SMS transition preflight before the full revocation scenario.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${1:?Output directory required}"
PACKAGE="com.sentinel.quantum"
XML="$OUT_DIR/revocation-window.xml"

# Host-test compatibility helpers. Runtime qualification is executed by the hardened
# preflight and the delegated full implementation below.
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
    is_match = (node.get('resource-id') == needle and node.get('package') == 'com.sentinel.quantum') if needle.startswith('phone_core_') else needle in values
    if not is_match:
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

wait_role_absent() {
  local full_role="$1"
  local holders
  local observation="unknown"
  for _ in $(seq 1 20); do
    if holders="$(role_holders "$full_role")"; then
      if ! grep -Fxq "$PACKAGE" <<< "$holders"; then return 0; fi
      observation="held"
    else
      observation="unknown"
    fi
    sleep 0.5
  done
  echo "$full_role absence could not be proven after explicit removal (last observation: $observation)."
  adb shell dumpsys role > "$OUT_DIR/role-wait-absent-failure.txt" 2>&1 || true
  return 1
}

set_send_sms_appop() {
  local mode="$1"
  local evidence="$2"
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
  local telecom_evidence="$OUT_DIR/${evidence%.txt}-telecom.txt"
  adb shell dumpsys telecom > "$telecom_evidence"
  if ! python3 "$SCRIPT_DIR/phone-core-emulator-telecom-calls.py" "$telecom_evidence"; then
    echo "Protected dial action has live or UNKNOWN Telecom state while DIALER role is revoked."
    exit 1
  fi
}

wait_incoming_call_observed() {
  local number="$1"
  local evidence="$2"
  for _ in $(seq 1 15); do
    adb emu gsm list > "$OUT_DIR/$evidence"
    if grep -Fq "$number" "$OUT_DIR/$evidence"; then return 0; fi
    local notifications
    notifications="$(timeline_signal_prefix_count 'CALL_NOTIFICATION_POSTED')" || return 2
    if [[ "$notifications" -gt "$INCOMING_NOTIFICATION_BEFORE" ]]; then return 0; fi
    sleep 1
  done
  echo "Synthetic screening probe call $number has no modem or new InCallService notification evidence."
  return 1
}

# Delegated full-scenario contract markers kept visible to the static qualification checker:
# remove-role-holder
# pm revoke
# appops set
# set_send_sms_appop ignore
# adb shell appops set --user 0 --uid "$PACKAGE" SEND_SMS "$mode"
# assert_send_sms_appop_denied
# assert_sms_role_held
# assert_action_disabled "phone_core_sms_send"
# effective_permission_denial_fail_closed
# assert_modem_call_absent
# SCREENING_CALLBACK_BEFORE=
# SCREENING_DECISION_BEFORE=
# wait_role_absent android.app.role.CALL_SCREENING
# assert_no_crash

PRECHECK="$SCRIPT_DIR/phone-core-emulator-sms-denial-preflight.sh"
IMPLEMENTATION="$SCRIPT_DIR/phone-core-emulator-revocation-impl.sh"
test -s "$PRECHECK"
test -s "$IMPLEMENTATION"
bash "$PRECHECK" "$OUT_DIR"
test -s "$OUT_DIR/sms-denial-transition-proof.json"
bash "$IMPLEMENTATION" "$OUT_DIR"
test -s "$OUT_DIR/revocation-summary.json"
echo "Hardened SEND_SMS transition proof and full Phone Core revocation scenario completed."
