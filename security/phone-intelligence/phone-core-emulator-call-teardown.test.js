import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const answerBridgeHelper = fs.readFileSync('scripts/phone-core-emulator-api37-answer-bridge.py', 'utf8');

function extractShellFunction(name, nextName) {
  const start = flow.indexOf(`${name}() {`);
  const end = flow.indexOf(`\n${nextName}() {`, start);
  assert.ok(start >= 0, `${name} must exist`);
  assert.ok(end > start, `${name} must end before ${nextName}`);
  return flow.slice(start, end);
}

test('incoming emulator call is fully torn down before the outgoing probe starts', () => {
  const cancelIndex = flow.indexOf('adb emu gsm cancel "$FLOW_NUMBER"');
  const absentIndex = flow.indexOf('wait_emulator_call_absent "$FLOW_NUMBER"', cancelIndex);
  const endedCaptureIndex = flow.indexOf('capture 04-ended-call', absentIndex);
  const outgoingIndex = flow.indexOf('FLOW_OUTGOING_ACTIVE=0', endedCaptureIndex);

  assert.ok(cancelIndex >= 0, 'incoming modem cancellation must be present');
  assert.ok(absentIndex > cancelIndex, 'the emulator modem must prove the incoming call is absent');
  assert.ok(endedCaptureIndex > absentIndex, 'post-teardown visual evidence must be captured');
  assert.ok(outgoingIndex > endedCaptureIndex, 'outgoing qualification must start only after teardown evidence');

  const transition = flow.slice(cancelIndex, outgoingIndex);
  assert.doesNotMatch(
    transition,
    /wait_text \"Appel terminé\"/,
    'a localized UI label is not a Telecom teardown oracle'
  );
  assert.doesNotMatch(
    transition,
    /adb emu gsm cancel \"\$FLOW_NUMBER\"\s*\nsleep 1\b/,
    'a fixed sleep is not a valid Telecom teardown oracle'
  );
});

