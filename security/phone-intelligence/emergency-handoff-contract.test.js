import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const handoff = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/EmergencyCallHandoff.kt',
  'utf8'
);

test('emergency handoff targets an external Android phone surface without choosing a SIM', () => {
  assert.match(handoff, /Intent\.ACTION_DIAL/);
  assert.match(handoff, /telecom\.systemDialerPackage/);
  assert.match(handoff, /telecom\.defaultDialerPackage/);
  assert.match(handoff, /resolvedPackage == context\.packageName/);
  assert.doesNotMatch(handoff, /TelecomManager\.EXTRA_PHONE_ACCOUNT_HANDLE/);
  assert.doesNotMatch(handoff, /placeCall\(/);
  assert.doesNotMatch(handoff, /ACTION_CALL/);
});
