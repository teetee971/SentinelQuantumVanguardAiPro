import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const workflow = readFileSync(new URL('../.github/workflows/android-emulation-qualification.yml', import.meta.url), 'utf8');
const step = workflow.split('- name: Collect qualification evidence even after failure\n')[1];
assert.ok(step, 'collector step exists');
const reportCode = step.split("node <<'NODE'\n")[1].split('\n          NODE')[0];

function fixture(alter = () => {}, apiLevel = '36') {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-schema3-report-'));
  const output = join(dir, 'evidence');
  mkdirSync(output);
  const put = (name, content) => writeFileSync(join(output, name), content);
  const remove = (name) => { const path = join(output, name); if (existsSync(path)) unlinkSync(path); };
  for (const name of [
    '01-dialer-first-launch', '01b-dialer-relaunch', '02-incoming-call', '03-incoming-active-evidence',
    '05-outgoing-call', '06-thread', '07-inline-reply', '08-sms-effective-permission-denied',
    '10-sms-role-revoked', '11-dialer-role-revoked', '12-call-screening-role-revoked'
  ]) put(name + '.png', Buffer.alloc(300));
  for (const name of ['sms-role-restored', 'dialer-role-restored', 'call-screening-role-restored']) {
    put(name + '.txt', 'com.sentinel.quantum\n');
  }
  put('logcat.txt', 'SentinelLifecycle: schema3 fixture\n');
  put('logcat-status.txt', '0\n');
  put('package.txt', 'Package [com.sentinel.quantum]\n');
  put('apk.sha256', 'd'.repeat(64) + '  app-debug.apk\n');
  put('call-screening-callback-logcat.txt', 'CallScreeningService:onScreenCall\n');
  put('phone-private-timeline-prefix.xml', 'CALL_SCREENED:ALLOW\n');
  put('phone-private-timeline-outgoing-sms_all_parts_sent.xml', 'SMS_ALL_PARTS_SENT\n');
  put('phone-private-timeline-outgoing-sms_all_parts_delivered.xml', 'SMS_ALL_PARTS_DELIVERED\n');
  put('phone-private-timeline-incoming-incall_active.xml', 'INCALL_ACTIVE\n');
  put('phone-private-timeline-outgoing-incall_active.xml', 'INCALL_ACTIVE\n');
  put('send-sms-appop-denied-after-launch.txt', 'Uid mode: SEND_SMS: ignore\n');
  put('revocation-summary.json', JSON.stringify({
    schema_version: 2,
    effective_permission_denial_fail_closed: true,
    role_revocation_fail_closed: true,
    effective_permission_probe: 'SEND_SMS_APP_OP_DENIED'
  }));

  const results = join(dir, 'app/build/outputs/androidTest-results');
  mkdirSync(results, { recursive: true });
  const xml = ['AllStaticNavigationSurfacesInstrumentationTest', 'StandaloneActivitySmokeInstrumentationTest', 'PhoneCoreSetupResumeInstrumentationTest']
    .map((name) => `<testcase classname="com.sentinel.quantum.${name}" name="fixture"/>`).join('');
  writeFileSync(join(results, 'TEST-fixture.xml'), `<testsuite>${xml}</testsuite>`);

  alter({ put, remove, output });
  const sha = 'a'.repeat(40);
  const result = spawnSync(process.execPath, ['-e', reportCode], {
    cwd: dir,
    encoding: 'utf8',
    env: {
      ...process.env,
      RUNNER_TEMP: dir,
      API_LEVEL: apiLevel,
      OUTPUT: output,
      HOST_CONTRACT_RESULT: 'success',
      INSTRUMENTATION_OUTCOME: 'success',
      RUNTIME_OUTCOME: 'success',
      BUILT_COMMIT: sha,
      GITHUB_SHA: sha,
      SOURCE_HEAD_SHA: 'b'.repeat(40),
      SOURCE_BASE_SHA: 'c'.repeat(40),
      SOURCE_HEAD_REF: 'fixture-branch',
      GITHUB_EVENT_NAME: 'pull_request'
    }
  });
  assert.ok(existsSync(join(output, 'qualification.json')), result.stderr || result.stdout);
  const report = JSON.parse(readFileSync(join(output, 'qualification.json'), 'utf8'));
  return { dir, result, report };
}

