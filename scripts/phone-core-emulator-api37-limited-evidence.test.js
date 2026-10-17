import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const workflow = readFileSync(new URL('../.github/workflows/android-emulation-qualification.yml', import.meta.url), 'utf8');
const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function reportCode() {
  const step = workflow.split('- name: Collect qualification evidence even after failure\n')[1];
  assert.ok(step, 'qualification evidence step must exist');
  return step.split("node <<'NODE'\n")[1].split('\n          NODE')[0];
}

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  return revocation.slice(start, revocation.indexOf('\n}', start) + 2);
}

function fixture({ limited = false, hostAccepted = false } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-api37-limited-'));
  const output = join(root, 'evidence');
  mkdirSync(output, { recursive: true });
  const put = (name, content) => writeFileSync(join(output, name), content);

  for (const name of [
    '01-dialer-first-launch', '01b-dialer-relaunch', '02-incoming-call', '05-outgoing-call',
    '06-thread', '07-inline-reply', '08-sms-effective-permission-denied', '10-sms-role-revoked',
    '11-dialer-role-revoked', '12-call-screening-role-revoked'
  ]) put(`${name}.png`, Buffer.alloc(300));
  if (!limited) put('03-incoming-active-evidence.png', Buffer.alloc(300));

  for (const name of ['sms-role-restored', 'dialer-role-restored', 'call-screening-role-restored']) {
    put(`${name}.txt`, 'com.sentinel.quantum\n');
  }
  put('logcat.txt', 'SentinelLifecycle: fixture only\n');
  put('logcat-status.txt', '0\n');
  put('package.txt', 'Package [com.sentinel.quantum]\n');
  put('apk.sha256', `${'d'.repeat(64)}  app-debug.apk\n`);
  put('call-screening-callback-logcat.txt', 'CallScreeningService:onScreenCall');
  put('phone-private-timeline-prefix.xml', 'CALL_SCREENED:ALLOW');
  put('phone-private-timeline-outgoing-incall_active.xml', 'INCALL_ACTIVE');
  put('phone-private-timeline-outgoing-sms_all_parts_sent.xml', 'SMS_ALL_PARTS_SENT');
  put('phone-private-timeline-outgoing-sms_all_parts_delivered.xml', 'SMS_ALL_PARTS_DELIVERED');
  if (!limited) put('phone-private-timeline-incoming-incall_active.xml', 'INCALL_ACTIVE');
  put('send-sms-appop-denied-after-launch.txt', 'Uid mode: SEND_SMS: ignore\n');
  put('revocation-summary.json', JSON.stringify({
    schema_version: 2,
    effective_permission_denial_fail_closed: true,
    role_revocation_fail_closed: true,
    effective_permission_probe: 'SEND_SMS_APP_OP_DENIED'
  }));

  if (limited) {
    const hostMutation = hostAccepted
      ? 'console_gsm_accept_after_answered_response=OK\ntransport_sync=emulator_console_gsm_accept_after_answered api=37\n'
      : '';
    put('api37-incoming-answer-limited.txt', [
      'CallSequencingController: answerCall: Beginning call sequencing transaction for answering incoming call. @abc',
      'Event: RecordEntry TC@1: REQUEST_ACCEPT @abc',
      'CallsManager: setCallState RINGING(RINGING) -> ANSWERED, call: [Call id=TC@1, handle=tel:*****00,] @abc',
      'CallsManager: setCallState ANSWERED(ANSWERED) -> DISCONNECTED, call: [Call id=TC@1, handle=tel:*****00,]',
      'Event: RecordEntry TC@1: SET_DISCONNECTED, Reason: (INCOMING_MISSED)',
      hostMutation.trim(),
      'emulator_limit=api37_incoming_answer_transaction_failed call_id=TC@1 transaction=abc'
    ].filter(Boolean).join('\n') + '\n');
  }

  const results = join(root, 'app/build/outputs/androidTest-results');
  mkdirSync(results, { recursive: true });
  const xml = [
    'AllStaticNavigationSurfacesInstrumentationTest',
    'StandaloneActivitySmokeInstrumentationTest',
    'PhoneCoreSetupResumeInstrumentationTest'
  ].map((name) => `<testcase classname="com.sentinel.quantum.${name}" name="fixture"/>`).join('');
  writeFileSync(join(results, 'TEST-fixture.xml'), `<testsuite>${xml}</testsuite>`);

  const sha = 'a'.repeat(40);
  const env = {
    ...process.env,
    RUNNER_TEMP: root,
    API_LEVEL: '37',
    OUTPUT: output,
    HOST_CONTRACT_RESULT: 'success',
    INSTRUMENTATION_OUTCOME: 'success',
    RUNTIME_OUTCOME: 'success',
    BUILT_COMMIT: sha,
    GITHUB_SHA: sha,
    SOURCE_HEAD_SHA: 'b'.repeat(40),
    SOURCE_BASE_SHA: 'c'.repeat(40),
    SOURCE_HEAD_REF: 'fixture-branch',
    GITHUB_EVENT_NAME: 'pull_request',
    GITHUB_REPOSITORY: 'teetee971/SentinelQuantumVanguardAiPro',
    GITHUB_RUN_ID: '1',
    GITHUB_RUN_ATTEMPT: '1'
  };

  try {
    const result = spawnSync(process.execPath, ['-e', reportCode()], {
      cwd: root,
      env,
      encoding: 'utf8'
    });
    assert.ok(existsSync(join(output, 'qualification.json')), result.stderr || result.stdout);
    return { result, report: JSON.parse(readFileSync(join(output, 'qualification.json'), 'utf8')) };
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('API 37 exact incoming-answer emulator limitation produces LIMITED while later evidence remains required', () => {
  const { result, report } = fixture({ limited: true });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(report.schema_version, 4);
  assert.equal(report.result, 'LIMITED');
  assert.equal(report.checks.incoming_call_telecom_flow, false);
  assert.equal(report.checks.api37_incoming_answer_emulator_limitation, true);
  assert.deepEqual(report.limitations, ['api37_incoming_answer_transaction_failed']);
  assert.equal(report.checks.outgoing_call_telecom_flow, true);
  assert.equal(report.checks.incoming_sms_and_inline_reply, true);
  assert.equal(report.physical_modem_claim, false);
  assert.equal(report.commercial_release_claim, false);
});

test('API 37 full causal ACTIVE evidence remains PASS rather than LIMITED', () => {
  const { result, report } = fixture();
  assert.equal(result.status, 0, result.stderr);
  assert.equal(report.result, 'PASS');
  assert.equal(report.checks.incoming_call_telecom_flow, true);
  assert.equal(report.checks.api37_incoming_answer_emulator_limitation, false);
  assert.deepEqual(report.limitations, []);
});

test('API 37 host modem acceptance cannot be certified as a limited app-answer observation', () => {
  const { result, report } = fixture({ limited: true, hostAccepted: true });
  assert.equal(result.status, 1);
  assert.equal(report.result, 'FAIL');
  assert.equal(report.checks.api37_incoming_answer_emulator_limitation, false);
  assert.ok(report.evidence_failures.includes('incoming_call_telecom_flow'));
});

test('API 37 UID SEND_SMS ignore remains authoritative when package mode is allow', () => {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-api37-appop-precedence-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nadb() {\n  if [[ "$*" == "shell appops get com.sentinel.quantum SEND_SMS" ]]; then\n    printf '%s\\n' 'Uid mode: SEND_SMS: ignore' 'SEND_SMS: allow; time=+1s'\n    return 0\n  fi\n  return 99\n}\n${shellFunction('assert_send_sms_appop_denied')}\nassert_send_sms_appop_denied precedence.txt`;
    const result = spawnSync('bash', ['-c', script, 'test', root], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.match(readFileSync(join(root, 'precedence.txt'), 'utf8'), /Uid mode: SEND_SMS: ignore/);
    assert.match(readFileSync(join(root, 'precedence.txt'), 'utf8'), /SEND_SMS: allow/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Phone Core evidence collection gives a transient offline emulator a bounded recovery window', () => {
  const evidence = workflow.split('- name: Collect qualification evidence even after failure\n')[1]
    ?.split('- name: Upload emulator qualification evidence\n')[0] ?? '';
  assert.match(evidence, /ADB_RECOVERY_READY=0/);
  assert.match(evidence, /for _ in \$\(seq 1 10\); do/);
  assert.match(evidence, /timeout 3s adb get-state[^\n]*grep -Fxq ['"]device['"]/);
  assert.match(evidence, /adb reconnect/);
  assert.match(evidence, /adb-recovery-status\.txt/);
  assert.ok(evidence.indexOf('ADB_RECOVERY_READY=0') < evidence.indexOf('adb logcat -d -v time'));
});
