import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync, mkdtempSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

// Host regressions exercise the real shell or report code with isolated fixtures.
// These fixtures are never Android, modem, or physical qualification evidence.
const workflow = readFileSync(new URL('../.github/workflows/android-emulation-qualification.yml', import.meta.url), 'utf8');
const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');
const runtimeFlow = readFileSync(new URL('./phone-core-emulator-flow.sh', import.meta.url), 'utf8');
function nodeCodeForStep(name) {
  const step = workflow.split(`- name: ${name}\n`)[1];
  assert.ok(step, `Workflow step exists: ${name}`);
  return step.split("node <<'NODE'\n")[1].split('\n          NODE')[0];
}
const reportCode = nodeCodeForStep('Collect qualification evidence even after failure');
const hostProvenanceCode = nodeCodeForStep('Record host evidence provenance');
const roleParser = new URL('./phone-core-emulator-role-holders.py', import.meta.url).pathname;

test('runtime setup installs after UTP cleanup, then rejects failed or unconfirmed data/log resets', () => {
  const runtime = workflow.split('- name: Run emulator application/runtime qualification\n')[1];
  const start = runtime.indexOf('          timeout --signal=INT --kill-after=30s 180s adb install -r');
  const end = runtime.indexOf('\n          if [[ "$API_LEVEL"', start);
  assert.ok(start >= 0 && end > start);
  const reset = runtime
    .slice(start, end)
    // The fixture stubs adb directly; the watchdog itself is covered by the workflow policy test.
    .replace('timeout --signal=INT --kill-after=30s 180s adb install -r "$APK_PATH"', 'adb install -r "$APK_PATH"')
    .replace('timeout --signal=INT --kill-after=5s 30s adb shell pm path com.sentinel.quantum', 'adb shell pm path com.sentinel.quantum');
  for (const [installStatus, dataStatus, dataOutput, logStatus, expected] of [
    [0, 0, 'Success', 0, 0], [1, 0, 'Success', 0, 1], [0, 1, 'Success', 0, 1], [0, 0, 'Failed', 0, 1], [0, 0, 'Success', 1, 1]
  ]) {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-runtime-reset-'));
    try {
      const fixture = join(dir, 'reset.sh');
      writeFileSync(fixture, `set -euo pipefail\nOUTPUT="$1"\nAPK_PATH=fixture.apk\nINSTALLED=0\nadb() {\n case "$*" in\n 'install -r fixture.apk') INSTALLED=1; echo INSTALL_EXECUTED; return ${installStatus};;\n 'shell pm path com.sentinel.quantum') if [[ "$INSTALLED" != 1 ]]; then return 1; fi; echo package:/data/app/com.sentinel.quantum/base.apk; return 0;;\n 'shell pm clear com.sentinel.quantum') if [[ "$INSTALLED" != 1 ]]; then echo Failed; return 1; fi; echo RESET_EXECUTED >&2; printf '%s\\n' '${dataOutput}'; return ${dataStatus};;\n 'logcat -c') return ${logStatus};;\n *) return 99;;\n esac\n}\n${reset}\necho FRESH_RUNTIME_SCOPE\n`, { mode: 0o600 });
      const result = spawnSync('bash', [fixture, dir], { encoding: 'utf8' });
      assert.equal(result.status, expected, result.stderr);
      assert.equal(result.stdout.includes('FRESH_RUNTIME_SCOPE'), expected === 0);
      assert.match(result.stdout, /INSTALL_EXECUTED/);
      if (installStatus !== 0) assert.equal(existsSync(join(dir, 'runtime-app-reset.txt')), false);
      if (installStatus !== 0 || dataStatus !== 0 || dataOutput !== 'Success') assert.equal(existsSync(join(dir, 'runtime-logcat-reset.txt')), false);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  }
  assert.doesNotMatch(runtime, /adb shell am start[^\n]*min-sdk-(?:first|second)-launch[^\n]*\|\| true/);
});

test('runtime qualification proves the exact package is installed through package manager state', () => {
  assert.match(workflow, /pm path com\.sentinel\.quantum/);
  assert.match(reportCode, /runtimePackagePathConfirmed/);
  assert.match(reportCode, /runtime_package_path/);
  assert.ok(reportCode.includes('^\\s*Package \\[com\\.sentinel\\.quantum\\]'));
});

test('incoming app surface does not require Android to expose the synthetic number in app UI', () => {
  const start = runtimeFlow.indexOf('wait_incoming_sentinel_surface() {');
  const end = runtimeFlow.indexOf('\n}\nwait_logcat_marker()', start);
  assert.ok(start >= 0 && end > start);
  const oracle = runtimeFlow.slice(start, end);
  assert.match(oracle, /sentinel_surface/);
  assert.doesNotMatch(oracle, /number_present/);
  assert.match(runtimeFlow, /wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"/);
  assert.match(runtimeFlow, /wait_text "phone_core_answer"/);
});

test('emulator qualification proves setup state across a real reboot before runtime reset', () => {
  const setupReboot = workflow.split('- name: Reboot emulator and verify interrupted setup resumes\n')[1]
    .split('- name: Run emulator application/runtime qualification\n')[0];
  assert.match(workflow, /id:\s*setup_reboot/);
  assert.match(workflow, /adb shell am instrument[\s\S]*PhoneCoreSetupRebootPreparationInstrumentationTest/);
  assert.match(workflow, /adb shell reboot/);
  assert.match(workflow, /timeout --signal=INT --kill-after=5s 30s \\\n\s+adb shell reboot/);
  assert.match(workflow, /ro\.build\.version\.sdk/);
  assert.equal((workflow.match(/^\s+wait_for_package_manager$/gm) || []).length, 2);
  assert.match(workflow, /cmd package list packages/);
  assert.match(workflow, /Can't find service: package/);
  assert.match(workflow, /run-as com\.sentinel\.quantum cat shared_prefs\/phone_core_setup_wizard_v2\.xml/);
  assert.match(workflow, /Configuration initiale/);
  assert.match(reportCode, /SETUP_REBOOT_OUTCOME/);
  assert.match(reportCode, /setupRebootObserved/);
  assert.match(reportCode, /sdkApiExact/);
  assert.match(reportCode, /sdk_api_exact/);
  assert.match(reportCode, /screeningLatencyObserved/);
  assert.match(reportCode, /screening_latency_observed/);
  assert.match(reportCode, /SCREENING_RESPONSE_BUDGET_MS = 450/);
  assert.match(reportCode, /screeningLatencyValues\.every\(\(elapsedMs\) =>/);
  assert.match(reportCode, /CallScreeningService:response_sent=true/);
  assert.doesNotMatch(setupReboot, /\.\/gradlew\s+:app:assembleDebug/);
  assert.match(setupReboot, /adb shell am start -W -n com\.sentinel\.quantum\/\.MainActivity/);
  assert.match(setupReboot, /setup-reboot-main-launch\.txt/);
});

test('setup reboot relaunches the known exported launcher component after package readiness', () => {
  const setupReboot = workflow.split('- name: Reboot emulator and verify interrupted setup resumes\n')[1]
    .split('- name: Run emulator application/runtime qualification\n')[0];
  const packageReady = setupReboot.indexOf('wait_for_package_manager');
  const forceStop = setupReboot.indexOf('adb shell am force-stop com.sentinel.quantum', packageReady);
  const relaunch = setupReboot.indexOf('adb shell am start -W -n com.sentinel.quantum/.MainActivity', forceStop);
  assert.ok(packageReady >= 0);
  assert.ok(forceStop > packageReady);
  assert.ok(relaunch > forceStop);
  assert.match(setupReboot, /for _ in \$\(seq 1 [3-9][0-9]\); do/);
  assert.match(setupReboot, /setup-reboot-main-launch\.txt/);
});

test('every synthetic runtime script bounds each ADB operation independently', () => {
  for (const script of [runtimeFlow, revocation]) {
    assert.match(script, /ADB_COMMAND_TIMEOUT_SECONDS="\$\{ADB_COMMAND_TIMEOUT_SECONDS:-30\}"/);
    assert.match(script, /ADB_COMMAND_KILL_GRACE_SECONDS="\$\{ADB_COMMAND_KILL_GRACE_SECONDS:-5\}"/);
    assert.match(script, /command timeout[\s\S]*adb "\$@"/);
    assert.match(script, /positive integer seconds/);
  }
});

test('runtime flow preserves the first unexpected shell failure as bounded evidence', () => {
  assert.match(runtimeFlow, /trap .*ERR/,
    'Phone Core runtime flow must trap unexpected shell failures');
  assert.match(runtimeFlow, /BASH_COMMAND/,
    'Phone Core runtime flow must record the failing shell command');
  assert.match(runtimeFlow, /FLOW_LAST_ADB_ARGS/,
    'Phone Core runtime flow must record the failing ADB arguments');
  assert.match(runtimeFlow, /failed_adb_args/,
    'Phone Core runtime flow must freeze ADB arguments before diagnostics run');
  assert.match(runtimeFlow, /FLOW_FAILURE_TRAP_SUSPENDED/,
    'Phone Core runtime flow must not treat intentionally handled ADB statuses as fatal');
  assert.match(runtimeFlow, /flow-failure\.txt/,
    'Phone Core runtime flow must persist failure diagnostics');
  assert.match(runtimeFlow, /Phone Core flow unexpected shell failure/,
    'Phone Core runtime flow must expose a non-sensitive failure summary');
  assert.match(runtimeFlow, /adb devices -l/,
    'Phone Core runtime flow must preserve ADB device diagnostics');
  assert.match(runtimeFlow, /adb logcat -d -v brief/,
    'Phone Core runtime flow must preserve logcat diagnostics');
});

test('runtime qualification exercises offline, rotation, kill/restart, and crash/ANR evidence', () => {
  assert.match(runtimeFlow, /run_stability_qualification\(\)/);
  assert.match(runtimeFlow, /svc wifi disable/);
  assert.match(runtimeFlow, /user_rotation/);
  assert.match(runtimeFlow, /pid_before=.*\n[\s\S]*adb shell am force-stop "\$FLOW_PACKAGE"/);
  assert.doesNotMatch(runtimeFlow, /kill -9/);
  assert.match(runtimeFlow, /FATAL EXCEPTION:|ANR in com\\\.sentinel\\\.quantum/);
  assert.match(runtimeFlow, /stability-offline/);
  assert.match(runtimeFlow, /stability-rotation/);
  assert.match(runtimeFlow, /stability-kill-restart/);
  assert.match(runtimeFlow, /stability-offline-launch\.txt[\s\S]*wait_for_online_adb "the offline qualification relaunch"/);
  assert.match(runtimeFlow, /stability-rotation-state\.txt[\s\S]*wait_for_online_adb "the rotation transition"/);
  assert.match(runtimeFlow, /am force-stop "\$FLOW_PACKAGE"[\s\S]*wait_for_online_adb "the process-death transition"/);
  assert.match(runtimeFlow, /stability-kill-restart-launch\.txt[\s\S]*wait_for_online_adb "the process restart"/);
  assert.match(runtimeFlow, /assert_no_crash_or_anr\(\) \{[\s\S]*wait_for_online_adb "the crash and ANR logcat capture"/);
});

test('runtime qualification restores the observed device state and fails closed on cleanup errors', () => {
  const restore = runtimeFlow.slice(
    runtimeFlow.indexOf('capture_original_device_state() {'),
    runtimeFlow.indexOf('\nrole_holders() {')
  );
  assert.match(restore, /ORIGINAL_USER_ROTATION/);
  assert.match(restore, /ORIGINAL_ACCELEROMETER_ROTATION/);
  assert.match(restore, /ORIGINAL_WIFI_ON/);
  assert.match(restore, /ORIGINAL_MOBILE_DATA/);
  assert.doesNotMatch(restore, /\|\| true/);
  assert.match(restore, /RESTORE_FAILED=true/);
  assert.match(restore, /status=\$\?/);
  assert.match(restore, /exit "\$status"/);
});

test('runtime qualification uses a version-tolerant mobile-data state oracle', () => {
  assert.match(runtimeFlow, /read_mobile_data_state\(\)/);
  assert.match(runtimeFlow, /cmd phone get-data-enabled/);
  assert.match(runtimeFlow, /settings get global mobile_data/);
  assert.match(runtimeFlow, /mobile_data_oracle/);
});

test('runtime qualification treats an unavailable mobile-data oracle as an explicit limitation', () => {
  assert.match(runtimeFlow, /UNAVAILABLE/);
  assert.match(runtimeFlow, /stability_verdict="LIMITED"/);
  assert.match(runtimeFlow, /ORIGINAL_MOBILE_DATA.*UNAVAILABLE/);
  assert.match(runtimeFlow, /if \[\[ "\$ORIGINAL_MOBILE_DATA" != UNAVAILABLE \]\]/);
});

test('runtime qualification opens the real incoming-call notification before requiring the app surface', () => {
  const screeningStart = runtimeFlow.indexOf('adb emu gsm call "$FLOW_NUMBER"');
  const incomingSurface = runtimeFlow.indexOf('wait_incoming_sentinel_surface', screeningStart);
  const notificationOpen = runtimeFlow.indexOf('open_incoming_call_notification', screeningStart);
  assert.ok(screeningStart >= 0);
  assert.ok(incomingSurface > notificationOpen);
});

test('incoming-call probe verifies ADB recovery after sleeping the emulator', () => {
  const call = runtimeFlow.indexOf('adb emu gsm call');
  const sleep = runtimeFlow.indexOf('KEYCODE_SLEEP', call);
  assert.ok(sleep >= 0);
  assert.ok(call < sleep);
  assert.match(runtimeFlow.slice(call, sleep), /CALL_NOTIFICATION_POSTED/);
  assert.match(runtimeFlow.slice(sleep), /wait_for_online_adb/);
  assert.match(runtimeFlow, /ADB device did not return online/);
  assert.match(runtimeFlow, /adb devices -l/);
});

test('incoming notification oracle uses the clickable row when compact CallStyle exposes only the app header', () => {
  const openStart = runtimeFlow.indexOf('open_incoming_call_notification() {');
  const openEnd = runtimeFlow.indexOf('\n}\n\nwait_reply_focus()', openStart);
  const notificationOpen = runtimeFlow.slice(openStart, openEnd);
  assert.match(notificationOpen, /row\.get\('bounds', ''\)/);
  assert.match(notificationOpen, /compact layout/);
  assert.match(notificationOpen, /row_center/);
  assert.match(notificationOpen, /node\.get\('resource-id'\) == 'android:id\/text'/);
});

test('runtime stability reports invalid original device state instead of exiting silently', () => {
  assert.match(runtimeFlow, /Invalid original user rotation state/);
  assert.match(runtimeFlow, /Invalid original accelerometer rotation state/);
  assert.match(runtimeFlow, /Invalid original Wi-Fi state/);
  assert.match(runtimeFlow, /Invalid original mobile-data state/);
  assert.match(runtimeFlow, /Failed to read original user rotation state/);
  assert.match(runtimeFlow, /Failed to read original accelerometer rotation state/);
  assert.match(runtimeFlow, /Failed to read original Wi-Fi state/);
  assert.match(runtimeFlow, /Failed to disable Wi-Fi for offline qualification/);
  assert.match(runtimeFlow, /Failed to disable mobile data for offline qualification/);
  assert.match(runtimeFlow, /Failed to read Wi-Fi state after offline transition/);
  assert.match(runtimeFlow, /Wi-Fi did not reach the disabled state/);
  assert.match(runtimeFlow, /mobile_data_disable_observed/);
  assert.match(runtimeFlow, /mobile_data_limitation/);
  assert.match(runtimeFlow, /stability_verdict="LIMITED"/);
  assert.match(runtimeFlow, /Unable to identify running process before restart/);
  assert.match(runtimeFlow, /Process remained alive after host force-stop/);
  assert.match(runtimeFlow, /Process did not restart after host force-stop/);
  assert.match(runtimeFlow, /read_process_ids\(\)/);
  assert.match(runtimeFlow, /Failed to restore user rotation state/);
  assert.match(runtimeFlow, /Failed to restore accelerometer rotation state/);
  assert.match(runtimeFlow, /Failed to restore Wi-Fi state/);
  assert.match(runtimeFlow, /Failed to restore mobile-data state/);
  assert.match(runtimeFlow, /restore_adb_diagnostics\(\)/);
  assert.match(runtimeFlow, /wait_for_restore_device\(\)/);
  assert.match(runtimeFlow, /state.*== device/);
  assert.match(runtimeFlow, /adb get-state/);
  assert.match(runtimeFlow, /restore_adb_devices=/);
  const stabilityStart = runtimeFlow.indexOf('run_stability_qualification() {');
  const stabilityEnd = runtimeFlow.indexOf('\n}\n\n# This is the first application launch', stabilityStart);
  const telecomProbe = runtimeFlow.indexOf('adb emu gsm call', stabilityEnd);
  assert.ok(stabilityStart >= 0 && stabilityEnd > stabilityStart && telecomProbe > stabilityEnd);
  const stability = runtimeFlow.slice(stabilityStart, stabilityEnd);
  assert.match(stability, /restore_device_state/);
  assert.match(stability, /FLOW_DEVICE_STATE_MUTATED=false/);
});

test('reboot preparation leaves process termination to the external workflow', () => {
  const source = readFileSync(new URL('../native-android-app/app/src/androidTest/java/com/sentinel/quantum/PhoneCoreSetupRebootPreparationInstrumentationTest.kt', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /executeShellCommand\("am force-stop/);
  const gate = spawnSync(
    process.execPath,
    [new URL('./check-phone-core-emulation-gate.js', import.meta.url).pathname],
    { cwd: new URL('..', import.meta.url).pathname, encoding: 'utf8' }
  );
  assert.equal(gate.status, 0, `${gate.stdout}\n${gate.stderr}`);
});

for (const [name, dump, status, holders] of [
  ['complete empty API 29 role', 'ROLE MANAGER STATE: { user_id=0 roles=[ {\nname=android.app.role.CALL_SCREENING\n}\n{\nname=android.app.role.SMS\nholders=com.sentinel.quantum\n} ] }', 0, ''],
  ['complete held API 29 role', 'ROLE MANAGER STATE: { user_id=0 roles=[ {\nname=android.app.role.CALL_SCREENING\nholders=com.sentinel.quantum\n} ] }', 0, 'com.sentinel.quantum'],
  ['compact empty role', 'RoleUserState { user_id=0 roleNameToPackageNames={android.app.role.CALL_SCREENING=[]} }', 0, ''],
  ['compact held role', 'RoleUserState { user_id=0 roleNameToPackageNames={android.app.role.CALL_SCREENING=[com.sentinel.quantum]} }', 0, 'com.sentinel.quantum'],
  ['missing role', 'ROLE MANAGER STATE: { user_id=0 roles=[ {\nname=android.app.role.SMS\nholders=com.sentinel.quantum\n} ] }', 1, ''],
  ['truncated role', 'ROLE MANAGER STATE: { user_id=0 roles=[ {\nname=android.app.role.CALL_SCREENING\n', 1, ''],
  ['diagnostic mentioning role', 'Error querying android.app.role.CALL_SCREENING', 1, ''],
  ['ambiguous multiple users', 'ROLE MANAGER STATE: { user_id=0 roles=[ {\nname=android.app.role.CALL_SCREENING\n} ] user_id=10 }', 1, ''],
  ['invalid holder field', 'ROLE MANAGER STATE: { user_id=0 roles=[ {\nname=android.app.role.CALL_SCREENING\nholders=Error querying package\n} ] }', 1, ''],
  ['compact key prefixed by another identifier', 'RoleUserState { user_id=0 roleNameToPackageNames={xandroid.app.role.CALL_SCREENING=[]} }', 1, ''],
  ['contradictory compact roles', 'RoleUserState { user_id=0 roleNameToPackageNames={android.app.role.CALL_SCREENING=[] android.app.role.CALL_SCREENING=[com.sentinel.quantum]} }', 1, ''],
  ['malformed nested holder brackets', 'RoleUserState { user_id=0 roleNameToPackageNames={android.app.role.CALL_SCREENING=[[[]]]} }', 1, '']
]) {
  test(`dumpsys oracle: ${name}`, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-role-dump-'));
    try {
      const file = join(dir, 'roles.txt');
      writeFileSync(file, dump);
      const result = spawnSync('python3', [roleParser, 'android.app.role.CALL_SCREENING', file], { encoding: 'utf8' });
      assert.equal(result.status, status, result.stderr);
      assert.equal(result.stdout.trim(), holders);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}

test('archived host provenance preserves exact build/source, base and branch provenance', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-host-provenance-'));
  try {
    const result = spawnSync(process.execPath, ['-e', hostProvenanceCode], { encoding: 'utf8', env: {
      ...process.env, RUNNER_TEMP: dir, BUILT_COMMIT: 'b'.repeat(40), GITHUB_SHA: 'a'.repeat(40),
      SOURCE_HEAD_SHA: 'b'.repeat(40), SOURCE_BASE_SHA: 'c'.repeat(40), SOURCE_HEAD_REF: 'fixture-branch'
    } });
    assert.equal(result.status, 0, result.stderr);
    const report = JSON.parse(readFileSync(join(dir, 'phone-core-host-evidence/provenance.json'), 'utf8'));
    assert.equal(report.built_commit, 'b'.repeat(40));
    assert.equal(report.workflow_commit, 'a'.repeat(40));
    assert.equal(report.source_head_sha, 'b'.repeat(40));
    assert.equal(report.source_base_sha, 'c'.repeat(40));
    assert.equal(report.source_head_ref, 'fixture-branch');
    assert.equal(report.qualification_scope, 'developer_qualification');
    assert.equal(report.rollout_mode, 'shadow');
    assert.equal(report.physical_validation, false);
    assert.equal(report.commercial_readiness, false);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('host provenance refuses a checkout different from the workflow commit', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-host-provenance-mismatch-'));
  try {
    const result = spawnSync(process.execPath, ['-e', hostProvenanceCode], { encoding: 'utf8', env: {
      ...process.env, RUNNER_TEMP: dir, BUILT_COMMIT: 'a'.repeat(40), GITHUB_SHA: 'b'.repeat(40)
    } });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Host checkout does not match/);
    assert.equal(existsSync(join(dir, 'phone-core-host-evidence/provenance.json')), false);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1);
  return revocation.slice(start, revocation.indexOf('\n}', start) + 2);
}

for (const [name, output, status, expected] of [
  ['oracle failure remains unknown', '', 2, 1],
  ['held role cannot be reported absent', 'com.sentinel.quantum', 0, 1],
  ['successful empty holder list proves absence', '', 0, 0],
  ['another package proves absence', 'com.sentinel.quantum.other', 0, 0]
]) {
  test(name, () => {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR=/tmp\nrole_holders() { printf '%s\\n' '${output}'; return ${status}; }\nsleep() { :; }\nadb() { :; }\n${shellFunction('wait_role_absent')}\nwait_role_absent android.app.role.SMS`;
    const result = spawnSync('bash', ['-c', script], { encoding: 'utf8' });
    assert.equal(result.status, expected, result.stderr);
    if (expected !== 0) {
      assert.match(result.stdout, status === 0 ? /last observation: held/ : /last observation: unknown/);
    }
  });
}

test('effective SEND_SMS denial and restore target the role-managed UID mode', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-appop-test-'));
  try {
    const result = spawnSync('bash', ['-c', `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nsleep() { :; }\nadb() { printf '%s\\n' "$*"; }\n${shellFunction('set_send_sms_appop')}\nset_send_sms_appop ignore deny.txt\nset_send_sms_appop allow restore.txt`, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(readFileSync(join(dir, 'deny.txt'), 'utf8'), /appops set --user 0 --uid com\.sentinel\.quantum SEND_SMS ignore/);
    assert.match(readFileSync(join(dir, 'restore.txt'), 'utf8'), /appops set --user 0 --uid com\.sentinel\.quantum SEND_SMS allow/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('UID denial with no observed effect falls back to package mode and restores both boundaries', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-appop-legacy-test-'));
  try {
    const result = spawnSync('bash', ['-c', `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nMODE=allow\nsleep() { :; }\nadb() {\n  printf '%s\\n' "$*" >> "$OUT_DIR/commands.txt"\n  if [[ "$*" == "shell appops get com.sentinel.quantum SEND_SMS" ]]; then printf 'SEND_SMS: %s\\n' "$MODE"; fi\n  if [[ "$*" == "shell appops set --user 0 com.sentinel.quantum SEND_SMS ignore" ]]; then MODE=ignore; fi\n  if [[ "$*" == "shell appops set --user 0 com.sentinel.quantum SEND_SMS allow" ]]; then MODE=allow; fi\n  return 0\n}\n${shellFunction('set_send_sms_appop')}\nset_send_sms_appop ignore deny.txt\nset_send_sms_appop allow restore.txt`, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    assert.match(readFileSync(join(dir, 'deny.txt'), 'utf8'), /SEND_SMS: ignore/);
    assert.match(readFileSync(join(dir, 'restore.txt'), 'utf8'), /SEND_SMS: allow/);
    const commands = readFileSync(join(dir, 'commands.txt'), 'utf8');
    assert.match(commands, /--uid com\.sentinel\.quantum SEND_SMS ignore/);
    assert.match(commands, /--user 0 com\.sentinel\.quantum SEND_SMS ignore/);
    assert.match(commands, /--uid com\.sentinel\.quantum SEND_SMS allow/);
    assert.match(commands, /--user 0 com\.sentinel\.quantum SEND_SMS allow/);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

function fixture(overrides = {}, alter = () => {}) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-report-test-'));
  const output = join(dir, 'evidence');
  mkdirSync(output);
  const put = (name, content) => writeFileSync(join(output, name), content);
  for (const name of ['01-dialer-first-launch', '01b-dialer-relaunch', '02-incoming-call', '03-incoming-active-evidence', '05-outgoing-call', '06-thread', '07-inline-reply', '08-sms-effective-permission-denied', '10-sms-role-revoked', '11-dialer-role-revoked', '12-call-screening-role-revoked']) put(name + '.png', Buffer.alloc(300));
  for (const name of ['sms-role-restored', 'dialer-role-restored', 'call-screening-role-restored']) put(name + '.txt', 'com.sentinel.quantum\n');
  put('logcat.txt', 'SentinelLifecycle: fixture only\n');
  put('logcat-status.txt', '0\n');
  put('package.txt', 'Package [com.sentinel.quantum]\n');
  put('runtime-package-path.txt', 'package:/data/app/com.sentinel.quantum/base.apk\n');
  put('apk.sha256', 'd'.repeat(64) + '  app-debug.apk\n');
  put('call-screening-callback-logcat.txt', 'CallScreeningService:onScreenCall');
  put(
    'call-screening-latency-logcat.txt',
    'CallScreeningService:response_elapsed_ms=12\nCallScreeningService:response_sent=true'
  );
  put('call-screening-response-sent-logcat.txt', 'CallScreeningService:response_sent=true');
  put('phone-private-timeline-prefix.xml', 'CALL_SCREENED:ALLOW');
  put('phone-private-timeline-outgoing-sms_all_parts_sent.xml', 'SMS_ALL_PARTS_SENT');
  put('phone-private-timeline-outgoing-sms_all_parts_delivered.xml', 'SMS_ALL_PARTS_DELIVERED');
  for (const direction of ['incoming', 'outgoing']) put(`phone-private-timeline-${direction}-incall_active.xml`, 'INCALL_ACTIVE');
  put('send-sms-appop-denied-after-launch.txt', 'Uid mode: SEND_SMS: ignore\n');
  put('setup-reboot-state-after-reboot.xml', '<string name="lifecycle_state_v1">IN_PROGRESS</string>\n<string name="attempted_target">DIALER_ROLE</string>\n<boolean name="completed" value="false" />\n');
  put('setup-reboot-sdk.txt', `${String(overrides.API_LEVEL ?? '36')}\n`);
  put('setup-reboot-ui.xml', '<node text="Configuration initiale" />\n');
  put('revocation-summary.json', JSON.stringify({ schema_version: 2, effective_permission_denial_fail_closed: true, role_revocation_fail_closed: true, effective_permission_probe: 'SEND_SMS_APP_OP_DENIED' }));
  const results = join(dir, 'app/build/outputs/androidTest-results');
  mkdirSync(results, { recursive: true });
  const xml = ['AllStaticNavigationSurfacesInstrumentationTest', 'StandaloneActivitySmokeInstrumentationTest', 'PhoneCoreSetupResumeInstrumentationTest'].map((name) => `<testcase classname="com.sentinel.quantum.${name}" name="fixture"/>`).join('');
  writeFileSync(join(results, 'TEST-fixture.xml'), `<testsuite>${xml}</testsuite>`);
  const sha = 'a'.repeat(40);
  const env = { ...process.env, RUNNER_TEMP: dir, API_LEVEL: '36', OUTPUT: output, HOST_CONTRACT_RESULT: 'success', INSTRUMENTATION_OUTCOME: 'success', RUNTIME_OUTCOME: 'success', SETUP_REBOOT_OUTCOME: 'success', BUILT_COMMIT: sha, GITHUB_SHA: sha, SOURCE_HEAD_SHA: sha, SOURCE_BASE_SHA: 'c'.repeat(40), SOURCE_HEAD_REF: 'fixture-branch', GITHUB_EVENT_NAME: 'pull_request', ...overrides };
  try {
    alter({ put, results, dir });
    const result = spawnSync(process.execPath, ['-e', reportCode], { cwd: dir, env, encoding: 'utf8' });
    assert.ok(existsSync(join(output, 'qualification.json')), result.stderr || result.stdout || 'report was not written');
    const report = JSON.parse(readFileSync(join(output, 'qualification.json'), 'utf8'));
    return { result, report };
  } finally { rmSync(dir, { recursive: true, force: true }); }
}

test('successful host fixture preserves exact build/source, base, and branch provenance', () => {
  const { result, report } = fixture();
  assert.equal(result.status, 0, result.stderr);
  assert.equal(report.result, 'PASS');
  assert.equal(report.build_commit, 'a'.repeat(40));
  assert.equal(report.source_head_commit, 'a'.repeat(40));
  assert.equal(report.source_base_commit, 'c'.repeat(40));
  assert.equal(report.source_head_ref, 'fixture-branch');
  assert.equal(report.apk_sha256, 'd'.repeat(64));
  assert.equal(report.rollout_mode, 'shadow');
  assert.equal(report.evidence_scope, 'developer_qualification');
  assert.equal(report.physical_modem_claim, false);
  assert.equal(report.commercial_release_claim, false);
});

test('screening latency without a successful Telecom response cannot qualify', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('call-screening-response-sent-logcat.txt', 'CallScreeningService:response_sent=false');
  });
  assert.equal(result.status, 1);
  assert.equal(report.checks.screening_latency_observed, false);
  assert.ok(report.evidence_failures.includes('screening_latency_observed'));
});

test('screening latency at the service budget cannot qualify', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put(
      'call-screening-latency-logcat.txt',
      'CallScreeningService:response_elapsed_ms=450\nCallScreeningService:response_sent=true'
    );
  });
  assert.equal(result.status, 1);
  assert.equal(report.checks.screening_latency_observed, false);
  assert.ok(report.evidence_failures.includes('screening_latency_observed'));
});

test('package lookup failure cannot masquerade as an installed application', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('package.txt', 'Unable to find package com.sentinel.quantum\n');
  });
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
  assert.match(report.evidence_failures.join('\n'), /package_state/);
});

test('dumpsys package metadata suffix still proves the exact installed package', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('package.txt', 'Package [com.sentinel.quantum] (8f3a1b2):\n');
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(report.evidence_failures.includes('package_state'), false);
});

test('runtime package path lookup failure cannot qualify the installed APK', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('runtime-package-path.txt', 'Error: package not found\n');
  });
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
  assert.equal(report.checks.runtime_package_path, false);
  assert.ok(report.evidence_failures.includes('runtime_package_path'));
});

test('qualification cannot pass when the persisted emulator SDK differs from the requested API', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('setup-reboot-sdk.txt', '35\n');
  });
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
  assert.equal(report.checks.sdk_api_exact, false);
  assert.ok(report.evidence_failures.includes('sdk_api_exact'));
});

for (const role of ['sms', 'dialer', 'call-screening']) {
  test(`a similarly named package cannot prove restored ${role} ownership`, () => {
    const { result, report } = fixture({}, ({ put }) => {
      put(`${role}-role-restored.txt`, 'com.sentinel.quantum.other\n');
    });
    assert.notEqual(result.status, 0);
    assert.equal(report.result, 'FAIL');
  });
}

test('shell diagnostics containing the package cannot prove restored role ownership', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('sms-role-restored.txt', 'Error: failed querying com.sentinel.quantum\n');
  });
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
});

test('partial logcat stdout with a failed read cannot prove absence of crashes', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('logcat-status.txt', '1\n');
    put('logcat-error.txt', 'adb: logcat read failed: device disconnected\n');
  });
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
  assert.equal(report.checks.no_crash_or_anr, false);
  assert.ok(report.evidence_failures.includes('no_crash_or_anr'));
});

test('missing logcat exit status cannot prove absence of crashes', () => {
  const { result, report } = fixture({}, ({ put }) => {
    put('logcat-status.txt', '');
  });
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
  assert.equal(report.checks.no_crash_or_anr, false);
});

function api24Evidence({ put, dir }, secondRenderStatus) {
  put('min-sdk-first-pid.txt', '100\n');
  put('min-sdk-second-pid.txt', '200\n');
  put('01-min-sdk-first-launch.png', Buffer.alloc(300));
  put('02-min-sdk-second-launch.png', Buffer.alloc(300));
  const renderClass = 'com.sentinel.quantum.ui.AllStaticNavigationSurfacesInstrumentationTest';
  const standaloneClass = 'com.sentinel.quantum.ui.StandaloneActivitySmokeInstrumentationTest';
  const event = (name, method, code) => `INSTRUMENTATION_STATUS: class=${name}\nINSTRUMENTATION_STATUS: test=${method}\nINSTRUMENTATION_STATUS_CODE: ${code}\n`;
  writeFileSync(join(dir, 'sentinel-instrumentation-api24-tests.log'),
    event(renderClass, 'homeRenders', 0) + event(renderClass, 'searchRenders', secondRenderStatus) +
    event(standaloneClass, 'activityRenders', 0) + 'OK (3 tests)\n');
}

test('API 24 raw instrumentation requires every render test to pass', () => {
  const { result, report } = fixture({ API_LEVEL: '24' }, (files) => api24Evidence(files, 0));
  assert.equal(result.status, 0, result.stderr);
  assert.equal(report.result, 'PASS');
  assert.equal(report.checks.all_static_navigation_surfaces_render, true);
});

test('API 24 passing render cannot hide an ignored test in the same class', () => {
  const { result, report } = fixture({ API_LEVEL: '24' }, (files) => api24Evidence(files, -3));
  assert.notEqual(result.status, 0);
  assert.equal(report.result, 'FAIL');
  assert.equal(report.checks.all_static_navigation_surfaces_render, false);
});

for (const [name, env, failure] of [
  ['failed runtime cannot use partial artifacts to pass', { RUNTIME_OUTCOME: 'failure' }, 'runtime_execution'],
  ['skipped runtime cannot qualify', { RUNTIME_OUTCOME: 'skipped' }, 'runtime_execution'],
  ['missing PR base invalidates provenance', { SOURCE_BASE_SHA: '' }, 'code_provenance'],
  ['build mismatch invalidates provenance', { BUILT_COMMIT: 'd'.repeat(40) }, 'code_provenance'],
  ['missing source branch invalidates provenance', { SOURCE_HEAD_REF: '' }, 'code_provenance']
]) {
  test(name, () => {
    const { result, report } = fixture(env);
    assert.equal(result.status, 1, result.stderr);
    assert.equal(report.result, 'FAIL');
    assert.ok(report.evidence_failures.includes(failure));
  });
}

test('background-thread crash is retained as failed evidence', () => {
  const { result, report } = fixture({}, ({ put }) => put('logcat.txt', 'FATAL EXCEPTION: pool-1-thread-1\n'));
  assert.equal(result.status, 1);
  assert.equal(report.checks.no_crash_or_anr, false);
});

test('a skipped named instrumentation class cannot qualify a rendered surface', () => {
  const { result, report } = fixture({}, ({ results }) => writeFileSync(join(results, 'TEST-fixture.xml'), '<testsuite><testcase classname="com.sentinel.quantum.AllStaticNavigationSurfacesInstrumentationTest"><skipped/></testcase></testsuite>'));
  assert.equal(result.status, 1);
  assert.equal(report.checks.all_static_navigation_surfaces_render, false);
});

test('evidence collection runs after failure and still writes a report', () => {
  assert.match(workflow, /name: Collect qualification evidence even after failure\s+if: always\(\)/);
  assert.match(workflow, /RUNTIME_OUTCOME: \$\{\{ steps\.runtime\.outcome \}\}/);
});

test('logcat oracle failure cannot become a zero-callback revocation proof', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-logcat-test-'));
  try {
    const result = spawnSync('bash', ['-c', `OUT_DIR="$1"\nadb() { return 1; }\n${shellFunction('screening_callback_count')}\nscreening_callback_count`, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 2, result.stderr);
    assert.equal(result.stdout, '');
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('missing APK digest cannot qualify provenance', () => {
  const { result, report } = fixture({}, ({ put }) => put('apk.sha256', ''));
  assert.equal(result.status, 1);
  assert.ok(report.evidence_failures.includes('apk_provenance'));
});

for (const [signal, file, check] of [
  ['SENT', 'phone-private-timeline-outgoing-sms_all_parts_sent.xml', 'sms_all_parts_sent_callback'],
  ['DELIVERED', 'phone-private-timeline-outgoing-sms_all_parts_delivered.xml', 'sms_all_parts_delivered_callback']
]) {
  test(`reply screenshots without Android ${signal} callback cannot qualify SMS`, () => {
    const { result, report } = fixture({}, ({ put }) => put(file, ''));
    assert.equal(result.status, 1);
    assert.equal(report.checks[check], false);
    assert.ok(report.evidence_failures.includes(check));
  });
}

test('revocation flow retains the preceding runtime logcat evidence', () => {
  assert.doesNotMatch(revocation, /adb logcat -c/);
});

for (const [label, output, status] of [
  ['readable zero callbacks', '', 0],
  ['readable callback invocation', 'CallScreeningService:onScreenCall', 0]
]) {
  test(label, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-logcat-test-'));
    try {
      const script = `OUT_DIR="$1"\nadb() { printf '%s\\n' '${output}'; return ${status}; }\n${shellFunction('screening_callback_count')}\nscreening_callback_count`;
      const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
      assert.equal(result.status, 0, result.stderr);
      assert.equal(result.stdout.trim(), output ? '1' : '0');
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}

for (const [label, modem, notificationCount, oracleStatus, expected] of [
  ['modem observation proves the incoming probe', '5550198', 1, 0, 0],
  ['new InCallService evidence proves a call when gsm list returns only OK', 'OK', 2, 0, 0],
  ['stale InCallService evidence cannot prove a new probe', 'OK', 1, 0, 1],
  ['unreadable InCallService evidence cannot prove a probe', 'OK', 0, 2, 2]
]) {
  test(label, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-call-probe-test-'));
    try {
      const script = `OUT_DIR="$1"\nINCOMING_NOTIFICATION_BEFORE=1\nsleep() { :; }\nadb() { printf '%s\\n' '${modem}'; }\ntimeline_signal_prefix_count() { printf '%s\\n' '${notificationCount}'; return ${oracleStatus}; }\n${shellFunction('wait_incoming_call_observed')}\nwait_incoming_call_observed 5550198 modem.txt`;
      const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
      assert.equal(result.status, expected, result.stderr);
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}
