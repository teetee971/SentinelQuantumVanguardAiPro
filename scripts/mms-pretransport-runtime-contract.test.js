import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const senderPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSender.kt';
const interlockPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsPreTransportTestInterlock.kt';
const instrumentationPath = 'native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/MmsPreTransportRevocationInstrumentationTest.kt';
const journalPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsProviderJournal.kt';
const recoveryPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsPreTransportRecovery.kt';
const workerPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsSendCleanupWorker.kt';
const conversationStorePath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsConversationStore.kt';

const sender = readFileSync(senderPath, 'utf8');

test('runtime MMS revocation race proves PDU/provider compensation before transport', () => {
  assert.equal(existsSync(interlockPath), true, 'debug-only MMS pre-transport interlock must exist');
  assert.equal(existsSync(instrumentationPath), true, 'MMS instrumentation revocation race proof must exist');
  const interlock = readFileSync(interlockPath, 'utf8');
  const instrumentation = readFileSync(instrumentationPath, 'utf8');
  const revalidation = sender.indexOf('revalidateBeforeTransport(subscriptionId)');
  const interlockCall = sender.lastIndexOf('MmsPreTransportTestInterlock.beforeFinalAuthorizationRecheck()', revalidation);
  const transportStarted = sender.indexOf('transportInvocationStarted = true', revalidation);
  const send = sender.indexOf('manager.sendMultimediaMessage(', transportStarted);
  assert.ok(interlockCall >= 0 && interlockCall < revalidation);
  assert.ok(transportStarted > revalidation && send > transportStarted);
  assert.match(interlock, /BuildConfig\.DEBUG/);
  assert.match(instrumentation, /SEND_SMS ignore/);
  assert.match(instrumentation, /SEND_SMS_PERMISSION_NOT_GRANTED/);
});

test('MMS journal distinguishes proven READY from ambiguous TRANSPORT_STARTED across process death', () => {
  const journal = readFileSync(journalPath, 'utf8');
  const recovery = readFileSync(recoveryPath, 'utf8');
  const worker = readFileSync(workerPath, 'utf8');

  assert.match(journal, /enum class Phase\s*\{[^}]*READY[^}]*TRANSPORT_STARTED[^}]*SUBMITTED/s);
  assert.match(journal, /fun markTransportStarted\(/);
  assert.match(recovery, /Phase\.READY/);
  assert.doesNotMatch(recovery, /Phase\.TRANSPORT_STARTED/,
    'ambiguous TRANSPORT_STARTED MMS records must never be auto-repaired');
  assert.match(recovery, /contentResolver\.delete/);
  assert.match(recovery, /MmsSendPduStager\.delete/);
  assert.match(worker, /MmsPreTransportRecovery\.repairReadyRecords/);

  const revalidation = sender.indexOf('revalidateBeforeTransport(subscriptionId)');
  const journalBoundary = sender.indexOf('providerJournal.markTransportStarted(', revalidation);
  const inMemoryBoundary = sender.indexOf('transportInvocationStarted = true', revalidation);
  const send = sender.indexOf('manager.sendMultimediaMessage(', revalidation);
  assert.ok(journalBoundary > revalidation, 'durable MMS transport marker must follow final authorization revalidation');
  assert.ok(inMemoryBoundary > journalBoundary, 'in-memory transport flag may flip only after durable transport marker');
  assert.ok(send > inMemoryBoundary, 'MMS transport invocation must follow both durable and in-memory markers');

  const reconcile = journal.slice(journal.indexOf('fun reconcileReadyAfterProcessDeath'));
  assert.doesNotMatch(reconcile, /markSubmissionUnknown\(/,
    'READY must remain a proven pre-transport state after process death');
});

test('MMS generic journal repair explicitly preserves TRANSPORT_STARTED ambiguity', () => {
  const store = readFileSync(conversationStorePath, 'utf8');
  const start = store.indexOf('fun repairJournal(): Int');
  const end = store.indexOf('\n    private fun transitionMessageBox', start);
  assert.ok(start >= 0 && end > start, 'MMS repairJournal must have a bounded implementation');
  const repair = store.slice(start, end);

  assert.match(
    repair,
    /MmsProviderJournal\.Phase\.READY,\s*MmsProviderJournal\.Phase\.TRANSPORT_STARTED,\s*MmsProviderJournal\.Phase\.SUBMITTED,\s*MmsProviderJournal\.Phase\.SUBMISSION_UNKNOWN\s*->\s*Unit/s,
    'generic provider recovery must explicitly preserve transport-start ambiguity'
  );
  assert.doesNotMatch(repair, /\belse\s*->/,
    'MMS recovery phases must remain exhaustively enumerated so new phases cannot compile silently');
});
