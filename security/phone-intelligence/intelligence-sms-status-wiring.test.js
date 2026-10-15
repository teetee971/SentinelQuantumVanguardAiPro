import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = resolve('native-android-app/app/src/main/java/com/sentinel/quantum');
const RECEIVER_PATH = resolve(ROOT, 'security/SentinelSmsStatusReceiver.kt');
const LOOKUP_PATH = resolve(ROOT, 'security/SmsProviderIdentityLookup.kt');
const RUNTIME_PATH = resolve(ROOT, 'intelligence/IntelligenceCoreRuntime.kt');

test('validated SMS callbacks feed Intelligence Core through provider id without durable raw PII', () => {
  const receiver = readFileSync(RECEIVER_PATH, 'utf8');
  const lookup = readFileSync(LOOKUP_PATH, 'utf8');
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');

  assert.match(
    lookup,
    /fun\s+addressForProviderMessage\s*\(\s*providerMessageId:\s*Long\s*\):\s*String\?/
  );
  assert.match(lookup, /Telephony\.Sms\.getDefaultSmsPackage\(context\)\s*==\s*context\.packageName/);
  assert.match(lookup, /Manifest\.permission\.READ_SMS/);
  assert.match(lookup, /arrayOf\(Telephony\.Sms\.ADDRESS\)/);
  assert.doesNotMatch(lookup, /Telephony\.Sms\.BODY/);

  assert.match(runtime, /fun\s+publishSmsDelivery\s*\(/);
  assert.match(runtime, /providerMessageId:\s*Long/);
  assert.match(runtime, /event:\s*SmsDeliveryStatusBus\.Event/);
  assert.match(
    runtime,
    /SmsProviderIdentityLookup\([^)]*\)[\s\S]*?\.addressForProviderMessage\(providerMessageId\)/
  );
  assert.match(runtime, /SmsMmsEvidenceAdapter\.fromSmsDelivery\(/);
  assert.match(runtime, /source\s*=\s*EvidenceSource\.SMS/);
  assert.match(runtime, /producer\s*=\s*EvidenceProvenance\.Producer\.SMS_CORE/);
  assert.match(runtime, /runtime\.fingerprinter\.fingerprint\(normalizedNumber\)/);
  assert.match(
    runtime,
    /"sms-status:\$\{event\.sendToken\}:\$\{event\.stage\.name\}:\$\{event\.partIndex\}:\$\{event\.partCount\}"/
  );

  const busPublish = receiver.indexOf('SmsDeliveryStatusBus.publish(statusEvent)');
  const corePublish = receiver.indexOf('IntelligenceCoreRuntime.publishSmsDelivery(');
  assert.ok(busPublish >= 0, 'validated callback bus publication is missing');
  assert.ok(corePublish > busPublish, 'Intelligence Core publication must happen after callback bus publication');

  assert.doesNotMatch(runtime, /IntelligenceEvent\([^)]*(?:address|body|messageBody|sender|otp)\s*=/s);
  assert.doesNotMatch(receiver, /LocalLogger\([^)]*\)[\s\S]{0,160}(?:address|body|messageBody|sender|otp)\s*=/s);
});
