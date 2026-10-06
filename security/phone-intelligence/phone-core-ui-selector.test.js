import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const revocation = fs.readFileSync('scripts/phone-core-emulator-revocation-flow.sh', 'utf8');
function extract(source, name, next) {
  const start = source.indexOf(`${name}() {`);
  const end = source.indexOf(`\n${next}() {`, start);
  assert.ok(start >= 0 && end > start);
  return source.slice(start, end);
}
function run(source, fn, next, invocation, xml) {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'phone-ui-selector-'));
  try {
    const file = path.join(temp, 'window.xml');
    fs.writeFileSync(file, xml);
    const script = `set -eu\nFLOW_XML="$1"\nXML="$1"\nfresh_ui() { :; }\ndump_ui() { :; }\nadb() { printf '%s\\n' "$*"; }\n${extract(source, fn, next)}\n${invocation}`;
    return spawnSync('bash', ['-c', script, 'test', file], { encoding: 'utf8' });
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
}
const node = (pkg, id, text, enabled = 'true') => `<node package="${pkg}" resource-id="${id}" text="${text}" content-desc="User accessibility label" bounds="[10,20][30,40]" clickable="true" enabled="${enabled}"/>`;

test('runtime taps the app-owned resource ID independently of language and accessibility text', () => {
  const xml = `<hierarchy>${node('other.package', 'phone_core_call', 'Appeler')}${node('com.sentinel.quantum', 'phone_core_call', 'Call')}</hierarchy>`;
  const result = run(flow, 'tap_text', 'open_incoming_call_notification', 'tap_text phone_core_call', xml);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /shell input tap 20 30/);
  const decoy = run(flow, 'tap_text', 'open_incoming_call_notification', 'tap_text phone_core_call', `<hierarchy>${node('other.package', 'phone_core_call', 'Appeler')}</hierarchy>`);
  assert.notEqual(decoy.status, 0);
});

test('revocation assertions reject missing or enabled stable controls with translated labels', () => {
  for (const [enabled, expected] of [['false', 0], ['true', 2]]) {
    const result = run(revocation, 'assert_action_disabled', 'assert_no_crash', 'assert_action_disabled phone_core_sms_send', `<hierarchy>${node('com.sentinel.quantum', 'phone_core_sms_send', 'Send', enabled)}</hierarchy>`);
    assert.equal(result.status, expected, result.stderr);
  }
  const missing = run(revocation, 'assert_action_disabled', 'assert_no_crash', 'assert_action_disabled phone_core_sms_send', `<hierarchy>${node('other.package', 'phone_core_sms_send', 'Envoyer', 'false')}</hierarchy>`);
  assert.equal(missing.status, 1);
});

test('revocation retains a disabled container match across following siblings without accepting unverified containers', () => {
  const action = node('com.sentinel.quantum', 'phone_core_sms_send', 'Send', 'false');
  const neighbor = node('com.sentinel.quantum', 'unrelated', 'Other content');
  const invoke = 'assert_action_disabled phone_core_sms_send';
  const result = run(revocation, 'assert_action_disabled', 'assert_no_crash', invoke, `<hierarchy>${action}${neighbor}</hierarchy>`);
  assert.equal(result.status, 0, result.stderr);
  const noContainer = run(revocation, 'assert_action_disabled', 'assert_no_crash', invoke, `<hierarchy>${action.replace('clickable="true"', 'clickable="false"')}${neighbor}</hierarchy>`);
  assert.equal(noContainer.status, 1);
  const enabled = run(revocation, 'assert_action_disabled', 'assert_no_crash', invoke, `<hierarchy>${action}${node('com.sentinel.quantum', 'phone_core_sms_send', 'Send', 'true')}${neighbor}</hierarchy>`);
  assert.equal(enabled.status, 2);
});

test('Phone Core automation never selects the six localized action labels', () => {
  for (const source of [flow, revocation]) {
    assert.doesNotMatch(source, /(?:wait_text|tap_text|wait_ui_contains|tap_ui_text|scroll_until_ui_contains|assert_action_disabled) "(?:Clavier|Appeler|Raccrocher|Décrocher|Répondre|Envoyer)"/);
  }
  const modifier = fs.readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/PhoneCoreTestIds.kt', 'utf8');
  assert.match(modifier, /testTagsAsResourceId = true/);
  assert.doesNotMatch(modifier, /contentDescription/);
});


test('incoming UI opens only through the matching Sentinel notification PendingIntent', () => {
  const xml = `<hierarchy><node package="com.android.systemui" resource-id="com.android.systemui:id/expandableNotificationRow" clickable="true">${node('com.android.systemui', 'android:id/app_name_text', 'Sentinel Quantum Vanguard')}${node('com.android.systemui', 'android:id/text', 'Incoming call')}</node></hierarchy>`;
  const result = run(flow, 'open_incoming_call_notification', 'wait_reply_focus', 'FLOW_NUMBER=5550100; capture() { :; }; sleep() { :; }; open_incoming_call_notification', xml);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /shell input tap 20 30/);
  const decoy = run(flow, 'open_incoming_call_notification', 'wait_reply_focus', 'FLOW_NUMBER=5550100; capture() { :; }; sleep() { :; }; open_incoming_call_notification', xml.replace('Sentinel Quantum Vanguard', 'Other Dialer'));
  assert.notEqual(decoy.status, 0);
  const wrongPackage = run(flow, 'open_incoming_call_notification', 'wait_reply_focus', 'capture() { :; }; sleep() { :; }; open_incoming_call_notification', xml.replaceAll('com.android.systemui', 'other.package'));
  assert.notEqual(wrongPackage.status, 0);
  assert.doesNotMatch(flow, /adb shell am start[^\n]*SentinelInCallActivity/);
});


test('notification navigation handles API 29 row IDs and API 37 compact caller headers without action labels', () => {
  const api29 = `<hierarchy><node package="com.android.systemui" clickable="true">${node('com.android.systemui', 'android:id/app_name_text', 'Sentinel Quantum Vanguard')}${node('com.android.systemui', 'android:id/text', 'Appel entrant')}</node></hierarchy>`;
  const compactRow = `<node package="com.android.systemui" resource-id="com.android.systemui:id/expandableNotificationRow" clickable="true">${node('com.android.systemui', 'android:id/title', '5550100 · Aucune information locale')}${node('com.android.systemui', 'android:id/text', 'Incoming call')}</node>`;
  const invoke = 'FLOW_NUMBER=5550100; capture() { :; }; sleep() { :; }; open_incoming_call_notification';
  for (const xml of [api29, `<hierarchy>${compactRow}</hierarchy>`]) {
    const result = run(flow, 'open_incoming_call_notification', 'wait_reply_focus', invoke, xml);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /shell input tap 20 30/);
  }
  for (const xml of [`<hierarchy>${compactRow}${compactRow}</hierarchy>`, `<hierarchy>${compactRow.replace('5550100 ·', '15550100 ·')}</hierarchy>`]) {
    const result = run(flow, 'open_incoming_call_notification', 'wait_reply_focus', invoke, xml);
    assert.notEqual(result.status, 0);
    assert.doesNotMatch(result.stdout, /shell input tap/);
  }
});
