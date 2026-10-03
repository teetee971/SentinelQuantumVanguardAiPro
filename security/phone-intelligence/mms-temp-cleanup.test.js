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
  assert.match(sendWorker, /MmsSendPduStager\.expire\(applicationContext, fileName\)/);
  assert.match(sendStager, /MmsSendCleanupWorker\.schedule\(context\.applicationContext, finalFile\.name\)/);
  assert.match(sendStager, /deleteInternal\(context, fileName, cancelCleanup = false\)/);
});

test('incoming MMS registers durable cleanup before crossing the Android download boundary', () => {
  const scheduleIndex = downloadCoordinator.indexOf('MmsDownloadCleanupWorker.schedule');
  const transportIndex = downloadCoordinator.indexOf('downloadMultimediaMessage');
  assert.ok(scheduleIndex >= 0, 'download cleanup scheduling must exist');
  assert.ok(transportIndex >= 0, 'Android MMS download transport must exist');
  assert.ok(scheduleIndex < transportIndex, 'cleanup must be durable before Android transport starts');
  assert.match(downloadCoordinator, /MMS_DOWNLOAD_CLEANUP_SCHEDULE_FAILED/);
  assert.match(downloadWorker, /setInputData\(workDataOf\(KEY_FILE_NAME to fileName\)\)/);
  assert.match(downloadWorker, /MmsDownloadCoordinator\.expire\(applicationContext, fileName\)/);
  assert.match(downloadWorker, /ExistingWorkPolicy\.KEEP/);
  assert.doesNotMatch(downloadWorker, /ExistingWorkPolicy\.REPLACE/);
});

test('incoming callback keeps cleanup scheduled until temporary deletion is confirmed', () => {
  assert.match(downloadReceiver, /val temporaryDeleted = MmsDownloadCoordinator\.delete\(context, fileName\)/);
  assert.match(downloadReceiver, /if \(!temporaryDeleted\)[\s\S]*nettoyage durable reste planifié/);
  assert.match(downloadCoordinator, /if \(removed && cancelCleanup\)[\s\S]*MmsDownloadCleanupWorker\.cancel/);
  assert.match(downloadCoordinator, /internal fun expire\(context: Context, fileName: String\): Boolean =[\s\S]*cancelCleanup = false/);
});
