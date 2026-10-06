import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');

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

test('API 37 incoming transport synchronization remains emulator-only and fail-closed', () => {
  const apiGuard = startBridge.indexOf('if [[ "$FLOW_API" -lt 37 ]]');
  const telecomMarker = startBridge.indexOf("CallsManager: setCallState RINGING(RINGING) -> ANSWERED");
  const markerFailure = startBridge.indexOf('telecom_answer_marker_missing');
  const modemAccept = startBridge.indexOf('adb emu gsm accept "$FLOW_NUMBER"');

  assert.ok(apiGuard >= 0, 'the modem bridge must be disabled below API 37');
  assert.ok(telecomMarker > apiGuard, 'API 37 must wait for Telecom ANSWERED evidence');
  assert.ok(markerFailure > telecomMarker, 'missing Telecom evidence must be an explicit failure');
  assert.ok(modemAccept > markerFailure, 'modem synchronization must happen only after Telecom evidence is validated');
  assert.match(startBridge, /timeout 5s adb logcat/, 'the Telecom watcher must remain bounded');
  assert.match(startBridge, /return 1/, 'missing marker evidence must fail closed');
  assert.doesNotMatch(startBridge, /adb emu gsm accept[^\n]*\|\|\s*true/, 'modem synchronization failure must not be ignored');
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
  assert.match(waitBridge, /wait "\$FLOW_INCOMING_TRANSPORT_PID"/, 'bridge completion must be awaited');
  assert.doesNotMatch(waitBridge, /wait "\$FLOW_INCOMING_TRANSPORT_PID"[^\n]*\|\|\s*true/, 'bridge failure must propagate');
});

function executeBridge({ api, marker = false, acceptFails = false }) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-api37-answer-bridge-'));
  const bin = path.join(tmp, 'bin');
  const output = path.join(tmp, 'evidence');
  const calls = path.join(tmp, 'adb-calls.txt');
  fs.mkdirSync(bin);
  fs.mkdirSync(output);
  const adb = path.join(bin, 'adb');
  fs.writeFileSync(adb, `#!/usr/bin/env bash
set -eu
printf '%s\\n' "$*" >> "$ADB_CALLS_FILE"
if [[ "${1:-}" == "logcat" ]]; then
  if [[ "${ADB_LOGCAT_MARKER:-0}" == "1" ]]; then
    printf '%s\\n' 'I/Telecom: CallsManager: setCallState RINGING(RINGING) -> ANSWERED'
  fi
  exit 0
fi
if [[ "$*" == "emu gsm accept 5550100" && "${ADB_ACCEPT_FAIL:-0}" == "1" ]]; then
  exit 23
fi
exit 0
`, { mode: 0o700 });

  const fixture = `set -euo pipefail
FLOW_OUTPUT_DIR="$1"
FLOW_API="$2"
FLOW_NUMBER="5550100"
FLOW_INCOMING_TRANSPORT_PID=""
${startBridge}
${waitBridge}
start_api37_incoming_answer_transport_bridge
wait_api37_incoming_answer_transport_bridge
`;
  const result = spawnSync('bash', ['-c', fixture, 'sentinel-test', output, String(api)], {
    encoding: 'utf8',
    env: {
      ...process.env,
      PATH: `${bin}:${process.env.PATH}`,
      ADB_CALLS_FILE: calls,
      ADB_LOGCAT_MARKER: marker ? '1' : '0',
      ADB_ACCEPT_FAIL: acceptFails ? '1' : '0',
    },
  });
  const callLog = fs.existsSync(calls) ? fs.readFileSync(calls, 'utf8') : '';
  const evidenceFile = path.join(output, 'api37-incoming-answer-transport.txt');
  const evidence = fs.existsSync(evidenceFile) ? fs.readFileSync(evidenceFile, 'utf8') : '';
  fs.rmSync(tmp, { recursive: true, force: true });
  return { result, callLog, evidence };
}

test('bridge performs no adb transport action below API 37', () => {
  const { result, callLog, evidence } = executeBridge({ api: 36 });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(callLog, '');
  assert.match(evidence, /not_required api=36/);
});

test('API 37 bridge cannot accept the modem call without Telecom ANSWERED evidence', () => {
  const { result, callLog, evidence } = executeBridge({ api: 37, marker: false });
  assert.notEqual(result.status, 0, 'missing Telecom evidence must fail qualification');
  assert.match(callLog, /logcat -v brief -T 1/);
  assert.doesNotMatch(callLog, /emu gsm accept/);
  assert.match(evidence, /telecom_answer_marker_missing/);
});

test('API 37 bridge synchronizes only after Telecom ANSWERED and propagates modem failure', () => {
  const success = executeBridge({ api: 37, marker: true });
  assert.equal(success.result.status, 0, success.result.stderr);
  assert.match(success.callLog, /logcat -v brief -T 1[\s\S]*emu gsm accept 5550100/);
  assert.match(success.evidence, /RINGING\(RINGING\) -> ANSWERED/);
  assert.match(success.evidence, /transport_sync=adb_emu_gsm_accept api=37 number=5550100/);

  const failure = executeBridge({ api: 37, marker: true, acceptFails: true });
  assert.notEqual(failure.result.status, 0, 'adb emu gsm accept failure must fail qualification');
  assert.match(failure.callLog, /emu gsm accept 5550100/);
});
