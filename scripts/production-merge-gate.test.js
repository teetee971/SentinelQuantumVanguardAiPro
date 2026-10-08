import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  ANDROID_WORKFLOWS,
  DEFAULT_GATE_TIMEOUT_MS,
  EMULATION_MAX_CRITICAL_PATH_MS,
  SECURITY_FUZZ_WORKFLOWS,
  UNIVERSAL_WORKFLOWS,
  WEB_WORKFLOWS,
  evaluateWorkflowRun,
  requiredWorkflowsForPaths,
  selectLatestExactHeadRun
} from './production-merge-gate.js';

function expectIncludes(actual, expected) {
  for (const item of expected) assert.ok(actual.includes(item), `missing ${item}`);
}

function expectExcludes(actual, expected) {
  for (const item of expected) assert.ok(!actual.includes(item), `unexpected ${item}`);
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

test('Phone Core workflows checkout and assert the pull request source head', () => {
  for (const workflowPath of [
    '.github/workflows/android-emulation-qualification.yml',
    '.github/workflows/build-native-android.yml',
    '.github/workflows/android-instrumentation.yml'
  ]) {
    const workflow = readFileSync(workflowPath, 'utf8');
    assert.match(
      workflow,
      /uses:\s*actions\/checkout@[^\n]+\n\s+with:\n\s+ref:\s*\$\{\{\s*github\.event\.pull_request\.head\.sha\s*\|\|\s*github\.sha\s*\}\}/,
      `${workflowPath} must checkout the exact source head`
    );
    assert.match(
      workflow,
      /test\s+"\$\(git rev-parse HEAD\)"\s*=\s*"\$TARGET_SHA"/,
      `${workflowPath} must fail closed when the checked out SHA differs`
    );
  }
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
