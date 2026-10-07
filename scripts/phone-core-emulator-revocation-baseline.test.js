import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdtempSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

// Contract: an unobservable runtime-revoke probe is not a PASS. It must report
// observable=false and return control so the caller can prove the AppOp fallback.
const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  return revocation.slice(start, revocation.indexOf('\n}', start) + 2);
}

function runProbe({ initiallyGranted, revokeStatus, revokeLeavesGranted, restoreAfterDeniedChecks = null }) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-send-sms-revoke-'));
  try {
    const script = `
set -euo pipefail
PACKAGE=com.sentinel.quantum
OUT_DIR="$1"
SEND_SMS_PM_REVOCATION_OBSERVABLE=false
GRANTED=${initiallyGranted ? 'true' : 'false'}
REVOKE_STATUS=${revokeStatus}
REVOKE_LEAVES_GRANTED=${revokeLeavesGranted ? 'true' : 'false'}
RESTORE_AFTER_DENIED_CHECKS=${restoreAfterDeniedChecks === null ? -1 : restoreAfterDeniedChecks}
REVOKE_COMPLETED=false
printf '0\n' > "$OUT_DIR/post-revoke-permission-checks.txt"
sleep() { :; }
adb() {
  if [[ "$*" == "shell dumpsys package $PACKAGE" ]]; then
    if [[ "$REVOKE_COMPLETED" == "true" && "$GRANTED" == "false" && "$RESTORE_AFTER_DENIED_CHECKS" -ge 0 ]]; then
      local checks
      checks="$(cat "$OUT_DIR/post-revoke-permission-checks.txt")"
      checks=$((checks + 1))
      printf '%s\n' "$checks" > "$OUT_DIR/post-revoke-permission-checks.txt"
      if [[ "$checks" -gt "$RESTORE_AFTER_DENIED_CHECKS" ]]; then
        echo 'android.permission.SEND_SMS: granted=true'
        return 0
      fi
    fi
    if [[ "$GRANTED" == "true" ]]; then
      echo 'android.permission.SEND_SMS: granted=true'
    else
      echo 'android.permission.SEND_SMS: granted=false'
    fi
    return 0
  fi
  if [[ "$*" == "shell pm revoke $PACKAGE android.permission.SEND_SMS" ]]; then
    printf 'called\n' >> "$OUT_DIR/revoke-called.txt"
    REVOKE_COMPLETED=true
    if [[ "$REVOKE_LEAVES_GRANTED" == "false" ]]; then GRANTED=false; fi
    return "$REVOKE_STATUS"
  fi
  return 99
}
${shellFunction('permission_granted')}
${shellFunction('probe_pm_revoke_send_sms')}
probe_pm_revoke_send_sms
printf 'observable=%s\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"
`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    return {
      result,
      revokeCalled: existsSync(join(dir, 'revoke-called.txt')),
      evidence: existsSync(join(dir, 'send-sms-pm-revoke-observation.txt'))
        ? readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8')
        : ''
    };
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('SEND_SMS revocation probe rejects an unconfirmed granted baseline and falls back without issuing pm revoke', () => {
  const { result, revokeCalled, evidence } = runProbe({
    initiallyGranted: false,
    revokeStatus: 1,
    revokeLeavesGranted: false
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(revokeCalled, false, 'pm revoke must not run until a granted baseline is proven');
  assert.match(result.stdout, /observable=false/);
  assert.match(evidence, /baseline_granted=false/);
  assert.match(evidence, /reason=baseline_grant_not_proven/);
});

test('SEND_SMS revocation probe rejects a failed pm revoke as runtime evidence and falls back', () => {
  const { result, evidence } = runProbe({
    initiallyGranted: true,
    revokeStatus: 1,
    revokeLeavesGranted: false
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /observable=false/);
  assert.match(evidence, /pm_revoke_status=1/);
  assert.match(evidence, /reason=pm_revoke_failed/);
});

test('SEND_SMS revocation probe qualifies a confirmed granted-to-denied transition', () => {
  const { result, evidence } = runProbe({
    initiallyGranted: true,
    revokeStatus: 0,
    revokeLeavesGranted: false
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /observable=true/);
  assert.match(evidence, /baseline_granted=true/);
  assert.match(evidence, /pm_revoke_status=0/);
  assert.match(evidence, /denial_stability_observations=3/);
  assert.match(evidence, /observable=true/);
});

test('SEND_SMS revocation probe treats immediate RoleController restoration as non-observable and falls back', () => {
  const { result, evidence } = runProbe({
    initiallyGranted: true,
    revokeStatus: 0,
    revokeLeavesGranted: true
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /observable=false/);
  assert.match(evidence, /reason=role_controller_restored_runtime_permission/);
});

test('SEND_SMS revocation probe rejects a transient denial restored by RoleController on a later observation', () => {
  const { result, evidence } = runProbe({
    initiallyGranted: true,
    revokeStatus: 0,
    revokeLeavesGranted: false,
    restoreAfterDeniedChecks: 1
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /observable=false/, 'a single transient denied read must not qualify runtime revocation');
  assert.match(evidence, /denial_stability_observations=1/);
  assert.match(evidence, /reason=role_controller_restored_runtime_permission/);
});
