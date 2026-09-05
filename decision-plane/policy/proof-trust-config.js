import { createPublicKey } from 'node:crypto';

const REQUIRED_PROOF_TYPES = Object.freeze(['authorization', 'approval', 'simulation']);

function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function nonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function fail(reason) {
  const error = new Error(reason);
  error.code = reason;
  throw error;
}

function parsePublicKey(value) {
  if (!nonEmptyString(value)) fail('TRUST_CONFIG_PUBLIC_KEY_REQUIRED');
  if (/PRIVATE KEY/.test(value)) fail('TRUST_CONFIG_PRIVATE_KEY_FORBIDDEN');

  let key;
  try {
    key = createPublicKey(value);
  } catch {
    fail('TRUST_CONFIG_PUBLIC_KEY_INVALID');
  }
  if (key.asymmetricKeyType !== 'ed25519') fail('TRUST_CONFIG_KEY_ALGORITHM_UNSUPPORTED');
  return key;
}

/**
 * Build the runtime trust object consumed by proof-authenticity.js from an
 * explicit, serializable deployment configuration.
 *
 * Private keys are forbidden. Key ids are globally unique to keep the current
 * global revocation semantics unambiguous. All three proof types required by
 * the final execution boundary must be present and have at least one issuer.
 */
export function buildProofTrustConfig(config) {
  if (!isPlainObject(config)) fail('TRUST_CONFIG_REQUIRED');
  if (!isPlainObject(config.issuers)) fail('TRUST_CONFIG_ISSUERS_REQUIRED');

  const unknownTypes = Object.keys(config.issuers).filter((type) => !REQUIRED_PROOF_TYPES.includes(type));
  if (unknownTypes.length > 0) fail('TRUST_CONFIG_UNKNOWN_PROOF_TYPE');

  const authorizedIssuers = {};
  const keyMap = new Map();
  const globalKeyIds = new Set();

  for (const proofType of REQUIRED_PROOF_TYPES) {
    const issuers = config.issuers[proofType];
    if (!Array.isArray(issuers) || issuers.length === 0) {
      fail(`TRUST_CONFIG_ISSUER_REQUIRED:${proofType}`);
    }

    const issuerIds = new Set();
    for (const issuer of issuers) {
      if (!isPlainObject(issuer) || !nonEmptyString(issuer.issuer_id)) {
        fail(`TRUST_CONFIG_ISSUER_ID_REQUIRED:${proofType}`);
      }
      const issuerId = issuer.issuer_id.trim();
      if (issuerIds.has(issuerId)) fail(`TRUST_CONFIG_DUPLICATE_ISSUER:${proofType}`);
      issuerIds.add(issuerId);

      if (!Array.isArray(issuer.keys) || issuer.keys.length === 0) {
        fail(`TRUST_CONFIG_KEY_REQUIRED:${proofType}:${issuerId}`);
      }

      for (const entry of issuer.keys) {
        if (!isPlainObject(entry) || !nonEmptyString(entry.key_id)) {
          fail(`TRUST_CONFIG_KEY_ID_REQUIRED:${proofType}:${issuerId}`);
        }
        const keyId = entry.key_id.trim();
        if (globalKeyIds.has(keyId)) fail('TRUST_CONFIG_DUPLICATE_KEY_ID');
        globalKeyIds.add(keyId);

        const publicKey = parsePublicKey(entry.public_key_pem);
        keyMap.set(`${proofType}\u0000${issuerId}\u0000${keyId}`, publicKey);
      }
    }
    authorizedIssuers[proofType] = issuerIds;
  }

  const revokedKeyIds = new Set();
  const revoked = config.revoked_key_ids ?? [];
  if (!Array.isArray(revoked)) fail('TRUST_CONFIG_REVOKED_KEYS_INVALID');
  for (const keyId of revoked) {
    if (!nonEmptyString(keyId)) fail('TRUST_CONFIG_REVOKED_KEY_ID_INVALID');
    const normalized = keyId.trim();
    if (!globalKeyIds.has(normalized)) fail('TRUST_CONFIG_REVOKED_KEY_UNKNOWN');
    revokedKeyIds.add(normalized);
  }

  return Object.freeze({
    authorizedIssuers: Object.freeze(authorizedIssuers),
    revokedKeyIds,
    resolvePublicKey({ issuerId, keyId, proofType } = {}) {
      if (!nonEmptyString(issuerId) || !nonEmptyString(keyId) || !nonEmptyString(proofType)) return null;
      return keyMap.get(`${proofType.trim()}\u0000${issuerId.trim()}\u0000${keyId.trim()}`) ?? null;
    },
  });
}

export { REQUIRED_PROOF_TYPES };
