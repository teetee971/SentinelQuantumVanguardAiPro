import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMissedCallReceiver.kt',
  'utf8'
);

test('missed-call notification returns Telecom clear intent on dismissal', () => {
  assert.match(
    receiver,
    /android\.telecom\.extra\.CLEAR_MISSED_CALLS_INTENT/,
    'receiver must read Telecom clear-missed-calls contract without relying on hidden SDK stubs'
  );
  assert.match(receiver, /readClearMissedCallsIntent\(intent\)/);
  assert.match(receiver, /clearMissedCalls\?\.let\(builder::setDeleteIntent\)/);
  assert.match(receiver, /coerceIn\(0, MAX_MISSED_CALL_COUNT\)/);
});