function schema3(probe) {
  return JSON.stringify({
    schema_version: 3,
    effective_permission_denial_fail_closed: true,
    role_revocation_fail_closed: true,
    effective_permission_probe: probe
  });
}

function schema4(probe) {
  return JSON.stringify({
    schema_version: 4,
    effective_permission_denial_fail_closed: true,
    role_revocation_fail_closed: true,
    effective_permission_probe: probe
  });
}

function schema5() {
  return JSON.stringify({
    schema_version: 5,
    sms_authorization_denial_fail_closed: true,
    role_revocation_fail_closed: true,
    sms_authorization_probe: 'SEND_SMS_ROLE_AUTHORIZATION_REVOKED'
  });
}

function putSchema5Boundary(put, rawState = 'android.permission.SEND_SMS: granted=true, flags=[ GRANTED_BY_ROLE ]\n') {
  const absent = 'role=android.app.role.SMS\npackage=com.sentinel.quantum\nabsent=true\n';
  put('send-sms-role-managed-role-absent-state.txt', absent);
  put('send-sms-role-managed-permission-state.txt', rawState);
  put('send-sms-role-managed-role-absent-after-launch.txt', absent);
  put('send-sms-role-managed-permission-state-after-launch.txt', rawState);
}

function expectPass(alter, apiLevel = '36') {
  const run = fixture(alter, apiLevel);
  try {
    assert.equal(run.result.status, 0, run.result.stderr);
    assert.equal(run.report.result, 'PASS');
    assert.equal(run.report.checks.sms_authorization_denial_fail_closed, true);
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
}

function expectDenialFail(alter, apiLevel = '36') {
  const run = fixture(alter, apiLevel);
  try {
    assert.notEqual(run.result.status, 0);
    assert.equal(run.report.result, 'FAIL');
    assert.equal(run.report.checks.sms_authorization_denial_fail_closed, false);
    assert.ok(run.report.evidence_failures.includes('sms_authorization_denial_fail_closed'));
  } finally { rmSync(run.dir, { recursive: true, force: true }); }
}

function expectAuthorizationPass(alter, apiLevel = '36') {
  expectPass(alter, apiLevel);
}

function expectAuthorizationFail(alter, apiLevel = '36') {
  expectDenialFail(alter, apiLevel);
}

test('schema 2 AppOp evidence remains backward compatible', () => expectPass(() => {}));

test('schema 3 accepts runtime permission denial with matching evidence', () => expectPass(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  put('send-sms-runtime-permission-denied-after-launch.txt', 'android.permission.SEND_SMS: granted=false\n');
  put('revocation-summary.json', schema3('SEND_SMS_RUNTIME_PERMISSION_REVOKED'));
}));

test('schema 3 accepts AppOp denial with matching evidence', () => expectPass(({ put }) => {
  put('revocation-summary.json', schema3('SEND_SMS_APP_OP_DENIED'));
}));

test('schema 4 accepts historical role-managed SEND_SMS permission loss with role-absence evidence on modern API', () => expectPass(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  put('send-sms-role-managed-permission-denied-after-launch.txt', 'android.permission.SEND_SMS: granted=false\n');
  put('send-sms-role-managed-role-absent-after-launch.txt', 'role=android.app.role.SMS\npackage=com.sentinel.quantum\nabsent=true\n');
  put('revocation-summary.json', schema4('SEND_SMS_ROLE_MANAGED_PERMISSION_REVOKED'));
}));

test('schema 4 rejects role-managed proof when the permission-denial evidence is missing', () => expectDenialFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('send-sms-role-managed-permission-denied-after-launch.txt');
  put('send-sms-role-managed-role-absent-after-launch.txt', 'role=android.app.role.SMS\npackage=com.sentinel.quantum\nabsent=true\n');
  put('revocation-summary.json', schema4('SEND_SMS_ROLE_MANAGED_PERMISSION_REVOKED'));
}));

test('schema 4 rejects role-managed proof when role-absence evidence is missing', () => expectDenialFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  put('send-sms-role-managed-permission-denied-after-launch.txt', 'android.permission.SEND_SMS: granted=false\n');
  remove('send-sms-role-managed-role-absent-after-launch.txt');
  put('revocation-summary.json', schema4('SEND_SMS_ROLE_MANAGED_PERMISSION_REVOKED'));
}));

