import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt',
  'utf8'
);
const deliveryWorker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingSmsDeliveryWorker.kt',
  'utf8'
);

test('incoming SMS persists the modern subscription extra before the legacy fallback', () => {
  assert.match(receiver, /EXTRA_SUBSCRIPTION_INDEX\s*=\s*"android\.telephony\.extra\.SUBSCRIPTION_INDEX"/);
  assert.match(receiver, /EXTRA_LEGACY_SUBSCRIPTION\s*=\s*"subscription"/);
  assert.match(
    receiver,
    /sequenceOf\(\s*intent\.getIntExtra\(EXTRA_SUBSCRIPTION_INDEX,[\s\S]*?intent\.getIntExtra\(EXTRA_LEGACY_SUBSCRIPTION,/,
    'modern Android subscription identity must take priority over the legacy extra'
  );
  assert.match(
    receiver,
    /firstOrNull\s*\{\s*it != SubscriptionManager\.INVALID_SUBSCRIPTION_ID && it >= 0\s*\}/,
    'invalid or sentinel subscription ids must not be persisted'
  );
  assert.match(
    deliveryWorker,
    /record\.subscriptionId\?\.let\s*\{\s*put\(Telephony\.Sms\.SUBSCRIPTION_ID, it\)\s*\}/,
    'the validated durable incoming subscription must be written into the canonical SMS provider row'
  );
});
