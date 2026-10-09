import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const numberSearch = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NumberSearchScreen.kt',
  'utf8'
);
const callBlocking = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CallBlockingScreen.kt',
  'utf8'
);
const wifiRisk = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WifiRiskEvaluator.kt',
  'utf8'
);

test('Phone Core visible fallback copy stays French', () => {
  assert.doesNotMatch(numberSearch, /Caller ID/);
  assert.doesNotMatch(callBlocking, /Caller ID/);
  assert.match(numberSearch, /identification de l’appelant/);
  assert.match(callBlocking, /identification de l’appelant/);
  assert.match(wifiRisk, /HIDDEN_SSID_LABEL = "\(SSID inconnu\)"/);
  assert.doesNotMatch(wifiRisk, /unknown ssid/i);
});
