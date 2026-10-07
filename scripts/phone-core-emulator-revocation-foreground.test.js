import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const flow = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');
const runtime = flow.slice(flow.indexOf('trap write_summary EXIT'));
const productionGate = JSON.parse(
  readFileSync(new URL('../config/phone-core-production-gates.json', import.meta.url), 'utf8')
);

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
    'SMS_AUTHORIZATION_PROBE="SEND_SMS_RUNTIME_PERMISSION_REVOKED"',
    'adb shell input keyevent KEYCODE_HOME',
    'wait_app_backgrounded',
    'launch_sms_surface "sms-send-runtime-permission-denied-launch.txt" warm',
    'assert_send_sms_runtime_permission_denied "send-sms-runtime-permission-denied-after-launch.txt"'
  );
});

test('modern Android archives role absence and raw permission truth before and after relaunch', () => {
  ordered(
    'elif [[ "$ANDROID_API" -ge 36 ]]',
    'SMS_AUTHORIZATION_PROBE="SEND_SMS_ROLE_AUTHORIZATION_REVOKED"',
    'adb shell input keyevent KEYCODE_HOME',
    'wait_app_backgrounded',
    'remove_role_holder android.app.role.SMS',
    'wait_role_absent android.app.role.SMS "send-sms-role-managed-role-absent-state.txt"',
    'archive_send_sms_runtime_permission_state "send-sms-role-managed-permission-state.txt"',
    'launch_sms_surface "sms-send-role-managed-authorization-denied-launch.txt" warm',
    'wait_role_absent android.app.role.SMS "send-sms-role-managed-role-absent-after-launch.txt"',
    'archive_send_sms_runtime_permission_state "send-sms-role-managed-permission-state-after-launch.txt"',
    'scroll_until_ui_contains "$SMS_AUTHORIZATION_UI_NEEDLE"',
    'assert_action_disabled "phone_core_sms_send"',
    'capture 08-sms-authorization-denied'
  );

  const modernStart = runtime.indexOf('elif [[ "$ANDROID_API" -ge 36 ]]');
  const legacyStart = runtime.indexOf('\nelse\n  adb shell pm grant "$PACKAGE" android.permission.SEND_SMS', modernStart);
  assert.ok(modernStart >= 0 && legacyStart > modernStart, 'modern and legacy denial branches must be distinct');
  const modernBranch = runtime.slice(modernStart, legacyStart);
  assert.doesNotMatch(modernBranch, /set_send_sms_appop/);
  assert.doesNotMatch(modernBranch, /assert_send_sms_appop_denied/);
  assert.doesNotMatch(modernBranch, /wait_send_sms_runtime_permission_denied/);
  assert.doesNotMatch(modernBranch, /assert_send_sms_runtime_permission_denied/);
});

test('legacy AppOp denial remains isolated behind the pre-API36 fallback', () => {
  ordered(
    'elif [[ "$ANDROID_API" -ge 36 ]]',
    'else\n  adb shell pm grant "$PACKAGE" android.permission.SEND_SMS',
    'SMS_AUTHORIZATION_PROBE="SEND_SMS_APP_OP_DENIED"',
    'set_send_sms_appop ignore "send-sms-appop-deny.txt"',
    'adb shell input keyevent KEYCODE_HOME',
    'wait_app_backgrounded',
    'launch_sms_surface "sms-send-appop-denied-launch.txt" warm',
    'assert_sms_role_held',
    'set_send_sms_appop ignore "send-sms-appop-reassert-after-launch.txt"',
    'assert_send_sms_appop_denied "send-sms-appop-denied-after-launch.txt"',
    'scroll_until_ui_contains "$SMS_AUTHORIZATION_UI_NEEDLE"',
    'assert_action_disabled "phone_core_sms_send"'
  );
});

test('emulator scope note distinguishes permission, role-authorization and legacy AppOp denial paths', () => {
  const note = productionGate.emulator_qualification?.scope_note || '';
  assert.match(note, /ROLE_SMS.*d[ée]tenu/i);
  assert.match(note, /retir|retrait/i);
  assert.match(note, /grant brut SEND_SMS/i);
  assert.match(note, /archive/i);
  assert.match(note, /AppOp/i);
  assert.match(note, /API 36/i);
  assert.match(note, /SmsManager/i);
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
