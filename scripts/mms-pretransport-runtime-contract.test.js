import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const senderPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelMmsSender.kt';
const interlockPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/MmsPreTransportTestInterlock.kt';
const instrumentationPath = 'native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/MmsPreTransportRevocationInstrumentationTest.kt';

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

  assert.ok(interlockCall >= 0 && interlockCall < revalidation, 'MMS interlock must execute immediately before final authorization revalidation');
  assert.ok(transportStarted > revalidation && send > transportStarted, 'MMS transport must remain after final revalidation');
  assert.match(interlock, /BuildConfig\.DEBUG/);
  assert.match(interlock, /installForInstrumentation/);
  assert.match(instrumentation, /SEND_SMS ignore/);
  assert.match(instrumentation, /SEND_SMS_PERMISSION_NOT_GRANTED/);
  assert.match(instrumentation, /sentinel_mms_send/);
  assert.match(instrumentation, /providerMessageId/);
  assert.match(instrumentation, /PhonePrivateTimelineStore/);
});
