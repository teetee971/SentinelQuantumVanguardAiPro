#!/usr/bin/env node
import fs from 'node:fs';
import { createHash, createPublicKey, verify } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX_MANIFEST_BYTES = 256 * 1024;
const MAX_TRUST_BYTES = 256 * 1024;
const MAX_EVIDENCE_BYTES = 16 * 1024 * 1024;
const MAX_APK_BYTES = 512 * 1024 * 1024;
const ISO_UTC = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;
const SHA256 = /^[a-f0-9]{64}$/;
const SOURCE_SHA = /^[a-f0-9]{40}$/;
const BASE64 = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;
const STATUSES = new Set(['PASS', 'FAIL', 'INCOMPLETE']);
const EVIDENCE_REF = /^(.*)#sha256=([a-f0-9]{64})$/;
export const PHONE_CORE_REQUIRED_SCENARIOS = Object.freeze(
  Array.from({ length: 26 }, (_, index) => `S${String(index + 1).padStart(2, '0')}`)
);

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function exactKeys(value, expected) {
  return isPlainObject(value) &&
    Object.keys(value).length === expected.length &&
    expected.every(key => Object.prototype.hasOwnProperty.call(value, key));
}

function canonical(value) {
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  if (isPlainObject(value)) {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
  }
  if (typeof value === 'number' && !Number.isFinite(value)) throw new Error('non-finite number');
  if (value === undefined) throw new Error('undefined value');
  return JSON.stringify(value);
}

export function physicalSessionSigningPayload(session) {
  if (!isPlainObject(session)) throw new Error('session must be an object');
  const unsigned = { ...session };
  delete unsigned.signature;
  return canonical(unsigned);
}

function safeRelativeFile(baseDir, relativePath, maxBytes) {
  if (typeof relativePath !== 'string' || !relativePath || relativePath.length > 512 ||
      relativePath.includes('\\') || relativePath.includes('\0') || path.isAbsolute(relativePath) ||
      /^[A-Za-z]:/.test(relativePath)) return null;
  const parts = relativePath.split('/');
  if (parts.some(part => !part || part === '.' || part === '..')) return null;
  const root = fs.realpathSync(baseDir);
  let current = root;
  for (const part of parts) {
    current = path.join(current, part);
    let stat;
    try { stat = fs.lstatSync(current); } catch { return null; }
    if (stat.isSymbolicLink()) return null;
  }
  // Re-resolve immediately before the final stat/read boundary. Component lstat checks reject
  // ordinary symlinks; this second resolution also fails closed if a path is swapped before hash.
  let resolved;
  try { resolved = fs.realpathSync(current); } catch { return null; }
  if (!resolved.startsWith(`${root}${path.sep}`)) return null;
  let stat;
  try { stat = fs.statSync(resolved); } catch { return null; }
  if (!stat.isFile() || stat.size <= 0 || stat.size > maxBytes) return null;
  return resolved;
}

export function fileSha256(filePath, maxBytes = Number.MAX_SAFE_INTEGER) {
  if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0) throw new Error('invalid maximum size');
  const hash = createHash('sha256');
  const descriptor = fs.openSync(filePath, 'r');
  const buffer = Buffer.allocUnsafe(1024 * 1024);
  try {
    let bytesRead;
    let totalBytes = 0;
    do {
      bytesRead = fs.readSync(descriptor, buffer, 0, buffer.length, null);
      if (bytesRead > 0) {
        if (bytesRead > maxBytes - totalBytes) throw new Error('file exceeds maximum size');
        totalBytes += bytesRead;
        hash.update(buffer.subarray(0, bytesRead));
      }
    } while (bytesRead > 0);
    return hash.digest('hex');
  } finally {
    fs.closeSync(descriptor);
  }
}

