import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const codeqlWorkflow = readFileSync('.github/workflows/codeql-analysis.yml', 'utf8');
const instrumentationWorkflow = readFileSync('.github/workflows/android-instrumentation.yml', 'utf8');

function requiredWorkflowBlock(workflow) {
  const start = workflow.indexOf('REQUIRED_WORKFLOWS=(');
  assert.notEqual(start, -1, 'CodeQL workflow must declare REQUIRED_WORKFLOWS');
  const end = workflow.indexOf('\n          )', start);
  assert.notEqual(end, -1, 'CodeQL REQUIRED_WORKFLOWS block must be bounded');
  return workflow.slice(start, end);
}

test('required CodeQL Android status waits for emulator and comprehensive merge gates', () => {
  const block = requiredWorkflowBlock(codeqlWorkflow);
  assert.match(block, /"android-instrumentation\.yml"/);
  assert.match(block, /"production-merge-gate\.yml"/);
});

test('Android emulator qualification covers minimum, current and newest runtime lanes', () => {
  assert.match(instrumentationWorkflow, /api-level:\s*24\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*36\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*37\b/);
  assert.match(instrumentationWorkflow, /connectedDebugAndroidTest/);
  assert.match(instrumentationWorkflow, /ACTUAL_API=.*ro\.build\.version\.sdk/);
});

test('Android instrumentation leaves enough time for the uncached API 24 lane', () => {
  assert.match(instrumentationWorkflow, /timeout-minutes:\s*45\b/);
});

test('API 24 warms the real APK artifacts before proving the build offline', () => {
  const api24Block = instrumentationWorkflow.match(
    /if \[\[ "\$API_LEVEL" == "24" \]\]; then[\s\S]*?else\n/
  )?.[0];
  assert.ok(api24Block, 'API 24 workflow branch must remain explicit');

  const warmupIndex = api24Block.indexOf('timeout 600s ./gradlew assembleDebug assembleDebugAndroidTest');
  const cleanIndex = api24Block.indexOf('timeout 300s ./gradlew clean');
  const offlineIndex = api24Block.indexOf('timeout 600s ./gradlew assembleDebug assembleDebugAndroidTest \\\n              --stacktrace --no-daemon --offline');

  assert.notEqual(warmupIndex, -1, 'API 24 must warm the actual app and androidTest APK artifacts online');
  assert.notEqual(cleanIndex, -1, 'API 24 must clean build outputs before the offline proof');
  assert.notEqual(offlineIndex, -1, 'API 24 must rebuild the app and androidTest APKs offline');
  assert.ok(warmupIndex < cleanIndex, 'online warm-up must run before clean');
  assert.ok(cleanIndex < offlineIndex, 'clean must run before the offline proof');
  assert.match(api24Block.slice(warmupIndex, cleanIndex), /--stacktrace --no-daemon(?![\s\S]*--offline)/);
  assert.match(api24Block.slice(offlineIndex), /--offline/);
});
