function requirePositiveInteger(value, code, min, max) {
  const n = Number(value);
  if (!Number.isInteger(n) || n < min || n > max) throw new TypeError(code);
  return n;
}

function requireSourceId(value) {
  if (typeof value !== 'string') throw new TypeError('THREAT_INTEL_SOURCE_REQUIRED');
  const source = value.trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9._-]{0,127}$/.test(source)) throw new TypeError('THREAT_INTEL_SOURCE_REQUIRED');
  return source;
}

function requireQueryKey(value) {
  if (typeof value !== 'string' || value.trim() === '' || value.length > 1024) {
    throw new TypeError('THREAT_INTEL_QUERY_KEY_REQUIRED');
  }
  return value.trim();
}

function snapshotResult(value) {
  const seen = new WeakSet();

  function validate(item) {
    if (item === null || typeof item === 'string' || typeof item === 'boolean') return;
    if (typeof item === 'number' && Number.isFinite(item)) return;
    if (!item || typeof item !== 'object') throw new TypeError('THREAT_INTEL_RESULT_NOT_CACHEABLE');
    if (seen.has(item)) throw new TypeError('THREAT_INTEL_RESULT_NOT_CACHEABLE');
    seen.add(item);

    const prototype = Object.getPrototypeOf(item);
    if (!Array.isArray(item) && prototype !== Object.prototype && prototype !== null) {
      throw new TypeError('THREAT_INTEL_RESULT_NOT_CACHEABLE');
    }
    if (Object.getOwnPropertySymbols(item).length > 0) throw new TypeError('THREAT_INTEL_RESULT_NOT_CACHEABLE');

    for (const descriptor of Object.values(Object.getOwnPropertyDescriptors(item))) {
      if (descriptor.get || descriptor.set) throw new TypeError('THREAT_INTEL_RESULT_NOT_CACHEABLE');
      if (Object.hasOwn(descriptor, 'value')) validate(descriptor.value);
    }
  }

  function freeze(item) {
    if (!item || typeof item !== 'object' || Object.isFrozen(item)) return item;
    for (const child of Object.values(item)) freeze(child);
    return Object.freeze(item);
  }

  validate(value);
  return freeze(structuredClone(value));
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
  let generation = 0;
  let lastObservedTime = -Infinity;

  function currentTime() {
    const value = Number(now());
    if (!Number.isFinite(value)) throw new TypeError('THREAT_INTEL_CLOCK_INVALID');
    if (value < lastObservedTime) throw new Error('THREAT_INTEL_CLOCK_ROLLBACK');
    lastObservedTime = value;
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
    const source = requireSourceId(sourceId);
    const query = requireQueryKey(queryKey);
    if (typeof execute !== 'function') throw new TypeError('THREAT_INTEL_EXECUTE_REQUIRED');

    const key = JSON.stringify([source, query]);
    const at = currentTime();
    pruneCache(at);

    const cached = cache.get(key);
    if (cached && cached.expiresAt > at) {
      return Object.freeze({ cache_hit: true, result: cached.result });
    }

    const active = inflight.get(key);
    if (active) return active.promise;

    const lastAttempt = lastAttemptBySource.get(source);
    if (Number.isFinite(lastAttempt) && at - lastAttempt < interval) {
      const error = new Error('THREAT_INTEL_RATE_LIMITED');
      error.retry_after_ms = interval - (at - lastAttempt);
      throw error;
    }

    lastAttemptBySource.set(source, at);
    const attemptGeneration = generation;
    const entry = {};
    entry.promise = (async () => {
      try {
        const result = snapshotResult(await Promise.resolve().then(execute));
        const completedAt = currentTime();
        if (generation === attemptGeneration && ttl > 0) {
          cache.set(key, { result, expiresAt: completedAt + ttl });
          pruneCache(completedAt);
        }
        return Object.freeze({ cache_hit: false, result });
      } finally {
        if (inflight.get(key) === entry) inflight.delete(key);
      }
    })();

    inflight.set(key, entry);
    return entry.promise;
  }

  function clear() {
    generation += 1;
    cache.clear();
    inflight.clear();
    lastAttemptBySource.clear();
  }

  return Object.freeze({ run, clear });
}
