import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const sender = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt',
  'utf8'
);
const application = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt',
  'utf8'
);
const ledgerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsOutgoingSubmissionStore.kt';
const workerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsSubmissionWatchdogWorker.kt';
const conversation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsConversationStore.kt',
  'utf8'
);
const statusReceiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsStatusReceiver.kt',
  'utf8'
);

test('SMS submission registers a durable callback watchdog before entering telephony', () => {
  assert.ok(fs.existsSync(ledgerPath));
  assert.ok(fs.existsSync(workerPath));
  const registerIndex = sender.indexOf('SmsOutgoingSubmissionStore(context).register');
  const transportIndex = sender.indexOf('sendTextMessage');
  assert.ok(registerIndex >= 0, 'submission must be recorded before transport');
  assert.ok(registerIndex < transportIndex, 'watchdog registration must precede SmsManager');
  assert.match(sender, /SmsSubmissionWatchdogWorker\.schedule\(context\)/);
  assert.match(sender, /SMS_SUBMISSION_WATCHDOG_UNAVAILABLE/);
});

test('SMS watchdog survives process death and expires only still-pending provider rows', () => {
  assert.match(application, /SmsSubmissionWatchdogWorker\.schedule\(this\)/);
  const worker = fs.readFileSync(workerPath, 'utf8');
  assert.match(worker, /PeriodicWorkRequestBuilder/);
  assert.match(worker, /SmsOutgoingSubmissionStore\(appContext\)[\s\S]*ledger\.stale/);
  assert.match(worker, /pendingProviderWrites/);
  assert.match(worker, /SmsProviderPersistence\.persist/);
  assert.ok(
    worker.indexOf('pendingProviderWrites') < worker.indexOf('markOutgoingTimedOut'),
    'persisted callback truth must be repaired before timeout failure'
  );
  assert.match(conversation, /markOutgoingTimedOut/);
  assert.match(
    conversation,
    /MESSAGE_TYPE_OUTBOX[\s\S]*MESSAGE_TYPE_QUEUED/
  );
});

test('terminal SMS callbacks retire the watchdog only after provider acknowledgement', () => {
  assert.match(statusReceiver, /val providerApplied = providerUpdated/);
  assert.match(
    statusReceiver,
    /if \(providerApplied && progress\.terminal\)[\s\S]*SmsOutgoingSubmissionStore\(context\)\.remove/
  );
});

test('SMS watchdog ledger contains opaque bounded metadata only', () => {
  const ledger = fs.readFileSync(ledgerPath, 'utf8');
  assert.match(ledger, /MAX_TRACKED/);
  assert.match(ledger, /CALLBACK_TIMEOUT_MS/);
  assert.match(ledger, /SharedPreferences/);
  assert.doesNotMatch(ledger, /body|address|destination/i);
});

test('SMS callback processing uses a bounded queue while repair remains scheduled', () => {
  assert.match(statusReceiver, /ThreadPoolExecutor\(/);
  assert.match(statusReceiver, /ArrayBlockingQueue< Runnable >|ArrayBlockingQueue<Runnable>/);
  assert.match(statusReceiver, /ThreadPoolExecutor\.AbortPolicy\(\)/);
  assert.match(statusReceiver, /REPAIR_SCHEDULER\.schedule/);
});

test('unexpected SMS callback worker failures trigger provider repair before finishing', () => {
  const worker = statusReceiver.match(
    /CALLBACK_EXECUTOR\.execute \{([\s\S]*?)pendingResult\.finish\(\)/
  );
  assert.ok(worker, 'SMS callback worker must finish PendingResult in one bounded block');
  assert.match(worker[1], /catch \(_: Exception\)/);
  assert.match(worker[1], /queueProviderRepair\(appContext\)/);
});
