import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  const end = revocation.indexOf('\n}', start);
  assert.notEqual(end, -1, `unterminated shell function ${name}`);
  return revocation.slice(start, end + 2);
}

function runProbe({ revokeStatus, leavesGranted, restoreAfterPermissionChecks = null }) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-send-sms-revoke-'));
  try {
    const script = `
set -euo pipefail
PACKAGE=com.sentinel.quantum
OUT_DIR="$1"
SEND_SMS_PM_REVOCATION_OBSERVABLE=false
GRANTED=true
PERMISSION_CHECKS=0
REVOKE_STATUS=${revokeStatus}
LEAVES_GRANTED=${leavesGranted ? 'true' : 'false'}
RESTORE_AFTER_PERMISSION_CHECKS=${restoreAfterPermissionChecks ?? -1}
sleep() { :; }
adb() {
  if [[ "$*" == "shell pm revoke $PACKAGE android.permission.SEND_SMS" ]]; then
    if [[ "$LEAVES_GRANTED" == "false" ]]; then GRANTED=false; fi
    return "$REVOKE_STATUS"
  fi
  if [[ "$*" == "shell dumpsys package $PACKAGE" ]]; then
    PERMISSION_CHECKS=$((PERMISSION_CHECKS + 1))
    if [[ "$RESTORE_AFTER_PERMISSION_CHECKS" -ge 0 && "$PERMISSION_CHECKS" -gt "$RESTORE_AFTER_PERMISSION_CHECKS" ]]; then
      GRANTED=true
    fi
    if [[ "$GRANTED" == "true" ]]; then
      echo 'android.permission.SEND_SMS: granted=true'
    else
      echo 'android.permission.SEND_SMS: granted=false'
    fi
    return 0
  fi
  return 99
}
${shellFunction('permission_granted')}
${shellFunction('probe_pm_revoke_send_sms')}
probe_pm_revoke_send_sms
printf 'observable=%s\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"
printf 'permission_checks=%s\n' "$PERMISSION_CHECKS"
`;
    return spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('failed pm revoke is non-observable and returns control for the documented AppOp fallback', () => {
  const result = runProbe({ revokeStatus: 1, leavesGranted: true });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /observable=false/);
});

test('stable granted-to-denied runtime revocation remains observable', () => {
  const result = runProbe({ revokeStatus: 0, leavesGranted: false });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /observable=true/);
});

test('RoleController restoration remains non-observable and returns control', () => {
  const result = runProbe({ revokeStatus: 0, leavesGranted: true });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /observable=false/);
});

test('delayed RoleController restoration after the old three-sample window is still non-observable', () => {
  const result = runProbe({
    revokeStatus: 0,
    leavesGranted: false,
    restoreAfterPermissionChecks: 3,
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /observable=false/);
  assert.match(result.stdout, /permission_checks=[4-9][0-9]*/);
});
