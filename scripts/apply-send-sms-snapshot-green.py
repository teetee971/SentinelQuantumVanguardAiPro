from pathlib import Path
import re

path = Path('scripts/phone-core-emulator-revocation-flow.sh')
source = path.read_text()

replacements = {
    'permission_granted': r'''permission_granted() {
  local permission="$1"
  local snapshot_file="${2:-}"
  local snapshot escaped_permission
  if [[ -n "$snapshot_file" ]]; then
    if ! snapshot="$(cat "$snapshot_file" 2>/dev/null)"; then
      return 2
    fi
  elif ! snapshot="$(adb shell dumpsys package "$PACKAGE" 2>&1)"; then
    return 2  # UNKNOWN: ADB failure is not evidence of denial.
  fi
  escaped_permission="${permission//./\\.}"
  local granted=false denied=false
  if grep -Eq "^[[:space:]]*${escaped_permission}: granted=true([,[:space:]]|$)" <<< "$snapshot"; then
    granted=true
  fi
  if grep -Eq "^[[:space:]]*${escaped_permission}: granted=false([,[:space:]]|$)" <<< "$snapshot"; then
    denied=true
  fi
  if [[ "$granted" == true && "$denied" == false ]]; then return 0; fi
  if [[ "$granted" == false && "$denied" == true ]]; then return 1; fi
  return 2  # UNKNOWN: missing or contradictory permission state.
}
''',
    'assert_send_sms_runtime_permission_granted': r'''assert_send_sms_runtime_permission_granted() {
  local evidence="$1"
  if ! adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then
    echo "SEND_SMS runtime grant baseline is unreadable."
    return 1
  fi
  local state=0
  permission_granted android.permission.SEND_SMS "$OUT_DIR/$evidence" || state=$?
  if [[ "$state" -ne 0 ]]; then
    echo "SEND_SMS runtime grant baseline is not uniquely proven."
    grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
    return 1
  fi
}
''',
    'archive_send_sms_runtime_permission_state': r'''archive_send_sms_runtime_permission_state() {
  local evidence="$1"
  if ! adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then
    echo "SEND_SMS runtime permission state is unreadable."
    return 1
  fi
  local state=0
  permission_granted android.permission.SEND_SMS "$OUT_DIR/$evidence" || state=$?
  if [[ "$state" -eq 2 ]]; then
    echo "SEND_SMS runtime permission state is ambiguous."
    grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
    return 1
  fi
}
''',
    'assert_send_sms_runtime_permission_denied': r'''assert_send_sms_runtime_permission_denied() {
  local evidence="$1"
  if ! adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then
    echo "SEND_SMS runtime permission denial state is unreadable."
    return 1
  fi
  local state=0
  permission_granted android.permission.SEND_SMS "$OUT_DIR/$evidence" || state=$?
  if [[ "$state" -ne 1 ]]; then
    echo "SEND_SMS runtime permission denial is not uniquely proven."
    grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
    return 1
  fi
}
''',
    'wait_send_sms_runtime_permission_denied': r'''wait_send_sms_runtime_permission_denied() {
  local evidence="$1"
  for _ in $(seq 1 20); do
    if adb shell dumpsys package "$PACKAGE" > "$OUT_DIR/$evidence" 2>&1; then
      local state=0
      permission_granted android.permission.SEND_SMS "$OUT_DIR/$evidence" || state=$?
      if [[ "$state" -eq 1 ]]; then
        return 0
      fi
    fi
    sleep 0.5
  done
  echo "SEND_SMS runtime permission did not become uniquely and observably denied after role removal."
  grep -E 'android\.permission\.SEND_SMS:' "$OUT_DIR/$evidence" || true
  return 1
}
''',
}

for name, replacement in replacements.items():
    pattern = re.compile(rf'^{re.escape(name)}\(\) \{{\n.*?^\}}\n', re.MULTILINE | re.DOTALL)
    source, count = pattern.subn(lambda _: replacement, source, count=1)
    if count != 1:
        raise SystemExit(f'expected exactly one {name} function, replaced {count}')

path.write_text(source)
