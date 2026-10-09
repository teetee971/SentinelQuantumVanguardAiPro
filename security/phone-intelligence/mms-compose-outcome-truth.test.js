import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const compose = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);
const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSendStatusReceiver.kt',
  'utf8'
);
const feedback = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsTransportFeedback.kt',
  'utf8'
);

test('MMS compose observes accepted and indeterminate transport outcomes by opaque identity', () => {
  assert.match(compose, /activeMmsToken/);
  assert.match(compose, /MmsTransportStatusBus\.events\.collectLatest/);
  const branch = compose.slice(
    compose.indexOf('val outcomeCanBeObserved', compose.indexOf('fun submitMms')),
    compose.indexOf('\n                        } finally', compose.indexOf('fun submitMms'))
  );
  assert.match(
    branch,
    /result\.token\s*!=\s*null[\s\S]*result\.providerMessageId\s*!=\s*null[\s\S]*MMS_SUBMISSION_OUTCOME_UNKNOWN/
  );
  assert.match(branch, /activeMmsToken\s*=\s*result\.token/);
  assert.match(branch, /activeMmsProviderMessageId\s*=\s*result\.providerMessageId/);
  assert.match(branch, /if \(result\.accepted\) onAccepted\(\)/);
});

test('MMS callback publishes both normal and repair-path transport feedback', () => {
  assert.match(receiver, /MmsTransportStatusBus\.publish\([\s\S]*providerWriteSucceeded = false/);
  assert.match(receiver, /MmsTransportStatusBus\.publish\([\s\S]*providerWriteSucceeded = providerUpdated/);
  assert.match(receiver, /outcome = MmsSendResultClassifier\.classify/);
});

test('MMS UI wording never upgrades transport success into recipient delivery', () => {
  assert.match(feedback, /transmission MMS/);
  assert.match(feedback, /réception par le destinataire n’est pas confirmée/);
  assert.match(feedback, /Aucun succès destinataire n’est déduit/);
});
