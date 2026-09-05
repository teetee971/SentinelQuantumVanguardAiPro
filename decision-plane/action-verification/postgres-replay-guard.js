const MAX_KEY_LENGTH = 512;

function validKey(key) {
  return typeof key === 'string' && key.length > 0 && key.length <= MAX_KEY_LENGTH;
}

/**
 * Contract adapter for a durable PostgreSQL anti-replay store.
 *
 * The supplied execute function MUST issue the parameterized INSERT below
 * against a table with a UNIQUE constraint on replay_key and return the
 * database result. Atomicity comes from the database uniqueness constraint,
 * not from this JavaScript wrapper.
 */
export function createPostgresReplayGuard({ execute } = {}) {
  if (typeof execute !== 'function') {
    return Object.freeze({
      consumeAtomically() {
        return Promise.resolve({ valid: false, reason: 'DURABLE_REPLAY_EXECUTOR_REQUIRED' });
      },
    });
  }

  return Object.freeze({
    async consumeAtomically(key) {
      if (!validKey(key)) return { valid: false, reason: 'INVALID_REPLAY_KEY' };
      try {
        const result = await execute({
          text: 'INSERT INTO sentinel_replay_consumptions (replay_key) VALUES ($1) ON CONFLICT (replay_key) DO NOTHING RETURNING replay_key',
          values: [key],
        });
        const inserted = Array.isArray(result?.rows) && result.rows.length === 1;
        return inserted
          ? { valid: true, reason: 'REPLAY_KEY_CONSUMED' }
          : { valid: false, reason: 'REPLAY_DETECTED' };
      } catch {
        return { valid: false, reason: 'REPLAY_STORE_UNAVAILABLE' };
      }
    },
  });
}

export { validKey as isValidReplayKey };
