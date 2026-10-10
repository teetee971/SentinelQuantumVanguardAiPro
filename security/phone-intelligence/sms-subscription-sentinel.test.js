import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const sender = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt',
  'utf8'
);
const state = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsSubscriptionState.kt',
  'utf8'
);
const diagnostics = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsActivationDiagnostics.kt',
  'utf8'
);
const respondService = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelRespondViaMessageService.kt',
  'utf8'
);

test('SMS SIM truth rejects every negative subscription sentinel', () => {
  assert.match(
    state,
    /\.filter\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'the SIM selector must not expose a negative subscription as active'
  );
  assert.match(
    diagnostics,
    /\.filter\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'SMS readiness must not become READY from a negative subscription id'
  );
  assert.match(
    sender,
    /\.filter\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'SMS transport must use the shared active-subscription boundary'
  );
  assert.match(
    sender,
    /\.takeIf\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'SMS transport must reject a negative default subscription'
  );
  assert.match(
    respondService,
    /\.takeIf\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'quick reply must reject a negative platform default subscription'
  );
});
