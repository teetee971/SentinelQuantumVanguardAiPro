import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const codeqlWorkflow = readFileSync('.github/workflows/codeql-analysis.yml', 'utf8');
const emulationWorkflow = readFileSync('.github/workflows/android-emulation-qualification.yml', 'utf8');

function requiredWorkflowBlock(workflow) {
  const start = workflow.indexOf('REQUIRED_WORKFLOWS=(');
  assert.notEqual(start, -1, 'CodeQL workflow must declare REQUIRED_WORKFLOWS');
  const end = workflow.indexOf('\n          )', start);
  assert.notEqual(end, -1, 'CodeQL REQUIRED_WORKFLOWS block must be bounded');
  return workflow.slice(start, end);
}

test('required CodeQL Android status waits for canonical emulator and comprehensive merge gates', () => {
  const block = requiredWorkflowBlock(codeqlWorkflow);
  assert.match(block, /"android-emulation-qualification\.yml"/);
  assert.doesNotMatch(block, /"android-instrumentation\.yml"/);
  assert.match(block, /"production-merge-gate\.yml"/);
});

test('Android emulator qualification covers minimum, RoleManager boundary, Android 16 and newest runtime lanes', () => {
  for (const apiLevel of [24, 29, 36, 37]) {
    assert.match(emulationWorkflow, new RegExp(`api_level:\\s*${apiLevel}\\b`));
  }
  assert.match(emulationWorkflow, /sdk_package_level:\s*'37\.0'/);
  assert.match(emulationWorkflow, /:app:connectedDebugAndroidTest/);
  assert.match(emulationWorkflow, /ACTUAL_API=.*ro\.build\.version\.sdk/);
  assert.match(emulationWorkflow, /if \[\[ "\$ACTUAL_API" != "\$API_LEVEL" \]\]/);
});
