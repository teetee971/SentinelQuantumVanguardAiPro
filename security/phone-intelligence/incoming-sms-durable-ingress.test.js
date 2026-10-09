import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const receiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsDeliverReceiver.kt',
  'utf8'
);
const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingSmsDeliveryStore.kt',
  'utf8'
);
const worker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingSmsDeliveryWorker.kt',
  'utf8'
);
const recovery = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingSmsRecoveryWorker.kt',
  'utf8'
);
const application = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt',
  'utf8'
);

test('SMS_DELIVER captures durable state and schedules idempotent projection', () => {
  assert.match(receiver, /IncomingSmsDeliveryStore\.persist\(context\.filesDir, record\)/);
  assert.match(receiver, /IncomingSmsDeliveryWorker\.schedule\(context, (?:id|record\.id|captured\.record\.id)\)/);
  assert.match(receiver, /IncomingSmsDeliveryWorker\.projectImmediately/);
  assert.doesNotMatch(receiver, /Executors\.newSingleThreadExecutor/);
});

test('SMS saturation keeps provider projection off the broadcast callback thread', () => {
  assert.match(receiver, /val pendingResult = goAsync\(\)/);
  assert.match(receiver, /RECEIVER_EXECUTOR\.execute/);
  assert.match(receiver, /ArrayBlockingQueue/);
  assert.match(receiver, /ThreadPoolExecutor\.AbortPolicy\(\)/);
  assert.doesNotMatch(receiver, /ThreadPoolExecutor\.CallerRunsPolicy\(\)/);
  assert.match(receiver, /private fun captureIncomingSms/);
  assert.match(receiver, /captureAndScheduleAfterSaturation/);
  const fallback = receiver.match(/if \(!submitted\) \{([\s\S]*?)pendingResult\.finish\(\)/);
  assert.ok(fallback, 'saturation fallback must finish PendingResult');
  assert.match(fallback[1], /captureAndScheduleAfterSaturation/);
  assert.doesNotMatch(fallback[1], /projectImmediately/);
  assert.match(receiver, /private fun processIncomingSms/);
  assert.match(receiver, /pendingResult\.finish\(\)/);
  assert.match(receiver, /LocalLogger\(appContext\)\.logAsync/);
});

test('unexpected SMS worker failures requeue the durable spool', () => {
  const workerFailure = receiver.match(
    /catch \(_: Exception\) \{([\s\S]*?)finally \{/
  );
  assert.ok(workerFailure, 'SMS worker must have an explicit exception boundary');
  assert.match(workerFailure[1], /IncomingSmsRecoveryWorker\.schedule\(appContext\)/);
});

test('SMS durable spool is bounded, fsynced and idempotency-keyed', () => {
  assert.match(store, /MAX_PENDING_RECORDS = 512/);
  assert.match(store, /MAX_RECORD_BYTES = 64L \* 1024L/);
  assert.match(store, /stream\.fd\.sync\(\)/);
  assert.match(store, /MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(store, /PersistState\.CAPACITY_EXCEEDED/);
  assert.match(store, /decodeRecordFile\(target, record\.id\) != null\) return PersistState\.EXISTING/);
});

test('corrupt SMS spool records cannot masquerade as replay or poison bounded capacity', () => {
  assert.match(store, /cleanInvalidRecordFiles\(directory\)/);
  assert.match(store, /if \(decoded == null\) runCatching \{ target\.delete\(\) \}/);
  assert.match(store, /if \(decodeRecordFile\(target, record\.id\) != null\) return PersistState\.EXISTING/);
  assert.match(store, /if \(!runCatching \{ target\.delete\(\) \}\.getOrDefault\(false\)\) return PersistState\.FAILED/);
});

test('existing replay fallback reuses durable receive identity when WorkManager submission fails', () => {
  assert.match(receiver, /persistState == IncomingSmsDeliveryStore\.PersistState\.EXISTING/);
  assert.match(receiver, /IncomingSmsDeliveryStore\.read\(context\.filesDir, (?:id|record\.id)\) \?: record/);
  assert.match(receiver, /record = projectionRecord/);
});

test('provider projection is role-gated, replay-safe and WorkManager-backed', () => {
  assert.match(worker, /readSmsRoleStateFailClosed\(\)/);
  assert.match(worker, /ProviderLookup\.FOUND/);
  assert.match(worker, /ProviderLookup\.UNKNOWN -> return Projection\.RETRY/);
  assert.match(worker, /enqueueUniqueWork/);
  assert.match(worker, /ExistingWorkPolicy\.KEEP/);
  assert.match(worker, /BackoffPolicy\.EXPONENTIAL/);
  assert.match(worker, /Telephony\.Sms\.Inbox\.CONTENT_URI/);
});

test('unexpected SMS provider projection failures remain retryable', () => {
  assert.match(worker, /val projection = runCatching \{[\s\S]*project\(/);
  assert.match(worker, /getOrElse \{[\s\S]*Projection\.RETRY/);
  assert.match(worker, /return when \(projection\)/);
});

test('SMS provider replay does not hide private spool cleanup failure', () => {
  const foundBranch = worker.match(
    /ProviderLookup\.FOUND -> \{([\s\S]*?)return Projection\.SUCCESS/
  );
  assert.ok(foundBranch, 'provider replay branch must remain explicit');
  assert.match(foundBranch[1], /deleteStageOnSuccess/);
  assert.match(foundBranch[1], /!IncomingSmsDeliveryStore\.delete\(/);
  assert.match(foundBranch[1], /return Projection\.RETRY/);
});

test('role-loss retry window retires private SMS content instead of leaking spool capacity', () => {
  assert.match(worker, /ROLE_RETRY_WINDOW_MS = 7L \* 24L \* 60L \* 60L \* 1000L/);
  assert.match(worker, /IncomingSmsDeliveryStore\.delete\(applicationContext\.filesDir, record\.id\)/);
  assert.match(worker, /if \(retired\) Result\.success\(\) else Result\.retry\(\)/);
  assert.match(worker, /contenu privé retiré/);
});

test('startup recovery itself runs off the Application main thread', () => {
  assert.match(application, /IncomingSmsRecoveryWorker\.schedule\(this\)/);
  assert.doesNotMatch(application, /IncomingSmsDeliveryStore\.pendingIds/);
  assert.match(recovery, /IncomingSmsDeliveryStore\.pendingIds\(applicationContext\.filesDir\)/);
  assert.match(recovery, /OneTimeWorkRequestBuilder<IncomingSmsRecoveryWorker>/);
});

test('startup recovery retries when a durable projection cannot be scheduled', () => {
  const doWork = recovery.match(/override fun doWork\(\): Result \{([\s\S]*?)\n    \}/);
  assert.ok(doWork, 'SMS startup recovery must expose an explicit doWork boundary');
  assert.match(doWork[1], /val pendingIds = runCatching \{/);
  assert.match(doWork[1], /getOrElse \{[\s\S]*return Result\.retry\(\)[\s\S]*\}/);
  assert.match(doWork[1], /var schedulingFailed = false/);
  assert.match(doWork[1], /if \(runCatching \{[\s\S]*IncomingSmsDeliveryWorker\.schedule\([\s\S]*\}\.isFailure\)\s*\{[\s\S]*schedulingFailed = true/);
  assert.match(doWork[1], /return if \(schedulingFailed\) Result\.retry\(\) else Result\.success\(\)/);
});
