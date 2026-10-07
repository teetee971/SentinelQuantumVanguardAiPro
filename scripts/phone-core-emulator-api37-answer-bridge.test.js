import assert from 'node:assert/strict';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import test from 'node:test';

const helper = path.resolve('scripts/phone-core-emulator-api37-answer-bridge.py');
const REQUEST = 'I/Telecom: CallSequencingController: answerCall: Beginning call sequencing transaction for answering incoming call.: ICA.aC(csq)@TX1🔒';
const ACCEPT = 'I/Telecom: Event: RecordEntry TC@1: REQUEST_ACCEPT, null: ICA.aC->CSC.aC->CSFM.rF(csq)@TX1🔒';
const ANSWERED = 'I/Telecom: CallsManager: setCallState RINGING(RINGING) -> ANSWERED, call: [Call id=TC@1, state=RINGING, tpac=fixture], voip=false: ICA.aC->CSC.aC->CSFM.rF(csq)@TX1🔒';
const ACTIVE = 'I/Telecom: CallsManager: setCallState ANSWERED(ANSWERED) -> ACTIVE, call: [Call id=TC@1, state=ANSWERED, tpac=fixture], voip=false: CSW.sA(cap)@TX2🔒';
const ACTIVE_COMPACT = 'I/Telecom: CallsManager: setCallState ANSWERED -> ACTIVE, call: [Call id=TC@1, state=ANSWERED, tpac=fixture], voip=false: CSW.sA(cap)@TX2🔒';
const PUBLIC_INCALL_ACTIVE = 'I/BluetoothInCallService( 1202): onStateChanged(Call [id: TC@1, state: ACTIVE, audioProcessingUseCase: 0, details: []], state=4)';
const HELD = 'I/Telecom: CallsManager: setCallState ANSWERED(ANSWERED) -> ON_HOLD, call: [Call id=TC@1, state=ANSWERED, tpac=fixture], voip=false: CSW.sA(cap)@TX2🔒';
const ACTIVE_FROM_HOLD = 'I/Telecom: CallsManager: setCallState ON_HOLD(ON_HOLD) -> ACTIVE, call: [Call id=TC@1, state=ON_HOLD, tpac=fixture], voip=false: CSW.sA(cap)@TX3🔒';

async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  return server.address().port;
}

async function runFixture({ lines, gsmResponse = 'OK\n', serial = null, api = 37 }) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-api37-console-'));
  const bin = path.join(tmp, 'bin');
  const evidence = path.join(tmp, 'evidence.txt');
  const marker = path.join(tmp, 'marker.txt');
  const token = path.join(tmp, 'token');
  const consoleCalls = [];
  fs.mkdirSync(bin);
  fs.writeFileSync(token, 'fixture-token\n');

  const server = net.createServer(socket => {
    socket.setEncoding('utf8');
    socket.write('Android Console: Authentication required\nUse auth <auth_token> to authenticate\nOK\n');
    let buffer = '';
    socket.on('data', chunk => {
      buffer += chunk;
      while (buffer.includes('\n')) {
        const index = buffer.indexOf('\n');
        const command = buffer.slice(0, index).trim();
        buffer = buffer.slice(index + 1);
        if (!command) continue;
        consoleCalls.push(command);
        if (command === 'auth fixture-token') socket.write('OK\n');
        else if (command === 'gsm accept 5550100') socket.write(gsmResponse);
        else socket.write('KO\n');
      }
    });
  });
  const port = await listen(server);

  const adb = path.join(bin, 'adb');
  fs.writeFileSync(adb, `#!/usr/bin/env bash
set -eu
if [[ "\${1:-}" == "get-serialno" ]]; then
  printf '%s\\n' "\${FAKE_SERIAL:-emulator-${port}}"
  exit 0
fi
if [[ "\${1:-}" == "logcat" ]]; then
  cat "\${FAKE_LOGCAT_FILE}"
  exit 0
fi
exit 64
`, { mode: 0o700 });
  const logcatFile = path.join(tmp, 'logcat.txt');
  fs.writeFileSync(logcatFile, lines.join('\n') + '\n');

  const args = [helper, '--api', String(api), '--number', '5550100', '--evidence', evidence, '--marker-file', marker, '--timeout', '1.5'];
  const child = spawn('python3', args, {
    env: {
      ...process.env,
      PATH: `${bin}:${process.env.PATH}`,
      FAKE_LOGCAT_FILE: logcatFile,
      FAKE_SERIAL: serial || `emulator-${port}`,
      SENTINEL_EMULATOR_CONSOLE_TOKEN_FILE: token,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', data => { stdout += data; });
  child.stderr.on('data', data => { stderr += data; });
  const status = await new Promise(resolve => child.on('close', resolve));
  await new Promise(resolve => server.close(resolve));
  const result = {
    status,
    stdout,
    stderr,
    calls: consoleCalls,
    evidence: fs.existsSync(evidence) ? fs.readFileSync(evidence, 'utf8') : '',
    marker: fs.existsSync(marker) ? fs.readFileSync(marker, 'utf8') : '',
  };
  fs.rmSync(tmp, { recursive: true, force: true });
  return result;
}

test('API 37 bridge binds REQUEST_ACCEPT, ANSWERED and ACTIVE to one causal Telecom call', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, ACTIVE] });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.marker, /answerCall/);
  assert.match(result.marker, /TC@1: REQUEST_ACCEPT/);
  assert.match(result.evidence, /transport_sync=.*call_id=TC@1 transaction=TX1/);
  assert.match(result.evidence, /RINGING\(RINGING\) -> ANSWERED/);
  assert.match(result.evidence, /ANSWERED\(ANSWERED\) -> ACTIVE/);
});