export function readUtf8Bounded(filePath, maxBytes) {
  if (!Number.isSafeInteger(maxBytes) || maxBytes <= 0) throw new Error('invalid maximum size');
  const descriptor = fs.openSync(filePath, 'r');
  const buffer = Buffer.allocUnsafe(1024 * 1024);
  const chunks = [];
  let totalBytes = 0;
  try {
    while (true) {
      const remainingBytes = maxBytes - totalBytes;
      const readLength = Math.min(buffer.length, remainingBytes + 1);
      const bytesRead = fs.readSync(descriptor, buffer, 0, readLength, null);
      if (bytesRead === 0) break;
      if (bytesRead > remainingBytes) throw new Error('file exceeds maximum size');
      chunks.push(Buffer.from(buffer.subarray(0, bytesRead)));
      totalBytes += bytesRead;
    }
    return Buffer.concat(chunks, totalBytes).toString('utf8');
  } finally {
    fs.closeSync(descriptor);
  }
}

function validIso(value) {
  if (typeof value !== 'string' || !ISO_UTC.test(value)) return false;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && new Date(parsed).toISOString() === value;
}

function add(errors, message) {
  errors.push(message);
}

function validateTrust(trust, errors) {
  if (!exactKeys(trust, ['schema_version', 'allowed_keys']) || trust.schema_version !== 1 ||
      !Array.isArray(trust.allowed_keys) || trust.allowed_keys.length > 32) {
    add(errors, 'trust configuration invalid');
    return [];
  }
  if (trust.allowed_keys.length === 0) add(errors, 'trust configuration has no authorized keys');
  const ids = new Set();
  return trust.allowed_keys.flatMap(entry => {
    if (!exactKeys(entry, ['key_id', 'public_key_pem', 'revoked']) ||
        typeof entry.key_id !== 'string' || !/^[A-Za-z0-9_-]{2,128}$/.test(entry.key_id) ||
        ids.has(entry.key_id) || typeof entry.public_key_pem !== 'string' ||
        !entry.public_key_pem.startsWith('-----BEGIN PUBLIC KEY-----') ||
        entry.public_key_pem.length > 8192 || typeof entry.revoked !== 'boolean') {
      add(errors, 'trust key invalid');
      return [];
    }
    ids.add(entry.key_id);
    try {
      const key = createPublicKey(entry.public_key_pem);
      if (key.asymmetricKeyType !== 'ed25519') throw new Error('key type');
      return [{ ...entry, key }];
    } catch {
      add(errors, 'trust key invalid');
      return [];
    }
  });
}

