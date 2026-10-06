import assert from 'node:assert/strict';
import test from 'node:test';
import {
  GITHUB_API_MAX_ATTEMPTS,
  githubJson,
  isRetryableGitHubStatus
} from './production-merge-gate.js';

function response(status, payload = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: () => null },
    async json() { return payload; }
  };
}

test('only transient GitHub control-plane statuses are retryable', () => {
  for (const status of [429, 500, 502, 503, 504, 599]) {
    assert.equal(isRetryableGitHubStatus(status), true, String(status));
  }
  for (const status of [400, 401, 403, 404, 422]) {
    assert.equal(isRetryableGitHubStatus(status), false, String(status));
  }
});

test('githubJson survives a transient 500 without converting it into a code failure', async () => {
  let calls = 0;
  const sleeps = [];
  const payload = await githubJson('https://api.github.com/example', 'token', {
    fetchImpl: async () => {
      calls += 1;
      return calls === 1 ? response(500) : response(200, { ok: true });
    },
    sleep: async (ms) => sleeps.push(ms),
    baseDelayMs: 1
  });

  assert.deepEqual(payload, { ok: true });
  assert.equal(calls, 2);
  assert.deepEqual(sleeps, [1]);
});

test('githubJson fails immediately for non-retryable authentication errors', async () => {
  let calls = 0;
  await assert.rejects(
    githubJson('https://api.github.com/example', 'token', {
      fetchImpl: async () => {
        calls += 1;
        return response(401);
      },
      sleep: async () => {},
      baseDelayMs: 0
    }),
    /GitHub API 401/
  );
  assert.equal(calls, 1);
});

test('githubJson keeps retry budget bounded for persistent 5xx responses', async () => {
  let calls = 0;
  await assert.rejects(
    githubJson('https://api.github.com/example', 'token', {
      fetchImpl: async () => {
        calls += 1;
        return response(500);
      },
      sleep: async () => {},
      baseDelayMs: 0
    }),
    /GitHub API 500/
  );
  assert.equal(calls, GITHUB_API_MAX_ATTEMPTS);
});

test('githubJson retries transport exceptions but still fails closed after the budget', async () => {
  let calls = 0;
  await assert.rejects(
    githubJson('https://api.github.com/example', 'token', {
      fetchImpl: async () => {
        calls += 1;
        throw new TypeError('socket reset');
      },
      sleep: async () => {},
      baseDelayMs: 0,
      maxAttempts: 3
    }),
    /transport failure.*after 3 attempts/
  );
  assert.equal(calls, 3);
});
