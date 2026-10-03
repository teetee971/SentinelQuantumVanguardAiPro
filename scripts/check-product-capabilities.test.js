import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { validateProductCapabilities } from './check-product-capabilities.js';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registry = JSON.parse(
  fs.readFileSync(path.join(rootDir, 'config', 'product-capabilities.json'), 'utf8')
);

test('canonical product capability registry satisfies truth invariants', () => {
  assert.deepEqual(validateProductCapabilities(registry, rootDir), []);
});

test('customer availability cannot bypass physical validation or signed release', () => {
  const copy = structuredClone(registry);
  const phoneCore = copy.capabilities.find((item) => item.id === 'phone_core_android');
  phoneCore.customer_available = true;
  phoneCore.runtime_verified = true;
  phoneCore.blockers = [];

  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(errors.some((error) => error.includes('physical validation')));
  assert.ok(errors.some((error) => error.includes('signed release')));
});

test('deployment cannot be asserted without implementation and configuration', () => {
  const copy = structuredClone(registry);
  const vpn = copy.capabilities.find((item) => item.id === 'sentinel_vpn_service');
  vpn.implemented = false;
  vpn.configured = false;
  vpn.deployed = true;

  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(errors.some((error) => error.includes('deployed requires implemented and configured')));
});

test('evidence paths must exist', () => {
  const copy = structuredClone(registry);
  copy.capabilities[0].evidence = ['does/not/exist'];
  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(errors.some((error) => error.includes('evidence path does not exist')));
});

test('impossible dates, future dates and runtime without configuration are blocked', () => {
  for (const date of ['2026-99-99', '2026-02-30', '2999-01-01']) {
    const copy = structuredClone(registry); copy.updated_at = date;
    assert.ok(validateProductCapabilities(copy, rootDir).some(error => error.includes('calendar date')));
  }
  const copy = structuredClone(registry);
  copy.capabilities.find(item => item.id === 'repository_i18n').runtime_verified = true;
  assert.ok(validateProductCapabilities(copy, rootDir).some(error => error.includes('runtime_verified requires configured')));
});

test('availability cannot be promoted by changing booleans without operational evidence', () => {
  const copy = structuredClone(registry);
  const capability = copy.capabilities[0];
  for (const field of ['deployed', 'runtime_verified', 'physically_validated', 'release_signed', 'customer_available']) capability[field] = true;
  capability.blockers = [];
  assert.ok(validateProductCapabilities(copy, rootDir).some(error => error.includes('requires operational evidence')));
});

test('unavailable dependencies, unknown dependencies and cycles are rejected', () => {
  const copy = structuredClone(registry);
  copy.capabilities.find(item => item.id === 'collective_defense_android').runtime_verified = true;
  assert.ok(validateProductCapabilities(copy, rootDir).some(error => error.includes('dependency collective_defense_backend requires')));
  copy.capabilities[0].dependencies = [{ id: 'missing', required_stages: ['deployed'] }];
  assert.ok(validateProductCapabilities(copy, rootDir).some(error => error.includes('invalid dependency')));
  copy.capabilities[0].dependencies = [{ id: copy.capabilities[0].id, required_stages: ['runtime_verified'] }];
  assert.ok(validateProductCapabilities(copy, rootDir).some(error => error.includes('dependency cycle')));
});

// Ephemeral signing material is confined to tests; the production trust registry starts empty.
import { createHash, generateKeyPairSync, sign } from 'node:crypto';
import { tmpdir } from 'node:os';
import { stableCapabilityProof } from './check-product-capabilities.js';

