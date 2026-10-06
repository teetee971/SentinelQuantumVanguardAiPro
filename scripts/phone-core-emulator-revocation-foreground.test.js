import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const flow = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');
const runtime = flow.slice(flow.indexOf('trap write_summary EXIT'));

function ordered(...needles) {
  let cursor = -1;
  for (const needle of needles) {
    const next = runtime.indexOf(needle, cursor + 1);
    assert.ok(next > cursor, `expected ${JSON.stringify(needle)} after offset ${cursor}`);
    cursor = next;
  }
}

test('runtime-permission denial waits for a proven background transition before warm return', () => {
  ordered(
    'EFFECTIVE_PERMISSION_PROBE="SEND_SMS_RUNTIME_PERMISSION_REVOKED"',
    'adb shell input keyevent KEYCODE_HOME',
    'wait_app_backgrounded',
    'launch_sms_surface "sms-send-runtime-permission-denied-launch.txt" warm',
    'assert_send_sms_runtime_permission_denied "send-sms-runtime-permission-denied-after-launch.txt"'
  );
});

test('AppOp denial is re-applied after the foreground return and before UI qualification', () => {
  ordered(
    'EFFECTIVE_PERMISSION_PROBE="SEND_SMS_APP_OP_DENIED"',
    'set_send_sms_appop ignore "send-sms-appop-deny.txt"',
    'adb shell input keyevent KEYCODE_HOME',
    'wait_app_backgrounded',
    'launch_sms_surface "sms-send-appop-denied-launch.txt" warm',
    'assert_sms_role_held',
    'set_send_sms_appop ignore "send-sms-appop-reassert-after-launch.txt"',
    'assert_send_sms_appop_denied "send-sms-appop-denied-after-launch.txt"',
    'scroll_until_ui_contains "Envoi SMS : autorisation Android requise."',
    'assert_action_disabled "phone_core_sms_send"'
  );
});

test('background oracle observes resumed-activity state instead of using a fixed sleep', () => {
  const start = flow.indexOf('wait_app_backgrounded() {');
  const end = flow.indexOf('\ntimeline_signal_prefix_count() {', start);
  assert.ok(start >= 0 && end > start, 'wait_app_backgrounded function must exist');
  const fn = flow.slice(start, end);
  assert.match(fn, /dumpsys activity activities/);
  assert.match(fn, /mResumedActivity:.*com\\\.sentinel\\\.quantum/);
  assert.match(fn, /return 1/);
});
