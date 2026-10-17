import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const SOURCE_ROOT = resolve(
  'native-android-app/app/src/main/java/com/sentinel/quantum'
);
const RUNTIME_PATH = resolve(
  SOURCE_ROOT,
  'intelligence/IntelligenceCoreRuntime.kt'
);
const ADAPTER_PATH = resolve(
  SOURCE_ROOT,
  'intelligence/adapters/ContactIntelligenceAdapter.kt'
);
const CALLER_ID_PATH = resolve(SOURCE_ROOT, 'CallerIdActivity.kt');

test('post-response contact observations reach Intelligence Core with versioned identity', () => {
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');
  const adapter = readFileSync(ADAPTER_PATH, 'utf8');
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');

  assert.match(runtime, /fun publishContactObservation\s*\(/);
  assert.match(runtime, /ContactIntelligenceAdapter/);
  assert.match(callerId, /IntelligenceCoreRuntime\.publishContactObservation/);
  assert.match(adapter, /SentinelIdentityKey\.phoneFingerprint/);
  assert.doesNotMatch(
    adapter,
    /SentinelIdentityKey\.phoneFingerprint\(localFingerprint, version = 1\)/,
    'contact evidence must preserve the fingerprint key version'
  );
});