function signedFixture() {
  const dir = fs.mkdtempSync(path.join(tmpdir(), 'sentinel-capability-proof-'));
  fs.writeFileSync(path.join(dir, 'runtime.js'), '// fixture runtime\n');
  const { privateKey, publicKey } = generateKeyPairSync('ed25519');
  const now = Date.parse('2026-10-03T12:00:00.000Z');
  const capability = structuredClone(registry.capabilities[0]);
  capability.evidence = ['runtime.js']; capability.source_scope = ['runtime.js'];
  capability.dependencies = []; capability.blockers = [];
  capability.evidence_sha = 'a'.repeat(40); capability.evidence_at = new Date(now - 1000).toISOString();
  const proofs = {};
  function write(stage) {
    const payload = proofs[stage];
    fs.writeFileSync(path.join(dir, `${stage}.json`), JSON.stringify({ issuer: 'test-only', payload,
      signature: sign(null, Buffer.from(stableCapabilityProof(payload)), privateKey).toString('base64') }));
  }
  for (const stage of ['deployed', 'runtime_verified', 'physically_validated', 'release_signed', 'customer_available']) {
    capability[stage] = true; capability.verification_evidence[stage] = `${stage}.json`;
    proofs[stage] = { proof_type: 'SENTINEL_PRODUCT_CAPABILITY_OBSERVATION', schema_version: 1, issuer_id: 'test-only', capability_id: capability.id, stage, result: 'PASS', evidence_sha: capability.evidence_sha,
      evidence_at: capability.evidence_at, expires_at: new Date(now + 100000).toISOString(),
      artifact_sha256: 'b'.repeat(64), environment: 'test-lab', scope_id: 'fixture-install:device',
      source_files: { 'runtime.js': createHash('sha256').update(fs.readFileSync(path.join(dir, 'runtime.js'))).digest('hex') },
      validation_kind: 'PHYSICAL_DEVICE', criteria: capability.required_physical_criteria.map(id => ({ id, passed: true })) };
    write(stage);
  }
  const trust = { schema_version: 1, issuers: [{ id: 'test-only', revoked: false, public_key_pem: publicKey.export({ type: 'spki', format: 'pem' }),
    stages: Object.keys(proofs), capabilities: [capability.id], environments: ['test-lab'] }] };
  return { dir, now, trust, proofs, write, registry: { schema_version: 2, updated_at: '2026-10-03', capabilities: [capability] } };
}

test('authorized signed observations are accepted only for an unchanged source and exact scope', () => {
  const fixture = signedFixture();
  try {
    assert.deepEqual(validateProductCapabilities(fixture.registry, fixture.dir, fixture), []);
    fs.appendFileSync(path.join(fixture.dir, 'runtime.js'), '// modified\n');
    assert.ok(validateProductCapabilities(fixture.registry, fixture.dir, fixture).some(error => error.includes('source scope changed')));
  } finally { fs.rmSync(fixture.dir, { recursive: true, force: true }); }
});

test('signed but incomplete physical evidence cannot qualify Phone Core', () => {
  const fixture = signedFixture();
  try {
    fixture.proofs.physically_validated.criteria.pop(); fixture.write('physically_validated');
    assert.ok(validateProductCapabilities(fixture.registry, fixture.dir, fixture).some(error => error.includes('physical criteria incomplete')));
  } finally { fs.rmSync(fixture.dir, { recursive: true, force: true }); }
});

test('expiry, issuer revocation, artifact substitution and signature tampering are rejected', () => {
  const fixture = signedFixture();
  try {
    assert.ok(validateProductCapabilities(fixture.registry, fixture.dir, { ...fixture, now: fixture.now + 100001 }).some(error => error.includes('freshness')));
    fixture.trust.issuers[0].revoked = true;
    assert.ok(validateProductCapabilities(fixture.registry, fixture.dir, fixture).some(error => error.includes('issuer not authorized')));
    fixture.trust.issuers[0].revoked = false;
    fixture.proofs.release_signed.artifact_sha256 = 'c'.repeat(64); fixture.write('release_signed');
    assert.ok(validateProductCapabilities(fixture.registry, fixture.dir, fixture).some(error => error.includes('proof scope mismatch')));
    const file = path.join(fixture.dir, 'deployed.json');
    const record = JSON.parse(fs.readFileSync(file, 'utf8')); record.payload.environment = 'substituted';
    fs.writeFileSync(file, JSON.stringify(record));
    assert.ok(validateProductCapabilities(fixture.registry, fixture.dir, fixture).some(error => error.includes('evidence rejected')));
  } finally { fs.rmSync(fixture.dir, { recursive: true, force: true }); }
});
