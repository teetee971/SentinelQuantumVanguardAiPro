#!/usr/bin/env node
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

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

const RFC3339_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|([+-])(\d{2}):(\d{2}))$/;
const VALID_SIM_MODES = new Set(['SINGLE_SIM', 'DUAL_SIM', 'MULTI_SIM']);
const VALID_EVIDENCE_STATUSES = new Set(['PASS', 'FAIL', 'NOT_EXECUTED', 'UNKNOWN', 'NOT_REPORTED']);
const ROOT_KEYS = new Set([
  'schema_version', 'source_head_sha', 'apk', 'device', 'session',
  'canonical_criteria', 'scenarios', 'residual_external_validation', 'attestation'
]);
const APK_KEYS = new Set(['package_name', 'version_name', 'version_code', 'sha256', 'certificate_sha256']);
const DEVICE_KEYS = new Set(['manufacturer', 'model', 'android_version', 'api_level', 'build_fingerprint']);
const SESSION_KEYS = new Set(['started_at', 'completed_at', 'sim_mode', 'operator_context']);
const ATTESTATION_KEYS = new Set(['kind', 'algorithm', 'signer_id', 'signature_base64']);
const EVIDENCE_ENTRY_KEYS = new Set(['status', 'evidence_refs']);

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

function requireObject(value, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail(`${label} must be an object`);
  return value;
}

function requireAllowedKeys(value, allowedKeys, label) {
  requireObject(value, label);
  const additional = Object.keys(value).filter(key => !allowedKeys.has(key));
  if (additional.length > 0) {
    fail(`${label} contains additional properties: ${additional.sort().join(', ')}`);
  }
  return value;
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

function requirePositiveInteger(value, label) {
  if (!Number.isInteger(value) || value < 1) fail(`${label} is required`);
  return value;
}

function requireDateTime(value, label) {
  requireText(value, label);
  const match = RFC3339_DATE_TIME.exec(value);
  if (!match) fail(`${label} must be a valid date-time`);

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6]);
  const offsetHour = match[8] === 'Z' ? 0 : Number(match[10]);
  const offsetMinute = match[8] === 'Z' ? 0 : Number(match[11]);
  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

  if (
    month < 1 || month > 12 ||
    day < 1 || day > daysInMonth[month - 1] ||
    hour > 23 || minute > 59 || second > 59 ||
    offsetHour > 23 || offsetMinute > 59
  ) {
    fail(`${label} must be a valid date-time`);
  }

  const parsed = Date.parse(value);
  if (!Number.isFinite(parsed)) fail(`${label} must be a valid date-time`);
  return parsed;
}

const EVIDENCE_REF = /^(evidence\/[A-Za-z0-9_./-]+)#sha256=([0-9a-fA-F]{64})$/;

function validateEvidenceEntryShape(entry, label) {
  requireAllowedKeys(entry, EVIDENCE_ENTRY_KEYS, label);
  if (!VALID_EVIDENCE_STATUSES.has(entry.status)) fail(`${label}.status is invalid`);
  if (!Array.isArray(entry.evidence_refs) || entry.evidence_refs.length === 0) {
    fail(`${label}.evidence_refs must be a nonempty array`);
  }
  if (entry.evidence_refs.some(ref => typeof ref !== 'string' || !EVIDENCE_REF.test(ref))) {
    fail(`${label}.evidence_refs must contain signed local evidence references`);
  }
  if (new Set(entry.evidence_refs).size !== entry.evidence_refs.length) {
    fail(`${label} repeats evidence references`);
  }
}

