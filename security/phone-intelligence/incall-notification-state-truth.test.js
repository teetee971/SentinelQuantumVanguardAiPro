import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallActionReceiver.kt',
  'utf8'
);
const service = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelInCallService.kt',
  'utf8'
);

test('incoming call notification is cancelled only after observed Telecom state transition', () => {
  assert.match(receiver, /SentinelInCallService\.answer\(callId\)/);
  assert.match(receiver, /SentinelInCallService\.reject\(callId\)/);
  assert.doesNotMatch(
    receiver,
    /SentinelCallNotificationHelper\.cancel\(context\)/,
    'notification action receiver must not treat command submission as confirmed call state'
  );
  assert.match(
    service,
    /if \(selected\?\.state != Call\.STATE_RINGING\) \{\s*SentinelCallNotificationHelper\.cancel\(this\)/s,
    'InCallService must own cancellation after observing a real non-ringing state'
  );
});