test('API 36 bridge binds the same causal call and records API 36 provenance', async () => {
  const compactAnswered = ANSWERED.replace('RINGING(RINGING)', 'RINGING');
  const result = await runFixture({ api: 36, lines: [REQUEST, ACCEPT, compactAnswered, ACTIVE_COMPACT] });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /transport_sync=.*api=36 .*call_id=TC@1 transaction=TX1/);
});

test('API 37 bridge accepts the compact ANSWERED -> ACTIVE log format for the same call', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, ACTIVE_COMPACT] });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
});

test('API 37 bridge recovers one same-call ANSWERED -> ON_HOLD transport transition before requiring ACTIVE', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, HELD, ACTIVE_FROM_HOLD] });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100', 'gsm accept 5550100']);
  assert.match(result.evidence, /ANSWERED\(ANSWERED\) -> ON_HOLD/);
  assert.match(result.evidence, /transport_recovery=.*call_id=TC@1/);
  assert.match(result.evidence, /ON_HOLD\(ON_HOLD\) -> ACTIVE/);
});

test('API 37 pre-synchronizes modem at answer-transaction start but does not credit success before REQUEST_ACCEPT', async () => {
  const result = await runFixture({ lines: [REQUEST] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /transport_pre_sync=.*api=37 .*transaction=TX1/);
  assert.doesNotMatch(result.evidence, /transport_sync=.*call_id=/);
  assert.match(result.evidence, /request_accept,transport_sync,answered_same_call,active_same_call/);
});

test('API 37 credits modem synchronization only after causal REQUEST_ACCEPT without crediting answer success', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT] });
  assert.notEqual(result.status, 0, 'same-call ANSWERED and ACTIVE are still required for qualification');
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /transport_pre_sync=.*api=37 .*transaction=TX1/);
  assert.match(result.evidence, /transport_sync=.*call_id=TC@1 transaction=TX1/);
  assert.match(result.evidence, /answered_same_call,active_same_call/);
});

test('REQUEST_ACCEPT from a different Telecom transaction cannot credit modem synchronization', async () => {
  const unrelatedAccept = ACCEPT.replace('@TX1🔒', '@OTHER🔒');
  const result = await runFixture({ lines: [REQUEST, unrelatedAccept, ANSWERED, ACTIVE] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /transport_pre_sync=.*transaction=TX1/);
  assert.doesNotMatch(result.evidence, /transport_sync=.*call_id=/);
  assert.match(result.evidence, /request_accept,transport_sync,answered_same_call,active_same_call/);
});

test('stale REQUEST_ACCEPT before the causal answer request is rejected', async () => {
  const result = await runFixture({ lines: [ACCEPT, REQUEST, ANSWERED, ACTIVE] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /transport_pre_sync=.*transaction=TX1/);
  assert.doesNotMatch(result.evidence, /transport_sync=.*call_id=/);
});

test('ANSWERED from a different call id cannot qualify the causal call', async () => {
  const wrongAnswered = ANSWERED.replaceAll('TC@1', 'TC@2');
  const result = await runFixture({ lines: [REQUEST, ACCEPT, wrongAnswered, ACTIVE] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /answered_same_call,active_same_call/);
});

test('ACTIVE from a different call id cannot qualify the synchronized call', async () => {
  const wrongActive = ACTIVE.replaceAll('TC@1', 'TC@2');
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, wrongActive] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /active_same_call/);
});

test('stale ANSWERED and ACTIVE before synchronization cannot qualify the causal call', async () => {
  const result = await runFixture({ lines: [ANSWERED, ACTIVE, REQUEST, ACCEPT] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /answered_same_call,active_same_call/);
});

test('ACTIVE before ANSWERED cannot qualify the call', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ACTIVE, ANSWERED] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /active_same_call/);
});

test('public InCall ACTIVE projection cannot qualify transport ACTIVE', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, PUBLIC_INCALL_ACTIVE] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /active_same_call/);
});

test('answer request without a transaction token fails closed', async () => {
  const tokenless = REQUEST.replace(/@TX1🔒$/, '');
  const result = await runFixture({ lines: [tokenless, ACCEPT, ANSWERED, ACTIVE] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token']);
  assert.match(result.evidence, /answer_request_missing_transaction_token=1/);
});

test('banner OK cannot mask rejection of API 37 pre-sync gsm accept', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, ACTIVE], gsmResponse: 'KO\n' });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /console_gsm_accept_pre_sync_response=KO/);
  assert.doesNotMatch(result.evidence, /transport_pre_sync=/);
  assert.doesNotMatch(result.evidence, /transport_sync=/);
});

test('API 37 bridge refuses any non-emulator adb target before opening transport', async () => {
  const result = await runFixture({ lines: [REQUEST, ACCEPT, ANSWERED, ACTIVE], serial: 'R5CT123456A' });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, []);
  assert.match(result.evidence, /refusing non-emulator adb target/);
});