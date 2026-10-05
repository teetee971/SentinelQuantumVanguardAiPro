import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const service = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelRespondViaMessageService.kt',
  'utf8'
);

test('platform quick replies reuse Android default SMS subscription without weakening fail-closed selection', () => {
  assert.match(service, /SubscriptionManager\.getDefaultSmsSubscriptionId\(\)/);
  assert.match(
    service,
    /takeUnless\s*\{\s*it == SubscriptionManager\.INVALID_SUBSCRIPTION_ID\s*\}/
  );
  assert.match(
    service,
    /SentinelSmsSender\(appContext\)\.send\(destination, body, subscriptionId\)/,
    'SMS quick reply must forward Android default subscription when one is available'
  );
  assert.match(
    service,
    /\?: SentinelSmsSender\(appContext\)\.send\(destination, body\)/,
    'missing Android default must keep the existing fail-closed sender path'
  );
  assert.match(
    service,
    /requestedSubscriptionId\s*=\s*platformDefaultSmsSubscriptionId/,
    'MMS quick reply must use the same Android default subscription contract'
  );
});
