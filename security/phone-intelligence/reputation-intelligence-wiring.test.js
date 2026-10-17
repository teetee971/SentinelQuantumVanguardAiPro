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

test('caller reputation is published only after local persistence accepts the result', () => {
  const callerId = readFileSync(CALLER_ID_PATH, 'utf8');
  const guardStart = callerId.indexOf('val reputationPersisted');
  const persist = callerId.indexOf('.persist(number, result');
  const publish = callerId.indexOf('IntelligenceCoreRuntime.publishRemoteReputation(');

  assert.ok(guardStart >= 0, 'caller reputation must retain a persistence guard');
  assert.ok(persist >= 0, 'caller reputation must persist the bounded result');
  assert.ok(publish > persist, 'publication must follow the persistence decision');

  const publicationWindow = callerId.slice(guardStart, publish + 700);
  assert.match(
    publicationWindow,
    /val\s+reputationPersisted\s*=\s*CommunicationReputationWriter[\s\S]*?\.persist\(number,\s*result\s*\)/,
    'the persistence acceptance boolean must be retained'
  );
  assert.match(
    publicationWindow,
    /if\s*\(\s*reputationPersisted\s*\)\s*\{[\s\S]*?IntelligenceCoreRuntime\.publishRemoteReputation\(/,
    'revoked persistence must prevent Intelligence Core publication'
  );
});
