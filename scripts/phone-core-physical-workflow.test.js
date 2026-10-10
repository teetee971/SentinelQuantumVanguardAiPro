import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const workflowPath = '.github/workflows/android-physical-proof-verification.yml';
const protocol = readFileSync('docs/PHONE_CORE_PHYSICAL_VALIDATION.md', 'utf8');
const trustConfig = JSON.parse(readFileSync('config/phone-core-physical-proof-trust.json', 'utf8'));
let workflow = '';
try {
  workflow = readFileSync(workflowPath, 'utf8');
} catch {
  workflow = '';
}

test('physical proof workflow is manual and requires an exact source head', () => {
  assert.notEqual(workflow, '', 'physical proof workflow must exist');
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /evidence_run_id:[\s\S]*required:\s*true/);
  assert.match(workflow, /evidence_artifact:[\s\S]*required:\s*true/);
  assert.match(workflow, /expected_source_sha:[\s\S]*required:\s*true/);
  assert.match(workflow, /actions:\s*read/);
  assert.match(workflow, /contents:\s*read/);
});

test('physical proof workflow downloads an artifact and invokes the fail-closed verifier', () => {
  assert.match(workflow, /actions\/download-artifact@37930b1c2abaa49bbe596cd826c3c89aef350131/);
  assert.match(workflow, /run-id:\s*\$\{\{ inputs\.evidence_run_id \}\}/);
  assert.match(workflow, /node trusted-source\/scripts\/verify-phone-core-physical-session\.js/);
  assert.match(workflow, /EXPECTED_SOURCE_SHA/);
  assert.match(workflow, /physical-session-bundle\/session\.json/);
  assert.match(workflow, /trusted-source\/config\/phone-core-physical-proof-trust\.json/);
});

test('physical proof trust root is repository-controlled and untrusted source is never checked out', () => {
  assert.match(workflow, /path:\s*trusted-source/);
  assert.match(workflow, /ref:\s*\$\{\{ github\.event\.repository\.default_branch \}\}/);
  assert.match(workflow, /git -C trusted-source fetch --no-tags --depth=1 origin "\$EXPECTED_SOURCE_SHA"/);
  assert.match(workflow, /git -C trusted-source rev-parse FETCH_HEAD/);
  assert.doesNotMatch(workflow, /ref:\s*\$\{\{ inputs\.expected_source_sha \}\}/);
  assert.doesNotMatch(workflow, /path:\s*source-head/);
  assert.doesNotMatch(workflow, /physical-session-bundle\/trust\.json/);
});

test('repository trust root has an exact public-key-only schema', () => {
  assert.deepEqual(Object.keys(trustConfig).sort(), ['allowed_keys', 'schema_version']);
  assert.equal(trustConfig.schema_version, 1);
  assert.ok(Array.isArray(trustConfig.allowed_keys));
  assert.doesNotMatch(JSON.stringify(trustConfig), /PRIVATE KEY/);
});

test('physical proof workflow does not classify emulator evidence as physical validation', () => {
  assert.doesNotMatch(workflow, /ANDROID_APPLICATION_EMULATION/);
  assert.doesNotMatch(workflow, /physical_validation:\s*false/);
});

test('physical protocol documents the exact signed bundle consumed by the workflow', () => {
  assert.match(protocol, /session\.json/);
  assert.match(protocol, /phone-core-physical-proof-trust\.json/);
  assert.doesNotMatch(protocol, /physical-session-bundle[\\/]trust\.json/);
  assert.match(protocol, /evidence_refs/);
  assert.match(protocol, /verify-phone-core-physical-session\.js/);
  assert.match(protocol, /expected_source_sha|SOURCE_SHA/);
  assert.match(protocol, /S01[\s\S]*S26/);
});
