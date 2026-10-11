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

test('API 24 bounds dependency resolution before using an offline test build', () => {
  const api24Block = instrumentationWorkflow.match(
    /if \[\[ "\$API_LEVEL" == "24" \]\]; then[\s\S]*?else\n/
  )?.[0];
  assert.ok(api24Block, 'API 24 workflow branch must remain explicit');
  assert.match(api24Block, /timeout 300s \.\/gradlew dependencies/);
  assert.match(api24Block, /--configuration debugAndroidTestRuntimeClasspath/);
  assert.match(api24Block, /assembleDebug assembleDebugAndroidTest[\s\S]*--offline/);
});
