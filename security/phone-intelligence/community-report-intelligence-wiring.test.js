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
const evidence = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/intelligence/IntelligenceEvidence.kt'),
  'utf8'
);
const store = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/intelligence/IntelligenceSnapshotStore.kt'),
  'utf8'
);
const caller = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/CallerIdActivity.kt'),
  'utf8'
);
const projection = fs.readFileSync(
  path.join(androidRoot, 'java/com/sentinel/quantum/intelligence/ScreeningProjection.kt'),
  'utf8'
);

test('community report publication is a typed, non-enforcement evidence path', () => {
  assert.match(runtime, /fun\s+publishCommunityReport\s*\(/);
  assert.match(runtime, /EvidenceSource\.COMMUNITY_REPORT/);
  assert.match(runtime, /EvidenceProvenance\.Producer\.COMMUNITY_CORE/);
  assert.match(evidence, /data\s+class\s+CommunityReport\s*\(/);
  assert.match(evidence, /CommunityModerationState/);
  assert.match(evidence, /CommunityReputationEffect/);
  assert.match(store, /"COMMUNITY_REPORT"/);
});

test('caller UI publishes community state only after explicit report submission', () => {
  const submit = caller.indexOf('reportClient.submit(');
  const publish = caller.indexOf('IntelligenceCoreRuntime.publishCommunityReport(');
  assert.ok(submit >= 0, 'caller UI must submit the explicit report');
  assert.ok(publish > submit, 'community evidence must follow the server submission');
  const window = caller.slice(publish, publish + 900);
  assert.match(window, /category/);
  assert.match(window, /result/);
  assert.doesNotMatch(window, /riskScore|ScreeningProjection|block|silence/i);
});

test('community report evidence cannot become a screening decision', () => {
  assert.doesNotMatch(projection, /COMMUNITY_REPORT|CommunityReport/);
  assert.doesNotMatch(runtime, /ScreeningProjection(?:\.|\()/);
});
