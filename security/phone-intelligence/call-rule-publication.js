import { createPrivateKey, createPublicKey, sign, verify } from 'node:crypto';

const DOMAIN = 'sentinel-call-rules-v1';
const PACKAGE_ID = 'fr-vigilance';
const SIGNATURE_ALGORITHM = 'sha256';
const ID_PATTERN = /^[A-Za-z0-9._:-]{1,64}$/;
const MAX_PREFIXES = 500;
const MAX_PAYLOAD_BYTES = 32_768;
const MAX_ENVELOPE_CHARS = 131_072;
const MAX_LIFETIME_MS = 31 * 24 * 60 * 60 * 1000;
const MAX_FUTURE_SKEW_MS = 5 * 60 * 1000;
const MIN_REPUTATION_PREFIX_DIGITS = 5;

function normalizePrefix(raw) {
  if (typeof raw !== 'string') return null;
  const input = raw.trim();
  if (input.length < 3 || input.length > 24) return null;
  if (![...input].every((value) => /[0-9+ ]/.test(value))) return null;
  if ((input.match(/\+/g) || []).length > 1 || (input.includes('+') && !input.startsWith('+'))) return null;
  const digits = input.replace(/\D/g, '');
  if (digits.length < 3 || digits.length > 15) return null;
  if (input.startsWith('+')) return `+${digits}`;
  if (digits.startsWith('00') && digits.length >= 5) return `+${digits.slice(2)}`;
  if (digits.startsWith('0')) return `+33${digits.slice(1)}`;
  return digits;
}

function p256Key(key, kind) {
  const isKeyObject = key && typeof key === 'object' && key.type === kind && typeof key.export === 'function';
  const parsed = isKeyObject ? key : (kind === 'private' ? createPrivateKey(key) : createPublicKey(key));
  const details = parsed.asymmetricKeyDetails || {};
  if (parsed.asymmetricKeyType !== 'ec' || !['prime256v1', 'secp256r1'].includes(details.namedCurve)) {
    throw new Error('CALL_RULE_KEY_ALGORITHM_INVALID');
  }
  return parsed;
}

function canonicalPrefixes(values) {
  if (!Array.isArray(values) || values.length > MAX_PREFIXES) throw new Error('CALL_RULE_PREFIXES_INVALID');
  const normalized = values.map(normalizePrefix);
  if (normalized.some((value) => !value || value.replace(/\D/g, '').length < MIN_REPUTATION_PREFIX_DIGITS)) {
    throw new Error('CALL_RULE_PREFIX_INVALID');
  }
  const unique = [...new Set(normalized)].sort();
  if (unique.length !== normalized.length) throw new Error('CALL_RULE_PREFIX_DUPLICATE');
  return unique;
}

function buildPayload({
  sequence,
  issuedAtMs,
  expiresAtMs,
  issuerId,
  keyId,
  silencePrefixes = [],
  packageId = PACKAGE_ID,
} = {}) {
  if (packageId !== PACKAGE_ID) throw new Error('CALL_RULE_PACKAGE_ID_INVALID');
  if (!Number.isSafeInteger(sequence) || sequence <= 0) throw new Error('CALL_RULE_SEQUENCE_INVALID');
  if (!Number.isSafeInteger(issuedAtMs) || issuedAtMs < 0 || !Number.isSafeInteger(expiresAtMs) || expiresAtMs < 0) {
    throw new Error('CALL_RULE_TIME_INVALID');
  }
  if (expiresAtMs <= issuedAtMs || expiresAtMs - issuedAtMs > MAX_LIFETIME_MS) throw new Error('CALL_RULE_LIFETIME_INVALID');
  if (!ID_PATTERN.test(String(issuerId || '')) || !ID_PATTERN.test(String(keyId || ''))) throw new Error('CALL_RULE_ID_INVALID');

  const prefixes = canonicalPrefixes(silencePrefixes);
  const lines = [
    DOMAIN,
    `package_id=${packageId}`,
    `sequence=${sequence}`,
    `issued_at_ms=${issuedAtMs}`,
    `expires_at_ms=${expiresAtMs}`,
    `issuer_id=${issuerId}`,
    `key_id=${keyId}`,
    ...prefixes.map((prefix) => `silence_prefix=${prefix}`),
  ];
  const payload = Buffer.from(lines.join('\n'), 'utf8');
  if (payload.length < 1 || payload.length > MAX_PAYLOAD_BYTES) throw new Error('CALL_RULE_PAYLOAD_TOO_LARGE');
  return payload;
}

function signCallRulePackage({ privateKey, ...fields } = {}) {
  const key = p256Key(privateKey, 'private');
  const payload = buildPayload(fields);
  const signature = sign(SIGNATURE_ALGORITHM, payload, key);
  if (signature.length < 64 || signature.length > 80) throw new Error('CALL_RULE_SIGNATURE_SIZE_INVALID');
  const envelope = [
    `key_id=${fields.keyId}`,
    `payload_hex=${payload.toString('hex')}`,
    `signature_hex=${signature.toString('hex')}`,
  ].join('\n');
  if (envelope.length > MAX_ENVELOPE_CHARS) throw new Error('CALL_RULE_ENVELOPE_TOO_LARGE');
  return { envelope, payload, signature };
}

function parseField(line, name) {
  const prefix = `${name}=`;
  return typeof line === 'string' && line.startsWith(prefix) && line.length > prefix.length ? line.slice(prefix.length) : null;
}

