function requirePositiveInteger(value, code, min, max) {
  const n = Number(value);
  if (!Number.isInteger(n) || n < min || n > max) throw new TypeError(code);
  return n;
}

function requireKey(value, code) {
  if (typeof value !== 'string' || value.trim() === '' || value.length > 1024) throw new TypeError(code);
  return value.trim();
}

export function createThreatIntelQueryGate({
  minIntervalMs = 1000,
  cacheTtlMs = 300000,
  maxEntries = 1000,
  now = () => Date.now()
} = {}) {
  const interval = requirePositiveInteger(minIntervalMs, 'THREAT_INTEL_MIN_INTERVAL_INVALID', 0, 86_400_000);
  const ttl = requirePositiveInteger(cacheTtlMs, 'THREAT_INTEL_CACHE_TTL_INVALID', 0, 86_400_000);
  const capacity = requirePositiveInteger(maxEntries, 'THREAT_INTEL_CACHE_CAPACITY_INVALID', 1, 100_000);
  if (typeof now !== 'function') throw new TypeError('THREAT_INTEL_CLOCK_REQUIRED');

  const lastAttemptBySource = new Map();
  const cache = new Map();
  const inflight = new Map();

  function currentTime() {
    const value = Number(now());
    if (!Number.isFinite(value)) throw new TypeError('THREAT_INTEL_CLOCK_INVALID');
    return value;
  }

  function pruneCache(at) {
    for (const [key, entry] of cache) {
      if (entry.expiresAt <= at) cache.delete(key);
    }
    while (cache.size > capacity) {
      const oldest = cache.keys().next().value;
      cache.delete(oldest);
    }
  }

  async function run({ sourceId, queryKey, execute } = {}) {
    const source = requireKey(sourceId, 'THREAT_INTEL_SOURCE_REQUIRED');
    const query = requireKey(queryKey, 'THREAT_INTEL_QUERY_KEY_REQUIRED');
    if (typeof execute !== 'function') throw new TypeError('THREAT_INTEL_EXECUTE_REQUIRED');

    const key = `${source}:${query}`;
    const at = currentTime();
    pruneCache(at);

    const cached = cache.get(key);
    if (cached && cached.expiresAt > at) {
      return Object.freeze({ cache_hit: true, result: cached.result });
    }

    if (inflight.has(key)) return inflight.get(key);

    const lastAttempt = lastAttemptBySource.get(source);
    if (Number.isFinite(lastAttempt) && at - lastAttempt < interval) {
      const error = new Error('THREAT_INTEL_RATE_LIMITED');
      error.retry_after_ms = interval - (at - lastAttempt);
      throw error;
    }

    lastAttemptBySource.set(source, at);
    const promise = (async () => {
      try {
        const result = await execute();
        const completedAt = currentTime();
        cache.set(key, { result, expiresAt: completedAt + ttl });
        pruneCache(completedAt);
        return Object.freeze({ cache_hit: false, result });
      } finally {
        inflight.delete(key);
      }
    })();

    inflight.set(key, promise);
    return promise;
  }

  function clear() {
    cache.clear();
    inflight.clear();
    lastAttemptBySource.clear();
  }

  return Object.freeze({ run, clear });
}
