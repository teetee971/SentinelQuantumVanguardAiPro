import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const verifier = path.resolve('scripts/verify-phone-core-physical-session.js');
const policyPath = path.resolve('config/phone-core-production-gates.json');
const schemaPath = path.resolve('config/phone-core-physical-session.schema.json');
const protocolPath = path.resolve('docs/PHONE_CORE_PHYSICAL_VALIDATION.md');
const SOURCE_SHA = '3a864a22db53fefcdf20f486b52fc5edfe4ab68b';
const APK_SHA = 'a'.repeat(64);
const CERT_SHA = 'b'.repeat(64);
const criteriaIds = [
  'incoming_call_connected', 'outgoing_call_connected', 'call_screening_observed',
  'contacts_provider_ready', 'call_history_provider_ready', 'incoming_sms_received',
  'outgoing_sms_submitted', 'outgoing_sms_delivered', 'incoming_mms_safe_preview',
  'outgoing_mms_sent', 'incoming_call_notification', 'incoming_sms_notification',
  'caller_id_ui_shown', 'in_call_ui_shown'
];
const scenarioIds = Array.from({ length: 26 }, (_, i) => `S${String(i + 1).padStart(2, '0')}`);
const residualIds = [
  'real_carrier_sms_mms_callbacks', 'samsung_s24_screening_latency',
  'android10_call_screening_decision', 'physical_dual_sim_oem_compatibility'
];

function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stable(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function signingPayload(manifest) {
  const copy = structuredClone(manifest);
  delete copy.attestation.signature_base64;
  return Buffer.from(stable(copy));
}

function baseManifest({ allScenarios = true, allResiduals = true } = {}) {
  const status = (id) => {
    const digest = crypto.createHash('sha256').update('fixture-proof:' + id + '\n').digest('hex');
    return { status: 'PASS', evidence_refs: ['evidence/' + id + '.txt#sha256=' + digest] };
  };
  return {
    schema_version: 1,
    source_head_sha: SOURCE_SHA,
    apk: {
      package_name: 'com.sentinel.quantum', version_name: '2.0.0-pro', version_code: 1,
      sha256: APK_SHA, certificate_sha256: CERT_SHA
    },
    device: {
      manufacturer: 'Samsung', model: 'SM-S926B', android_version: '16', api_level: 36,
      build_fingerprint: 'samsung/e3qxxx/e3q:16/test/release-keys'
    },
    session: {
      started_at: '2026-10-07T22:00:00Z', completed_at: '2026-10-07T22:30:00Z',
      sim_mode: 'DUAL_SIM', operator_context: 'REDACTED'
    },
    canonical_criteria: Object.fromEntries(criteriaIds.map(id => [id, status(id)])),
    scenarios: Object.fromEntries((allScenarios ? scenarioIds : scenarioIds.slice(0, -1)).map(id => [id, status(id)])),
    residual_external_validation: Object.fromEntries((allResiduals ? residualIds : residualIds.slice(0, -1)).map(id => [id, status(id)])),
    attestation: {
      kind: 'PHYSICAL_DEVICE', algorithm: 'ed25519', signer_id: 'release-qa-lab-1', signature_base64: ''
    }
  };
}

function writeSignedFixture(manifest, { tamperAfterSign = false, mutateFixture } = {}) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-physical-proof-'));
  const bin = path.join(tmp, 'bin');
  const evidence = path.join(tmp, 'evidence.txt');
  const marker = path.join(tmp, 'marker.txt');
  const token = path.join(tmp, 'token');
  fs.mkdirSync(bin);
  fs.mkdirSync(path.join(tmp, 'evidence'));
  fs.writeFileSync(marker, 'outside-evidence\n');
  for (const map of [manifest.canonical_criteria, manifest.scenarios, manifest.residual_external_validation]) {
    for (const [id, entry] of Object.entries(map)) {
      if (entry.evidence_refs?.some(ref => ref.startsWith('evidence/' + id + '.txt#sha256='))) {
        fs.writeFileSync(path.join(tmp, 'evidence', id + '.txt'), 'fixture-proof:' + id + '\n');
      }
    }
  }
  fs.writeFileSync(token, 'fixture-token\n');
  const manifestPath = path.join(tmp, 'session.json');
  const publicKeyPath = path.join(tmp, 'trusted-public-key.pem');
  const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
  manifest.attestation.signature_base64 = crypto.sign(null, signingPayload(manifest), privateKey).toString('base64');
  if (tamperAfterSign) manifest.device.model = 'TAMPERED';
  fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  fs.writeFileSync(publicKeyPath, publicKey.export({ type: 'spki', format: 'pem' }));
  const fixture = { tmp, manifestPath, publicKeyPath, evidence, marker };
  if (mutateFixture) mutateFixture(fixture);
  return fixture;
}

