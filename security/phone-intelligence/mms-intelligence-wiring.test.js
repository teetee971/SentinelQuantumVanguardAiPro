import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const root = process.cwd();
const androidRoot = path.join(root, 'native-android-app/app/src/main');
const runtime = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/intelligence/IntelligenceCoreRuntime.kt'),
  'utf8'
);
const downloadReceiver = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/security/SentinelMmsDownloadReceiver.kt'),
  'utf8'
);
const deliverReceiver = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/security/SentinelMmsDeliverReceiver.kt'),
  'utf8'
);
const adapter = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/intelligence/SmsMmsEvidenceAdapter.kt'),
  'utf8'
);

test('MMS transport has an Intelligence Core publication contract', () => {
  assert.match(runtime, /fun\s+publishIncomingMmsObservation\s*\(/);
  assert.match(runtime, /SmsMmsEvidenceAdapter\.fromMms\(/);
  assert.match(runtime, /EvidenceSource\.MMS/);
  assert.match(runtime, /EvidenceProvenance\.Producer\.MMS_CORE/);
  assert.match(runtime, /PrivacyClass\.BOUNDED_METADATA/);
});

test('both incoming MMS ingress paths publish only after bounded processing', () => {
  for (const source of [downloadReceiver, deliverReceiver]) {
    const publication = source.indexOf('IntelligenceCoreRuntime.publishIncomingMmsObservation(');
    assert.ok(publication >= 0, 'MMS receiver must publish bounded evidence');
    const providerProjection = source.lastIndexOf('providerResult');
    const timeline = source.lastIndexOf('PhonePrivateTimelineStore');
    assert.ok(publication > providerProjection, 'publication must follow provider decision');
    assert.ok(publication > timeline, 'publication must follow private timeline update');
    assert.doesNotMatch(
      source.slice(publication, publication + 600),
      /\b(body|text|payload|url|subject)\b/i,
      'publication call must not pass raw MMS content'
    );
  }
});

test('MMS adapter remains bounded and content-free', () => {
  assert.match(adapter, /fun\s+fromMms\s*\(/);
  assert.match(adapter, /categoryCode\s*:\s*String\?\s*=\s*null/);
  assert.match(adapter, /analysisOutcome\s*:\s*AnalysisOutcome\?\s*=\s*null/);
  assert.doesNotMatch(adapter, /raw|body|url|payload/i);
});
