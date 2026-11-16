import { createHash } from 'node:crypto';

const MAX_SERIALIZED_LENGTH = 16384;

function canonicalValue(value) {
  if (value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map(canonicalValue);
  return Object.fromEntries(
    Object.keys(value).sort().map((key) => [key, canonicalValue(value[key])]),
  );
}

export function canonicalizeOperation(operation) {
  if (!operation || typeof operation !== 'object' || Array.isArray(operation)) {
    return { valid: false, reason: 'INVALID_OPERATION' };
  }
  const canonical = canonicalValue(operation);
  const serialized = JSON.stringify(canonical);
  if (serialized.length > MAX_SERIALIZED_LENGTH) {
    return { valid: false, reason: 'OPERATION_TOO_LARGE' };
  }
  return { valid: true, canonical, serialized };
}

export function computeOperationDigest(operation) {
  const result = canonicalizeOperation(operation);
  if (!result.valid) return result;
  return {
    valid: true,
    digest: createHash('sha256').update(result.serialized, 'utf8').digest('hex'),
    serialized: result.serialized,
  };
}

export function verifyOperationDigest(operation, expectedDigest) {
  if (typeof expectedDigest !== 'string' || !/^[a-f0-9]{64}$/.test(expectedDigest)) {
    return { valid: false, reason: 'INVALID_EXPECTED_DIGEST' };
  }
  const computed = computeOperationDigest(operation);
  if (!computed.valid) return computed;
  return computed.digest === expectedDigest
    ? { valid: true, reason: 'OPERATION_DIGEST_MATCH' }
    : { valid: false, reason: 'OPERATION_DIGEST_MISMATCH' };
}