function run(manifest, options = {}) {
  const fixture = writeSignedFixture(manifest, options);
  const result = spawnSync(process.execPath, [
    verifier, fixture.manifestPath,
    '--trusted-public-key', fixture.publicKeyPath,
    '--expected-source-sha', SOURCE_SHA,
    '--expected-apk-sha256', APK_SHA,
    '--expected-certificate-sha256', CERT_SHA
  ], { encoding: 'utf8' });
  fs.rmSync(fixture.tmp, { recursive: true, force: true });
  return result;
}

test('physical session verifier exists', () => {
  assert.equal(fs.existsSync(verifier), true, 'physical session verifier must exist');
});

test('fully signed exact-artifact physical evidence is commercially eligible', { skip: !fs.existsSync(verifier) }, () => {
  const result = run(baseManifest());
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /COMMERCIAL_RELEASE_ELIGIBLE/);
});

test('signed PASS evidence rejects missing files', () => {
  const result = run(baseManifest(), {
    mutateFixture: fixture => fs.rmSync(path.join(fixture.tmp, 'evidence', 'S01.txt'))
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /evidence file is unavailable/i);
});

test('signed PASS evidence rejects altered file bytes', () => {
  const result = run(baseManifest(), {
    mutateFixture: fixture => fs.writeFileSync(path.join(fixture.tmp, 'evidence', 'S01.txt'), 'altered proof\n')
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /SHA-256 mismatch/i);
});

test('signed PASS evidence rejects symbolic links', () => {
  const result = run(baseManifest(), {
    mutateFixture: fixture => {
      const file = path.join(fixture.tmp, 'evidence', 'S01.txt');
      fs.rmSync(file);
      fs.symlinkSync(fixture.marker, file);
    }
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /symbolic links/i);
});

test('signed PASS evidence rejects traversal and missing digests', () => {
  for (const ref of ['evidence/../marker.txt#sha256=' + 'a'.repeat(64), 'evidence/S01.txt']) {
    const manifest = baseManifest();
    manifest.scenarios.S01.evidence_refs = [ref];
    const result = run(manifest);
    assert.equal(result.status, 1, 'unsafe evidence ref must fail closed');
    assert.match(result.stderr, /unsafe evidence path|signed SHA-256 digest/i);
  }
});

test('phone-core can pass while unresolved commercial residuals still block release', { skip: !fs.existsSync(verifier) }, () => {
  const result = run(baseManifest({ allResiduals: false }));
  assert.equal(result.status, 2, result.stderr);
  assert.match(result.stdout, /PHYSICAL_PHONE_CORE_PASS/);
  assert.doesNotMatch(result.stdout, /COMMERCIAL_RELEASE_ELIGIBLE/);
});

test('missing S01-S26 physical scenario keeps the session incomplete', { skip: !fs.existsSync(verifier) }, () => {
  const result = run(baseManifest({ allScenarios: false }));
  assert.equal(result.status, 2, result.stderr);
  assert.match(result.stdout, /PHYSICAL_SESSION_INCOMPLETE/);
});

test('emulator evidence can never masquerade as physical evidence', { skip: !fs.existsSync(verifier) }, () => {
  const manifest = baseManifest();
  manifest.attestation.kind = 'EMULATOR';
  const result = run(manifest);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /PHYSICAL_DEVICE/);
});

test('tampered signed manifest is rejected', { skip: !fs.existsSync(verifier) }, () => {
  const result = run(baseManifest(), { tamperAfterSign: true });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /signature/i);
});

test('exact source SHA and APK digest are mandatory', { skip: !fs.existsSync(verifier) }, () => {
  const manifest = baseManifest();
  manifest.source_head_sha = '0'.repeat(40);
  const result = run(manifest);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /source head/i);
});

