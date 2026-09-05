import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { generateReleaseEvidence } from './generate-release-evidence.js';

const provenance = {
  GITHUB_REPOSITORY: 'teetee971/SentinelQuantumVanguardAiPro',
  GITHUB_SHA: 'a'.repeat(40),
  GITHUB_WORKFLOW: 'Release',
  GITHUB_WORKFLOW_REF: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/android-release.yml@main',
  GITHUB_RUN_ID: '1',
  GITHUB_RUN_ATTEMPT: '1',
  GITHUB_REF: 'refs/tags/v1.0.0',
};

test('requires complete CI provenance before producing release evidence', () => {
  assert.throws(
    () => generateReleaseEvidence({ sbom: 'missing', output: 'output', artifacts: ['artifact'] }, {}),
    /MISSING_CI_PROVENANCE/,
  );
});

test('records content hashes for an SBOM and signed artifact', () => {
  const directory = mkdtempSync(join(tmpdir(), 'sentinel-evidence-'));
  try {
    writeFileSync(join(directory, 'sbom.json'), '{"bomFormat":"CycloneDX"}');
    writeFileSync(join(directory, 'release.apk'), 'signed-binary');
    const result = generateReleaseEvidence({
      sbom: 'sbom.json', output: 'evidence.json', artifacts: ['release.apk'],
    }, provenance, directory);
    assert.equal(result.artifacts[0].bytes, 13);
    assert.match(result.artifacts[0].sha256, /^[a-f0-9]{64}$/);
    assert.deepEqual(JSON.parse(readFileSync(join(directory, 'evidence.json'), 'utf8')).provenance, {
      repository: provenance.GITHUB_REPOSITORY,
      commit: provenance.GITHUB_SHA,
      workflow: provenance.GITHUB_WORKFLOW,
      workflow_ref: provenance.GITHUB_WORKFLOW_REF,
      run_id: provenance.GITHUB_RUN_ID,
      run_attempt: provenance.GITHUB_RUN_ATTEMPT,
      ref: provenance.GITHUB_REF,
    });
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
