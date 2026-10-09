import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const coordinator = readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/SmsActivationStateCoordinator.kt', 'utf8');
const composer = readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt', 'utf8');
const smsSender = readFileSync('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelSmsSender.kt', 'utf8');
const flow = readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const revocationFlow = readFileSync('scripts/phone-core-emulator-revocation-flow.sh', 'utf8');
const answerBridge = readFileSync('scripts/phone-core-emulator-api37-answer-bridge.py', 'utf8');
const crashOracle = path.resolve('scripts/phone-core-logcat-crash-oracle.py');

function extractFunction(source, name, nextName) {
  const start = source.indexOf(`${name}() {`);
  const end = source.indexOf(`\n${nextName}() {`, start);
  assert.ok(start >= 0, `${name} must exist`);
  assert.ok(end > start, `${name} must end before ${nextName}`);
  return source.slice(start, end);
}

function runCrashOracle(lines) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'sentinel-crash-oracle-'));
  const file = path.join(dir, 'logcat.txt');
  writeFileSync(file, `${lines.join('\n')}\n`);
  const result = spawnSync('python3', [crashOracle, file], { encoding: 'utf8' });
  rmSync(dir, { recursive: true, force: true });
  return result;
}

test('crash oracle fails closed when a second FATAL arrives before the first is attributed', () => {
  const result = runCrashOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: first',
    'E/AndroidRuntime: FATAL EXCEPTION: second',
    'E/AndroidRuntime: Process: com.android.systemui, PID: 200',
    'E/AndroidRuntime: Process: com.sentinel.quantum, PID: 100',
  ]);
  assert.notEqual(result.status, 0, result.stderr || result.stdout);
});

test('crash oracle rejects a truncated fatal followed by an unrelated attributed crash', () => {
  const result = runCrashOracle([
    'E/AndroidRuntime: FATAL EXCEPTION: first',
    'E/AndroidRuntime: FATAL EXCEPTION: unrelated',
    'E/AndroidRuntime: Process: com.android.systemui, PID: 200',
  ]);
  assert.notEqual(result.status, 0, result.stderr || result.stdout);
});

test('SMS authorization refresh does not recreate the composer and discard non-saveable send state', () => {
  assert.doesNotMatch(coordinator, /activity\.recreate\(\)/);
  assert.match(coordinator, /activity\.refreshActivationAuthorization\(\)/);
  assert.match(composer, /fun refreshActivationAuthorization\(\)/);
  assert.match(composer, /activationEpochState/);
});

test('SMS and MMS composer submissions share an idempotent in-flight gate', () => {
  assert.match(composer, /val submissionGate = remember \{ SmsSubmissionGate\(\) \}/);
  assert.equal((composer.match(/submissionGate\.tryAcquire\(\)/g) || []).length, 2);
  assert.match(composer, /var submissionInFlight by remember \{ mutableStateOf\(false\) \}/);
  assert.match(composer, /&& !submissionInFlight/);
  assert.match(composer, /permit\.release\(\)/);
});

test('SMS sender revalidates authorization after callback preparation and immediately before telephony submission', () => {
  const callbackBoundary = smsSender.indexOf('PreparedCallbacks(sendToken, sent, delivered)');
  const revalidation = smsSender.indexOf('revalidateBeforeSubmission(prepared.subscriptionId)', callbackBoundary);
  const textSend = smsSender.indexOf('prepared.manager.sendTextMessage(', callbackBoundary);
  const multipartSend = smsSender.indexOf('prepared.manager.sendMultipartTextMessage(', callbackBoundary);

  assert.ok(callbackBoundary >= 0, 'callback preparation boundary must exist');
  assert.ok(revalidation > callbackBoundary, 'authorization must be revalidated after callbacks are prepared');
  assert.ok(textSend > revalidation, 'single-part SmsManager submission must occur after revalidation');
  assert.ok(multipartSend > revalidation, 'multipart SmsManager submission must occur after revalidation');
  assert.match(smsSender.slice(revalidation, textSend), /markOutgoingFailed\(persistedMessageId\)/);
});

