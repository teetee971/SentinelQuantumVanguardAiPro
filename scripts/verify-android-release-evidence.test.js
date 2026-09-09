import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { verifyAndroidReleaseEvidence } from './verify-android-release-evidence.js';

const hash = (value) => createHash('sha256').update(value).digest('hex');

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-release-'));
  const apk = Buffer.from('signed-apk-fixture');
  const apkName = 'sentinel-release.apk';
  const checksum = `${hash(apk)}  ${apkName}\n`;
  const certificate = `Signer #1 certificate SHA-256 digest: ${'a'.repeat(64)}\n`;
  const sbom = '{"bomFormat":"CycloneDX"}\n';
  writeFileSync(join(root, apkName), apk);
  writeFileSync(join(root, `${apkName}.sha256`), checksum);
  writeFileSync(join(root, `${apkName}.certificates.txt`), certificate);
  writeFileSync(join(root, 'release-sbom.cdx.json'), sbom);
  const evidence = {
    schema_version: 1,
    provenance: {
      repository: 'teetee971/SentinelQuantumVanguardAiPro',
      commit: 'b'.repeat(40),
      workflow: 'Android Release APK',
      workflow_ref: 'teetee971/SentinelQuantumVanguardAiPro/.github/workflows/android-release.yml@refs/tags/v1.0.0',
      run_id: '123',
      run_attempt: '1',
      ref: 'refs/tags/v1.0.0',
    },
    sbom: { path: 'release-sbom.cdx.json', sha256: hash(sbom) },
    artifacts: [
      { path: `nested/${apkName}`, sha256: hash(apk), bytes: apk.length },
      { path: `nested/${apkName}.sha256`, sha256: hash(checksum), bytes: Buffer.byteLength(checksum) },
      { path: `nested/${apkName}.certificates.txt`, sha256: hash(certificate), bytes: Buffer.byteLength(certificate) },
    ],
  };
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  return { root, evidence, apkName };
}

test('verifies a complete flattened Android release bundle', (t) => {
  const { root, apkName } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const result = verifyAndroidReleaseEvidence({ root });
  assert.equal(result.verified, true);
  assert.equal(result.apk, apkName);
  assert.equal(result.certificate_sha256, 'a'.repeat(64));
});

test('rejects an APK modified after evidence generation', (t) => {
  const { root, apkName } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  writeFileSync(join(root, apkName), 'tampered');
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /SIZE_MISMATCH|HASH_MISMATCH/);
});

test('rejects a certificate report without a public SHA-256 digest', (t) => {
  const { root, evidence, apkName } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const invalid = 'certificate missing digest\n';
  writeFileSync(join(root, `${apkName}.certificates.txt`), invalid);
  evidence.artifacts[2].sha256 = hash(invalid);
  evidence.artifacts[2].bytes = Buffer.byteLength(invalid);
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /INVALID_CERTIFICATE_REPORT/);
});

test('rejects evidence bound to another workflow or repository', (t) => {
  const { root, evidence } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  evidence.provenance.repository = 'attacker/repository';
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /REPOSITORY_MISMATCH/);
});
