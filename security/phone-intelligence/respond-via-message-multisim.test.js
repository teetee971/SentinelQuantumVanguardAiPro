import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const service = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelRespondViaMessageService.kt',
  'utf8'
);

test('platform quick replies prefer the incoming-call subscription, then Android SMS default', () => {
  assert.match(service, /android\.telephony\.extra\.SUBSCRIPTION_INDEX/);
  assert.match(service, /EXTRA_LEGACY_SUBSCRIPTION\s*=\s*"subscription"/);
  assert.match(
    service,
    /val quickReplySubscriptionId = intentSubscriptionId \?: platformDefaultSmsSubscriptionId/,
    'Telecom-provided call subscription must take priority over the device SMS default'
  );
  assert.match(service, /SubscriptionManager\.getDefaultSmsSubscriptionId\(\)/);
  assert.match(
    service,
    /takeUnless\s*\{\s*it == SubscriptionManager\.INVALID_SUBSCRIPTION_ID\s*\}/
  );
  assert.match(
    service,
    /SentinelSmsSender\(appContext\)\.send\(destination, body, subscriptionId\)/,
    'SMS quick reply must forward the resolved subscription when one is available'
  );
  assert.match(
    service,
    /\?: SentinelSmsSender\(appContext\)\.send\(destination, body\)/,
    'missing subscription evidence must keep the existing fail-closed sender path'
  );
  assert.match(
    service,
    /requestedSubscriptionId\s*=\s*quickReplySubscriptionId/,
    'MMS quick reply must use the same resolved subscription contract'
  );
});
