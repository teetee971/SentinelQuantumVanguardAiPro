/**
 * Shared freshness primitives for security proofs.
 *
 * This module validates time semantics only. It does not authenticate the
 * producer, prove cryptographic integrity, or persist replay state.
 * Those responsibilities belong to the execution boundary.
 */

const DEFAULT_MAX_CLOCK_SKEW_MS = 30_000;

function parseTimestamp(value) {
  if (typeof value !== 'string' || value.trim() === '') return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function validateProofWindow({ issuedAt, expiresAt, now = Date.now(), maxClockSkewMs = DEFAULT_MAX_CLOCK_SKEW_MS }) {
  const issued = parseTimestamp(issuedAt);
  const expires = parseTimestamp(expiresAt);

  if (issued === null || expires === null || expires <= issued) {
    return { valid: false, reason: 'INVALID_PROOF_WINDOW' };
  }
  if (!Number.isFinite(now) || !Number.isFinite(maxClockSkewMs) || maxClockSkewMs < 0) {
    return { valid: false, reason: 'INVALID_TIME_REFERENCE' };
  }
  if (issued > now + maxClockSkewMs) {
    return { valid: false, reason: 'PROOF_ISSUED_IN_FUTURE' };
  }
  if (now >= expires) {
    return { valid: false, reason: 'PROOF_EXPIRED' };
  }

  return { valid: true, reason: 'PROOF_WINDOW_VALID', issuedAt: issued, expiresAt: expires };
}

export { DEFAULT_MAX_CLOCK_SKEW_MS, parseTimestamp, validateProofWindow };
