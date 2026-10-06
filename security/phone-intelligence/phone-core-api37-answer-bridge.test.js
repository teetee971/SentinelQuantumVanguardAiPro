import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const helper = fs.readFileSync('scripts/phone-core-emulator-api37-answer-bridge.py', 'utf8');

function extract(name, nextName) {
  const start = flow.indexOf(`${name}() {`);
  const end = flow.indexOf(`\n${nextName}() {`, start);
  assert.ok(start >= 0, `${name} must exist`);
  assert.ok(end > start, `${name} must end before ${nextName}`);
  return flow.slice(start, end);
}

const startBridge = extract(
  'start_api37_incoming_answer_transport_bridge',
  'wait_api37_incoming_answer_transport_bridge'
);
const waitBridge = extract(
  'wait_api37_incoming_answer_transport_bridge',
  'wait_emulator_call_absent'
);

test('API 37 synchronization delegates to the preconnected emulator-only helper', () => {
  const apiGuard = startBridge.indexOf('if [[ "$FLOW_API" -lt 37 ]]');
  const helperInvocation = startBridge.indexOf('phone-core-emulator-api37-answer-bridge.py');

  assert.ok(apiGuard >= 0, 'the modem bridge must be disabled below API 37');
  assert.ok(helperInvocation > apiGuard, 'API 37 must delegate only after the API guard');
  assert.match(startBridge, /--number "\$FLOW_NUMBER"/);
  assert.match(startBridge, /--evidence "\$evidence"/);
  assert.match(startBridge, /--marker-file "\$marker_file"/);
  assert.doesNotMatch(startBridge, /adb emu gsm accept/, 'the shell harness must not start a second adb modem command');
});

test('low-latency helper requires causal answer ownership and real Telecom ACTIVE', () => {
  const request = helper.indexOf('CallSequencingController: answerCall: Beginning call sequencing transaction for answering incoming call.');
  const socketSend = helper.indexOf('gsm accept {number}');
  const answered = helper.indexOf('CallsManager: setCallState RINGING(RINGING) -> ANSWERED');
  const active = helper.indexOf('CallsManager: setCallState ANSWERED(ANSWERED) -> ACTIVE');

  assert.ok(request >= 0, 'helper must wait for the Telecom answer transaction initiated by the app');
  assert.ok(socketSend > request, 'synthetic modem synchronization must be causally gated by the answer request');
  assert.ok(answered >= 0, 'Telecom ANSWERED evidence must remain required');
  assert.ok(active >= 0, 'Telecom ACTIVE evidence must remain required');
  assert.match(helper, /SERIAL_RE = re\.compile\(r"\^emulator-/,
    'helper must reject non-emulator adb targets');
  assert.match(helper, /socket\.create_connection/, 'console connection must be established before waiting for the tap');
  assert.doesNotMatch(helper, /subprocess\.run\(\s*\["adb", "emu"/, 'helper must not pay a second adb startup cost');
});

test('Sentinel answer action remains between bridge arming and final application ACTIVE proof', () => {
  const incomingReady = flow.indexOf('wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"');
  const bridgeStart = flow.indexOf('start_api37_incoming_answer_transport_bridge', incomingReady);
  const answerTap = flow.indexOf('tap_text "phone_core_answer"', bridgeStart);
  const bridgeWait = flow.indexOf('wait_api37_incoming_answer_transport_bridge', answerTap);
  const appActive = flow.indexOf('wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"', bridgeWait);

  assert.ok(incomingReady >= 0, 'incoming notification evidence must exist');
  assert.ok(bridgeStart > incomingReady, 'the bridge must be armed only after Sentinel observed the incoming call');
  assert.ok(answerTap > bridgeStart, 'Sentinel UI must initiate Call.answer()');
  assert.ok(bridgeWait > answerTap, 'transport synchronization must complete after the app-owned tap');
  assert.ok(appActive > bridgeWait, 'private Sentinel INCALL_ACTIVE evidence must remain mandatory');

  const runtimeSection = flow.slice(incomingReady, appActive);
  assert.doesNotMatch(runtimeSection, /adb emu gsm accept/,
    'incoming runtime must use only the guarded low-latency bridge');
});

test('transport bridge wait propagates helper failure instead of converting it to evidence', () => {
  assert.match(waitBridge, /wait "\$FLOW_INCOMING_TRANSPORT_PID"/, 'bridge completion must be awaited');
  assert.doesNotMatch(waitBridge, /wait "\$FLOW_INCOMING_TRANSPORT_PID"[^\n]*\|\|\s*true/,
    'bridge failure must propagate');
});

test('API guard returns fail-closed evidence before any helper invocation below API 37', () => {
  const apiGuard = startBridge.indexOf('if [[ "$FLOW_API" -lt 37 ]]');
  const notRequired = startBridge.indexOf("printf 'not_required api=%s\\n'", apiGuard);
  const guardReturn = startBridge.indexOf('return 0', notRequired);
  const guardEnd = startBridge.indexOf('fi', guardReturn);
  const helperInvocation = startBridge.indexOf('phone-core-emulator-api37-answer-bridge.py');

  assert.ok(apiGuard >= 0, 'API guard must exist');
  assert.ok(notRequired > apiGuard, 'below-37 path must record explicit not-required evidence');
  assert.ok(guardReturn > notRequired, 'below-37 path must terminate successfully after recording evidence');
  assert.ok(guardEnd > guardReturn, 'API guard must close after its bounded return path');
  assert.ok(helperInvocation > guardEnd, 'helper invocation must be unreachable from the below-37 branch');
});