function decodeHex(value, maxChars) {
  if (typeof value !== 'string' || value.length % 2 || value.length > maxChars || !/^[a-f0-9]+$/i.test(value)) return null;
  return Buffer.from(value, 'hex');
}

function parsePayload(payload, envelopeKeyId) {
  const text = payload.toString('utf8');
  if (Buffer.byteLength(text, 'utf8') !== payload.length) return null;
  const lines = text.split('\n');
  if (lines.length < 7 || lines.length > 7 + MAX_PREFIXES || lines[0] !== DOMAIN || lines.some((line) => line.endsWith('\r'))) return null;
  if (parseField(lines[1], 'package_id') !== PACKAGE_ID) return null;
  const sequence = Number(parseField(lines[2], 'sequence'));
  const issuedAtMs = Number(parseField(lines[3], 'issued_at_ms'));
  const expiresAtMs = Number(parseField(lines[4], 'expires_at_ms'));
  const issuerId = parseField(lines[5], 'issuer_id');
  const keyId = parseField(lines[6], 'key_id');
  if (!Number.isSafeInteger(sequence) || sequence <= 0 || !Number.isSafeInteger(issuedAtMs) || issuedAtMs < 0 ||
      !Number.isSafeInteger(expiresAtMs) || expiresAtMs < 0 || !ID_PATTERN.test(String(issuerId || '')) ||
      !ID_PATTERN.test(String(keyId || '')) || keyId !== envelopeKeyId) return null;

  const prefixes = lines.slice(7).map((line) => parseField(line, 'silence_prefix'));
  if (prefixes.some((value) => !value || normalizePrefix(value) !== value || value.replace(/\D/g, '').length < MIN_REPUTATION_PREFIX_DIGITS)) return null;
  if (prefixes.join('\n') !== [...prefixes].sort().join('\n') || new Set(prefixes).size !== prefixes.length) return null;
  return { packageId: PACKAGE_ID, sequence, issuedAtMs, expiresAtMs, issuerId, keyId, silencePrefixes: new Set(prefixes) };
}

function verifyCallRulePackage(envelope, {
  trustedKeys,
  expectedIssuerId,
  highestAcceptedSequence = 0,
  now = Date.now(),
} = {}) {
  if (typeof envelope !== 'string' || envelope.length < 1 || envelope.length > MAX_ENVELOPE_CHARS ||
      !(trustedKeys instanceof Map) || !ID_PATTERN.test(String(expectedIssuerId || '')) ||
      !Number.isSafeInteger(highestAcceptedSequence) || highestAcceptedSequence < 0 || !Number.isFinite(now) || now < 0) {
    return { accepted: false, reason: 'SIGNED_RULE_INPUT_INVALID' };
  }
  const lines = envelope.split('\n');
  if (lines.length !== 3 || lines.some((line) => line.endsWith('\r'))) return { accepted: false, reason: 'SIGNED_RULE_ENVELOPE_INVALID' };
  const keyId = parseField(lines[0], 'key_id');
  if (!ID_PATTERN.test(String(keyId || ''))) return { accepted: false, reason: 'SIGNED_RULE_KEY_ID_INVALID' };
  const payload = decodeHex(parseField(lines[1], 'payload_hex'), MAX_PAYLOAD_BYTES * 2);
  const signature = decodeHex(parseField(lines[2], 'signature_hex'), 160);
  if (!payload || !signature || payload.length < 1 || payload.length > MAX_PAYLOAD_BYTES || signature.length < 64 || signature.length > 80) {
    return { accepted: false, reason: 'SIGNED_RULE_SIZE_INVALID' };
  }
  const rawKey = trustedKeys.get(keyId);
  if (!rawKey) return { accepted: false, reason: 'SIGNED_RULE_KEY_UNKNOWN' };

  let key;
  try { key = p256Key(rawKey, 'public'); } catch { return { accepted: false, reason: 'SIGNED_RULE_KEY_ALGORITHM_INVALID' }; }
  if (!verify(SIGNATURE_ALGORITHM, payload, key, signature)) return { accepted: false, reason: 'SIGNED_RULE_SIGNATURE_INVALID' };

  const rulePackage = parsePayload(payload, keyId);
  if (!rulePackage) return { accepted: false, reason: 'SIGNED_RULE_PAYLOAD_SCHEMA_INVALID' };
  if (rulePackage.issuerId !== expectedIssuerId) return { accepted: false, reason: 'SIGNED_RULE_ISSUER_INVALID' };
  if (rulePackage.sequence <= highestAcceptedSequence) return { accepted: false, reason: 'SIGNED_RULE_ROLLBACK_REJECTED' };
  if (rulePackage.issuedAtMs > now + MAX_FUTURE_SKEW_MS) return { accepted: false, reason: 'SIGNED_RULE_ISSUED_IN_FUTURE' };
  if (rulePackage.expiresAtMs <= now) return { accepted: false, reason: 'SIGNED_RULE_EXPIRED' };
  if (rulePackage.expiresAtMs <= rulePackage.issuedAtMs || rulePackage.expiresAtMs - rulePackage.issuedAtMs > MAX_LIFETIME_MS) {
    return { accepted: false, reason: 'SIGNED_RULE_LIFETIME_INVALID' };
  }
  return { accepted: true, reason: 'SIGNED_RULE_ACCEPTED', rulePackage };
}

export {
  DOMAIN,
  MAX_LIFETIME_MS,
  MAX_PREFIXES,
  PACKAGE_ID,
  buildPayload,
  normalizePrefix,
  signCallRulePackage,
  verifyCallRulePackage,
};