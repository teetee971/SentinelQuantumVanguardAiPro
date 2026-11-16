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

for (const [name, source] of [
  ['download', downloadReceiver],
  ['deliver', deliverReceiver]
]) {
  test(`${name} maps the actual bounded MMS outcome before publication`, () => {
    // These predicates must remain tied to the provider's actual insert/replay result.
    assert.match(source, /val providerInserted =\s*providerResult is IncomingMmsConversationStore\.ProjectResult\.Ready && !providerResult\.replay/);
    assert.match(source, /val providerReplay =\s*providerResult is IncomingMmsConversationStore\.ProjectResult\.Ready && providerResult\.replay/);
    const mapping = source.match(/val transportState = when\s*\{([^}]+)\}/)?.[1];
    assert.ok(mapping, 'transport state must be derived from the bounded outcome, not hardcoded PROVIDER_READY');
    assert.equal(
      mapping.replace(/\s+/g, ' ').trim(),
      'providerInserted -> SmsMmsEvidenceAdapter.MmsTransportState.PROVIDER_READY ' +
        'providerReplay -> SmsMmsEvidenceAdapter.MmsTransportState.PROVIDER_REPLAY_NO_DUPLICATE ' +
        'safePreview is MmsDecodePipeline.Result.Accepted -> SmsMmsEvidenceAdapter.MmsTransportState.SAFE_PREVIEW_READY ' +
        'else -> SmsMmsEvidenceAdapter.MmsTransportState.LOCAL_QUARANTINE',
      'provider success and replay take precedence over safe preview and quarantine'
    );
    assert.match(
      source,
      /IntelligenceCoreRuntime\.publishIncomingMmsObservation\(\s*context = context,\s*providerMessageId = projected\.providerMessageId,\s*rawSender = plan\.sender,\s*transportState = transportState\s*\)/,
      'publication must consume the computed state and only bounded identity inputs'
    );
    assert.ok(source.indexOf('val transportState = when') < source.indexOf('IntelligenceCoreRuntime.publishIncomingMmsObservation('));
  });

  test(`${name} keeps private-only MMS outcomes behind the canonical identity boundary`, () => {
    assert.match(source, /val plan = \(prepared as\? IncomingMmsProjectionPipeline\.Result\.Ready\)\?\.plan/);
    assert.match(source, /val projected = providerResult as\? IncomingMmsConversationStore\.ProjectResult\.Ready/);
    assert.match(
      source,
      /if \(plan != null && projected != null\)\s*\{\s*IntelligenceCoreRuntime\.publishIncomingMmsObservation\([^)]*\)\s*\}/,
      'quarantine or safe preview without canonical provider identity must not invent an Intelligence event'
    );
    assert.equal((source.match(/IntelligenceCoreRuntime\.publishIncomingMmsObservation\(/g) ?? []).length, 1);
  });
}

test('MMS adapter remains bounded and content-free', () => {
  assert.match(adapter, /fun\s+fromMms\s*\(/);
  assert.match(adapter, /categoryCode\s*:\s*String\?\s*=\s*null/);
  assert.match(adapter, /analysisOutcome\s*:\s*AnalysisOutcome\?\s*=\s*null/);
  assert.doesNotMatch(adapter, /raw|body|url|payload/i);
});
