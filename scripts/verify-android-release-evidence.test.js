import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
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
  const nativeInventory = `${JSON.stringify({
    schema_version: 2,
    configuration: 'releaseRuntimeClasspath',
    root_component: 'gradle-project::app',
    component_count: 2,
    relationship_count: 1,
    components: [
      { key: 'pkg:maven/com.example/fixture@1.0.0', display_name: 'com.example:fixture:1.0.0', type: 'module', group: 'com.example', name: 'fixture', version: '1.0.0', purl: 'pkg:maven/com.example/fixture@1.0.0' },
      { key: 'gradle-project::app', display_name: 'project :app', type: 'project', project_path: ':app', build_tree_path: ':app' },
    ],
    relationships: [
      { from: 'gradle-project::app', to: 'pkg:maven/com.example/fixture@1.0.0', requested: 'com.example:fixture:1.0.0', constraint: false },
    ],
  })}\n`;
  const nativeInventoryPath = join(root, 'native-android-app', 'app', 'build', 'reports', 'release-dependencies.json');
  mkdirSync(join(root, 'native-android-app', 'app', 'build', 'reports'), { recursive: true });
  writeFileSync(join(root, apkName), apk);
  writeFileSync(join(root, `${apkName}.sha256`), checksum);
  writeFileSync(join(root, `${apkName}.certificates.txt`), certificate);
  writeFileSync(join(root, aabName), aab);
  writeFileSync(join(root, `${aabName}.sha256`), aabChecksum);
  writeFileSync(join(root, `${aabName}.certificates.txt`), aabCertificate);
  writeFileSync(join(root, 'release-sbom.cdx.json'), sbom);
  writeFileSync(nativeInventoryPath, nativeInventory);
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
      { path: 'native-android-app/app/build/reports/release-dependencies.json', sha256: hash(nativeInventory), bytes: Buffer.byteLength(nativeInventory) },
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

test('rejects a native dependency graph modified after evidence generation', (t) => {
  const { root } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  writeFileSync(
    join(root, 'native-android-app', 'app', 'build', 'reports', 'release-dependencies.json'),
    '{"schema_version":2,"configuration":"releaseRuntimeClasspath","root_component":"project:app","component_count":1,"relationship_count":0,"components":[],"relationships":[]}\n'
  );
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /SIZE_MISMATCH|HASH_MISMATCH/);
});

test('rejects a dependency relationship whose endpoint is not in the component set', (t) => {
  const { root, evidence } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const path = join(root, 'native-android-app', 'app', 'build', 'reports', 'release-dependencies.json');
  const inventory = JSON.parse(readFileSync(path, 'utf8'));
  inventory.relationships[0].to = 'pkg:maven/unknown/missing@9.9.9';
  const invalid = `${JSON.stringify(inventory)}\n`;
  writeFileSync(path, invalid);
  evidence.artifacts[6].sha256 = hash(invalid);
  evidence.artifacts[6].bytes = Buffer.byteLength(invalid);
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /INVALID_NATIVE_DEPENDENCY_RELATIONSHIP_ENDPOINT/);
});

test('rejects a native dependency graph with duplicate component keys', (t) => {
  const { root, evidence } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const path = join(root, 'native-android-app', 'app', 'build', 'reports', 'release-dependencies.json');
  const inventory = JSON.parse(readFileSync(path, 'utf8'));
  inventory.components[1].key = inventory.components[0].key;
  inventory.root_component = inventory.components[0].key;
  const invalid = `${JSON.stringify(inventory)}\n`;
  writeFileSync(path, invalid);
  evidence.artifacts[6].sha256 = hash(invalid);
  evidence.artifacts[6].bytes = Buffer.byteLength(invalid);
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /INVALID_NATIVE_DEPENDENCY_COMPONENT_KEY/);
});

test('rejects a project component without a build-tree identity', (t) => {
  const { root, evidence } = fixture();
  t.after(() => rmSync(root, { recursive: true }));
  const path = join(root, 'native-android-app', 'app', 'build', 'reports', 'release-dependencies.json');
  const inventory = JSON.parse(readFileSync(path, 'utf8'));
  delete inventory.components.find((component) => component.type === 'project').build_tree_path;
  const invalid = `${JSON.stringify(inventory)}\n`;
  writeFileSync(path, invalid);
  evidence.artifacts[6].sha256 = hash(invalid);
  evidence.artifacts[6].bytes = Buffer.byteLength(invalid);
  writeFileSync(join(root, 'release-evidence.json'), `${JSON.stringify(evidence)}\n`);
  assert.throws(() => verifyAndroidReleaseEvidence({ root }), /INVALID_NATIVE_DEPENDENCY_PROJECT/);
});
