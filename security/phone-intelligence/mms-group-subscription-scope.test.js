import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsConversationStore.kt',
  'utf8'
);
const plan = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsProjectionPlan.kt',
  'utf8'
);

test('incoming MMS group self identity is scoped to the receiving subscription', () => {
  assert.match(store, /readSelfAddresses\(plan\.subscriptionId\)/);
  assert.match(store, /private fun readSelfAddresses\(subscriptionId: Int\): Set<String>/);
  assert.match(store, /MmsSubscriptionResolver\.isValidSubscriptionId\(subscriptionId\)/);
  assert.match(store, /subscriptionManager\.getPhoneNumber\(subscriptionId\)/);
  assert.match(store, /firstOrNull \{ it\.subscriptionId == subscriptionId \}/);
  assert.match(store, /createForSubscriptionId\(subscriptionId\)\.line1Number/);
  assert.doesNotMatch(
    store,
    /activeSubscriptionInfoList\.orEmpty\(\)\.forEach\s*\{\s*info\s*->/
  );
});

test('related MMS provider projection preserves presentation root and part references', () => {
  assert.match(plan, /val smilCount = safeParts\.count \{ it\.mimeType == SMIL_MIME \}/);
  assert.match(plan, /RELATED_SMIL_REQUIRED/);
  assert.match(plan, /RELATED_PART_REFERENCE_REQUIRED/);
  assert.match(store, /is IncomingMmsProjectionPlan\.Part\.Smil/);
  assert.match(store, /put\(Telephony\.Mms\.Part\.SEQ, SMIL_SEQUENCE\)/);
  assert.match(store, /const val SMIL_SEQUENCE = -1/);
  assert.match(store, /Telephony\.Mms\.Part\.CONTENT_ID/);
  assert.match(store, /Telephony\.Mms\.Part\.CONTENT_LOCATION/);
  assert.match(store, /insertedCount == plan\.parts\.size && insertedCount > 0/);
});
