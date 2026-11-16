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

test('incoming notification is replaced or cancelled only after observed Telecom state transition', () => {
  assert.match(receiver, /SentinelInCallService\.answer\(callId\)/);
  assert.match(receiver, /SentinelInCallService\.reject\(callId\)/);
  assert.doesNotMatch(
    receiver,
    /SentinelCallNotificationHelper\.cancel\(context\)/,
    'notification action receiver must not treat command submission as confirmed call state'
  );
  assert.match(
    service,
    /if \(selected\?\.state != Call\.STATE_RINGING\) \{[\s\S]*?selected\.state != Call\.STATE_DISCONNECTED && selected\.state != Call\.STATE_DISCONNECTING[\s\S]*?SentinelCallNotificationHelper\.showOngoing\(this, snapshot\)[\s\S]*?else \{\s*SentinelCallNotificationHelper\.cancel\(this\)/s,
    'InCallService must replace the ringing notification for a live call and cancel it on terminal/absent state'
  );
});

// Both forms use one notification identity, so ongoing publication replaces ringing actions.
test('incoming and ongoing notifications share a single replacement identity', () => {
  const helper = fs.readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt', 'utf8');
  assert.match(helper, /fun showIncoming[\s\S]*?showCall\(context, snapshot, incoming = true\)/);
  assert.match(helper, /fun showOngoing[\s\S]*?showCall\(context, snapshot, incoming = false\)/);
  assert.match(helper, /manager\.notify\(NOTIFICATION_ID, builder\.build\(\)\)/);
  assert.match(helper, /cancel\(NOTIFICATION_ID\)/);
});
