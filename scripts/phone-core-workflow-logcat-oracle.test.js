import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const workflows = [
  '.github/workflows/build-native-android.yml',
  '.github/workflows/android-emulation-qualification.yml'
];
const crashConsumers = [
  ...workflows,
  'scripts/phone-core-emulator-revocation-flow.sh'
];
const oraclePath = 'scripts/phone-core-logcat-crash-oracle.py';

const unsafeCrashOracle = /if\s+adb\s+logcat\s+-d\s+-v\s+brief\s*\|\s*grep\s+-E[q]?/m;

test('Android workflows never treat an unreadable logcat pipe as crash-free evidence', () => {
  for (const path of workflows) {
    const source = readFileSync(path, 'utf8');
    assert.doesNotMatch(
      source,
      unsafeCrashOracle,
      `${path} must capture and validate adb logcat exit status before interpreting crash absence`
    );
  }
});

test('Android workflows reject Sentinel crashes on any thread, not only the main thread', () => {
  for (const path of workflows) {
    const source = readFileSync(path, 'utf8');
    assert.doesNotMatch(
      source,
      /FATAL EXCEPTION: main/,
      `${path} must not limit crash detection to the main thread because AndroidRuntime crashes may occur on worker or binder threads`
    );
  }
});

test('all Android crash qualification paths use the process-attributed crash oracle', () => {
  for (const path of crashConsumers) {
    const source = readFileSync(path, 'utf8');
    assert.match(
      source,
      /phone-core-logcat-crash-oracle\.py/,
      `${path} must attribute FATAL EXCEPTION evidence to com.sentinel.quantum instead of failing on unrelated Android process crashes`
    );
  }
});

function runOracle(logcat) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-logcat-oracle-'));
  try {
    const evidence = join(dir, 'logcat.txt');
    writeFileSync(evidence, logcat);
    return spawnSync('python3', [oraclePath, evidence], { encoding: 'utf8' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('process-attributed crash oracle rejects a Sentinel fatal exception on a worker thread', () => {
  const result = runOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: DefaultDispatcher-worker-2',
    'E/AndroidRuntime: Process: com.sentinel.quantum, PID: 4242',
    'E/AndroidRuntime: java.lang.IllegalStateException: boom'
  ].join('\n'));
  assert.notEqual(result.status, 0, 'Sentinel worker-thread fatal exception must fail qualification');
});

test('process-attributed crash oracle ignores a fatal exception from an unrelated Android process', () => {
  const result = runOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: Binder:system_server',
    'E/AndroidRuntime: Process: com.android.systemui, PID: 101',
    'E/AndroidRuntime: java.lang.IllegalStateException: unrelated'
  ].join('\n'));
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test('process-attributed crash oracle rejects a Sentinel ANR and accepts clean logs', () => {
  const anr = runOracle('E/ActivityManager: ANR in com.sentinel.quantum (com.sentinel.quantum/.MainActivity)\n');
  assert.notEqual(anr.status, 0, 'Sentinel ANR must fail qualification');

  const clean = runOracle('I/SentinelLifecycle: MainActivity resumed\n');
  assert.equal(clean.status, 0, clean.stderr || clean.stdout);
});