test('schema 4 role-managed proof is forbidden below API 36', () => expectDenialFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  put('send-sms-role-managed-permission-denied-after-launch.txt', 'android.permission.SEND_SMS: granted=false\n');
  put('send-sms-role-managed-role-absent-after-launch.txt', 'role=android.app.role.SMS\npackage=com.sentinel.quantum\nabsent=true\n');
  put('revocation-summary.json', schema4('SEND_SMS_ROLE_MANAGED_PERMISSION_REVOKED'));
}, '29'));

test('schema 5 accepts role-based authorization denial when Android keeps SEND_SMS granted by role metadata', () => expectAuthorizationPass(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  put('revocation-summary.json', schema5());
}));

test('schema 5 also accepts role-based authorization denial when the raw permission happens to be revoked', () => expectAuthorizationPass(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put, 'android.permission.SEND_SMS: granted=false\n');
  put('revocation-summary.json', schema5());
}));

test('schema 5 rejects authorization proof without pre-launch role absence', () => expectAuthorizationFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  remove('send-sms-role-managed-role-absent-state.txt');
  put('revocation-summary.json', schema5());
}));

test('schema 5 rejects authorization proof without post-launch role absence', () => expectAuthorizationFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  remove('send-sms-role-managed-role-absent-after-launch.txt');
  put('revocation-summary.json', schema5());
}));

test('schema 5 rejects authorization proof without pre-launch raw permission state', () => expectAuthorizationFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  remove('send-sms-role-managed-permission-state.txt');
  put('revocation-summary.json', schema5());
}));

test('schema 5 rejects authorization proof without post-launch raw permission state', () => expectAuthorizationFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  remove('send-sms-role-managed-permission-state-after-launch.txt');
  put('revocation-summary.json', schema5());
}));

test('schema 5 rejects ambiguous raw SEND_SMS permission evidence', () => expectAuthorizationFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  put('send-sms-role-managed-permission-state-after-launch.txt', 'permission state unavailable\n');
  put('revocation-summary.json', schema5());
}));

test('schema 5 role-authorization proof is forbidden below API 36', () => expectAuthorizationFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('08-sms-effective-permission-denied.png');
  put('08-sms-authorization-denied.png', Buffer.alloc(300));
  putSchema5Boundary(put);
  put('revocation-summary.json', schema5());
}, '29'));

test('runtime probe rejects missing runtime-denial evidence', () => expectDenialFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  remove('send-sms-runtime-permission-denied-after-launch.txt');
  put('revocation-summary.json', schema3('SEND_SMS_RUNTIME_PERMISSION_REVOKED'));
}));

test('runtime probe rejects granted=true evidence', () => expectDenialFail(({ put, remove }) => {
  remove('send-sms-appop-denied-after-launch.txt');
  put('send-sms-runtime-permission-denied-after-launch.txt', 'android.permission.SEND_SMS: granted=true\n');
  put('revocation-summary.json', schema3('SEND_SMS_RUNTIME_PERMISSION_REVOKED'));
}));

test('runtime probe cannot be satisfied by mismatched AppOp evidence', () => expectDenialFail(({ put, remove }) => {
  remove('send-sms-runtime-permission-denied-after-launch.txt');
  put('send-sms-appop-denied-after-launch.txt', 'Uid mode: SEND_SMS: ignore\n');
  put('revocation-summary.json', schema3('SEND_SMS_RUNTIME_PERMISSION_REVOKED'));
}));

test('unknown SMS authorization probe is rejected', () => expectDenialFail(({ put }) => {
  put('revocation-summary.json', schema3('UNKNOWN_PROBE'));
}));

test('unsupported revocation evidence schema is rejected', () => expectDenialFail(({ put }) => {
  put('revocation-summary.json', JSON.stringify({
    schema_version: 6,
    effective_permission_denial_fail_closed: true,
    role_revocation_fail_closed: true,
    effective_permission_probe: 'SEND_SMS_APP_OP_DENIED'
  }));
}));

test('missing revocation summary is rejected', () => expectDenialFail(({ remove }) => {
  remove('revocation-summary.json');
}));