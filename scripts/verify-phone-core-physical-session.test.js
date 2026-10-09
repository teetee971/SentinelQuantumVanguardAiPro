import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { mkdtemp, mkdir, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import {
  PHONE_CORE_REQUIRED_SCENARIOS,
  fileSha256,
  physicalSessionSigningPayload,
  verifyPhoneCorePhysicalSession
} from './verify-phone-core-physical-session.js';

const NOW = Date.parse('2026-10-08T12:00:00.000Z');

function makeKeys() {
  const pair = generateKeyPairSync('ed25519');
  return {
    privateKey: pair.privateKey,
    publicKeyPem: pair.publicKey.export({ type: 'spki', format: 'pem' })
  };
}

async function makeFixture(t, mutate = () => {}) {
  const root = await mkdtemp(join(tmpdir(), 'sentinel-phone-core-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  await mkdir(join(root, 'artifacts'));
  await mkdir(join(root, 'evidence'));
  const apk = Buffer.from('signed apk fixture');
  const certificateDigest = 'e'.repeat(64);
  const certificate = Buffer.from(`Signer #1 certificate SHA-256 digest: ${certificateDigest}\n`);
  await writeFile(join(root, 'artifacts/app.apk'), apk);
  await writeFile(join(root, 'artifacts/app.apk.certificates.txt'), certificate);
  const evidenceDigests = new Map();
  for (const scenario of PHONE_CORE_REQUIRED_SCENARIOS) {
    const evidence = Buffer.from(`${scenario}: observed\n`);
    await writeFile(join(root, `evidence/${scenario}.log`), evidence);
    evidenceDigests.set(scenario, createHash('sha256').update(evidence).digest('hex'));
  }
  const keys = makeKeys();
  const session = {
    schema_version: 1,
    session_id: 'lab-session-20261008',
    status: 'PASS',
    verdict: 'PASS',
    started_at: '2026-10-08T11:00:00.000Z',
    finished_at: '2026-10-08T11:30:00.000Z',
    observed_at: '2026-10-08T11:30:00.000Z',
    signer_key_id: 'lab-key-1',
    source_sha: 'a'.repeat(40),
    artifact: {
      apk: { path: 'artifacts/app.apk', sha256: 'b'.repeat(64) },
      certificate: {
        path: 'artifacts/app.apk.certificates.txt',
        sha256: 'c'.repeat(64),
        certificate_sha256: certificateDigest
      }
    },
    metadata: {
      device_model: 'SM-S926B',
      android_sdk: 35,
      build_fingerprint: 'samsung/lab/device:15/physical/keys',
      sim_slots: 2,
      operator_profile: 'lab-carrier'
    },
    required_scenarios: [...PHONE_CORE_REQUIRED_SCENARIOS],
    evidence: Object.fromEntries(PHONE_CORE_REQUIRED_SCENARIOS.map(scenario => [scenario, {
      status: 'PASS',
      evidence_refs: [`evidence/${scenario}.log#sha256=${evidenceDigests.get(scenario)}`]
    }])),
    residuals: [],
    signature: ''
  };
  session.artifact.apk.sha256 = createHash('sha256').update(apk).digest('hex');
  session.artifact.certificate.sha256 = createHash('sha256').update(certificate).digest('hex');
  await mutate(session, root);
  session.signature = sign(null, Buffer.from(physicalSessionSigningPayload(session)), keys.privateKey).toString('base64');
  return {
    root,
    session,
    trust: { schema_version: 1, allowed_keys: [{ key_id: 'lab-key-1', public_key_pem: keys.publicKeyPem, revoked: false }] }
  };
}

test('accepts a complete signed Phone Core physical session', async t => {
  const fixture = await makeFixture(t);
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.deepEqual(result, { ok: true, verdict: 'PASS', errors: [] });
});
test('rejects an unknown status before accepting the verdict', async t => {
  const fixture = await makeFixture(t, session => { session.status = 'READY'; });
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /status invalid/);
});

test('rejects traversal, symlink and empty evidence references', async t => {
  const fixture = await makeFixture(t, session => {
    session.evidence.S01.evidence_refs = ['../outside.log'];
    session.evidence.S02.evidence_refs = ['evidence/S02.log', ''];
  });
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /evidence reference invalid/);
});
test('rejects a symlinked or missing evidence file', async t => {
  const fixture = await makeFixture(t, async (session, root) => {
    session.evidence.S01.evidence_refs = ['evidence/linked.log'];
    session.evidence.S02.evidence_refs = ['evidence/missing.log'];
    await symlink(join(root, 'evidence/S03.log'), join(root, 'evidence/linked.log'));
  });
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.equal(result.errors.filter(error => error.startsWith('evidence reference invalid')).length, 2);
});

test('rejects missing scenarios and artifact digest substitution', async t => {
  const fixture = await makeFixture(t, session => {
    session.required_scenarios = session.required_scenarios.slice(1);
    session.artifact.apk.sha256 = 'd'.repeat(64);
  });
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /required scenarios invalid|artifact sha256 mismatch/);
});

test('rejects an empty evidence file and a post-signature manifest mutation', async t => {
  const fixture = await makeFixture(t);
  await writeFile(join(fixture.root, 'evidence/S01.log'), '');
  fixture.session.metadata.device_model = 'tampered-after-signing';
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /signature invalid/);
  assert.match(result.errors.join('\n'), /evidence reference invalid/);
});

test('rejects a nonempty evidence substitution after signing', async t => {
  const fixture = await makeFixture(t);
  await writeFile(join(fixture.root, 'evidence/S01.log'), 'different observed content\n');
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /evidence sha256 mismatch/);
});

test('rejects a PASS session with unresolved residuals', async t => {
  const fixture = await makeFixture(t, session => {
    session.residuals = ['S24 latency measurement pending'];
  });
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /residuals invalid for PASS/);
});

test('rejects a signed session bound to a different source head', async t => {
  const fixture = await makeFixture(t);
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: fixture.trust,
    expectedSourceSha: 'b'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /source sha mismatch/);
});

test('rejects a validly signed session when the trust root has no authorized keys', async t => {
  const fixture = await makeFixture(t);
  const result = verifyPhoneCorePhysicalSession({
    session: fixture.session,
    baseDir: fixture.root,
    trust: { schema_version: 1, allowed_keys: [] },
    expectedSourceSha: 'a'.repeat(40),
    now: NOW
  });
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /no authorized keys/);
});

test('hashes bounded artifacts incrementally instead of buffering the complete file', () => {
  const source = readFileSync(new URL('./verify-phone-core-physical-session.js', import.meta.url), 'utf8');
  assert.match(source, /fs\.readSync/);
  assert.doesNotMatch(source, /createHash\('sha256'\)\.update\(fs\.readFileSync\(filePath\)\)/);
});

test('hashing fails closed when a file exceeds its read-time bound', async t => {
  const root = await mkdtemp(join(tmpdir(), 'sentinel-phone-core-hash-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const file = join(root, 'evidence.log');
  await writeFile(file, '0123456789');
  assert.throws(() => fileSha256(file, 4), /file exceeds maximum size/);
});
