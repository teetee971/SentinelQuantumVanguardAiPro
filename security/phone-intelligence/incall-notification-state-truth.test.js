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
const helper = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelCallNotificationHelper.kt',
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
  assert.match(helper, /fun showIncoming[\s\S]*?showCall\(context, snapshot, incoming = true\)/);
  assert.match(helper, /fun showOngoing[\s\S]*?showCall\(context, snapshot, incoming = false\)/);
  assert.match(helper, /manager\.notify\(NOTIFICATION_ID, builder\.build\(\)\)/);
  assert.match(helper, /cancel\(NOTIFICATION_ID\)/);
});

test('the ongoing notification sends its disconnect action to Telecom for the bound call', () => {
  assert.match(helper, /CallStyle\.forOngoingCall\(\s*caller,\s*callAction\(ACTION_DISCONNECT, "disconnect", 3\)/);
  assert.match(
    receiver,
    /intent\.action == SentinelCallNotificationHelper\.ACTION_DISCONNECT && kind == "disconnect"\s*->\s*SentinelInCallService\.disconnect\(callId\)/,
    'the receiver must dispatch the action emitted by the ongoing call notification'
  );
});

test('a notification action cannot substitute another current call for a wrong or stale identity', () => {
  assert.match(helper, /Uri\.encode\(snapshot\.id\)[\s\S]*?"\/\$kind"[\s\S]*?putExtra\(EXTRA_CALL_ID, snapshot\.id\)/);
  assert.match(helper, /PendingIntent\.FLAG_UPDATE_CURRENT or PendingIntent\.FLAG_IMMUTABLE/);
  assert.match(receiver, /val callId = path\[0\]\.takeIf \{ CALL_ID\.matches\(it\) \} \?: return/);
  assert.match(receiver, /if \(intent\.getStringExtra\(SentinelCallNotificationHelper\.EXTRA_CALL_ID\) != callId\) return/);
  assert.match(service, /override fun onCallRemoved\(call: Call\) \{[\s\S]*?callIds\.remove\(call\)/);
  assert.match(
    service,
    /private fun callById\(id: String\): Call\? = activeService\?\.let \{ service ->\s*service\.callIds\.entries\.firstOrNull \{ it\.value == id \}\?\.key\s*\}/,
    'only the exact call ID of the active Telecom service is eligible'
  );
  assert.match(service, /fun disconnect\(id: String\): Boolean = callById\(id\)\?\.let \{ call ->\s*if \(call\.state == Call\.STATE_DISCONNECTED \|\| call\.state == Call\.STATE_DISCONNECTING\) return@let false/);
});
