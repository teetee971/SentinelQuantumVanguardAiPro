import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const manifest = fs.readFileSync(
  'native-android-app/app/src/main/AndroidManifest.xml',
  'utf8'
);

function receiverBlock(androidName) {
  const escapedName = androidName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const pattern = new RegExp(
    `<receiver\\b(?=[^>]*android:name="${escapedName}")[^>]*>[\\s\\S]*?<\\/receiver>`,
    'm'
  );
  return manifest.match(pattern)?.[0] ?? '';
}

test('default-dialer missed-call receiver is reachable only through privileged Telecom sender', () => {
  const receiver = receiverBlock('.security.SentinelMissedCallReceiver');
  assert.ok(receiver, 'SentinelMissedCallReceiver must be declared');
  assert.match(receiver, /android:exported="true"/);
  assert.match(
    receiver,
    /android:permission="android\.permission\.MODIFY_PHONE_STATE"/,
    'external Telecom broadcast must remain protected by a signature/privileged sender permission'
  );
  assert.match(
    receiver,
    /android\.telecom\.action\.SHOW_MISSED_CALLS_NOTIFICATION/,
    'receiver must expose the Android default-dialer missed-call contract'
  );
});
