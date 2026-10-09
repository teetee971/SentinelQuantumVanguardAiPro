import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);

test('dialer role requests contain OEM launch failures and clear pending intent', () => {
  assert.match(
    dialer,
    /private fun launchDialerRoleRequest\(request: Intent, pendingCall: Boolean\) \{[\s\S]*?dialerRoleLauncher\.launch\(request\)[\s\S]*?recentsDialerRoleLauncher\.launch\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'both dialer role launchers must share a framework exception boundary'
  );
  assert.match(
    dialer,
    /catch \(_: ActivityNotFoundException\)[\s\S]*?pendingNumber = null[\s\S]*?callActionStatus =/,
    'a failed call-role request must not retain a stale number for a later action'
  );
  assert.equal(
    (dialer.match(/dialerRoleLauncher\.launch\(/g) ?? []).length,
    1,
    'ordinary role requests must use the guarded launcher'
  );
  assert.equal(
    (dialer.match(/recentsDialerRoleLauncher\.launch\(/g) ?? []).length,
    1,
    'history role requests must use the guarded launcher'
  );
});

test('role request callers use the shared launch boundary', () => {
  assert.match(
    dialer,
    /if \(request != null\) \{[\s\S]*?launchDialerRoleRequest\(request, pendingCall = true\)/,
    'the outgoing-call role path must use the shared boundary'
  );
  assert.match(
    dialer,
    /if \(request != null\) \{[\s\S]*?launchDialerRoleRequest\(request, pendingCall = false\)/,
    'the recents role path must use the shared boundary'
  );
});
