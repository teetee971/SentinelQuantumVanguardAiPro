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
  'intelligence/ReputationEvidenceAdapter.kt'
);
const CALLER_ID_PATH = resolve(SOURCE_ROOT, 'CallerIdActivity.kt');

test('consented caller reputation is published into Intelligence Core after UI enrichment', () => {
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');
  const adapter = readFileSync(ADAPTER_PATH, 'utf8');
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');

  assert.match(
    runtime,
    /fun publishRemoteReputation\s*\(/,
    'Intelligence Core needs a bounded reputation publication entry point'
  );
  assert.match(
    runtime,
    /ReputationEvidenceAdapter\.fromRemoteResult/,
    'remote reputation must use the existing evidence adapter'
  );
  assert.match(
    adapter,
    /TrustClass\.UNVERIFIED_REMOTE/,
    'remote reputation must remain explicitly unverified'
  );
  assert.match(
    callerId,
    /IntelligenceCoreRuntime\.publishRemoteReputation/,
    'Caller ID must publish the consented result into the shared core'
  );
});
