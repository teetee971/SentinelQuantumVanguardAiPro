import { createHmac } from 'node:crypto';

const SUBJECT = /^[A-Za-z0-9._:@+-]{1,256}$/;
const ENDPOINT = /^\/[A-Za-z0-9/_-]{1,127}$/;
const CLUSTER_TAG = '{sentinel-phone-rate-limit}';

const REDIS_SLIDING_WINDOW_SCRIPT = `
local redis_time = redis.call('TIME')
local now_ms = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
local counter_key = KEYS[#KEYS]
local sequence = redis.call('INCR', counter_key)
local member = tostring(now_ms) .. '-' .. tostring(sequence)
local blocked_index = 0
local retry_after = 0
local max_window_ms = 0

for index = 1, #KEYS - 1 do
  local window_ms = tonumber(ARGV[(index - 1) * 2 + 1])
  local limit = tonumber(ARGV[(index - 1) * 2 + 2])
  max_window_ms = math.max(max_window_ms, window_ms)
  local cutoff = now_ms - window_ms
  redis.call('ZREMRANGEBYSCORE', KEYS[index], '-inf', cutoff)
  local count = redis.call('ZCARD', KEYS[index])
  if count >= limit and blocked_index == 0 then
    local oldest = redis.call('ZRANGE', KEYS[index], 0, 0, 'WITHSCORES')
    blocked_index = index
    retry_after = math.max(1, tonumber(oldest[2]) + window_ms - now_ms)
  end
end

if blocked_index ~= 0 then
  redis.call('PEXPIRE', counter_key, max_window_ms)
  return {0, blocked_index, retry_after}
end

for index = 1, #KEYS - 1 do
  local window_ms = tonumber(ARGV[(index - 1) * 2 + 1])
  redis.call('ZADD', KEYS[index], now_ms, member)
  redis.call('PEXPIRE', KEYS[index], window_ms)
end
redis.call('PEXPIRE', counter_key, max_window_ms)
return {1, 0, 0}
`;

const DEFAULT_POLICIES = Object.freeze({
  global: Object.freeze({ limit: 1000, windowMs: 60_000 }),
  ip: Object.freeze({ limit: 100, windowMs: 60_000 }),
  user: Object.freeze({ limit: 60, windowMs: 60_000 }),
  defaultEndpoint: Object.freeze({ limit: 120, windowMs: 60_000 }),
  sensitiveEndpoints: Object.freeze({
    auth: Object.freeze({ limit: 10, windowMs: 60_000 }),
    publication: Object.freeze({ limit: 5, windowMs: 60_000 }),
    sync: Object.freeze({ limit: 30, windowMs: 60_000 }),
  }),
});

function validPolicy(value) {
  return Number.isInteger(value?.limit) && value.limit >= 1 && value.limit <= 1_000_000 &&
    Number.isInteger(value?.windowMs) && value.windowMs >= 1000 && value.windowMs <= 24 * 60 * 60 * 1000;
}

function endpointClass(endpoint) {
  if (endpoint.startsWith('/auth/')) return 'auth';
  if (endpoint.startsWith('/publication/')) return 'publication';
  if (endpoint.startsWith('/sync/')) return 'sync';
  return 'default';
}

function digest(value, key) {
  return createHmac('sha256', key).update(value).digest('hex');
}

function unavailableLimiter(reason = 'REDIS_RATE_LIMIT_EXECUTOR_REQUIRED') {
  return Object.freeze({ consume: async () => ({ allowed: false, reason, retry_after_ms: 60_000 }) });
}

export function createRedisRateLimiter({ execute, subjectHmacKey, policies = DEFAULT_POLICIES } = {}) {
  if (typeof execute !== 'function') return unavailableLimiter();
  if (!((typeof subjectHmacKey === 'string' || Buffer.isBuffer(subjectHmacKey)) && subjectHmacKey.length >= 32)) {
    throw new Error('REDIS_SUBJECT_HMAC_KEY_INVALID');
  }
  if (!validPolicy(policies.global) || !validPolicy(policies.ip) || !validPolicy(policies.user) ||
      !validPolicy(policies.defaultEndpoint) || !['auth', 'publication', 'sync'].every((key) => validPolicy(policies.sensitiveEndpoints?.[key]))) {
    throw new Error('REDIS_RATE_LIMIT_CONFIG_INVALID');
  }

  return Object.freeze({
    async consume({ ip_subject, user_subject, endpoint } = {}) {
      if (typeof ip_subject !== 'string' || !SUBJECT.test(ip_subject) ||
          (user_subject !== undefined && (typeof user_subject !== 'string' || !SUBJECT.test(user_subject))) ||
          typeof endpoint !== 'string' || !ENDPOINT.test(endpoint)) {
        return { allowed: false, reason: 'REDIS_RATE_LIMIT_INPUT_INVALID', retry_after_ms: policies.global.windowMs };
      }

      const category = endpointClass(endpoint);
      const endpointPolicy = category === 'default' ? policies.defaultEndpoint : policies.sensitiveEndpoints[category];
      const scopes = [
        { name: 'global', key: `${CLUSTER_TAG}:global`, policy: policies.global },
        { name: 'ip', key: `${CLUSTER_TAG}:ip:${digest(String(ip_subject), subjectHmacKey)}`, policy: policies.ip },
      ];
      if (user_subject !== undefined) {
        scopes.push({ name: 'user', key: `${CLUSTER_TAG}:user:${digest(String(user_subject), subjectHmacKey)}`, policy: policies.user });
      }
      scopes.push({
        name: `endpoint:${category}`,
        key: `${CLUSTER_TAG}:endpoint:${category}:${digest(`${ip_subject}\n${user_subject ?? '-'}`, subjectHmacKey)}`,
        policy: endpointPolicy,
      });

      const keys = [...scopes.map((scope) => scope.key), `${CLUSTER_TAG}:sequence`];
      const args = scopes.flatMap((scope) => [String(scope.policy.windowMs), String(scope.policy.limit)]);
      try {
        const result = await execute({ script: REDIS_SLIDING_WINDOW_SCRIPT, keys, arguments: args });
        if (!Array.isArray(result) || result.length !== 3 || ![0, 1].includes(Number(result[0]))) {
          return { allowed: false, reason: 'REDIS_RATE_LIMIT_RESPONSE_INVALID', retry_after_ms: policies.global.windowMs };
        }
        if (Number(result[0]) === 1) return { allowed: true, reason: 'REDIS_RATE_LIMIT_OK' };
        const blocked = scopes[Number(result[1]) - 1];
        const retry = Number(result[2]);
        if (!blocked || !Number.isFinite(retry) || retry < 1) {
          return { allowed: false, reason: 'REDIS_RATE_LIMIT_RESPONSE_INVALID', retry_after_ms: policies.global.windowMs };
        }
        return { allowed: false, reason: 'REDIS_RATE_LIMITED', scope: blocked.name, retry_after_ms: retry };
      } catch {
        return { allowed: false, reason: 'REDIS_RATE_LIMIT_UNAVAILABLE', retry_after_ms: policies.global.windowMs };
      }
    },
  });
}

export { DEFAULT_POLICIES, REDIS_SLIDING_WINDOW_SCRIPT };
