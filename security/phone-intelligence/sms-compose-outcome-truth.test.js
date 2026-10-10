import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const compose = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

test('SMS compose observes an indeterminate telephony submission without accepting it', () => {
  const unknownOutcome = compose.indexOf('val outcomeCanBeObserved');
  const acceptedBranch = compose.indexOf('if (result.accepted || outcomeCanBeObserved)');
  assert.ok(unknownOutcome >= 0, 'the compose screen must identify the indeterminate outcome');
  assert.ok(acceptedBranch > unknownOutcome, 'the indeterminate outcome must feed the state branch');
  assert.match(
    compose.slice(unknownOutcome, acceptedBranch),
    /result\.reason\s*==\s*"TELEPHONY_SUBMISSION_OUTCOME_UNKNOWN"[\s\S]*result\.sendToken\s*!=\s*null[\s\S]*result\.providerMessageId\s*!=\s*null/
  );

  const observedBranch = compose.slice(acceptedBranch, compose.indexOf('\n                            }', acceptedBranch));
  assert.match(observedBranch, /activeSendToken\s*=\s*result\.sendToken/);
  assert.match(observedBranch, /activeProviderMessageId\s*=\s*result\.providerMessageId/);
  assert.match(observedBranch, /if \(result\.accepted\) onAccepted\(\)/);
});

test('SMS compose does not clear an indeterminate draft or retry it automatically', () => {
  const unknownOutcome = compose.indexOf('val outcomeCanBeObserved');
  const nextSubmission = compose.indexOf('fun submitMms', unknownOutcome);
  const block = compose.slice(unknownOutcome, nextSubmission);
  assert.match(block, /if \(result\.accepted\) onAccepted\(\)/);
  const submitStart = compose.indexOf('fun submitSms');
  const submitBlock = compose.slice(submitStart, nextSubmission);
  assert.equal(
    (submitBlock.match(/sender\.send\(/g) ?? []).length,
    1,
    'an indeterminate result must not trigger an automatic second telephony submission'
  );
});
