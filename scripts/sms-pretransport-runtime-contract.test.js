import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const senderPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt';
const interlockPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreTransportTestInterlock.kt';
const instrumentationPath = 'native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/SmsPreTransportRevocationInstrumentationTest.kt';
const roleFixturePath = 'native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/SmsRoleLossRecoveryInstrumentationTest.kt';
const roleFlowPath = 'scripts/phone-core-role-sms-process-death-flow.sh';
const emulationWorkflowPath = '.github/workflows/android-emulation-qualification.yml';
const journalPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreSubmitJournal.kt';
const recoveryPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreSubmitRecoveryWorker.kt';
const providerPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreSubmitProvider.kt';
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
  assert.ok(interlockCall >= 0 && interlockCall < revalidation);
  assert.ok(textSend > revalidation);
  assert.match(interlock, /BuildConfig\.DEBUG/);
  assert.match(instrumentation, /SEND_SMS ignore/);
  assert.match(instrumentation, /SEND_SMS_PERMISSION_NOT_GRANTED/);
  assert.match(instrumentation, /Telephony\.Sms\.MESSAGE_TYPE_FAILED/);
  assert.doesNotMatch(
    instrumentation,
    /remove-role-holder --user 0 android\.app\.role\.SMS/,
    'ROLE_SMS removal kills the target process and must not be attempted inside one instrumentation method'
  );
});

test('SMS provider mutation is journaled before transport and only proven pre-transport state is auto-repaired', () => {
  assert.equal(existsSync(journalPath), true, 'durable SMS pre-submit journal must exist');
  assert.equal(existsSync(recoveryPath), true, 'durable SMS pre-submit recovery worker must exist');
  assert.equal(existsSync(providerPath), true, 'recoverable SMS provider boundary must exist');

  const journal = readFileSync(journalPath, 'utf8');
  const recovery = readFileSync(recoveryPath, 'utf8');
  const provider = readFileSync(providerPath, 'utf8');
  const application = readFileSync(applicationPath, 'utf8');

  assert.match(journal, /enum class Phase\s*\{[^}]*PREPARING[^}]*PROVIDER_READY[^}]*TRANSPORT_STARTED/s);
  assert.match(journal, /fun begin\(/);
  assert.match(journal, /fun recordProvider\(/);
  assert.match(journal, /fun markTransportStarted\(/);
  assert.match(provider, /Telephony\.Sms\.CREATOR/);
  assert.match(provider, /PreparedOutboxRepair\.AMBIGUOUS/);
  assert.match(recovery, /Phase\.PREPARING/);
  assert.match(recovery, /Phase\.PROVIDER_READY/);
  assert.match(recovery, /Phase\.TRANSPORT_STARTED/);
  assert.match(recovery, /SmsPreSubmitProvider\.markOutgoingFailed/);
  assert.match(recovery, /val repairable = records\.filter/);
  assert.match(recovery, /if \(repairable\.isEmpty\(\)\) return Result\.success\(\)/);
  assert.match(application, /SmsPreSubmitRecoveryWorker\.scheduleStartupRecovery\(this\)/);

  const begin = sender.indexOf('preSubmitJournal.begin(');
  const providerInsert = sender.indexOf('SmsPreSubmitProvider.insertOutgoingOutbox(');
  const recordProvider = sender.indexOf('preSubmitJournal.recordProvider(');
  const markTransport = sender.indexOf('preSubmitJournal.markTransportStarted(');
  const textSend = sender.indexOf('prepared.manager.sendTextMessage(');
  const multipartSend = sender.indexOf('prepared.manager.sendMultipartTextMessage(');

  assert.ok(begin >= 0 && begin < providerInsert, 'journal PREPARING must precede provider mutation');
  assert.ok(recordProvider > providerInsert && recordProvider < markTransport, 'provider id must be durably correlated before transport state');
  assert.ok(markTransport > recordProvider, 'transport ambiguity marker must follow provider correlation');
  assert.ok(textSend > markTransport && multipartSend > markTransport, 'SmsManager may be entered only after durable TRANSPORT_STARTED');

  assert.match(recovery,
    /val repairable = records\.filter\s*\{\s*it\.phase != SmsPreSubmitJournal\.Phase\.TRANSPORT_STARTED\s*\}/,
    'ambiguous TRANSPORT_STARTED records must be excluded before provider repair');
  assert.match(recovery,
    /SmsPreSubmitJournal\.Phase\.TRANSPORT_STARTED\s*->\s*Unit\b/,
    'TRANSPORT_STARTED must remain a no-op in the recovery dispatch');
});

test('ROLE_SMS loss is qualified as an external process-death and durable recovery flow', () => {
  assert.equal(existsSync(roleFixturePath), true, 'ROLE_SMS process-death fixture instrumentation must exist');
  assert.equal(existsSync(roleFlowPath), true, 'external ROLE_SMS process-death flow must exist');
  const fixture = readFileSync(roleFixturePath, 'utf8');
  const flow = readFileSync(roleFlowPath, 'utf8');
  const workflow = readFileSync(emulationWorkflowPath, 'utf8');

  assert.match(fixture, /SmsPreSubmitJournal\.Phase\.PROVIDER_READY/);
  assert.match(fixture, /SmsPreSubmitProvider\.insertOutgoingOutbox/);
  assert.match(fixture, /SmsPreSubmitRecoveryWorker\.scheduleStartupRecovery\(context\)/);
  assert.match(fixture, /Telephony\.Sms\.MESSAGE_TYPE_FAILED/);
  assert.match(fixture, /Telephony\.Sms\.STATUS_FAILED/);
  assert.match(flow, /remove-role-holder --user 0 android\.app\.role\.SMS/);
  assert.match(flow, /add-role-holder --user 0 android\.app\.role\.SMS/);
  assert.match(flow, /pidof com\.sentinel\.quantum/);
  assert.match(flow, /SmsRoleLossRecoveryInstrumentationTest#prepareProviderReadyFixture/);
  assert.match(flow, /SmsRoleLossRecoveryInstrumentationTest#recoverAfterRoleRestoration/);
  assert.match(workflow, /phone-core-role-sms-process-death-flow\.sh/);
});
