import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const sendWorker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSendCleanupWorker.kt',
  'utf8'
);
const sendStager = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSendPduStager.kt',
  'utf8'
);
const downloadWorker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsDownloadCleanupWorker.kt',
  'utf8'
);
const downloadRecoveryWorker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsDownloadRecoveryWorker.kt',
  'utf8'
);
const downloadCoordinator = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsDownloadCoordinator.kt',
  'utf8'
);
const downloadReceiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsDownloadReceiver.kt',
  'utf8'
);
const deliverReceiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsDeliverReceiver.kt',
  'utf8'
);
const wapJournal = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsWapIngressJournal.kt',
  'utf8'
);
const wapStore = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsWapIngressStore.kt',
  'utf8'
);
const wapRecoveryWorker = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsWapIngressRecoveryWorker.kt',
  'utf8'
);
const application = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt',
  'utf8'
);
const privateStore = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/IncomingMmsPrivateStore.kt',
  'utf8'
);
const sendStatusReceiver = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSendStatusReceiver.kt',
  'utf8'
);

test('outgoing MMS cleanup is bound to each staged PDU instead of a replaceable global deadline', () => {
  assert.match(sendWorker, /fun schedule\(context: Context, fileName: String\)/);
  assert.match(sendWorker, /setInputData\(workDataOf\(KEY_FILE_NAME to fileName\)\)/);
  assert.match(sendWorker, /ExistingWorkPolicy\.KEEP/);
  assert.doesNotMatch(sendWorker, /ExistingWorkPolicy\.REPLACE/);
  assert.match(sendWorker, /val expired = MmsSendPduStager\.expire\(applicationContext, fileName\)/);
  assert.match(sendWorker, /if \(!expired\)[\s\S]*return Result\.retry\(\)/);
  assert.match(sendWorker, /setBackoffCriteria\(BackoffPolicy\.EXPONENTIAL, 30L, TimeUnit\.SECONDS\)/);
  assert.match(sendStager, /deleteInternal\(context, fileName, cancelCleanup = false\)/);
});

