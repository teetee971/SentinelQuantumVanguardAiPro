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
const progressStore = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsCallbackProgressStore.kt',
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

test('SMS watchdog retries when durable ledger retirement is not confirmed', () => {
  const worker = fs.readFileSync(workerPath, 'utf8');
  assert.match(worker, /val ledgerRemoved = runCatching \{[\s\S]*ledger\.remove/);
  assert.match(worker, /if \(!ledgerRemoved\) \{[\s\S]*retry = true/);
});

test('SMS watchdog retries when ledger enumeration fails', () => {
  const worker = fs.readFileSync(workerPath, 'utf8');
  assert.match(worker, /val staleSubmissions = runCatching \{[\s\S]*ledger\.stale\(\)/);
  assert.match(worker, /getOrElse \{[\s\S]*return Result\.retry\(\)/);
  assert.match(worker, /staleSubmissions\.forEach/);
});

test('corrupt or unprunable SMS submission ledger state cannot be reported as accepted', () => {
  const ledger = fs.readFileSync(ledgerPath, 'utf8');
  assert.match(
    ledger,
    /fun stale\([\s\S]*if \(!validateEntries\(\)\)[\s\S]*throw IllegalStateException/s,
    'the watchdog must retry instead of treating corrupt entries as no pending submissions'
  );
  assert.match(
    ledger,
    /if \(!validateEntries\(\)\) return@synchronized false/,
    'new transport must fail closed when the existing ledger is corrupt'
  );
  assert.match(
    ledger,
    /if \(!prune\(nowMs\)\) return@synchronized false/,
    'a failed retention cleanup must block a new registration'
  );
  assert.match(ledger, /private fun prune\(nowMs: Long\): Boolean/);
});

test('corrupt SMS callback progress cannot be treated as an empty provider queue', () => {
  assert.match(
    progressStore,
    /pendingProviderWrites\([\s\S]*decode\([\s\S]*enforceTtl = false[\s\S]*IllegalStateException/,
    'a malformed callback record must stop watchdog timeout decisions and trigger retry'
  );
  assert.match(
    fs.readFileSync(workerPath, 'utf8'),
    /val pendingProviderWrites = runCatching \{[\s\S]*progressStore\.pendingProviderWrites\(\)[\s\S]*\}\.getOrElse \{[\s\S]*return Result\.retry\(\)/,
    'the watchdog must retry when callback progress cannot be read truthfully'
  );
});

test('terminal callback progress validates its opaque key before being ignored', () => {
  const keyValidationIndex = progressStore.indexOf('val ids = key.split(":", limit = 2)');
  const providerAppliedIndex = progressStore.indexOf('if (record.providerApplied) return@mapNotNull null');
  assert.ok(keyValidationIndex >= 0, 'callback progress must parse the storage key');
  assert.ok(providerAppliedIndex > keyValidationIndex, 'provider-applied tombstones must not bypass key validation');
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

test('SMS callback saturation durably captures opaque progress without provider work', () => {
  const catchStart = statusReceiver.indexOf('} catch (_: RuntimeException) {');
  const methodEnd = statusReceiver.indexOf(
    '\n    private fun processValidatedCallback',
    catchStart
  );
  assert.ok(catchStart >= 0 && methodEnd > catchStart, 'queue saturation must have an explicit bounded fallback');
  const fallback = statusReceiver.slice(catchStart, methodEnd);
  assert.match(fallback, /captureAndScheduleAfterSaturation/);
  assert.match(fallback, /logAsync/);
  assert.doesNotMatch(fallback, /SmsProviderPersistence|SmsConversationStore|ContentResolver/);
  assert.match(statusReceiver, /private fun captureAndScheduleAfterSaturation/);
  assert.match(statusReceiver, /SmsCallbackProgressStore\(context\)\.record/);
  assert.match(statusReceiver, /queueProviderRepair\(context\)/);
});
