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
