import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');

function extract(name, nextName) {
  const start = flow.indexOf(`${name}() {`);
  const end = flow.indexOf(`\n${nextName}() {`, start);
  assert.ok(start >= 0, `${name} must exist`);
  assert.ok(end > start, `${name} must end before ${nextName}`);
  return flow.slice(start, end);
}

test('API 37 incoming transport synchronization remains emulator-only and fail-closed', () => {
  const bridge = extract(
    'start_api37_incoming_answer_transport_bridge',
    'wait_api37_incoming_answer_transport_bridge'
  );

  const apiGuard = bridge.indexOf('if [[ "$FLOW_API" -lt 37 ]]');
  const telecomMarker = bridge.indexOf("CallsManager: setCallState RINGING(RINGING) -> ANSWERED");
  const markerFailure = bridge.indexOf('telecom_answer_marker_missing');
  const modemAccept = bridge.indexOf('adb emu gsm accept "$FLOW_NUMBER"');

  assert.ok(apiGuard >= 0, 'the modem bridge must be disabled below API 37');
  assert.ok(telecomMarker > apiGuard, 'API 37 must wait for Telecom ANSWERED evidence');
  assert.ok(markerFailure > telecomMarker, 'missing Telecom evidence must be an explicit failure');
  assert.ok(modemAccept > markerFailure, 'modem synchronization must happen only after Telecom evidence is validated');
  assert.match(bridge, /timeout 5s adb logcat/, 'the Telecom watcher must remain bounded');
  assert.match(bridge, /return 1/, 'missing marker evidence must fail closed');
  assert.doesNotMatch(bridge, /adb emu gsm accept[^\n]*\|\|\s*true/, 'modem synchronization failure must not be ignored');
});

test('Sentinel answer action still precedes API 37 modem synchronization and app ACTIVE remains the success oracle', () => {
  const incomingReady = flow.indexOf('wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"');
  const bridgeStart = flow.indexOf('start_api37_incoming_answer_transport_bridge', incomingReady);
  const answerTap = flow.indexOf('tap_text "phone_core_answer"', bridgeStart);
  const bridgeWait = flow.indexOf('wait_api37_incoming_answer_transport_bridge', answerTap);
  const appActive = flow.indexOf('wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"', bridgeWait);

  assert.ok(incomingReady >= 0, 'incoming notification evidence must exist');
  assert.ok(bridgeStart > incomingReady, 'the watcher must be armed only after Sentinel observed the incoming call');
  assert.ok(answerTap > bridgeStart, 'Sentinel UI must initiate Call.answer(), never the emulator modem');
  assert.ok(bridgeWait > answerTap, 'the host bridge may synchronize transport only after the app answer action');
  assert.ok(appActive > bridgeWait, 'private Sentinel INCALL_ACTIVE evidence must remain mandatory after transport sync');

  const runtimeSection = flow.slice(incomingReady, appActive);
  assert.doesNotMatch(
    runtimeSection,
    /adb emu gsm accept/,
    'the runtime sequence must call the guarded bridge, not directly accept the modem call'
  );
});

test('transport bridge wait propagates failure instead of converting it to evidence', () => {
  const waitBridge = extract('wait_api37_incoming_answer_transport_bridge', 'wait_emulator_call_absent');
  assert.match(waitBridge, /wait "\$FLOW_INCOMING_TRANSPORT_PID"/, 'bridge completion must be awaited');
  assert.doesNotMatch(waitBridge, /wait "\$FLOW_INCOMING_TRANSPORT_PID"[^\n]*\|\|\s*true/, 'bridge failure must propagate');
});