function verifyEvidenceRef(ref, label) {
  const match = EVIDENCE_REF.exec(ref);
  if (!match) fail(label + ' must be a local evidence path with a signed SHA-256 digest');
  const parts = match[1].split('/');
  if (parts.some(part => !part || part === '.' || part === '..')) {
    fail(label + ' contains an unsafe evidence path');
  }

  const root = path.dirname(path.resolve(options.manifestPath));
  const evidencePath = path.resolve(root, ...parts);
  if (!evidencePath.startsWith(root + path.sep)) {
    fail(label + ' escapes the session directory');
  }

  let current = root;
  try {
    for (let index = 0; index < parts.length; index++) {
      current = path.join(current, parts[index]);
      const entry = fs.lstatSync(current);
      if (entry.isSymbolicLink()) fail(label + ' must not use symbolic links');
      if (index < parts.length - 1 && !entry.isDirectory()) fail(label + ' has a non-directory parent');
      if (index === parts.length - 1 && !entry.isFile()) fail(label + ' is not a regular file');
    }
  } catch (error) {
    fail(label + ' evidence file is unavailable or unsafe: ' + error.message);
  }

  let fd;
  try {
    fd = fs.openSync(evidencePath, fs.constants.O_RDONLY | (fs.constants.O_NOFOLLOW || 0));
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || stat.size < 1) fail(label + ' evidence file must be nonempty and regular');
    const digest = crypto.createHash('sha256');
    const buffer = Buffer.allocUnsafe(65536);
    let bytesRead;
    while ((bytesRead = fs.readSync(fd, buffer, 0, buffer.length, null)) > 0) {
      digest.update(buffer.subarray(0, bytesRead));
    }
    if (digest.digest('hex') !== match[2].toLowerCase()) fail(label + ' evidence SHA-256 mismatch');
  } catch (error) {
    fail(label + ' evidence verification failed: ' + error.message);
  } finally {
    if (fd !== undefined) fs.closeSync(fd);
  }
}

function validateEvidenceMap(map, ids, label) {
  requireObject(map, label);
  for (const [entryId, entry] of Object.entries(map)) {
    const entryLabel = `${label}.${entryId}`;
    validateEvidenceEntryShape(entry, entryLabel);
    if (entry.status === 'PASS') {
      for (const ref of entry.evidence_refs) {
        if (/(^|[\/_.-])(emulator|synthetic|mock)([\/_.-]|$)/i.test(ref)) {
          fail(entryLabel + ' references non-physical evidence');
        }
        verifyEvidenceRef(ref, entryLabel);
      }
    }
  }

  const nonPass = [];
  for (const id of ids) {
    const entry = map[id];
    if (!entry) {
      nonPass.push(id);
      continue;
    }
    if (entry.status !== 'PASS') {
      nonPass.push(id);
      continue;
    }
  }
  return nonPass;
}

const options = parseArgs(process.argv.slice(2));
const manifest = readJson(options.manifestPath, 'physical session manifest');

requireAllowedKeys(manifest, ROOT_KEYS, 'manifest');
requireAllowedKeys(manifest.apk, APK_KEYS, 'apk');
requireAllowedKeys(manifest.device, DEVICE_KEYS, 'device');
requireAllowedKeys(manifest.session, SESSION_KEYS, 'session');
requireAllowedKeys(manifest.attestation, ATTESTATION_KEYS, 'attestation');

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
requireText(manifest.apk?.version_name, 'APK version_name');
requirePositiveInteger(manifest.apk?.version_code, 'APK version_code');
requireText(manifest.device?.manufacturer, 'device manufacturer');
requireText(manifest.device?.model, 'device model');
requireText(manifest.device?.android_version, 'device android_version');
requireText(manifest.device?.build_fingerprint, 'device build_fingerprint');
if (!Number.isInteger(manifest.device?.api_level) || manifest.device.api_level < 1) fail('device api_level is required');
requireText(manifest.session?.sim_mode, 'session sim_mode');
if (!VALID_SIM_MODES.has(manifest.session.sim_mode)) fail('session sim_mode is invalid');
requireText(manifest.session?.operator_context, 'session operator_context');
const startedAt = requireDateTime(manifest.session?.started_at, 'session started_at');
const completedAt = requireDateTime(manifest.session?.completed_at, 'session completed_at');
if (completedAt <= startedAt) fail('session completed_at must be after session started_at');

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
