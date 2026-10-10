import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  ANDROID_WORKFLOWS,
  DEFAULT_GATE_TIMEOUT_MS,
  EMULATION_MAX_CRITICAL_PATH_MS,
  GITHUB_API_MAX_ATTEMPTS,
  SECURITY_FUZZ_WORKFLOWS,
  UNIVERSAL_WORKFLOWS,
  WEB_WORKFLOWS,
  evaluateWorkflowRun,
  githubJson,
  isRetryableGitHubStatus,
  requiredWorkflowsForPaths,
  selectLatestExactHeadRun
} from './production-merge-gate.js';

function expectIncludes(actual, expected) {
  for (const item of expected) assert.ok(actual.includes(item), `missing ${item}`);
}

function expectExcludes(actual, expected) {
  for (const item of expected) assert.ok(!actual.includes(item), `unexpected ${item}`);
}

function response(status, payload = {}, retryAfter = null) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => name.toLowerCase() === 'retry-after' ? retryAfter : null },
    async json() { return payload; }
  };
}

test('docs-only changes require universal gates without unrelated Android or web gates', () => {
  const required = requiredWorkflowsForPaths(['docs/ARCHITECTURE.md']);
  expectIncludes(required, UNIVERSAL_WORKFLOWS);
  expectExcludes(required, ANDROID_WORKFLOWS);
  expectExcludes(required, WEB_WORKFLOWS);
  expectExcludes(required, SECURITY_FUZZ_WORKFLOWS);
});

test('Android-only changes require Phone Core emulation, APK, AAB and legacy instrumentation', () => {
  const required = requiredWorkflowsForPaths([
    'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt'
  ]);
  expectIncludes(required, [...UNIVERSAL_WORKFLOWS, ...ANDROID_WORKFLOWS]);
  assert.ok(required.includes('android-emulation-qualification.yml'));
  expectExcludes(required, WEB_WORKFLOWS);
});

test('web-only changes require frontend and Lighthouse gates', () => {
  const required = requiredWorkflowsForPaths(['public/index.js']);
  expectIncludes(required, [...UNIVERSAL_WORKFLOWS, ...WEB_WORKFLOWS]);
  expectExcludes(required, ANDROID_WORKFLOWS);
});

test('mixed shared-runtime changes require Android and web gates', () => {
  const required = requiredWorkflowsForPaths(['scripts/check-product-capabilities.js']);
  expectIncludes(required, [...UNIVERSAL_WORKFLOWS, ...ANDROID_WORKFLOWS, ...WEB_WORKFLOWS]);
});

test('security changes also require dedicated fuzz evidence', () => {
  const required = requiredWorkflowsForPaths(['security/phone-intelligence/mms-policy.js']);
  expectIncludes(required, [...UNIVERSAL_WORKFLOWS, ...ANDROID_WORKFLOWS, ...SECURITY_FUZZ_WORKFLOWS]);
});

test('workflow-only production gate changes do not create a self-dependency', () => {
  const required = requiredWorkflowsForPaths(['.github/workflows/production-merge-gate.yml']);
  expectIncludes(required, UNIVERSAL_WORKFLOWS);
  expectExcludes(required, [...ANDROID_WORKFLOWS, ...WEB_WORKFLOWS, ...SECURITY_FUZZ_WORKFLOWS]);
  assert.ok(!required.includes('production-merge-gate.yml'));
});

test('workflow changes for Android or web require the affected gate family', () => {
  expectIncludes(
    requiredWorkflowsForPaths(['.github/workflows/android-instrumentation.yml']),
    ANDROID_WORKFLOWS
  );
  expectIncludes(
    requiredWorkflowsForPaths(['.github/workflows/android-emulation-qualification.yml']),
    ANDROID_WORKFLOWS
  );
  expectIncludes(
    requiredWorkflowsForPaths(['.github/workflows/lighthouse-preproduction.yml']),
    WEB_WORKFLOWS
  );
});

test('production gate timeout exceeds the longest dependent emulator critical path', () => {
  assert.ok(
    DEFAULT_GATE_TIMEOUT_MS > EMULATION_MAX_CRITICAL_PATH_MS,
    'merge gate timeout must exceed 45m host + 60m dependent emulator matrix'
  );
});

test('production merge workflow job outlives its internal waiter', () => {
  const workflow = readFileSync('.github/workflows/production-merge-gate.yml', 'utf8');
  const jobTimeoutMinutes = Number(workflow.match(/timeout-minutes:\s*(\d+)/)?.[1] ?? 0);
  assert.ok(
    jobTimeoutMinutes * 60 * 1000 > DEFAULT_GATE_TIMEOUT_MS + (5 * 60 * 1000),
    'Production Merge Gate job must keep headroom beyond its internal polling deadline'
  );
});

