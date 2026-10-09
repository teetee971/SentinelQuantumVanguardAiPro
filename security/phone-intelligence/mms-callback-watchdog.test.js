import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const journal = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsProviderJournal.kt',
  'utf8'
);
const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsConversationStore.kt',
  'utf8'
);
const sender = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSender.kt',
  'utf8'
);
const eligibility = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSendEligibilityPolicy.kt',
  'utf8'
);
const statusReceiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSendStatusReceiver.kt',
  'utf8'
);
const cleanup = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSendCleanupWorker.kt',
  'utf8'
);

test('MMS provider journal exposes stale submitted transport attempts', () => {
  assert.match(journal, /READY/);
  assert.match(journal, /SUBMITTED/);
  assert.match(journal, /SUBMISSION_UNKNOWN/);
  assert.match(journal, /staleTransportSubmissions/);
  assert.match(journal, /MMS_CALLBACK_TIMEOUT_MS/);
});

test('MMS watchdog resolves only still-outbox provider rows and survives restart', () => {
  const workerPath =
    'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSubmissionWatchdogWorker.kt';
  assert.ok(fs.existsSync(workerPath));
  const worker = fs.readFileSync(workerPath, 'utf8');
  assert.match(worker, /PeriodicWorkRequestBuilder/);
  assert.match(worker, /staleTransportSubmissions/);
  assert.match(worker, /markTransportTimedOut/);
  assert.match(cleanup, /MmsSubmissionWatchdogWorker\.schedule/);
  assert.match(store, /markTransportTimedOut/);
  assert.match(store, /MESSAGE_BOX_OUTBOX/);
});

test('MMS transport cannot start without a durable watchdog', () => {
  assert.match(sender, /MmsSubmissionWatchdogWorker\.schedule\(context\)/);
  const watchdogIndex = sender.indexOf('MmsSubmissionWatchdogWorker.schedule(context)');
  const transportIndex = sender.indexOf('transportInvocationStarted = true');
  assert.ok(watchdogIndex >= 0 && watchdogIndex < transportIndex);
  assert.match(sender, /MMS_SUBMISSION_WATCHDOG_UNAVAILABLE/);
});

test('late MMS callbacks cannot overwrite a timed-out or terminal journal outcome', () => {
  assert.match(journal, /fun markResult\([\s\S]*Phase\.RESULT_SENT[\s\S]*Phase\.RESULT_FAILED/);
  assert.match(store, /if \(!outcomeJournaled\) return false/);
  assert.match(store, /MESSAGE_BOX_FAILED/);
});

test('MMS callback saturation journals transport truth without provider work', () => {
  const fallback = statusReceiver.match(
    /if \(!scheduled\) \{([\s\S]*?)pendingResult\.finish\(\)/
  );
  assert.ok(fallback, 'MMS callback saturation must have an explicit bounded fallback');
  assert.match(fallback[1], /captureAndScheduleAfterSaturation/);
  assert.doesNotMatch(fallback[1], /MmsConversationStore|ContentResolver/);
  assert.match(statusReceiver, /private fun captureAndScheduleAfterSaturation/);
  assert.match(statusReceiver, /MmsProviderJournal\(context\)\.markResult/);
  assert.match(statusReceiver, /queueProviderRepair\(context\)/);
});

test('MMS rejects every negative subscription sentinel at each transport boundary', () => {
  assert.match(
    eligibility,
    /if \(!MmsSubscriptionResolver\.isValidSubscriptionId\(subscriptionId\)\)/,
    'eligibility must reject every negative subscription id, not only -1'
  );
  assert.match(
    sender,
    /\.filter\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'active MMS subscriptions must be filtered through the shared validity contract'
  );
  assert.match(
    sender,
    /\.takeIf\(MmsSubscriptionResolver::isValidSubscriptionId\)/,
    'default MMS subscription must be filtered through the shared validity contract'
  );
  assert.match(
    statusReceiver,
    /if \(!MmsSubscriptionResolver\.isValidSubscriptionId\(subscriptionId\)\) return/,
    'MMS callback identity must fail closed before asynchronous processing'
  );
});
