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
const activity = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelInCallActivity.kt',
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

test('incoming notification fails closed when Android denies full-screen intent use', () => {
  assert.match(
    helper,
    /fun isFullScreenIntentAllowed\(context: Context\): Boolean/,
    'the notification helper must expose one runtime truth for full-screen permission'
  );
  assert.match(
    helper,
    /canUseFullScreenIntent\(\) == true/,
    'API 34+ full-screen intent permission must be observed from NotificationManager'
  );
  assert.match(
    helper,
    /if \(!isFullScreenIntentAllowed\(context\)\) return false/,
    'notification submission must not be reported when full-screen use is denied'
  );
});

test('in-call UI evidence waits for a rendered frame before recording visibility', () => {
  assert.match(
    activity,
    /withFrameNanos\s*\{\s*\}/,
    'UI evidence must wait for Compose to reach a frame boundary'
  );
  assert.match(
    activity,
    /withFrameNanos\s*\{\s*\}[\s\S]*SentinelInCallService\.sessions\.value\.primary\?\.id/s,
    'the evidence path must re-check the live Telecom session after the frame'
  );
});

test('Telecom callback registration is idempotent across platform reconciliation', () => {
  assert.match(
    service,
    /private fun trackCall\(call: Call\): Boolean[\s\S]*?if \(!callbacksRegistered\.contains\(call\)\)[\s\S]*?call\.registerCallback\(callback/s,
    'callback registration must be centralized behind an idempotent tracked-call guard'
  );
  assert.match(
    service,
    /override fun onCallAdded\(call: Call\)[\s\S]*?trackCall\(call\)/,
    'onCallAdded must use the same guarded registration path as platform reconciliation'
  );
});
