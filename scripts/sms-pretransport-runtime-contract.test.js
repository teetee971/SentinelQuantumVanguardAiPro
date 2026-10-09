import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const senderPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt';
const interlockPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreTransportTestInterlock.kt';
const instrumentationPath = 'native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/SmsPreTransportRevocationInstrumentationTest.kt';
const journalPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreSubmitJournal.kt';
const recoveryPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreSubmitRecoveryWorker.kt';
const applicationPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt';

const sender = readFileSync(senderPath, 'utf8');

test('runtime SMS revocation race has a debug-only final-boundary interlock and emulator proof', () => {
  assert.equal(existsSync(interlockPath), true, 'debug-only pre-transport interlock must exist');
  assert.equal(existsSync(instrumentationPath), true, 'instrumentation revocation race proof must exist');

  const interlock = readFileSync(interlockPath, 'utf8');
  const instrumentation = readFileSync(instrumentationPath, 'utf8');
  const revalidation = sender.indexOf('revalidateBeforeSubmission(prepared.subscriptionId)');
  const interlockCall = sender.lastIndexOf('SmsPreTransportTestInterlock.beforeFinalAuthorizationRecheck()', revalidation);
  const textSend = sender.indexOf('prepared.manager.sendTextMessage(', revalidation);

  assert.ok(interlockCall >= 0 && interlockCall < revalidation, 'interlock must execute immediately before final authorization revalidation');
  assert.ok(textSend > revalidation, 'telephony submission must remain after final revalidation');
  assert.match(interlock, /BuildConfig\.DEBUG/);
  assert.match(interlock, /installForInstrumentation/);
  assert.match(instrumentation, /SEND_SMS ignore/);
  assert.match(instrumentation, /SEND_SMS_PERMISSION_NOT_GRANTED/);
  assert.match(instrumentation, /Telephony\.Sms\.MESSAGE_TYPE_FAILED/);
  assert.match(instrumentation, /sentCallbackCount/);
  assert.match(instrumentation, /deliveredCallbackCount/);
});

test('SMS provider mutation is journaled before transport and only proven pre-transport state is auto-repaired', () => {
  assert.equal(existsSync(journalPath), true, 'durable SMS pre-submit journal must exist');
  assert.equal(existsSync(recoveryPath), true, 'durable SMS pre-submit recovery worker must exist');

  const journal = readFileSync(journalPath, 'utf8');
  const recovery = readFileSync(recoveryPath, 'utf8');
  const application = readFileSync(applicationPath, 'utf8');

  assert.match(journal, /enum class Phase\s*\{[^}]*PREPARING[^}]*PROVIDER_READY[^}]*TRANSPORT_STARTED/s);
  assert.match(journal, /fun begin\(/);
  assert.match(journal, /fun recordProvider\(/);
  assert.match(journal, /fun markTransportStarted\(/);
  assert.match(recovery, /Phase\.PREPARING/);
  assert.match(recovery, /Phase\.PROVIDER_READY/);
  assert.match(recovery, /Phase\.TRANSPORT_STARTED/);
  assert.match(recovery, /markOutgoingFailed/);
  assert.match(application, /SmsPreSubmitRecoveryWorker\.scheduleStartupRecovery\(this\)/);

  const begin = sender.indexOf('preSubmitJournal.begin(');
  const providerInsert = sender.indexOf('insertOutgoingOutbox(');
  const recordProvider = sender.indexOf('preSubmitJournal.recordProvider(');
  const markTransport = sender.indexOf('preSubmitJournal.markTransportStarted(');
  const textSend = sender.indexOf('prepared.manager.sendTextMessage(');
  const multipartSend = sender.indexOf('prepared.manager.sendMultipartTextMessage(');

  assert.ok(begin >= 0 && begin < providerInsert, 'journal PREPARING must precede provider mutation');
  assert.ok(recordProvider > providerInsert && recordProvider < markTransport, 'provider id must be durably correlated before transport state');
  assert.ok(markTransport > recordProvider, 'transport ambiguity marker must follow provider correlation');
  assert.ok(textSend > markTransport && multipartSend > markTransport, 'SmsManager may be entered only after durable TRANSPORT_STARTED');

  const transportRecovery = recovery.slice(recovery.indexOf('Phase.TRANSPORT_STARTED'));
  assert.doesNotMatch(transportRecovery, /markOutgoingFailed/,
    'ambiguous TRANSPORT_STARTED records must never be auto-converted to FAILED');
});