test('physical session timestamps must be parseable date-times', { skip: !fs.existsSync(verifier) }, () => {
  const manifest = baseManifest();
  manifest.session.completed_at = 'not-a-date';
  const result = run(manifest);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /completed_at.*date-time/i);
});

test('physical session timestamps reject impossible calendar dates', { skip: !fs.existsSync(verifier) }, () => {
  const manifest = baseManifest();
  manifest.session.completed_at = '2026-02-31T22:30:00Z';
  const result = run(manifest);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /completed_at.*date-time/i);
});

test('physical session completion must be strictly after start', { skip: !fs.existsSync(verifier) }, () => {
  const manifest = baseManifest();
  manifest.session.completed_at = manifest.session.started_at;
  const result = run(manifest);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /completed_at.*after.*started_at/i);
});

test('schema-required physical session metadata cannot be omitted from a signed manifest', { skip: !fs.existsSync(verifier) }, () => {
  const cases = [
    ['APK version_name', manifest => { delete manifest.apk.version_name; }],
    ['APK version_code', manifest => { delete manifest.apk.version_code; }],
    ['device android_version', manifest => { delete manifest.device.android_version; }],
    ['session sim_mode', manifest => { delete manifest.session.sim_mode; }],
    ['session operator_context', manifest => { delete manifest.session.operator_context; }]
  ];

  for (const [label, mutate] of cases) {
    const manifest = baseManifest();
    mutate(manifest);
    const result = run(manifest);
    assert.equal(result.status, 1, `${label} omission must fail closed; stderr=${result.stderr}; stdout=${result.stdout}`);
  }
});

test('production policy declares the machine-readable physical session contract', () => {
  const policy = JSON.parse(fs.readFileSync(policyPath, 'utf8'));
  const physical = policy.physical_session_manifest;
  assert.ok(physical, 'physical_session_manifest policy is required');
  assert.equal(physical.schema_version, 1);
  assert.equal(physical.schema_path, 'config/phone-core-physical-session.schema.json');
  assert.equal(physical.verifier_path, 'scripts/verify-phone-core-physical-session.js');
  assert.equal(physical.attestation_kind, 'PHYSICAL_DEVICE');
  assert.equal(physical.signature_algorithm, 'ed25519');
  assert.deepEqual(physical.required_canonical_criteria, criteriaIds);
  assert.deepEqual(physical.required_campaign_scenarios, scenarioIds);
  assert.deepEqual(physical.required_commercial_residuals, residualIds);
  assert.equal(physical.commercial_release_verdict, 'COMMERCIAL_RELEASE_ELIGIBLE');
});

test('physical session JSON schema and protocol document the same fail-closed boundary', () => {
  assert.equal(fs.existsSync(schemaPath), true, 'physical session schema must exist');
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
  assert.equal(schema.properties?.attestation?.properties?.kind?.const, 'PHYSICAL_DEVICE');
  assert.equal(schema.properties?.attestation?.properties?.algorithm?.const, 'ed25519');
  assert.deepEqual(schema.required, ['schema_version', 'source_head_sha', 'apk', 'device', 'session', 'canonical_criteria', 'scenarios', 'residual_external_validation', 'attestation']);
  const protocol = fs.readFileSync(protocolPath, 'utf8');
  assert.match(protocol, /verify-phone-core-physical-session\.js/);
  assert.match(protocol, /PHYSICAL_SESSION_INCOMPLETE/);
  assert.match(protocol, /PHYSICAL_PHONE_CORE_PASS/);
  assert.match(protocol, /COMMERCIAL_RELEASE_ELIGIBLE/);
  assert.match(protocol, /Ed25519/i);
});
