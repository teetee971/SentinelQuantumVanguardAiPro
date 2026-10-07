import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const coordinator = readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/SmsActivationStateCoordinator.kt', 'utf8');
const composer = readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt', 'utf8');
const flow = readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const revocationFlow = readFileSync('scripts/phone-core-emulator-revocation-flow.sh', 'utf8');
const crashOracle = path.resolve('scripts/phone-core-logcat-crash-oracle.py');

function extractFunction(source, name, nextName) {
  const start = source.indexOf(`${name}() {`);
  const end = source.indexOf(`\n${nextName}() {`, start);
  assert.ok(start >= 0, `${name} must exist`);
  assert.ok(end > start, `${name} must end before ${nextName}`);
  return source.slice(start, end);
}

function runCrashOracle(lines) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'sentinel-crash-oracle-'));
  const file = path.join(dir, 'logcat.txt');
  writeFileSync(file, `${lines.join('\n')}\n`);
  const result = spawnSync('python3', [crashOracle, file], { encoding: 'utf8' });
  rmSync(dir, { recursive: true, force: true });
  return result;
}

test('crash oracle fails closed when a second FATAL arrives before the first is attributed', () => {
  const result = runCrashOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: first',
    'E/AndroidRuntime: FATAL EXCEPTION: second',
    'E/AndroidRuntime: Process: com.android.systemui, PID: 200',
    'E/AndroidRuntime: Process: com.sentinel.quantum, PID: 100',
  ]);
  assert.notEqual(result.status, 0, result.stderr || result.stdout);
});

test('crash oracle rejects a truncated fatal followed by an unrelated attributed crash', () => {
  const result = runCrashOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: first',
    'E/AndroidRuntime: FATAL EXCEPTION: unrelated',
    'E/AndroidRuntime: Process: com.android.systemui, PID: 200',
  ]);
  assert.notEqual(result.status, 0, result.stderr || result.stdout);
});

test('SMS authorization refresh does not recreate the composer and discard non-saveable send state', () => {
  assert.doesNotMatch(coordinator, /activity\.recreate\(\)/);
  assert.match(coordinator, /activity\.refreshActivationAuthorization\(\)/);
  assert.match(composer, /fun refreshActivationAuthorization\(\)/);
  assert.match(composer, /activationEpochState/);
});

test('incoming answer bridge must prove its log stream is armed before the answer tap is allowed', () => {
  const startBridge = extractFunction(flow, 'start_api37_incoming_answer_transport_bridge', 'wait_api37_incoming_answer_transport_bridge');
  assert.match(startBridge, /ready_file=/);
  assert.match(startBridge, /--ready-file "\$ready_file"/);
  assert.match(startBridge, /bridge_ready=1/);
  assert.match(startBridge, /kill -0 "\$FLOW_INCOMING_TRANSPORT_PID"/);

  const start = flow.indexOf('start_api37_incoming_answer_transport_bridge');
  const tap = flow.indexOf('tap_text "phone_core_answer"', start);
  assert.ok(start >= 0 && tap > start, 'bridge readiness must complete before the answer tap');
});

test('foreground-return oracle requires a concrete non-Sentinel resumed activity record', () => {
  const fn = extractFunction(revocationFlow, 'wait_app_backgrounded', 'timeline_signal_prefix_count');
  assert.match(fn, /resumed_line=/);
  assert.match(fn, /mResumedActivity: null/);
  assert.match(fn, /-z "\$resumed_line"/);
  assert.match(fn, /com\\\.sentinel\\\.quantum/);
  assert.match(fn, /return 0/);
});
