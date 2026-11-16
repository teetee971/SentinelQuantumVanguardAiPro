import { createHash } from 'node:crypto';

const MIN_TTL_MS = 60_000;
const MAX_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const SAFE_SEGMENT = /^[a-z0-9][a-z0-9_-]{0,63}$/;

function invalidGuard(reason) {
  return Object.freeze({
    async consumeAtomically() {
      return { valid: false, reason };
    },
  });
}

/**
 * Redis anti-replay adapter with an intentionally narrow injected contract.
 *
 * setNxPx({ key, value, ttlMs }) must perform one atomic Redis SET with NX and
 * PX semantics. It returns "OK"/true only when the key was created, and
 * null/false when it already exists. Credentials and client construction stay
 * outside this module.
 */
function createRedisReplayGuard({
  setNxPx,
  environment,
  namespace = 'soc-replay',
  ttlMs = 24 * 60 * 60 * 1000,
} = {}) {
  if (typeof setNxPx !== 'function') return invalidGuard('REDIS_SET_NX_PX_REQUIRED');
  if (!SAFE_SEGMENT.test(environment ?? '')) return invalidGuard('REPLAY_ENVIRONMENT_INVALID');
  if (!SAFE_SEGMENT.test(namespace)) return invalidGuard('REPLAY_NAMESPACE_INVALID');
  if (!Number.isInteger(ttlMs) || ttlMs < MIN_TTL_MS || ttlMs > MAX_TTL_MS) {
    return invalidGuard('REPLAY_TTL_INVALID');
  }

  return Object.freeze({
    async consumeAtomically(rawKey) {
      if (typeof rawKey !== 'string' || rawKey.length === 0 || rawKey.length > 512) {
        return { valid: false, reason: 'INVALID_REPLAY_KEY' };
      }
      const digest = createHash('sha256').update(rawKey, 'utf8').digest('hex');
      const key = `sentinel:${environment}:${namespace}:${digest}`;
      try {
        const result = await setNxPx({ key, value: '1', ttlMs });
        if (result === 'OK' || result === true) {
          return { valid: true, reason: 'REPLAY_KEY_CONSUMED' };
        }
        if (result === null || result === false) {
          return { valid: false, reason: 'REPLAY_DETECTED' };
        }
        return { valid: false, reason: 'REPLAY_STORE_PROTOCOL_ERROR' };
      } catch {
        return { valid: false, reason: 'REPLAY_STORE_UNAVAILABLE' };
      }
    },
  });
}

export { MIN_TTL_MS, MAX_TTL_MS, createRedisReplayGuard };