function validateArtifact(artifact, name, baseDir, maxBytes, errors, certificate = false) {
  const expectedKeys = certificate ? ['path', 'sha256', 'certificate_sha256'] : ['path', 'sha256'];
  if (!exactKeys(artifact, expectedKeys) || !SHA256.test(artifact.sha256) ||
      (certificate && !SHA256.test(artifact.certificate_sha256))) {
    add(errors, `${name} artifact metadata invalid`);
    return;
  }
  const file = safeRelativeFile(baseDir, artifact.path, maxBytes);
  if (!file) {
    add(errors, `${name} artifact path invalid`);
    return;
  }
  if (fileSha256(file, maxBytes) !== artifact.sha256) add(errors, `${name} artifact sha256 mismatch`);
  if (certificate) {
    const text = readUtf8Bounded(file, maxBytes);
    const digests = [...text.matchAll(/Signer #1 certificate SHA-256 digest:\s*([a-f0-9]{64})/g)]
      .map(match => match[1]);
    if (digests.length !== 1 || digests[0] !== artifact.certificate_sha256) {
      add(errors, 'certificate sha256 mismatch');
    }
  }
}

function validateEvidence(session, baseDir, errors) {
  if (!Array.isArray(session.required_scenarios) ||
      session.required_scenarios.length !== PHONE_CORE_REQUIRED_SCENARIOS.length ||
      session.required_scenarios.some((id, index) => id !== PHONE_CORE_REQUIRED_SCENARIOS[index])) {
    add(errors, 'required scenarios invalid');
    return;
  }
  if (!exactKeys(session.evidence, PHONE_CORE_REQUIRED_SCENARIOS)) {
    add(errors, 'evidence map invalid');
    return;
  }
  for (const scenario of PHONE_CORE_REQUIRED_SCENARIOS) {
    const record = session.evidence[scenario];
    if (!exactKeys(record, ['status', 'evidence_refs']) || record.status !== 'PASS' ||
        !Array.isArray(record.evidence_refs) || record.evidence_refs.length === 0 ||
        record.evidence_refs.length > 8) {
      add(errors, `${scenario} evidence invalid`);
      continue;
    }
    for (const reference of record.evidence_refs) {
      const match = typeof reference === 'string' ? EVIDENCE_REF.exec(reference) : null;
      if (!match) {
        add(errors, `evidence reference invalid: ${scenario}`);
        continue;
      }
      const file = safeRelativeFile(baseDir, match[1], MAX_EVIDENCE_BYTES);
      if (!file) {
        add(errors, `evidence reference invalid: ${scenario}`);
        continue;
      }
      if (fileSha256(file, MAX_EVIDENCE_BYTES) !== match[2]) add(errors, `evidence sha256 mismatch: ${scenario}`);
    }
  }
}

function validateManifestShape(session, errors) {
  const keys = [
    'schema_version', 'session_id', 'status', 'verdict', 'started_at', 'finished_at', 'observed_at',
    'signer_key_id', 'source_sha', 'artifact', 'metadata', 'required_scenarios', 'evidence', 'residuals', 'signature'
  ];
  if (!exactKeys(session, keys) || session.schema_version !== 1) {
    add(errors, 'manifest schema invalid');
    return false;
  }
  if (typeof session.session_id !== 'string' || !/^[A-Za-z0-9_-]{2,128}$/.test(session.session_id) ||
      !STATUSES.has(session.status) || !STATUSES.has(session.verdict) || session.status !== session.verdict) {
    add(errors, 'status invalid');
  }
  if (!SOURCE_SHA.test(session.source_sha) || typeof session.signer_key_id !== 'string' ||
      !/^[A-Za-z0-9_-]{2,128}$/.test(session.signer_key_id)) add(errors, 'identity metadata invalid');
  for (const field of ['started_at', 'finished_at', 'observed_at']) {
    if (!validIso(session[field])) add(errors, `timestamp invalid: ${field}`);
  }
  if (!isPlainObject(session.artifact) || !exactKeys(session.artifact, ['apk', 'certificate']) ||
      !isPlainObject(session.metadata) ||
      !exactKeys(session.metadata, ['device_model', 'android_sdk', 'build_fingerprint', 'sim_slots', 'operator_profile']) ||
      !Array.isArray(session.residuals) || session.residuals.length > 64 ||
      session.residuals.some(item => typeof item !== 'string' || item.length === 0 || item.length > 256) ||
      typeof session.signature !== 'string' || !BASE64.test(session.signature)) add(errors, 'manifest metadata invalid');
  if (isPlainObject(session.metadata) &&
      (typeof session.metadata.device_model !== 'string' || session.metadata.device_model.length < 2 || session.metadata.device_model.length > 128 ||
       !Number.isInteger(session.metadata.android_sdk) || session.metadata.android_sdk < 24 || session.metadata.android_sdk > 37 ||
       typeof session.metadata.build_fingerprint !== 'string' || session.metadata.build_fingerprint.length < 2 || session.metadata.build_fingerprint.length > 256 ||
       !Number.isInteger(session.metadata.sim_slots) || session.metadata.sim_slots < 1 || session.metadata.sim_slots > 2 ||
       typeof session.metadata.operator_profile !== 'string' || session.metadata.operator_profile.length < 1 || session.metadata.operator_profile.length > 128)) {
    add(errors, 'device metadata invalid');
  }
  if (session.status === 'PASS' && Array.isArray(session.residuals) && session.residuals.length > 0) {
    add(errors, 'residuals invalid for PASS');
  }
  if (session.status !== 'PASS' || session.verdict !== 'PASS') add(errors, 'verdict is not PASS');
  return true;
}

export function verifyPhoneCorePhysicalSession({ session, baseDir, trust, expectedSourceSha, now = Date.now() }) {
  const errors = [];
  if (!isPlainObject(session) || !validateManifestShape(session, errors)) {
    return { ok: false, verdict: 'FAIL', errors };
  }
  if (typeof expectedSourceSha !== 'string' || !SOURCE_SHA.test(expectedSourceSha)) {
    add(errors, 'source binding missing or invalid');
  } else if (session.source_sha !== expectedSourceSha) {
    add(errors, 'source sha mismatch');
  }
  const trustedKeys = validateTrust(trust, errors);
  const signer = trustedKeys.find(entry => entry.key_id === session.signer_key_id && entry.revoked !== true);
  if (!signer) add(errors, 'signer key is not allowed');
  if (signer && session.signature.length > 256) add(errors, 'signature invalid');
  if (signer && session.signature.length <= 256) {
    try {
      const signature = Buffer.from(session.signature, 'base64');
      if (signature.length !== 64 || !verify(null, Buffer.from(physicalSessionSigningPayload(session)), signer.key, signature)) {
        add(errors, 'signature invalid');
      }
    } catch { add(errors, 'signature invalid'); }
  }

  const started = Date.parse(session.started_at);
  const finished = Date.parse(session.finished_at);
  const observed = Date.parse(session.observed_at);
  if (!Number.isFinite(started) || !Number.isFinite(finished) || !Number.isFinite(observed) ||
      started > finished || finished > observed || observed > now) add(errors, 'timestamp order invalid');

  if (typeof baseDir !== 'string') add(errors, 'base directory invalid');
  else {
    try {
      validateArtifact(session.artifact.apk, 'apk', baseDir, MAX_APK_BYTES, errors);
      validateArtifact(session.artifact.certificate, 'certificate', baseDir, MAX_EVIDENCE_BYTES, errors, true);
      validateEvidence(session, baseDir, errors);
    } catch { add(errors, 'evidence file validation failed'); }
  }
  return errors.length ? { ok: false, verdict: 'FAIL', errors } : { ok: true, verdict: 'PASS', errors: [] };
}

function main() {
  const manifestPath = process.argv[2];
  const trustPath = process.argv[3];
  const expectedSourceSha = process.argv[4] || process.env.SOURCE_SHA;
  if (!manifestPath || !trustPath || !expectedSourceSha) {
    console.error('PHONE CORE PHYSICAL SESSION: FAIL');
    console.error('usage: verify-phone-core-physical-session.js <session.json> <trust-config.json> <expected-source-sha>');
    process.exitCode = 2;
    return;
  }
  try {
    if (fs.statSync(manifestPath).size > MAX_MANIFEST_BYTES) throw new Error('manifest too large');
    const session = JSON.parse(readUtf8Bounded(manifestPath, MAX_MANIFEST_BYTES));
    const trust = JSON.parse(readUtf8Bounded(trustPath, MAX_TRUST_BYTES));
    const result = verifyPhoneCorePhysicalSession({
      session,
      baseDir: path.dirname(path.resolve(manifestPath)),
      trust,
      expectedSourceSha
    });
    if (!result.ok) {
      console.error('PHONE CORE PHYSICAL SESSION: FAIL');
      for (const error of result.errors) console.error(`- ${error}`);
      process.exitCode = 1;
      return;
    }
    console.log('PHONE CORE PHYSICAL SESSION: PASS');
  } catch (error) {
    console.error('PHONE CORE PHYSICAL SESSION: FAIL');
    console.error(`- manifest unreadable: ${error.message}`);
    process.exitCode = 1;
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main();
