#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';

const REQUIRED_CANONICAL_CRITERIA = [
  'incoming_call_connected',
  'outgoing_call_connected',
  'call_screening_observed',
  'contacts_provider_ready',
  'call_history_provider_ready',
  'incoming_sms_received',
  'outgoing_sms_submitted',
  'outgoing_sms_delivered',
  'incoming_mms_safe_preview',
  'outgoing_mms_sent',
  'incoming_call_notification',
  'incoming_sms_notification',
  'caller_id_ui_shown',
  'in_call_ui_shown'
];

const REQUIRED_SCENARIOS = Array.from(
  { length: 26 },
  (_, index) => `S${String(index + 1).padStart(2, '0')}`
);

const REQUIRED_COMMERCIAL_RESIDUALS = [
  'real_carrier_sms_mms_callbacks',
  'samsung_s24_screening_latency',
  'android10_call_screening_decision',
  'physical_dual_sim_oem_compatibility'
];

function fail(message) {
  process.stderr.write(`PHONE CORE PHYSICAL SESSION: ${message}\n`);
  process.exit(1);
}

function usage() {
  fail('usage: verify-phone-core-physical-session.js <manifest.json> --trusted-public-key <key.pem> --expected-source-sha <sha> --expected-apk-sha256 <sha256> --expected-certificate-sha256 <sha256>');
}

