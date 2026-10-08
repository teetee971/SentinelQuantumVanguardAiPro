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
