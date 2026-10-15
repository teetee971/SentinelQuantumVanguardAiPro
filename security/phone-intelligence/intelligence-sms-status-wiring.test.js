import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = resolve('native-android-app/app/src/main/java/com/sentinel/quantum');
const RECEIVER_PATH = resolve(ROOT, 'security/SentinelSmsStatusReceiver.kt');
const STORE_PATH = resolve(ROOT, 'security/SmsConversationStore.kt');
const RUNTIME_PATH = resolve(ROOT, 'intelligence/IntelligenceCoreRuntime.kt');

test('validated SMS callbacks feed Intelligence Core through provider id without durable raw PII', () => {
  const receiver = readFileSync(RECEIVER_PATH, 'utf8');
  const store = readFileSync(STORE_PATH, 'utf8');
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');

  assert.match(
    store,
    /fun\s+addressForProviderMessage\s*\(\s*providerMessageId:\s*Long\s*\):\s*String\?/
  );
  assert.match(runtime, /fun\s+publishSmsDelivery\s*\(/);
  assert.match(runtime, /providerMessageId:\s*Long/);
  assert.match(runtime, /event:\s*SmsDeliveryStatusBus\.Event/);
  assert.match(runtime, /SmsConversationStore\([^)]*\)\.addressForProviderMessage\(providerMessageId\)/);
  assert.match(runtime, /SmsMmsEvidenceAdapter\.fromSmsDelivery\(/);
  assert.match(runtime, /source\s*=\s*EvidenceSource\.SMS/);
  assert.match(runtime, /producer\s*=\s*EvidenceProvenance\.Producer\.SMS_CORE/);
  assert.match(runtime, /fingerprinter\.fingerprint\(/);

  const busPublish = receiver.indexOf('SmsDeliveryStatusBus.publish(\n            SmsDeliveryStatusBus.Event(');
  const corePublish = receiver.indexOf('IntelligenceCoreRuntime.publishSmsDelivery(');
  assert.ok(busPublish >= 0, 'validated callback bus publication is missing');
  assert.ok(corePublish > busPublish, 'Intelligence Core publication must happen after callback validation/publication');

  assert.doesNotMatch(runtime, /IntelligenceEvent\([^)]*(?:address|body|messageBody|sender|otp)\s*=/s);
});
