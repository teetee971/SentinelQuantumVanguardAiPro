import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const flow = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = flow.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  const end = flow.indexOf('\n}', start);
  assert.notEqual(end, -1, `unterminated shell function ${name}`);
  return flow.slice(start, end + 2);
}

test('runtime revocation cannot be credited when pm revoke fails', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-revoke-status-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=false\nsleep(){ :; }\nadb(){ return 7; }\npermission_granted(){ return 1; }\n${shellFunction('probe_pm_revoke_send_sms')}\nif probe_pm_revoke_send_sms; then rc=0; else rc=$?; fi\nprintf '%s|%s\\n' "$rc" "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /1\|false/);
    const evidence = readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /pm_revoke_status=7/);
    assert.match(evidence, /reason=pm_revoke_command_failed/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('successful pm revoke plus denied state qualifies runtime revocation', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-revoke-success-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=false\nsleep(){ :; }\nadb(){ return 0; }\npermission_granted(){ return 1; }\n${shellFunction('probe_pm_revoke_send_sms')}\nprobe_pm_revoke_send_sms\nprintf '%s\\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /true/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('successful pm revoke followed by RoleController grant selects AppOp fallback', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-revoke-restored-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=true\nsleep(){ :; }\nadb(){ return 0; }\npermission_granted(){ return 0; }\n${shellFunction('probe_pm_revoke_send_sms')}\nprobe_pm_revoke_send_sms\nprintf '%s\\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /false/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

for (const [name, output, expected] of [
  ['uid allow overrides package ignore', 'Uid mode: SEND_SMS: allow\nSEND_SMS: ignore', 1],
  ['uid deny is effective', 'Uid mode: SEND_SMS: ignore\nSEND_SMS: allow', 0],
  ['legacy package deny with no uid mode is effective', 'SEND_SMS: ignore', 0],
  ['unknown AppOp output fails closed', 'No operations.', 1]
]) {
  test(`SEND_SMS AppOp oracle: ${name}`, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-appop-state-'));
    try {
      const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nadb(){ printf '%s\\n' "$APPOP_OUTPUT"; }\n${shellFunction('assert_send_sms_appop_denied')}\nassert_send_sms_appop_denied state.txt`;
      const result = spawnSync('bash', ['-c', script, 'test', dir], {
        encoding: 'utf8',
        env: { ...process.env, APPOP_OUTPUT: output }
      });
      assert.equal(result.status === 0 ? 0 : 1, expected, result.stderr || result.stdout);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}

test('executed flow proves a granted baseline before probing revocation', () => {
  const runtime = flow.slice(flow.indexOf('trap write_summary EXIT'));
  const beforeGrant = runtime.indexOf('assert_send_sms_runtime_permission_granted "send-sms-runtime-permission-granted-before-launch.txt"');
  const launch = runtime.indexOf('launch_sms_surface "sms-before-effective-denial-launch.txt"');
  const afterGrant = runtime.indexOf('assert_send_sms_runtime_permission_granted "send-sms-runtime-permission-granted-after-launch.txt"');
  const probe = runtime.indexOf('probe_pm_revoke_send_sms');
  assert.ok(beforeGrant >= 0 && beforeGrant < launch);
  assert.ok(launch < afterGrant && afterGrant < probe);
});
