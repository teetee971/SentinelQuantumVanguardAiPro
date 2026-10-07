import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const revocationPath = 'scripts/phone-core-emulator-revocation-flow.sh';
const oraclePath = 'scripts/phone-core-logcat-crash-oracle.py';
const revocation = readFileSync(revocationPath, 'utf8');

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  const end = revocation.indexOf('\n}', start);
  assert.notEqual(end, -1, `unterminated shell function ${name}`);
  return revocation.slice(start, end + 2);
}

function runOracle(logcat) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-advanced-gate-oracle-'));
  try {
    const evidence = join(dir, 'logcat.txt');
    writeFileSync(evidence, logcat);
    return spawnSync('python3', [oraclePath, evidence], { encoding: 'utf8' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('advanced Phone Core branch uses fail-closed process-attributed logcat evidence', () => {
  const fn = shellFunction('assert_no_crash');
  assert.doesNotMatch(
    fn,
    /adb\s+logcat\s+-d\s+-v\s+brief\s*\|\s*grep\s+-E[q]?/,
    'a failed adb logcat pipe must never qualify as crash-free evidence'
  );
  assert.match(
    fn,
    /phone-core-logcat-crash-oracle\.py/,
    'revocation flow must delegate crash attribution to the shared oracle'
  );
  assert.equal(existsSync(oraclePath), true, 'shared process-attributed crash oracle must exist');
});

test('shared oracle rejects Sentinel failures without blaming unrelated Android processes', () => {
  const workerCrash = runOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: DefaultDispatcher-worker-2',
    'E/AndroidRuntime: Process: com.sentinel.quantum:telecom, PID: 4242',
    'E/AndroidRuntime: java.lang.IllegalStateException: boom'
  ].join('\n'));
  assert.notEqual(workerCrash.status, 0, 'Sentinel secondary-process fatal exception must fail qualification');

  const secondaryAnr = runOracle('E/ActivityManager: ANR in com.sentinel.quantum:telecom (com.sentinel.quantum/.MainActivity)\n');
  assert.notEqual(secondaryAnr.status, 0, 'Sentinel secondary-process ANR must fail qualification');

  const unrelated = runOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: Binder:system_server',
    'E/AndroidRuntime: Process: com.android.systemui, PID: 101',
    'E/AndroidRuntime: java.lang.IllegalStateException: unrelated'
  ].join('\n'));
  assert.equal(unrelated.status, 0, unrelated.stderr || unrelated.stdout);

  const ambiguous = runOracle('E/AndroidRuntime: FATAL EXCEPTION: pool-4-thread-1\n');
  assert.notEqual(ambiguous.status, 0, 'unattributed fatal evidence must remain fail-closed');
});

test('API 29 SEND_SMS revoke evidence must reject transient one-read denial', () => {
  const fn = shellFunction('probe_pm_revoke_send_sms');
  assert.match(
    fn,
    /for\s+observation\s+in\s+1\s+2\s+3/,
    'one denied read after pm revoke is not durable evidence; require repeated observations'
  );
  assert.match(fn, /denial_stability_observations=/);
  assert.match(fn, /role_controller_restored_runtime_permission/);
});
