import { createPublicKey, verify } from 'node:crypto';

const SUPPORTED_ALGORITHM = 'ed25519';
const DOMAIN = 'sentinel-proof-v1';
const REQUIRED_AUTHENTICITY_FIELDS = Object.freeze([
  'issuer_id',
  'key_id',
  'signature_alg',
  'signature',
]);

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function canonicalize(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(',')}]`;
  const keys = Object.keys(value).sort();
  return `{${keys.map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(',')}}`;
}

function normalizeSet(value) {
  if (value instanceof Set) return value;
  if (Array.isArray(value)) return new Set(value);
  return null;
}

function signingPayload(record, proofType) {
  const unsigned = { ...record };
  delete unsigned.signature;
  return Buffer.from(`${DOMAIN}:${proofType}\n${canonicalize(unsigned)}`, 'utf8');
}

function resolveKey(trust, issuerId, keyId, proofType) {
  if (!trust || typeof trust !== 'object' || Array.isArray(trust)) return null;
  if (typeof trust.resolvePublicKey !== 'function') return null;
  return trust.resolvePublicKey({ issuerId, keyId, proofType });
}

function issuerAllowed(trust, issuerId, proofType) {
  const byType = trust?.authorizedIssuers;
  if (!byType || typeof byType !== 'object' || Array.isArray(byType)) return false;
  const allowed = normalizeSet(byType[proofType]);
  return Boolean(allowed?.has(issuerId));
}

function keyRevoked(trust, keyId) {
  const revoked = normalizeSet(trust?.revokedKeyIds);
  return Boolean(revoked?.has(keyId));
}

function verifyProofAuthenticity(record, proofType, trust) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) {
    return { valid: false, reason: 'INVALID_SIGNED_PROOF' };
  }
  if (!isNonEmptyString(proofType)) {
    return { valid: false, reason: 'PROOF_TYPE_REQUIRED' };
  }
  if (!trust || typeof trust !== 'object' || Array.isArray(trust)) {
    return { valid: false, reason: 'PROOF_TRUST_REQUIRED' };
  }

  for (const field of REQUIRED_AUTHENTICITY_FIELDS) {
    if (!isNonEmptyString(record[field])) {
      return { valid: false, reason: `PROOF_AUTHENTICITY_FIELD_REQUIRED:${field}` };
    }
  }

  const issuerId = record.issuer_id.trim();
  const keyId = record.key_id.trim();
  if (!issuerAllowed(trust, issuerId, proofType)) {
    return { valid: false, reason: 'PROOF_ISSUER_UNAUTHORIZED' };
  }
  if (keyRevoked(trust, keyId)) {
    return { valid: false, reason: 'PROOF_KEY_REVOKED' };
  }
  if (record.signature_alg.trim().toLowerCase() !== SUPPORTED_ALGORITHM) {
    return { valid: false, reason: 'PROOF_SIGNATURE_ALGORITHM_UNSUPPORTED' };
  }

  let signature;
  let publicKey;
  try {
    signature = Buffer.from(record.signature, 'base64');
    if (signature.length === 0) return { valid: false, reason: 'PROOF_SIGNATURE_INVALID' };
    const resolved = resolveKey(trust, issuerId, keyId, proofType);
    if (!resolved) return { valid: false, reason: 'PROOF_VERIFICATION_KEY_NOT_FOUND' };
    publicKey = typeof resolved === 'string' || Buffer.isBuffer(resolved) ? createPublicKey(resolved) : resolved;
  } catch {
    return { valid: false, reason: 'PROOF_VERIFICATION_KEY_INVALID' };
  }

  try {
    const valid = verify(null, signingPayload(record, proofType), publicKey, signature);
    return valid
      ? { valid: true, reason: 'PROOF_AUTHENTICITY_VALID' }
      : { valid: false, reason: 'PROOF_SIGNATURE_INVALID' };
  } catch {
    return { valid: false, reason: 'PROOF_SIGNATURE_INVALID' };
  }
}

export {
  DOMAIN,
  REQUIRED_AUTHENTICITY_FIELDS,
  signingPayload,
  verifyProofAuthenticity,
};
