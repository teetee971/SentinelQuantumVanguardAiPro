import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const senderPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt';
const interlockPath = 'native-android-app/app/src/main/java/com/sentinel/quantum/security/SmsPreTransportTestInterlock.kt';
const instrumentationPath = 'native-android-app/app/src/androidTest/java/com/sentinel/quantum/security/SmsPreTransportRevocationInstrumentationTest.kt';

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
