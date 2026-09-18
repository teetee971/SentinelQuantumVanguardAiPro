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
  const aab = Buffer.from('signed-aab-fixture');
  const aabName = 'sentinel-release.aab';
  const checksum = `${hash(apk)}  ${apkName}\n`;
  const certificate = `Signer #1 certificate SHA-256 digest: ${'a'.repeat(64)}\n`;
  const aabChecksum = `${hash(aab)}  ${aabName}\n`;
  const aabFingerprint = Array(32).fill('AA').join(':');
  const aabCertificate = `SHA256: ${aabFingerprint}\n`;
  const sbom = '{"bomFormat":"CycloneDX"}\n';
  writeFileSync(join(root, apkName), apk);
  writeFileSync(join(root, `${apkName}.sha256`), checksum);
  writeFileSync(join(root, `${apkName}.certificates.txt`), certificate);
  writeFileSync(join(root, aabName), aab);
  writeFileSync(join(root, `${aabName}.sha256`), aabChecksum);
  writeFileSync(join(root, `${aabName}.certificates.txt`), aabCertificate);
  writeFileSync(join(root, 'release-sbom.cdx.json'), sbom);
  const evidence = {
    schema_version: 2,
    provenance: {
      repository: 'teetee971/SentinelQuantumVanguardAiPro',
      commit: 'b'.repeat(40),
      workflow: 'Android Release',
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
      { path: `nested/${aabName}`, sha256: hash(aab), bytes: aab.length },
      { path: `nested/${aabName}.sha256`, sha256: hash(aabChecksum), bytes: Buffer.byteLength(aabChecksum) },
      { path: `nested/${aabName}.certificates.txt`, sha256: hash(aabCertificate), bytes: Buffer.byteLength(aabCertificate) },
    ],
  };
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  return { root, evidence, apkName, aabName };
}

test('verifies a complete flattened Android release bundle', (t) => {
  const { root, apkName, aabName } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const result = verifyAndroidReleaseEvidence({ root });
  assert.equal(result.verified, true);
  assert.equal(result.apk, apkName);
  assert.equal(result.apk_certificate_sha256, 'a'.repeat(64));
  assert.equal(result.aab, aabName);
  assert.equal(result.aab_certificate_sha256, 'a'.repeat(64));
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
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /INVALID_APK_CERTIFICATE_REPORT/);
});

test('rejects evidence bound to another workflow or repository', (t) => {
  const { root, evidence } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  evidence.provenance.repository = 'attacker/repository';
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /REPOSITORY_MISMATCH/);
});


test('rejects an AAB modified after evidence generation', (t) => {
  const { root, aabName } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  writeFileSync(join(root, aabName), 'tampered-aab');
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /SIZE_MISMATCH|HASH_MISMATCH/);
});


test('rejects APK and AAB signed by different certificates', (t) => {
  const { root, evidence, aabName } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const mismatch = `SHA256: ${Array(32).fill('BB').join(':')}\n`;
  writeFileSync(join(root, `${aabName}.certificates.txt`), mismatch);
  evidence.artifacts[5].sha256 = hash(mismatch);
  evidence.artifacts[5].bytes = Buffer.byteLength(mismatch);
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /SIGNER_MISMATCH/);
});
