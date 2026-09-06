import { createPublicKey, verify } from 'node:crypto';

const DOMAIN = 'sentinel-owner-device-challenge-v1';
const ACTIVE_DEVICE_STATE = 'ACTIVE';
const MAX_CHALLENGE_BYTES = 4096;
const MAX_VALIDITY_MS = 5 * 60 * 1000;
const MAX_CLOCK_SKEW_MS = 30 * 1000;

function nonEmpty(value, max = 512) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= max;
}

function canonicalize(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(',')}]`;
  const keys = Object.keys(value).sort();
  return `{${keys.map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(',')}}`;
}

export function serializeOwnerDeviceChallenge(challenge) {
  return Buffer.from(`${DOMAIN}\n${canonicalize(challenge)}`, 'utf8');
}

function parseTimestamp(value) {
  if (!nonEmpty(value, 64)) return Number.NaN;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : Number.NaN;
}

function validateShape(device, challenge) {
  if (!device || typeof device !== 'object' || Array.isArray(device)) {
    return { valid: false, reason: 'OWNER_DEVICE_REQUIRED' };
  }
  if (!challenge || typeof challenge !== 'object' || Array.isArray(challenge)) {
    return { valid: false, reason: 'OWNER_DEVICE_CHALLENGE_REQUIRED' };
  }

  for (const field of ['device_id', 'owner_subject', 'state', 'public_key_spki_base64']) {
    if (!nonEmpty(device[field], 4096)) {
      return { valid: false, reason: `OWNER_DEVICE_FIELD_REQUIRED:${field}` };
    }
  }
  for (const field of ['challenge_id', 'device_id', 'owner_subject', 'nonce_base64', 'issued_at', 'expires_at']) {
    if (!nonEmpty(challenge[field], 4096)) {
      return { valid: false, reason: `OWNER_DEVICE_CHALLENGE_FIELD_REQUIRED:${field}` };
    }
  }

  if (device.state !== ACTIVE_DEVICE_STATE) return { valid: false, reason: 'OWNER_DEVICE_NOT_ACTIVE' };
  if (challenge.device_id !== device.device_id) return { valid: false, reason: 'OWNER_DEVICE_BINDING_MISMATCH' };
  if (challenge.owner_subject !== device.owner_subject) return { valid: false, reason: 'OWNER_SUBJECT_BINDING_MISMATCH' };

  let nonce;
  try {
    nonce = Buffer.from(challenge.nonce_base64, 'base64');
  } catch {
    return { valid: false, reason: 'OWNER_DEVICE_NONCE_INVALID' };
  }
  if (nonce.length < 16 || nonce.length > 128) return { valid: false, reason: 'OWNER_DEVICE_NONCE_INVALID' };

  const payload = serializeOwnerDeviceChallenge(challenge);
  if (payload.length > MAX_CHALLENGE_BYTES) return { valid: false, reason: 'OWNER_DEVICE_CHALLENGE_TOO_LARGE' };

  return { valid: true, reason: 'OWNER_DEVICE_SHAPE_VALID' };
}

function validateFreshness(challenge, nowMs) {
  const issuedAt = parseTimestamp(challenge.issued_at);
  const expiresAt = parseTimestamp(challenge.expires_at);
  if (!Number.isFinite(issuedAt) || !Number.isFinite(expiresAt)) {
    return { valid: false, reason: 'OWNER_DEVICE_CHALLENGE_TIME_INVALID' };
  }
  if (expiresAt <= issuedAt || expiresAt - issuedAt > MAX_VALIDITY_MS) {
    return { valid: false, reason: 'OWNER_DEVICE_CHALLENGE_WINDOW_INVALID' };
  }
  if (issuedAt - nowMs > MAX_CLOCK_SKEW_MS) {
    return { valid: false, reason: 'OWNER_DEVICE_CHALLENGE_NOT_YET_VALID' };
  }
  if (nowMs > expiresAt) return { valid: false, reason: 'OWNER_DEVICE_CHALLENGE_EXPIRED' };
  return { valid: true, reason: 'OWNER_DEVICE_CHALLENGE_FRESH' };
}

function resolvePublicKey(device) {
  try {
    const der = Buffer.from(device.public_key_spki_base64, 'base64');
    if (der.length === 0 || der.length > 4096) return null;
    return createPublicKey({ key: der, format: 'der', type: 'spki' });
  } catch {
    return null;
  }
}

export function verifyOwnerDeviceChallenge({
  device,
  challenge,
  signature_base64,
  replayGuard,
  nowMs = Date.now(),
} = {}) {
  const shape = validateShape(device, challenge);
  if (!shape.valid) return shape;

  const freshness = validateFreshness(challenge, nowMs);
  if (!freshness.valid) return freshness;

  if (!nonEmpty(signature_base64, 4096)) {
    return { valid: false, reason: 'OWNER_DEVICE_SIGNATURE_REQUIRED' };
  }
  if (!replayGuard || typeof replayGuard.consumeAtomically !== 'function') {
    return { valid: false, reason: 'OWNER_DEVICE_REPLAY_GUARD_REQUIRED' };
  }

  const publicKey = resolvePublicKey(device);
  if (!publicKey || publicKey.asymmetricKeyType !== 'ec') {
    return { valid: false, reason: 'OWNER_DEVICE_PUBLIC_KEY_INVALID' };
  }

  let signature;
  try {
    signature = Buffer.from(signature_base64, 'base64');
  } catch {
    return { valid: false, reason: 'OWNER_DEVICE_SIGNATURE_INVALID' };
  }
  if (signature.length === 0 || signature.length > 512) {
    return { valid: false, reason: 'OWNER_DEVICE_SIGNATURE_INVALID' };
  }

  let signatureValid = false;
  try {
    signatureValid = verify(
      'sha256',
      serializeOwnerDeviceChallenge(challenge),
      publicKey,
      signature,
    );
  } catch {
    signatureValid = false;
  }
  if (!signatureValid) return { valid: false, reason: 'OWNER_DEVICE_SIGNATURE_INVALID' };

  const replay = replayGuard.consumeAtomically(`owner-device-challenge:${challenge.challenge_id}`);
  if (!replay?.valid) {
    return { valid: false, reason: replay?.reason || 'OWNER_DEVICE_REPLAY_REJECTED' };
  }

  return {
    valid: true,
    reason: 'OWNER_DEVICE_CHALLENGE_VERIFIED',
    owner_subject: device.owner_subject,
    device_id: device.device_id,
    challenge_id: challenge.challenge_id,
  };
}

export { DOMAIN, MAX_VALIDITY_MS, MAX_CLOCK_SKEW_MS };
