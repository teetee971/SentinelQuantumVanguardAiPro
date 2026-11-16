const MAX_KEY_LENGTH = 512;

function validKey(key) {
  return typeof key === 'string' && key.length > 0 && key.length <= MAX_KEY_LENGTH;
}

/**
 * Minimal anti-replay contract for the execution boundary.
 * A production adapter must implement consumeAtomically() against durable
 * storage with a uniqueness/compare-and-set guarantee. This reference adapter
 * is intentionally in-memory and is suitable only for deterministic tests.
 */
export function createInMemoryReplayGuard() {
  const consumed = new Set();

  return Object.freeze({
    consumeAtomically(key) {
      if (!validKey(key)) return { valid: false, reason: 'INVALID_REPLAY_KEY' };
      if (consumed.has(key)) return { valid: false, reason: 'REPLAY_DETECTED' };
      consumed.add(key);
      return { valid: true, reason: 'REPLAY_KEY_CONSUMED' };
    },
    hasConsumed(key) {
      return validKey(key) && consumed.has(key);
    },
  });
}

export function consumeAuthorizationOnce(replayGuard, authorizationId) {
  if (!replayGuard || typeof replayGuard.consumeAtomically !== 'function') {
    return { valid: false, reason: 'ANTI_REPLAY_GUARD_REQUIRED' };
  }
  if (!validKey(authorizationId)) return { valid: false, reason: 'INVALID_AUTHORIZATION_ID' };
  return replayGuard.consumeAtomically(`authorization:${authorizationId}`);
}
