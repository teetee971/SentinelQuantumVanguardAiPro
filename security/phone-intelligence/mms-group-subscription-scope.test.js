import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsConversationStore.kt',
  'utf8'
);

test('incoming MMS group self identity is scoped to the receiving subscription', () => {
  assert.match(store, /readSelfAddresses\(plan\.subscriptionId\)/);
  assert.match(store, /private fun readSelfAddresses\(subscriptionId: Int\): Set<String>/);
  assert.match(store, /SubscriptionManager\.isValidSubscriptionId\(subscriptionId\)/);
  assert.match(store, /subscriptionManager\.getPhoneNumber\(subscriptionId\)/);
  assert.match(store, /firstOrNull \{ it\.subscriptionId == subscriptionId \}/);
  assert.match(store, /createForSubscriptionId\(subscriptionId\)\.line1Number/);
  assert.doesNotMatch(
    store,
    /activeSubscriptionInfoList\.orEmpty\(\)\.forEach\s*\{\s*info\s*->/
  );
});
