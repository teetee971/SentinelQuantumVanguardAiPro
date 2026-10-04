import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const codeqlWorkflow = readFileSync('.github/workflows/codeql-analysis.yml', 'utf8');
const instrumentationWorkflow = readFileSync('.github/workflows/android-instrumentation.yml', 'utf8');
const phoneCoreFlow = readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');

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

test('Phone Core Android 16 flow proves call screening before in-call handling', () => {
  assert.match(phoneCoreFlow, /DIALER SMS CALL_SCREENING/);
  assert.match(phoneCoreFlow, /cmd role get-role-holders/);

  const incomingCall = phoneCoreFlow.indexOf('adb emu gsm call "$FLOW_NUMBER"');
  const screened = phoneCoreFlow.indexOf('wait_text "Appel autorisé"');
  const dismissCallerId = phoneCoreFlow.indexOf('tap_text "Fermer la fiche"');
  const answer = phoneCoreFlow.indexOf('wait_text "Décrocher"');

  assert.ok(incomingCall >= 0, 'Phone Core flow must place a synthetic incoming call');
  assert.ok(screened > incomingCall, 'Call Screening proof must follow the incoming call');
  assert.ok(dismissCallerId > screened, 'Caller ID must be dismissed after screening proof');
  assert.ok(answer > dismissCallerId, 'InCall proof must happen after Call Screening proof');
});
