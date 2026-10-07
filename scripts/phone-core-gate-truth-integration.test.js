import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';

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
