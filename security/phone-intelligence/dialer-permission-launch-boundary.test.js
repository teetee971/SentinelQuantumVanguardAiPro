import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);

test('dialer permission requests share a visible OEM/framework failure boundary', () => {
  assert.match(
    dialer,
    /private fun launchPermissionOrReport\([\s\S]*?launcher\.launch\(permission\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'dialer permission prompts must be guarded against missing or rejected Android surfaces'
  );
  for (const launcher of [
    'callPermissionLauncher',
    'phoneStatePermissionLauncher',
    'callLogPermissionLauncher',
    'contactsPermissionLauncher'
  ]) {
    assert.doesNotMatch(
      dialer,
      new RegExp(`${launcher}\\.launch\\(`),
      `${launcher} must not bypass the shared permission boundary`
    );
  }
});

test('a failed dialer permission prompt clears pending action state', () => {
  assert.match(
    dialer,
    /private fun launchPermissionOrReport\([\s\S]*?onFailure\(\)[\s\S]*?callActionStatus = failureMessage/s,
    'permission launch failure must expose customer-visible status and cleanup'
  );
  assert.match(
    dialer,
    /launchPermissionOrReport\([\s\S]*?onFailure = \{\s*pendingNumber = null/s,
    'CALL_PHONE and READ_PHONE_STATE launch failures must not retain a stale number'
  );
});