test('modem teardown oracle fails closed when adb gsm-list evidence is unreadable', () => {
  const waitFunction = extractShellFunction('wait_emulator_call_absent', 'tap_text');
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-modem-oracle-'));
  try {
    const script = `
set -u
FLOW_OUTPUT_DIR="$1"
sleep() { :; }
adb() { printf 'transport unavailable\\n' >&2; return 17; }
${waitFunction}
wait_emulator_call_absent 5550100
`;
    const result = spawnSync('bash', ['-c', script, 'sentinel-test', tmp], {
      encoding: 'utf8',
    });

    assert.notEqual(result.status, 0, 'an unreadable modem oracle must never prove call absence');
    assert.match(
      `${result.stdout}${result.stderr}`,
      /could not prove call 5550100 absent/,
      'failure must explicitly report that absence could not be proven'
    );
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
});

test('outgoing emulator qualification preserves fail-closed app policy and waits for runtime truth', () => {
  const retryIndex = flow.indexOf('FLOW_OUTGOING_ACTIVE=0');
  const dialIndex = flow.indexOf('android.intent.action.DIAL -d tel:5550101', retryIndex);
  const activeIndex = flow.indexOf('private_timeline_has_event "OUTGOING" "INCALL_ACTIVE"', dialIndex);
  const uiEvidenceIndex = flow.indexOf('wait_private_timeline_event "LOCAL" "INCALL_UI_SHOWN"', activeIndex);
  const hangupIndex = flow.indexOf('tap_text "phone_core_hangup"', uiEvidenceIndex);
  const modemEndedIndex = flow.indexOf('wait_emulator_call_absent 5550101', hangupIndex);

  assert.ok(retryIndex >= 0, 'outgoing recovery window must be explicit and bounded');
  assert.ok(dialIndex > retryIndex, 'Sentinel must originate the outgoing probe from its dialer');
  const pollIndex = flow.indexOf('for FLOW_ATTEMPT in $(seq 1 12); do', dialIndex);
  assert.ok(pollIndex > dialIndex, 'placement must precede the observation loop');
  assert.doesNotMatch(flow.slice(pollIndex, activeIndex), /tap_text|am start/,
    'asynchronous timeline persistence must never cause duplicate outgoing placement');
  assert.ok(activeIndex > dialIndex, 'connected-state proof must come from the private runtime timeline');
  assert.ok(uiEvidenceIndex > activeIndex, 'app-owned in-call UI proof must remain independently required');
  assert.ok(hangupIndex > uiEvidenceIndex, 'the Sentinel hang-up control must still be exercised');
  assert.ok(modemEndedIndex > hangupIndex, 'the modem must prove that Sentinel hang-up ended the call');

  const outgoingSection = flow.slice(retryIndex, modemEndedIndex);
  assert.doesNotMatch(
    outgoingSection,
    /wait_text \"Composition\"|wait_text \"En communication\"/,
    'localized transient call-state labels must not gate functional qualification'
  );
});

function telecomOracle(dump) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-telecom-oracle-'));
  try {
    const file = path.join(tmp, 'telecom.txt');
    fs.writeFileSync(file, dump);
    return spawnSync('python3', ['scripts/phone-core-emulator-telecom-calls.py', file], { encoding: 'utf8' });
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

const liveCall = '    [Call id=TC@2, state=ACTIVE, tpac=ComponentInfo{com.android.phone/TelephonyConnectionService}, handle=tel:*****01], voip=false';
const telecomDump = (entries) => `Init Path: On mainline\nCallsManager: \n  mCalls: \n${entries}  mCallAudioManager:\n    Historical Events:\n      [Call id=TC@1, state=ACTIVE, handle=tel:*****00]\n`;

test('Telecom teardown rejects the orphan ACTIVE call observed in the API 37 failure', () => {
  assert.equal(telecomOracle(telecomDump(liveCall + '\n')).status, 1);
  assert.equal(telecomOracle(telecomDump(liveCall.replace('ACTIVE', 'DISCONNECTED') + '\n')).status, 1);
  assert.equal(telecomOracle(telecomDump('')).status, 0, 'historical ACTIVE calls must not prevent teardown');
});

test('Telecom teardown never treats missing, truncated or ambiguous dumps as empty', () => {
  for (const dump of ['OK\n', 'CallsManager:\n  mCalls:\n', telecomDump('') + '  mCalls:\n',
    telecomDump('    unexpected payload\n'), 'Permission Denial: dumpsys telecom']) {
    const result = telecomOracle(dump);
    assert.equal(result.status, 2);
    assert.match(result.stderr, /UNKNOWN/);
  }
});

test('OK-only modem output cannot mask an ACTIVE Telecom call', () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-combined-teardown-'));
  try {
    const script = `
set -u
FLOW_OUTPUT_DIR="$1"
FLOW_SCRIPT_DIR="$2"
sleep() { :; }
adb() {
  if [[ "$1" == "emu" ]]; then printf 'OK\\n'; else
    printf 'CallsManager:\\n  mCalls:\\n    [Call id=TC@2, state=ACTIVE, handle=tel:*****01]\\n  mCallAudioManager:\\n'
  fi
}
${extractShellFunction('wait_emulator_call_absent', 'tap_text')}
wait_emulator_call_absent 5550101
`;
    const fixture = path.join(tmp, 'teardown-fixture.sh');
    fs.writeFileSync(fixture, script, { mode: 0o600 });
    const result = spawnSync('bash', [fixture, tmp, path.resolve('scripts')], { encoding: 'utf8' });
    assert.notEqual(result.status, 0, 'a live Telecom call must prevent modem-only false PASS');
    assert.match(result.stdout, /could not prove call/);
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
});


test('revoked dial action also requires empty readable Telecom state despite OK-only modem output', () => {
  const source = fs.readFileSync('scripts/phone-core-emulator-revocation-flow.sh', 'utf8');
  const start = source.indexOf('assert_modem_call_absent() {');
  const end = source.indexOf('\nwait_incoming_call_observed() {', start);
  assert.ok(start >= 0 && end > start);
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-revoked-telecom-'));
  try {
    const script = `set -eu
OUT_DIR="$1"
SCRIPT_DIR="$2"
adb() { if [[ "$1" == "emu" ]]; then printf 'OK\\n'; else printf 'CallsManager:\\n  mCalls:\\n    [Call id=TC@1, state=DIALING, handle=tel:*****97]\\n  mCallAudioManager:\\n'; fi; }
${source.slice(start, end)}
assert_modem_call_absent 5550197 probe.txt`;
    const fixture = path.join(temp, 'revoked-fixture.sh');
    fs.writeFileSync(fixture, script, { mode: 0o600 });
    const result = spawnSync('bash', [fixture, temp, path.resolve('scripts')], { encoding: 'utf8' });
    assert.notEqual(result.status, 0);
    assert.match(result.stdout, /live or UNKNOWN Telecom/);
  } finally { fs.rmSync(temp, { recursive: true, force: true }); }
});


test('incoming answer stays Sentinel-owned and API 37 evidence is bound to one Telecom transaction and call id', () => {
  const bridge = extractShellFunction(
    'start_api37_incoming_answer_transport_bridge',
    'wait_api37_incoming_answer_transport_bridge'
  );
  const api37Guard = bridge.indexOf('if [[ "$FLOW_API" -lt 37 ]]');
  const helperInvocation = bridge.indexOf('phone-core-emulator-api37-answer-bridge.py', api37Guard);

  assert.ok(api37Guard >= 0, 'the incoming modem bridge must remain restricted to API 37+');
  assert.ok(helperInvocation > api37Guard, 'API 37 synchronization must delegate to the preconnected helper');
  assert.doesNotMatch(bridge, /adb emu gsm accept/, 'the shell must not race Telecom with a second adb modem command');

  const answerRequest = answerBridgeHelper.indexOf('if answer_transaction is None and ANSWER_REQUEST_MARKER in line:');
  const acceptMatch = answerBridgeHelper.indexOf('accept_match = REQUEST_ACCEPT_RE.search(line)', answerRequest);
  const transactionBind = answerBridgeHelper.indexOf('transaction_token(line) == answer_transaction', acceptMatch);
  const modemAccept = answerBridgeHelper.indexOf('console.sendall', transactionBind);
  const answeredMatch = answerBridgeHelper.indexOf('answered_match = ANSWERED_RE.search(line)', modemAccept);
  const answeredCallBind = answerBridgeHelper.indexOf('answered_match.group(1) == call_id', answeredMatch);
  const activeMatch = answerBridgeHelper.indexOf('active_match = ACTIVE_RE.search(line)', answeredCallBind);
  const activeCallBind = answerBridgeHelper.indexOf('active_match.group(1) == call_id', activeMatch);

  assert.ok(answerRequest >= 0, 'the helper must first observe Sentinel-owned Telecom answer intent');
  assert.ok(acceptMatch > answerRequest, 'REQUEST_ACCEPT must be observed only after the causal answer request');
  assert.ok(transactionBind > acceptMatch, 'REQUEST_ACCEPT must belong to the same Telecom transaction');
  assert.ok(modemAccept > transactionBind, 'emulator gsm accept must occur only after transaction binding');
  assert.ok(answeredMatch > modemAccept, 'Telecom ANSWERED must be observed after transport synchronization');
  assert.ok(answeredCallBind > answeredMatch, 'ANSWERED must belong to the same call id');
  assert.ok(activeMatch > answeredCallBind, 'ACTIVE evidence must follow same-call ANSWERED');
  assert.ok(activeCallBind > activeMatch, 'ACTIVE must belong to the same call id');

  const ready = flow.indexOf('wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"');
  const openUi = flow.indexOf('open_incoming_call_notification', ready);
  const armBridge = flow.indexOf('start_api37_incoming_answer_transport_bridge', openUi);
  const answer = flow.indexOf('tap_text "phone_core_answer"', armBridge);
  const waitBridge = flow.indexOf('wait_api37_incoming_answer_transport_bridge', answer);
  const active = flow.indexOf('wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"', waitBridge);

  assert.ok(ready >= 0, 'Sentinel must first prove its incoming-call notification path');
  assert.ok(openUi > ready, 'the app-owned incoming-call surface must be opened before answering');
  assert.ok(armBridge > openUi, 'the host watcher must be armed before the user answer action');
  assert.ok(answer > armBridge, 'Sentinel UI must submit the answer action after the watcher is armed');
  assert.ok(waitBridge > answer, 'the flow must fail closed if the post-answer transport bridge cannot synchronize');
  assert.ok(active > waitBridge, 'Sentinel INCALL_ACTIVE remains the independent application-level success oracle');
});
