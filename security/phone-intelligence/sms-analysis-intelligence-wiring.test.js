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
  'intelligence/SmsMmsEvidenceAdapter.kt'
);
const RECEIVER_PATH = resolve(
  SOURCE_ROOT,
  'security/SentinelSmsDeliverReceiver.kt'
);

test('incoming SMS link analysis is published as bounded shared evidence', () => {
  const runtime = readFileSync(RUNTIME_PATH, 'utf8');
  const adapter = readFileSync(ADAPTER_PATH, 'utf8');
  const receiver = readFileSync(RECEIVER_PATH, 'utf8');

  assert.match(runtime, /fun publishIncomingSmsAnalysis\s*\(/);
  assert.match(runtime, /SmsMmsEvidenceAdapter\.fromIncomingAnalysis/);
  assert.match(adapter, /fun fromIncomingAnalysis\s*\(/);
  assert.match(receiver, /IntelligenceCoreRuntime\.publishIncomingSmsAnalysis/);

  const insertedIndex = receiver.indexOf('val inserted =');
  const publicationIndex = receiver.indexOf(
    'IntelligenceCoreRuntime.publishIncomingSmsAnalysis'
  );
  assert.ok(insertedIndex >= 0, 'SMS provider insertion boundary is missing');
  assert.ok(
    publicationIndex > insertedIndex,
    'SMS intelligence publication must happen after provider insertion'
  );

  const publicationWindow = receiver.slice(publicationIndex, publicationIndex + 420);
  assert.doesNotMatch(
    publicationWindow,
    /\bbody\b/,
    'raw SMS body must not be passed into Intelligence Core'
  );
});
