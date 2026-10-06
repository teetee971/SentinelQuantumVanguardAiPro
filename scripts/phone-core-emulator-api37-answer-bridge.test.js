import assert from 'node:assert/strict';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import test from 'node:test';

const helper = path.resolve('scripts/phone-core-emulator-api37-answer-bridge.py');
const REQUEST = 'I/Telecom: CallSequencingController: answerCall: Beginning call sequencing transaction for answering incoming call.';
const ANSWERED = 'I/Telecom: CallsManager: setCallState RINGING(RINGING) -> ANSWERED';
const ACTIVE = 'I/Telecom: CallsManager: setCallState ANSWERED(ANSWERED) -> ACTIVE';

async function listen(server) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  return server.address().port;
}

async function runFixture({ lines, gsmResponse = 'OK\n', serial = null }) {
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
    // Match Android's documented console protocol: the initial authentication-required banner
    // itself terminates with OK. The bridge must consume this banner-level OK before sending auth,
    // otherwise it could attribute a stale response to a later command.
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

  const args = [helper, '--number', '5550100', '--evidence', evidence, '--marker-file', marker, '--timeout', '1.5'];
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

test('API 37 bridge consumes banner OK, authenticates locally, synchronizes on causal answer request, then requires ANSWERED -> ACTIVE', async () => {
  const result = await runFixture({ lines: [REQUEST, ANSWERED, ACTIVE] });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /console_target=127\.0\.0\.1:\d+/);
  assert.match(result.evidence, /console_greeting=.*Authentication required.*OK/);
  assert.match(result.evidence, /console_auth_response=OK/);
  assert.match(result.marker, /CallSequencingController: answerCall/);
  assert.match(result.evidence, /transport_sync=emulator_console_gsm_accept api=37 number=5550100/);
  assert.match(result.evidence, /RINGING\(RINGING\) -> ANSWERED/);
  assert.match(result.evidence, /ANSWERED\(ANSWERED\) -> ACTIVE/);
});

test('API 37 bridge cannot synthesize transport without a causal Telecom answer request', async () => {
  const result = await runFixture({ lines: [ANSWERED, ACTIVE] });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token']);
  assert.doesNotMatch(result.evidence, /transport_sync=/);
  assert.match(result.evidence, /missing Telecom bridge evidence: answer_request/);
});

test('banner OK cannot mask rejection of the later gsm accept command', async () => {
  const result = await runFixture({ lines: [REQUEST, ANSWERED, ACTIVE], gsmResponse: 'KO\n' });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, ['auth fixture-token', 'gsm accept 5550100']);
  assert.match(result.evidence, /console_greeting=.*OK/);
  assert.match(result.evidence, /console_auth_response=OK/);
  assert.match(result.evidence, /console_gsm_accept_response=KO/);
  assert.doesNotMatch(result.evidence, /transport_sync=/);
  assert.match(result.evidence, /emulator console gsm accept failed/);
});

test('API 37 bridge refuses any non-emulator adb target before opening transport', async () => {
  const result = await runFixture({ lines: [REQUEST, ANSWERED, ACTIVE], serial: 'R5CT123456A' });
  assert.notEqual(result.status, 0);
  assert.deepEqual(result.calls, []);
  assert.match(result.evidence, /refusing non-emulator adb target/);
});