test('incoming MMS journals identity before staging and binds both safety nets before Android transport starts', () => {
  const journalIndex = downloadCoordinator.indexOf('recoveryJournal.record');
  const fileCreateIndex = downloadCoordinator.indexOf('canonicalFile.createNewFile');
  const cleanupIndex = downloadCoordinator.indexOf('MmsDownloadCleanupWorker.scheduleAtDeadline');
  const recoveryIndex = downloadCoordinator.indexOf('MmsDownloadRecoveryWorker.schedule');
  const transportIndex = downloadCoordinator.indexOf('downloadMultimediaMessage');

  assert.ok(journalIndex >= 0, 'download recovery journal must exist');
  assert.ok(fileCreateIndex >= 0, 'download staging file creation must exist');
  assert.ok(cleanupIndex >= 0, 'download cleanup scheduling must exist');
  assert.ok(recoveryIndex >= 0, 'download lost-callback recovery scheduling must exist');
  assert.ok(transportIndex >= 0, 'Android MMS download transport must exist');
  assert.ok(journalIndex < fileCreateIndex, 'recovery metadata must be durable before staging side effects');
  assert.ok(journalIndex < transportIndex, 'recovery metadata must be durable before Android transport starts');
  assert.ok(cleanupIndex < transportIndex, 'cleanup deadline must be durable before Android transport starts');
  assert.ok(recoveryIndex < transportIndex, 'lost-callback recovery must be scheduled before Android transport starts');

  assert.match(downloadCoordinator, /val requestedAtMs = System\.currentTimeMillis\(\)/);
  assert.match(downloadCoordinator, /recoveryJournal\.record\(canonicalFile\.name, subscriptionId, requestedAtMs\)/);
  assert.match(
    downloadCoordinator,
    /MmsDownloadCleanupWorker\.scheduleAtDeadline\([\s\S]*requestedAtMs = requestedAtMs/
  );
  assert.match(downloadCoordinator, /MMS_DOWNLOAD_RECOVERY_JOURNAL_FAILED/);
  assert.match(downloadCoordinator, /MMS_DOWNLOAD_RECOVERY_SCHEDULE_FAILED/);
  assert.match(downloadWorker, /setInputData\(workDataOf\(KEY_FILE_NAME to fileName\)\)/);
  assert.match(downloadWorker, /allowQuarantine = true/);
  assert.match(downloadWorker, /val expired = MmsDownloadCoordinator\.expire\(applicationContext, fileName\)/);
  assert.match(downloadWorker, /if \(!expired\)[\s\S]*return Result\.retry\(\)/);
  assert.match(downloadWorker, /setBackoffCriteria\(BackoffPolicy\.EXPONENTIAL, 30L, TimeUnit\.SECONDS\)/);
  assert.match(downloadWorker, /ExistingWorkPolicy\.KEEP/);
  assert.doesNotMatch(downloadWorker, /ExistingWorkPolicy\.REPLACE/);
  assert.match(downloadRecoveryWorker, /allowQuarantine = false/);
});

test('private MMS persistence keeps its own non-empty transport size boundary', () => {
  assert.match(
    privateStore,
    /if \(data\.isEmpty\(\) \|\| data\.size\.toLong\(\) > MmsDownloadCoordinator\.MAX_DOWNLOADED_PDU_BYTES\) \{\s*return failed\(\)\s*\}/
  );
});

test('transport request exceptions retain staged MMS until durable recovery decides its fate', () => {
  const requestFailure = downloadCoordinator.match(
    /catch \(_:\s*Exception\) \{([\s\S]*?)Result\.Rejected\("MMS_DOWNLOAD_REQUEST_FAILED"\)/
  );
  assert.ok(requestFailure, 'transport failure branch must remain explicit');
  assert.doesNotMatch(
    requestFailure[1],
    /delete\(/,
    'an exception after transport acceptance must not delete the callback target'
  );
  assert.match(
    requestFailure[1],
    /copie temporaire|reprise|journal/i,
    'the retained target must be explained as recoverable state'
  );
});

test('MMS broadcast saturation never performs synchronous file logging on the callback thread', () => {
  for (const [name, source] of [
    ['WAP_PUSH_DELIVER', deliverReceiver],
    ['download callback', downloadReceiver]
  ]) {
    const fallback = source.match(/if \(!submitted\) \{([\s\S]*?)pendingResult\.finish\(\)/);
    assert.ok(fallback, `${name} fallback must finish PendingResult`);
    assert.match(fallback[1], /LocalLogger\(appContext\)\.logAsync/);
    assert.doesNotMatch(fallback[1], /LocalLogger\(appContext\)\.log\(/);
  }
});

test('process-death recovery reconstructs the original incoming MMS cleanup deadline', () => {
  assert.match(
    downloadRecoveryWorker,
    /MmsDownloadRecoveryJournal\(context\)\.all\(\)[\s\S]*MmsDownloadCleanupWorker\.scheduleAtDeadline\([\s\S]*requestedAtMs = record\.requestedAtMs/
  );
  assert.match(
    downloadWorker,
    /remainingDelayMs\(requestedAtMs, nowMs\)[\s\S]*requestedAtMs \+ MmsDownloadCoordinator\.DOWNLOAD_TTL_MS/
  );
  assert.match(downloadWorker, /ExistingWorkPolicy\.KEEP/);
});

test('startup recovery retries failures and prunes legacy incoming MMS staging', () => {
  assert.match(sendWorker, /val recovered = runCatching \{/);
  assert.match(sendWorker, /MmsDownloadCoordinator\.pruneExpired\(applicationContext\)/);
  assert.match(sendWorker, /MmsDownloadRecoveryWorker\.schedulePendingNow\(applicationContext\)/);
  assert.match(sendWorker, /if \(!recovered\)[\s\S]*return Result\.retry\(\)/);
  assert.match(sendWorker, /fun scheduleStartupRecovery\([\s\S]*setBackoffCriteria\(BackoffPolicy\.EXPONENTIAL, 30L, TimeUnit\.SECONDS\)/);
});

test('incoming callback retires cleanup only after provider success or explicit quarantine and confirmed deletion', () => {
  const providerRejectIndex = downloadReceiver.indexOf(
    'providerResult is IncomingMmsConversationStore.ProjectResult.Rejected'
  );
  const temporaryDeleteIndex = downloadReceiver.indexOf(
    'val temporaryDeleted = MmsDownloadCoordinator.delete(context, fileName)'
  );

  assert.ok(providerRejectIndex >= 0, 'provider rejection branch must exist');
  assert.ok(temporaryDeleteIndex > providerRejectIndex, 'staging deletion must happen after provider decision');
  assert.match(
    downloadReceiver,
    /providerResult is IncomingMmsConversationStore\.ProjectResult\.Rejected\)[\s\S]*copie staged et le journal de reprise restent actifs[\s\S]*return/
  );
  assert.match(downloadReceiver, /if \(!temporaryDeleted\)[\s\S]*nettoyage durable reste planifié/);
  assert.match(
    downloadCoordinator,
    /val removed =[\s\S]*if \(!removed\) return false[\s\S]*if \(cancelCleanup\)[\s\S]*MmsDownloadCleanupWorker\.cancel/
  );
  assert.match(
    downloadCoordinator,
    /internal fun expire\(context: Context, fileName: String\): Boolean =[\s\S]*cancelCleanup = false/
  );
});

test('recovery metadata is durably retired before cleanup or recovery workers can be cancelled', () => {
  const metadataIndex = downloadCoordinator.indexOf(
    'val metadataRemoved = MmsDownloadRecoveryJournal(context.applicationContext).remove(fileName)'
  );
  const metadataFailureIndex = downloadCoordinator.indexOf('if (!metadataRemoved) return false');
  const cleanupCancelIndex = downloadCoordinator.indexOf('MmsDownloadCleanupWorker.cancel');
  const recoveryCancelIndex = downloadCoordinator.indexOf('MmsDownloadRecoveryWorker.cancel');

  assert.ok(metadataIndex >= 0, 'recovery metadata retirement must be explicit');
  assert.ok(metadataFailureIndex > metadataIndex, 'metadata commit failure must fail closed');
  assert.ok(cleanupCancelIndex > metadataFailureIndex, 'cleanup worker cancellation must follow durable journal retirement');
  assert.ok(recoveryCancelIndex > metadataFailureIndex, 'recovery worker cancellation must follow durable journal retirement');
});

test('secondary prune uses the same final recovery lifecycle instead of raw-unlinking valid staged MMS', () => {
  assert.match(
    downloadCoordinator,
    /MmsDownloadRecoveryJournal\(context\)\.read\(file\.name\)[\s\S]*MmsDownloadRecovery\.recover\([\s\S]*allowQuarantine = true/
  );
  assert.match(downloadCoordinator, /expire\(context, file\.name\)/);
  assert.match(downloadCoordinator, /!isValidStagedFileName\(it\.name\)/);
});

test('MMS WAP saturation durably stages the PDU and defers provider work', () => {
  assert.match(deliverReceiver, /ThreadPoolExecutor\(/, 'WAP_PUSH_DELIVER must use a bounded executor');
  assert.match(deliverReceiver, /ArrayBlockingQueue< Runnable >|ArrayBlockingQueue<Runnable>/);
  assert.match(deliverReceiver, /ThreadPoolExecutor\.AbortPolicy\(\)/);
  assert.doesNotMatch(deliverReceiver, /ThreadPoolExecutor\.CallerRunsPolicy\(\)/);
  assert.match(deliverReceiver, /captureAndScheduleRecovery/);
  assert.match(deliverReceiver, /IncomingMmsWapIngressStore\.persist/);
  assert.match(deliverReceiver, /IncomingMmsWapIngressJournal\(context\)\.record/);
  assert.match(deliverReceiver, /IncomingMmsWapIngressRecoveryWorker\.schedule/);
  const fallback = deliverReceiver.match(/captureAndScheduleRecovery[\s\S]*?\n    \}/);
  assert.ok(fallback, 'WAP saturation must have a bounded durable fallback');
  assert.doesNotMatch(fallback[0], /MmsDownloadCoordinator\.request/);
  assert.doesNotMatch(fallback[0], /IncomingMmsConversationStore/);
  assert.match(wapStore, /MAX_STORED_WAP = 64/);
  assert.match(wapStore, /stream\.fd\.sync\(\)/);
  assert.match(wapStore, /IncomingMmsIdentity\.sha256Hex/);
  assert.match(wapStore, /FileInputStream/);
  assert.match(wapStore, /private fun readBounded/);
  assert.match(wapJournal, /MAX_RECORDS = 64/);
  assert.match(wapJournal, /commit\(\)/);
  assert.match(wapRecoveryWorker, /val journal = IncomingMmsWapIngressJournal\(applicationContext\)[\s\S]*journal\.all\(\)/);
  assert.match(wapRecoveryWorker, /IncomingMmsWapIngressStore\.read/);
  assert.match(wapRecoveryWorker, /IncomingMmsWapIngressStore\.delete/);
  assert.match(wapRecoveryWorker, /MAX_RETRY_AGE_MS = 24L \* 60L \* 60L \* 1000L/);
  assert.match(wapRecoveryWorker, /nowMs - record\.receivedAtMs/);
  assert.match(wapRecoveryWorker, /journal\.remove\(record\.digestHex\)/);
  assert.match(wapRecoveryWorker, /Result\.retry\(\)/);
  assert.match(application, /IncomingMmsWapIngressRecoveryWorker\.schedule\(this\)/);

  for (const [name, source] of [
    ['download callback', downloadReceiver],
    ['send callback', sendStatusReceiver]
  ]) {
    assert.match(source, /ThreadPoolExecutor\(/, `${name} must use a bounded executor`);
    assert.match(source, /ArrayBlockingQueue< Runnable >|ArrayBlockingQueue<Runnable>/, `${name} queue must be bounded`);
    assert.match(source, /ThreadPoolExecutor\.AbortPolicy\(\)/, `${name} must reject saturation`);
    assert.doesNotMatch(source, /Executors\.newSingleThreadExecutor/, `${name} must not use an unbounded queue`);
  }
});

test('WAP recovery removes unreadable staging before retiring its journal record', () => {
  const unreadableBranch = wapRecoveryWorker.match(
    /if \(data == null\) \{([\s\S]*?)return@forEach/
  );
  assert.ok(unreadableBranch, 'unreadable WAP data must have an explicit cleanup branch');
  const branch = unreadableBranch[1];
  const stagedDeleteIndex = branch.indexOf('IncomingMmsWapIngressStore.delete');
  const journalRemoveIndex = branch.indexOf('journal.remove');
  assert.ok(stagedDeleteIndex >= 0, 'unreadable WAP bytes must be retired');
  assert.ok(journalRemoveIndex > stagedDeleteIndex, 'journal metadata must outlive staged cleanup');
  assert.match(branch, /val journalRemoved = stagedRemoved && journal\.remove/);
  assert.match(branch, /if \(!journalRemoved\) retry = true/);
});

test('unexpected WAP worker failures preserve the PDU for durable recovery', () => {
  const workerFailure = deliverReceiver.match(
    /catch \(_: Exception\) \{([\s\S]*?)finally \{/
  );
  assert.ok(workerFailure, 'WAP worker must have an explicit exception boundary');
  assert.match(workerFailure[1], /getByteArrayExtra\("data"\)/);
  assert.match(workerFailure[1], /captureAndScheduleRecovery/);
});

test('unexpected download worker failures requeue the callback journal', () => {
  const workerFailure = downloadReceiver.match(
    /catch \(_: Exception\) \{([\s\S]*?)finally \{/
  );
  assert.ok(workerFailure, 'download callback worker must have an explicit exception boundary');
  assert.match(workerFailure[1], /MmsDownloadRecoveryWorker\.schedule\(appContext, fileName\)/);
});

test('MMS provider repair scheduling is coalesced across callback bursts', () => {
  assert.match(sendStatusReceiver, /repairQueued = AtomicBoolean\(false\)/);
  assert.match(sendStatusReceiver, /repairQueued\.compareAndSet\(false, true\)/);
  assert.match(sendStatusReceiver, /fun queueProviderRepair\(context: Context\)/);
  assert.match(sendStatusReceiver, /queueProviderRepair\(context\)/);
  assert.match(sendStatusReceiver, /REPAIR_WORKER\.schedule/);
});
