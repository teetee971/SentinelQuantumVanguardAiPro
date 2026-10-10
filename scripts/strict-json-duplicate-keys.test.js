import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { rejectDuplicateJsonKeys } from './strict-json-duplicate-keys.js';

const verifier = path.resolve('scripts/verify-phone-core-physical-session.js');

function runVerifier(rawManifest) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-duplicate-json-'));
  try {
    const manifestPath = path.join(tmp, 'session.json');
    const publicKeyPath = path.join(tmp, 'trusted-public-key.pem');
    fs.writeFileSync(manifestPath, rawManifest);
    fs.writeFileSync(publicKeyPath, 'not-used-before-strict-json-validation\n');
    return spawnSync(process.execPath, [
      verifier,
      manifestPath,
      '--trusted-public-key', publicKeyPath,
      '--expected-source-sha', 'a'.repeat(40),
      '--expected-apk-sha256', 'b'.repeat(64),
      '--expected-certificate-sha256', 'c'.repeat(64)
    ], { encoding: 'utf8' });
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

test('strict JSON guard accepts unique keys at every object depth', () => {
  assert.doesNotThrow(() => rejectDuplicateJsonKeys('{"a":1,"nested":{"a":2},"list":[{"b":3}]}'));
});

test('strict JSON guard rejects duplicate root keys', () => {
  assert.throws(
    () => rejectDuplicateJsonKeys('{"verdict":"FAIL","verdict":"PASS"}'),
    /duplicate object key.*verdict/i
  );
});

test('strict JSON guard rejects escaped-equivalent nested duplicate keys', () => {
  assert.throws(
    () => rejectDuplicateJsonKeys('{"entry":{"status":"FAIL","st\\u0061tus":"PASS"}}'),
    /duplicate object key.*status/i
  );
});

test('strict JSON guard permits the same key name in distinct objects', () => {
  assert.doesNotThrow(() => rejectDuplicateJsonKeys('[{"status":"PASS"},{"status":"FAIL"}]'));
});

test('physical session verifier fails closed on duplicate manifest keys before qualification logic', () => {
  const result = runVerifier('{"schema_version":1,"schema_version":1}');
  assert.equal(result.status, 1, `stdout=${result.stdout}\nstderr=${result.stderr}`);
  assert.match(result.stderr, /duplicate object key.*schema_version/i);
});

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
const SOURCE_SHA = 'a'.repeat(40);
const APK_SHA = 'b'.repeat(64);
const CERT_SHA = 'c'.repeat(64);

function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stable(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function evidenceEntry(id) {
  const bytes = `physical-proof:${id}\n`;
  return {
    bytes,
    entry: {
      status: 'PASS',
      evidence_refs: [`evidence/${id}.txt#sha256=${crypto.createHash('sha256').update(bytes).digest('hex')}`]
    }
  };
}

function runWithAdditionalEntry(extraEntry) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-nonpass-proof-'));
  try {
    const evidenceDir = path.join(tmp, 'evidence');
    fs.mkdirSync(evidenceDir);
    const buildMap = ids => Object.fromEntries(ids.map(id => {
      const { bytes, entry } = evidenceEntry(id);
      fs.writeFileSync(path.join(evidenceDir, `${id}.txt`), bytes);
      return [id, entry];
    }));
    const manifest = {
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
        started_at: '2026-10-09T05:00:00Z', completed_at: '2026-10-09T05:30:00Z',
        sim_mode: 'DUAL_SIM', operator_context: 'REDACTED'
      },
      canonical_criteria: buildMap(criteriaIds),
      scenarios: buildMap(scenarioIds),
      residual_external_validation: buildMap(residualIds),
      attestation: {
        kind: 'PHYSICAL_DEVICE', algorithm: 'ed25519', signer_id: 'release-qa-lab-1', signature_base64: ''
      }
    };
    manifest.scenarios.EXTRA = extraEntry;
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
    const unsigned = structuredClone(manifest);
    delete unsigned.attestation.signature_base64;
    manifest.attestation.signature_base64 = crypto.sign(null, Buffer.from(stable(unsigned)), privateKey).toString('base64');

    const manifestPath = path.join(tmp, 'session.json');
    const publicKeyPath = path.join(tmp, 'trusted-public-key.pem');
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
    fs.writeFileSync(publicKeyPath, publicKey.export({ type: 'spki', format: 'pem' }));
    fs.writeFileSync(path.join(tmp, 'outside.txt'), 'outside\n');

    return spawnSync(process.execPath, [
      verifier, manifestPath,
      '--trusted-public-key', publicKeyPath,
      '--expected-source-sha', SOURCE_SHA,
      '--expected-apk-sha256', APK_SHA,
      '--expected-certificate-sha256', CERT_SHA
    ], { encoding: 'utf8' });
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

test('non-PASS evidence refs cannot bypass path and file verification', () => {
  const cases = [
    {
      label: 'traversal',
      entry: { status: 'NOT_REPORTED', evidence_refs: [`evidence/../outside.txt#sha256=${'d'.repeat(64)}`] },
      diagnostic: /unsafe evidence path/i
    },
    {
      label: 'missing file',
      entry: { status: 'FAIL', evidence_refs: [`evidence/missing.txt#sha256=${'e'.repeat(64)}`] },
      diagnostic: /evidence file is unavailable/i
    }
  ];

  for (const { label, entry, diagnostic } of cases) {
    const result = runWithAdditionalEntry(entry);
    assert.equal(result.status, 1, `${label} must fail closed; stdout=${result.stdout}; stderr=${result.stderr}`);
    assert.match(result.stderr, diagnostic, `${label} must report evidence verification failure`);
    assert.doesNotMatch(result.stdout, /COMMERCIAL_RELEASE_ELIGIBLE/);
  }
});