function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stable(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function signingPayload(manifest) {
  const copy = structuredClone(manifest);
  if (!copy.attestation || typeof copy.attestation !== 'object') {
    fail('attestation is required');
  }
  delete copy.attestation.signature_base64;
  return Buffer.from(stable(copy));
}

function parseArgs(argv) {
  if (argv.length < 1 || argv[0].startsWith('--')) usage();
  const options = { manifestPath: argv[0] };
  for (let i = 1; i < argv.length; i += 2) {
    const flag = argv[i];
    const value = argv[i + 1];
    if (!flag?.startsWith('--') || value === undefined) usage();
    if (flag === '--trusted-public-key') options.publicKeyPath = value;
    else if (flag === '--expected-source-sha') options.expectedSourceSha = value;
    else if (flag === '--expected-apk-sha256') options.expectedApkSha256 = value;
    else if (flag === '--expected-certificate-sha256') options.expectedCertificateSha256 = value;
    else fail(`unknown argument: ${flag}`);
  }
  if (!options.publicKeyPath || !options.expectedSourceSha || !options.expectedApkSha256 || !options.expectedCertificateSha256) usage();
  return options;
}

function readJson(path, label) {
  try {
    return JSON.parse(fs.readFileSync(path, 'utf8'));
  } catch (error) {
    fail(`cannot read ${label}: ${error.message}`);
  }
}

function requireHex(value, length, label) {
  const pattern = new RegExp(`^[0-9a-fA-F]{${length}}$`);
  if (typeof value !== 'string' || !pattern.test(value)) fail(`${label} must be ${length} hexadecimal characters`);
  return value.toLowerCase();
}

function requireText(value, label) {
  if (typeof value !== 'string' || value.trim() === '') fail(`${label} is required`);
  return value;
}

function validateEvidenceMap(map, ids, label) {
  if (!map || typeof map !== 'object' || Array.isArray(map)) return [...ids];
  const nonPass = [];
  for (const id of ids) {
    const entry = map[id];
    if (!entry || typeof entry !== 'object' || entry.status !== 'PASS') {
      nonPass.push(id);
      continue;
    }
    if (!Array.isArray(entry.evidence_refs) || entry.evidence_refs.length === 0 || entry.evidence_refs.some(ref => typeof ref !== 'string' || ref.trim() === '')) {
      nonPass.push(id);
      continue;
    }
    if (entry.evidence_refs.some(ref => /(^|[\/_.-])(emulator|synthetic|mock)([\/_.-]|$)/i.test(ref))) {
      fail(`${label}.${id} references non-physical evidence`);
    }
  }
  return nonPass;
}

const options = parseArgs(process.argv.slice(2));
const manifest = readJson(options.manifestPath, 'physical session manifest');

if (manifest.schema_version !== 1) fail('unsupported physical session schema_version');
if (manifest.attestation?.kind !== 'PHYSICAL_DEVICE') fail('attestation kind must be PHYSICAL_DEVICE');
if (manifest.attestation?.algorithm !== 'ed25519') fail('attestation algorithm must be ed25519');
requireText(manifest.attestation?.signer_id, 'attestation signer_id');
requireText(manifest.attestation?.signature_base64, 'attestation signature_base64');

const sourceSha = requireHex(manifest.source_head_sha, 40, 'source head SHA');
const apkSha256 = requireHex(manifest.apk?.sha256, 64, 'APK SHA-256');
const certificateSha256 = requireHex(manifest.apk?.certificate_sha256, 64, 'certificate SHA-256');
const expectedSourceSha = requireHex(options.expectedSourceSha, 40, 'expected source head SHA');
const expectedApkSha256 = requireHex(options.expectedApkSha256, 64, 'expected APK SHA-256');
const expectedCertificateSha256 = requireHex(options.expectedCertificateSha256, 64, 'expected certificate SHA-256');

requireText(manifest.apk?.package_name, 'APK package_name');
requireText(manifest.device?.manufacturer, 'device manufacturer');
requireText(manifest.device?.model, 'device model');
requireText(manifest.device?.build_fingerprint, 'device build_fingerprint');
if (!Number.isInteger(manifest.device?.api_level) || manifest.device.api_level < 1) fail('device api_level is required');
requireText(manifest.session?.started_at, 'session started_at');
requireText(manifest.session?.completed_at, 'session completed_at');

let publicKey;
try {
  publicKey = fs.readFileSync(options.publicKeyPath, 'utf8');
} catch (error) {
  fail(`cannot read trusted public key: ${error.message}`);
}

let signature;
try {
  signature = Buffer.from(manifest.attestation.signature_base64, 'base64');
  if (signature.length === 0) throw new Error('empty signature');
} catch (error) {
  fail(`invalid signature encoding: ${error.message}`);
}

let signatureValid = false;
try {
  signatureValid = crypto.verify(null, signingPayload(manifest), publicKey, signature);
} catch (error) {
  fail(`signature verification failed: ${error.message}`);
}
if (!signatureValid) fail('signature verification failed');

if (sourceSha !== expectedSourceSha) fail(`source head SHA mismatch: expected ${expectedSourceSha}, got ${sourceSha}`);
if (apkSha256 !== expectedApkSha256) fail(`APK SHA-256 mismatch: expected ${expectedApkSha256}, got ${apkSha256}`);
if (certificateSha256 !== expectedCertificateSha256) fail(`certificate SHA-256 mismatch: expected ${expectedCertificateSha256}, got ${certificateSha256}`);

const canonicalNonPass = validateEvidenceMap(manifest.canonical_criteria, REQUIRED_CANONICAL_CRITERIA, 'canonical_criteria');
const scenarioNonPass = validateEvidenceMap(manifest.scenarios, REQUIRED_SCENARIOS, 'scenarios');
const residualNonPass = validateEvidenceMap(manifest.residual_external_validation, REQUIRED_COMMERCIAL_RESIDUALS, 'residual_external_validation');

let verdict;
let exitCode;
if (canonicalNonPass.length > 0 || scenarioNonPass.length > 0) {
  verdict = 'PHYSICAL_SESSION_INCOMPLETE';
  exitCode = 2;
} else if (residualNonPass.length > 0) {
  verdict = 'PHYSICAL_PHONE_CORE_PASS';
  exitCode = 2;
} else {
  verdict = 'COMMERCIAL_RELEASE_ELIGIBLE';
  exitCode = 0;
}

process.stdout.write(`${JSON.stringify({
  verdict,
  source_head_sha: sourceSha,
  apk_sha256: apkSha256,
  certificate_sha256: certificateSha256,
  attestation: {
    kind: manifest.attestation.kind,
    algorithm: manifest.attestation.algorithm,
    signer_id: manifest.attestation.signer_id,
    signature_verified: true
  },
  device: {
    manufacturer: manifest.device.manufacturer,
    model: manifest.device.model,
    api_level: manifest.device.api_level,
    build_fingerprint: manifest.device.build_fingerprint
  },
  incomplete: {
    canonical_criteria: canonicalNonPass,
    scenarios: scenarioNonPass,
    residual_external_validation: residualNonPass
  }
}, null, 2)}\n`);
process.exit(exitCode);