test('required CodeQL Android waiter outlives the production merge gate', () => {
  const workflow = readFileSync('.github/workflows/codeql-analysis.yml', 'utf8');
  const androidJob = workflow.match(/analyze-android:[\s\S]*$/)?.[0] ?? '';
  const jobTimeoutMinutes = Number(androidJob.match(/timeout-minutes:\s*(\d+)/)?.[1] ?? 0);
  const waiterDeadlineSeconds = Number(androidJob.match(/DEADLINE=\$\(\(SECONDS \+ (\d+)\)\)/)?.[1] ?? 0);

  assert.match(androidJob, /"production-merge-gate\.yml"/);
  assert.ok(
    waiterDeadlineSeconds * 1000 > DEFAULT_GATE_TIMEOUT_MS,
    'required CodeQL waiter must outlive Production Merge Gate'
  );
  assert.ok(
    jobTimeoutMinutes * 60 > waiterDeadlineSeconds + (20 * 60),
    'required CodeQL job needs headroom for build/extraction before its waiter'
  );
});

test('exact-head selection rejects unrelated SHA and selects newest retry', () => {
  const sha = 'a'.repeat(40);
  const other = 'b'.repeat(40);
  const selected = selectLatestExactHeadRun([
    { id: 1, head_sha: other, event: 'pull_request', run_number: 99, run_attempt: 1 },
    { id: 2, head_sha: sha, event: 'push', run_number: 11, run_attempt: 1 },
    { id: 3, head_sha: sha, event: 'pull_request', run_number: 11, run_attempt: 1 },
    { id: 4, head_sha: sha, event: 'pull_request', run_number: 11, run_attempt: 2 }
  ], sha);
  assert.equal(selected.id, 4);
});

test('missing and in-progress evidence waits while non-success completion fails closed', () => {
  assert.deepEqual(evaluateWorkflowRun(null), { state: 'wait', reason: 'MISSING_EXACT_HEAD_RUN' });
  assert.equal(evaluateWorkflowRun({ status: 'in_progress' }).state, 'wait');
  for (const conclusion of ['failure', 'cancelled', 'skipped', 'timed_out', 'neutral']) {
    const result = evaluateWorkflowRun({ status: 'completed', conclusion });
    assert.equal(result.state, 'fail', conclusion);
  }
  assert.deepEqual(
    evaluateWorkflowRun({ status: 'completed', conclusion: 'success' }),
    { state: 'pass', reason: 'SUCCESS' }
  );
});

test('only transient GitHub control-plane statuses are retryable', () => {
  for (const status of [429, 500, 502, 503, 504, 599]) {
    assert.equal(isRetryableGitHubStatus(status), true, String(status));
  }
  for (const status of [400, 401, 403, 404, 422]) {
    assert.equal(isRetryableGitHubStatus(status), false, String(status));
  }
});

test('GitHub API retry absorbs a single transient 500 with backoff', async () => {
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

test('GitHub API retry honors Retry-After for transient responses', async () => {
  let calls = 0;
  const sleeps = [];
  await githubJson('https://api.github.com/example', 'token', {
    fetchImpl: async () => {
      calls += 1;
      return calls === 1 ? response(503, {}, '2') : response(200, { ok: true });
    },
    sleep: async (ms) => sleeps.push(ms),
    baseDelayMs: 1
  });
  assert.deepEqual(sleeps, [2000]);
});

test('Retry-After cannot overshoot the production-gate request deadline', async () => {
  let calls = 0;
  const sleeps = [];
  await assert.rejects(
    githubJson('https://api.github.com/example', 'token', {
      fetchImpl: async () => {
        calls += 1;
        return response(503, {}, '3600');
      },
      sleep: async (ms) => sleeps.push(ms),
      baseDelayMs: 1,
      deadlineMs: 10_000,
      now: () => 9_000
    }),
    /GitHub API retry deadline exceeded.*requested 3600000ms.*1000ms remaining/
  );
  assert.equal(calls, 1);
  assert.deepEqual(sleeps, []);
});

test('successful HTTP response with transient body-read failure is retried within the same budget', async () => {
  let calls = 0;
  const sleeps = [];
  const payload = await githubJson('https://api.github.com/example', 'token', {
    fetchImpl: async () => {
      calls += 1;
      if (calls === 1) {
        return {
          ...response(200),
          async json() { throw new TypeError('socket reset while reading response body'); }
        };
      }
      return response(200, { ok: true });
    },
    sleep: async (ms) => sleeps.push(ms),
    baseDelayMs: 1
  });

  assert.deepEqual(payload, { ok: true });
  assert.equal(calls, 2);
  assert.deepEqual(sleeps, [1]);
});

test('GitHub API retry fails immediately for non-retryable authentication errors', async () => {
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

test('GitHub API retry budget stays bounded for persistent 5xx responses', async () => {
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

test('GitHub API retry handles transport exceptions but remains bounded and fail-closed', async () => {
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