test('incoming answer bridge must prove its log stream is armed before the answer tap is allowed', () => {
  const startBridge = extractFunction(flow, 'start_api37_incoming_answer_transport_bridge', 'wait_api37_incoming_answer_transport_bridge');
  assert.match(startBridge, /ready_file=/);
  assert.match(startBridge, /--ready-file "\$ready_file"/);
  assert.match(startBridge, /bridge_ready=1/);
  assert.match(startBridge, /kill -0 "\$FLOW_INCOMING_TRANSPORT_PID"/);

  const start = flow.indexOf('start_api37_incoming_answer_transport_bridge');
  const tap = flow.indexOf('tap_text "phone_core_answer"', start);
  assert.ok(start >= 0 && tap > start, 'bridge readiness must complete before the answer tap');
});

test('incoming answer bridge gives the causal Telecom transaction a fresh timeout budget', () => {
  const marker = answerBridge.indexOf('if answer_transaction is None and ANSWER_REQUEST_MARKER in line:');
  const token = answerBridge.indexOf('answer_transaction = observed_transaction', marker);
  const nextDeadline = answerBridge.indexOf('deadline = time.monotonic() + timeout_s', token);
  const requestAccept = answerBridge.indexOf('if answer_transaction is not None and not accept_requested:', token);
  assert.ok(marker >= 0 && token > marker, 'causal answer marker must establish a transaction token');
  assert.ok(nextDeadline > token, 'causal answer transaction must reset the transport timeout budget');
  assert.ok(nextDeadline < requestAccept, 'timeout reset must happen before waiting for REQUEST_ACCEPT/ANSWERED/ACTIVE');
});

test('foreground-return oracle accepts modern resumed-activity fields but remains fail-closed', () => {
  const fn = extractFunction(revocationFlow, 'wait_app_backgrounded', 'timeline_signal_prefix_count');
  assert.match(fn, /resumed_line=/);
  assert.match(fn, /mResumedActivity:/);
  assert.match(fn, /topResumedActivity=/);
  assert.match(fn, /ResumedActivity:/);
  assert.match(fn, /null/);
  assert.match(fn, /-z "\$resumed_line"/);
  assert.match(fn, /com\.sentinel\.quantum/);
  assert.match(fn, /ActivityRecord/);
  assert.match(fn, /return 0/);
});

test('SEND_SMS revoke probe refuses pm revoke unless it proves a granted baseline itself', () => {
  const fn = extractFunction(revocationFlow, 'probe_pm_revoke_send_sms', 'assert_send_sms_runtime_permission_denied');
  assert.match(fn, /permission_granted android\.permission\.SEND_SMS/);
  assert.match(fn, /baseline_granted=false/);
  assert.match(fn, /baseline_grant_not_proven/);

  const dir = mkdtempSync(path.join(os.tmpdir(), 'sentinel-send-sms-baseline-'));
  try {
    const script = `
set -euo pipefail
PACKAGE=com.sentinel.quantum
OUT_DIR="$1"
SEND_SMS_PM_REVOCATION_OBSERVABLE=true
sleep(){ :; }
permission_granted(){ return 1; }
adb(){
  if [[ "$*" == 'shell pm revoke com.sentinel.quantum android.permission.SEND_SMS' ]]; then
    printf 'called\n' > "$OUT_DIR/revoke-called.txt"
    return 0
  fi
  return 0
}
${fn}
probe_pm_revoke_send_sms
printf 'observable=%s\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"
`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.equal(existsSync(path.join(dir, 'revoke-called.txt')), false, 'pm revoke must not execute without a granted baseline');
    assert.match(result.stdout, /observable=false/);
    const evidence = readFileSync(path.join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /baseline_granted=false/);
    assert.match(evidence, /reason=baseline_grant_not_proven/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
