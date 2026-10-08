import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const workflowPath = '.github/workflows/android-physical-proof-verification.yml';
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
  assert.match(workflow, /node scripts\/verify-phone-core-physical-session\.js/);
  assert.match(workflow, /EXPECTED_SOURCE_SHA/);
  assert.match(workflow, /physical-session-bundle\/session\.json/);
  assert.match(workflow, /physical-session-bundle\/trust\.json/);
});

test('physical proof workflow does not classify emulator evidence as physical validation', () => {
  assert.doesNotMatch(workflow, /ANDROID_APPLICATION_EMULATION/);
  assert.doesNotMatch(workflow, /physical_validation:\s*false/);
});
