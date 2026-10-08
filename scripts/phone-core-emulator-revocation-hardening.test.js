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
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=false\nsleep(){ :; }\nadb(){ return 7; }\npermission_granted(){ return 0; }\n${shellFunction('probe_pm_revoke_send_sms')}\nif probe_pm_revoke_send_sms; then rc=0; else rc=$?; fi\nprintf '%s|%s\\n' "$rc" "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /0\|false/);
    const evidence = readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /baseline_granted=true/);
    assert.match(evidence, /pm_revoke_status=7/);
    assert.match(evidence, /reason=pm_revoke_command_failed/);
    assert.match(evidence, /transition_observed=false/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('successful pm revoke plus denied state qualifies runtime revocation', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-revoke-success-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=false\nPERMISSION_CHECKS=0\nsleep(){ :; }\nadb(){ return 0; }\npermission_granted(){ PERMISSION_CHECKS=$((PERMISSION_CHECKS + 1)); if [[ "$PERMISSION_CHECKS" -eq 1 ]]; then return 0; fi; return 1; }\n${shellFunction('probe_pm_revoke_send_sms')}\nprobe_pm_revoke_send_sms\nprintf '%s\\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /true/);
    const evidence = readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /baseline_granted=true/);
    assert.match(evidence, /denial_stability_observations=6/);
    assert.match(evidence, /transition_observed=true/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('successful pm revoke followed by RoleController grant marks runtime revocation unobservable', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-revoke-restored-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=true\nsleep(){ :; }\nadb(){ return 0; }\npermission_granted(){ return 0; }\n${shellFunction('probe_pm_revoke_send_sms')}\nprobe_pm_revoke_send_sms\nprintf '%s\\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /false/);
    const evidence = readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /reason=role_controller_restored_runtime_permission/);
    assert.match(evidence, /transition_observed=false/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('transient SEND_SMS denial restored on a later observation is never credited as revocation', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-revoke-transient-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nSEND_SMS_PM_REVOCATION_OBSERVABLE=true\nPERMISSION_CHECKS=0\nsleep(){ :; }\nadb(){ return 0; }\npermission_granted(){ PERMISSION_CHECKS=$((PERMISSION_CHECKS + 1)); if [[ "$PERMISSION_CHECKS" -eq 1 || "$PERMISSION_CHECKS" -ge 3 ]]; then return 0; fi; return 1; }\n${shellFunction('probe_pm_revoke_send_sms')}\nprobe_pm_revoke_send_sms\nprintf '%s\\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /false/);
    const evidence = readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /denial_stability_observations=1/);
    assert.match(evidence, /reason=role_controller_restored_runtime_permission/);
    assert.match(evidence, /transition_observed=false/);
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

test('AppOp setter writes both UID and package boundaries for persistent deny and symmetric restore', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-appop-setter-'));
  try {
    const script = `set -e\nPACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nsleep(){ :; }\nadb(){\n  printf '%s\\n' "$*" >> "$OUT_DIR/calls.txt"\n  if [[ "$*" == 'shell appops get com.sentinel.quantum SEND_SMS' ]]; then printf 'SEND_SMS: allow\\n'; fi\n  return 0\n}\n${shellFunction('set_send_sms_appop')}\nset_send_sms_appop ignore deny.txt\nset_send_sms_appop allow restore.txt`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    const calls = readFileSync(join(dir, 'calls.txt'), 'utf8');
    for (const expected of [
      'shell appops set --user 0 --uid com.sentinel.quantum SEND_SMS ignore',
      'shell appops set --user 0 com.sentinel.quantum SEND_SMS ignore',
      'shell appops set --user 0 --uid com.sentinel.quantum SEND_SMS allow',
      'shell appops set --user 0 com.sentinel.quantum SEND_SMS allow'
    ]) assert.match(calls, new RegExp(expected.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    assert.match(readFileSync(join(dir, 'deny.txt'), 'utf8'), /uid_set_status=0\npackage_set_status=0/);
    assert.match(readFileSync(join(dir, 'restore.txt'), 'utf8'), /uid_set_status=0\npackage_set_status=0/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('AppOp setter fails closed when persistent package boundary cannot be written', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-appop-persistence-fail-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nsleep(){ :; }\nadb(){\n  if [[ "$*" == 'shell appops set --user 0 com.sentinel.quantum SEND_SMS ignore' ]]; then return 9; fi\n  return 0\n}\n${shellFunction('set_send_sms_appop')}\nif set_send_sms_appop ignore deny.txt; then rc=0; else rc=$?; fi\nprintf '%s\\n' "$rc"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /^1\s*$/m);
    const evidence = readFileSync(join(dir, 'deny.txt'), 'utf8');
    assert.match(evidence, /package_set_status=9/);
    assert.match(evidence, /persistent SEND_SMS AppOp state cannot be established/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('AppOp setter tolerates missing UID command only when package persistence succeeds', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-appop-uid-fallback-'));
  try {
    const script = `set -e\nPACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nsleep(){ :; }\nadb(){\n  if [[ "$*" == 'shell appops set --user 0 --uid com.sentinel.quantum SEND_SMS ignore' ]]; then return 8; fi\n  if [[ "$*" == 'shell appops get com.sentinel.quantum SEND_SMS' ]]; then printf 'SEND_SMS: ignore\\n'; fi\n  return 0\n}\n${shellFunction('set_send_sms_appop')}\nset_send_sms_appop ignore deny.txt`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    const evidence = readFileSync(join(dir, 'deny.txt'), 'utf8');
    assert.match(evidence, /uid_set_status=8\npackage_set_status=0/);
    assert.match(evidence, /package boundary is the persistence fallback/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('executed flow proves a granted baseline before probing revocation', () => {
  const runtime = flow.slice(flow.indexOf('trap write_summary EXIT'));
  const beforeGrant = runtime.indexOf('assert_send_sms_runtime_permission_granted "send-sms-runtime-permission-granted-before-launch.txt"');
  const launch = runtime.indexOf('launch_sms_surface "sms-before-effective-denial-launch.txt"');
  const afterGrant = runtime.indexOf('assert_send_sms_runtime_permission_granted "send-sms-runtime-permission-granted-after-launch.txt"');
  const probe = runtime.indexOf('probe_pm_revoke_send_sms');
  assert.ok(beforeGrant >= 0 && beforeGrant < launch);
  assert.ok(launch < afterGrant && afterGrant < probe);
});

test('role removal retries one transient adb failure and only succeeds after absence is proven', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-role-remove-retry-'));
  try {
    const script = `set -e\nPACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nROLE_HELD=true\nREMOVE_ATTEMPTS=0\nsleep(){ :; }\nrole_holders(){ if [[ "$ROLE_HELD" == true ]]; then printf '%s\\n' "$PACKAGE"; fi; return 0; }\nadb(){\n  printf '%s\\n' "$*" >> "$OUT_DIR/calls.txt"\n  if [[ "$*" == 'shell cmd role remove-role-holder --user 0 android.app.role.SMS com.sentinel.quantum' ]]; then\n    REMOVE_ATTEMPTS=$((REMOVE_ATTEMPTS + 1))\n    if [[ "$REMOVE_ATTEMPTS" -eq 1 ]]; then return 1; fi\n    ROLE_HELD=false\n    return 0\n  fi\n  return 0\n}\n${shellFunction('wait_role_absent')}\n${shellFunction('remove_role_holder')}\nremove_role_holder android.app.role.SMS\nprintf 'attempts=%s\\n' "$REMOVE_ATTEMPTS"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /attempts=2/);
    const calls = readFileSync(join(dir, 'calls.txt'), 'utf8');
    assert.equal((calls.match(/remove-role-holder/g) || []).length, 2);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('executed revocation flow routes every role removal through the retrying boundary', () => {
  const runtime = flow.slice(flow.indexOf('trap write_summary EXIT'));
  assert.doesNotMatch(runtime, /adb shell cmd role remove-role-holder/);
  for (const role of ['android.app.role.SMS', 'android.app.role.DIALER', 'android.app.role.CALL_SCREENING']) {
    assert.match(runtime, new RegExp(`remove_role_holder ${role.replaceAll('.', '\\.')}`));
  }
});

test('screening callback oracle retries transient adb loss and accepts only a complete readable snapshot', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-screening-logcat-retry-'));
  try {
    const script = `set -e\nOUT_DIR="$1"\nCOUNT_FILE="$OUT_DIR/count"\nprintf '0\\n' > "$COUNT_FILE"\nsleep(){ :; }\nadb(){\n  count=$(cat "$COUNT_FILE")\n  count=$((count + 1))\n  printf '%s\\n' "$count" > "$COUNT_FILE"\n  if [[ "$count" -eq 1 ]]; then\n    printf 'partial logcat\\n'\n    printf 'transport reset\\n' >&2\n    return 7\n  fi\n  printf 'I/SentinelLifecycle: CallScreeningService:onScreenCall\\n'\n  return 0\n}\n${shellFunction('screening_callback_count')}\nscreening_callback_count before`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /^1\s*$/m);
    assert.equal(readFileSync(join(dir, 'count'), 'utf8').trim(), '2');
    assert.match(
      readFileSync(join(dir, 'screening-callback-count-before-attempt-1.err'), 'utf8'),
      /transport reset[\s\S]*adb_logcat_status=7/
    );
    assert.match(
      readFileSync(join(dir, 'screening-callback-count-before-logcat.txt'), 'utf8'),
      /CallScreeningService:onScreenCall/
    );
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('screening callback oracle still fails closed after all bounded adb retries fail', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-screening-logcat-fail-'));
  try {
    const script = `set -e\nOUT_DIR="$1"\nCOUNT_FILE="$OUT_DIR/count"\nprintf '0\\n' > "$COUNT_FILE"\nsleep(){ :; }\nadb(){\n  count=$(cat "$COUNT_FILE")\n  count=$((count + 1))\n  printf '%s\\n' "$count" > "$COUNT_FILE"\n  printf 'partial logcat\\n'\n  printf 'transport unavailable\\n' >&2\n  return 7\n}\n${shellFunction('screening_callback_count')}\nif screening_callback_count after; then rc=0; else rc=$?; fi\nprintf 'rc=%s\\n' "$rc"`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /^rc=2\s*$/m);
    assert.equal(readFileSync(join(dir, 'count'), 'utf8').trim(), '5');
    assert.match(result.stderr, /unreadable after bounded ADB retries/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('screening callback oracle treats a successful zero-match snapshot as a proven zero', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-screening-logcat-zero-'));
  try {
    const script = `set -e\nOUT_DIR="$1"\nsleep(){ :; }\nadb(){ printf 'I/Other: no screening callback\\n'; return 0; }\n${shellFunction('screening_callback_count')}\nscreening_callback_count zero`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(result.stdout, /^0\s*$/m);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('SEND_SMS permission evidence rejects missing, contradictory and unreadable snapshots in every oracle', () => {
  const functions = [
    'permission_state_from_snapshot',
    'permission_granted',
    'assert_send_sms_runtime_permission_granted',
    'archive_send_sms_runtime_permission_state',
    'assert_send_sms_runtime_permission_denied',
    'wait_send_sms_runtime_permission_denied'
  ].map(shellFunction).join('\n');

  for (const [label, snapshot, adbExit, expected] of [
    ['granted', 'android.permission.SEND_SMS: granted=true', 0, [0, 0, 1, 1, 0]],
    ['denied', 'android.permission.SEND_SMS: granted=false', 0, [1, 1, 0, 0, 0]],
    ['contradictory', 'android.permission.SEND_SMS: granted=true\nandroid.permission.SEND_SMS: granted=false', 0, [2, 1, 1, 1, 1]],
    ['missing', 'android.permission.READ_SMS: granted=false', 0, [2, 1, 1, 1, 1]],
    ['adb failure', 'android.permission.SEND_SMS: granted=false', 7, [2, 1, 1, 1, 1]]
  ]) {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-permission-snapshot-'));
    try {
      const script = [
        'PACKAGE=com.sentinel.quantum',
        'OUT_DIR="$1"',
        'sleep(){ :; }',
        'adb(){ printf "%s\\n" "$PERMISSION_DUMP"; return "$ADB_EXIT"; }',
        functions,
        'if permission_granted android.permission.SEND_SMS; then rc=0; else rc=$?; fi; printf "probe=%s\\n" "$rc"',
        'for fn in assert_send_sms_runtime_permission_granted archive_send_sms_runtime_permission_state assert_send_sms_runtime_permission_denied wait_send_sms_runtime_permission_denied; do',
        '  if "$fn" evidence.txt; then rc=0; else rc=$?; fi',
        '  printf "%s=%s\\n" "$fn" "$rc"',
        'done'
      ].join('\n');
      const result = spawnSync('bash', ['-c', script, 'test', dir], {
        encoding: 'utf8',
        env: { ...process.env, PERMISSION_DUMP: snapshot, ADB_EXIT: String(adbExit) }
      });
      assert.equal(result.status, 0, label + ': ' + result.stderr);
      const actual = result.stdout.match(/^(?:probe|assert_send_sms_runtime_permission_granted|archive_send_sms_runtime_permission_state|assert_send_sms_runtime_permission_denied|wait_send_sms_runtime_permission_denied)=(\d+)$/gm)
        ?.map(line => Number(line.split('=')[1]));
      assert.deepEqual(actual, expected, label + ': ' + result.stdout + '\n' + result.stderr);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  }
});
