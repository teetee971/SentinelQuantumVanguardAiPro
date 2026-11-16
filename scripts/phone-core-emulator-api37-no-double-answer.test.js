import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const flowSource = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const bridgePath = 'scripts/phone-core-emulator-api37-answer-bridge.py';
const bridgeSource = fs.existsSync(bridgePath) ? fs.readFileSync(bridgePath, 'utf8') : '';
const workflowSource = fs.readFileSync('.github/workflows/android-emulation-qualification.yml', 'utf8');
const instrumentationWorkflowSource = fs.readFileSync('.github/workflows/android-instrumentation.yml', 'utf8');
const composerSource = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

function blockBetween(source, startNeedle, endNeedle) {
  const start = source.indexOf(startNeedle);
  const end = source.indexOf(endNeedle, start);
  assert.ok(start >= 0, `missing start marker: ${startNeedle}`);
  assert.ok(end > start, `missing end marker: ${endNeedle}`);
  return source.slice(start, end);
}

test('the incoming flow arms the transport bridge before the answer tap', () => {
  const waitForNotification = flowSource.indexOf(
    'wait_private_timeline_event "INCOMING" "CALL_NOTIFICATION_POSTED"'
  );
  const armBridge = flowSource.indexOf(
    'start_api37_incoming_answer_transport_bridge',
    waitForNotification
  );
  const answerTap = flowSource.indexOf('tap_text "phone_core_answer"', armBridge);
  const waitForBridge = flowSource.indexOf(
    'wait_api37_incoming_answer_transport_bridge',
    answerTap
  );

  assert.ok(waitForNotification >= 0);
  assert.ok(armBridge > waitForNotification);
  assert.ok(answerTap > armBridge);
  assert.ok(waitForBridge > answerTap);
});

test('the incoming flow waits for ADB to return online before injecting gsm call', () => {
  const sleep = flowSource.indexOf('adb shell input keyevent KEYCODE_SLEEP');
  const onlineWait = flowSource.indexOf(
    'wait_for_online_adb "the sleep transition before the incoming gsm call"',
    sleep
  );
  const gsmCall = flowSource.indexOf('adb emu gsm call "$FLOW_NUMBER"', sleep);

  assert.ok(sleep >= 0);
  assert.ok(onlineWait > sleep, 'sleep transition must be followed by an online-state wait');
  assert.ok(gsmCall > onlineWait, 'synthetic call must be injected only after ADB is online');
});

test('API 37 never races REQUEST_ACCEPT with a host gsm accept', () => {
  const requestAcceptBlock = blockBetween(
    bridgeSource,
    'if answer_transaction is not None and not accept_requested:',
    'if accept_requested and not answered and call_id is not None:'
  );

  assert.match(requestAcceptBlock, /if api == 36:[\s\S]*send_console_accept/);
  assert.doesNotMatch(
    requestAcceptBlock,
    /api == 37[\s\S]*send_console_accept/,
    'API 37 must not mutate the synthetic modem while Telecom is still accepting the app answer'
  );
});

test('API 37 never turns Telecom ANSWERED into a host modem acceptance', () => {
  const answeredBlock = blockBetween(
    bridgeSource,
    'if accept_requested and not answered and call_id is not None:',
    'if synchronized and answered and not active and call_id is not None:'
  );

  const answeredGuard = answeredBlock.indexOf('answered_match.group(1) == call_id');
  const answeredState = answeredBlock.indexOf('answered = True');

  assert.ok(answeredGuard >= 0, 'ANSWERED must be correlated to the causal Telecom call');
  assert.ok(answeredState > answeredGuard, 'the bridge must observe ANSWERED on the causal call');
  assert.doesNotMatch(
    answeredBlock,
    /api == 37[\s\S]*send_console_accept/,
    'API 37 must not manufacture ACTIVE by accepting the synthetic modem after Telecom ANSWERED'
  );
  assert.doesNotMatch(
    answeredBlock,
    /emulator_console_gsm_accept_after_answered/,
    'API 37 evidence must never claim a host modem answer as app answer success'
  );
});

test('the bridge keeps the normal same-call ACTIVE path', () => {
  assert.match(bridgeSource, /call_id = accept_match\.group\(1\)/);
  assert.match(bridgeSource, /active_match\.group\(1\) == call_id/);
});

test('API 37 records the causal emulator limitation instead of accepting a replacement Telecom call', () => {
  assert.match(bridgeSource, /INCOMING_MISSED/);
  assert.match(bridgeSource, /emulator_limit=api37_incoming_answer_transaction_failed/);
  assert.doesNotMatch(bridgeSource, /UNKNOWN_CALL_RE/);
  assert.doesNotMatch(bridgeSource, /UNKNOWN_ACTIVE_RE/);
  assert.doesNotMatch(bridgeSource, /transport_handoff=android17_emulator_unknown_active/);
});

test('API 37 limited incoming-answer path is explicit and still tears down before later probes', () => {
  const answerTap = flowSource.indexOf('tap_text "phone_core_answer"');
  const bridgeWait = flowSource.indexOf('wait_api37_incoming_answer_transport_bridge', answerTap);
  const limitationCheck = flowSource.indexOf(
    'emulator_limit=api37_incoming_answer_transaction_failed',
    bridgeWait
  );
  const activeProof = flowSource.indexOf(
    'wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"',
    bridgeWait
  );
  const teardown = flowSource.indexOf('wait_emulator_call_absent "$FLOW_NUMBER"', bridgeWait);
  const outgoing = flowSource.indexOf('FLOW_OUTGOING_ACTIVE=0', bridgeWait);

  assert.ok(bridgeWait > answerTap);
  assert.ok(limitationCheck > bridgeWait, 'API 37 limitation must be explicitly recognized by the flow');
  assert.ok(activeProof > bridgeWait, 'the normal full-proof path must remain present');
  assert.ok(teardown > limitationCheck, 'limited incoming transport must still be torn down before continuing');
  assert.ok(outgoing > teardown, 'outgoing qualification must continue only after bounded teardown');
});

test('composer acquires the process submission gate only after its lifecycle scope starts', () => {
  const smsBlock = blockBetween(composerSource, 'fun submitSms(', 'fun submitMms(');
  const mmsBlock = blockBetween(composerSource, 'fun submitMms(', 'DisposableEffect(activationEpoch)');

  for (const [name, block] of [['SMS', smsBlock], ['MMS', mmsBlock]]) {
    const launch = block.indexOf('ioScope.launch {');
    const acquire = block.indexOf('if (!beginSubmission()) return@launch', launch);

    assert.ok(launch >= 0, `${name} submission must run in the lifecycle coroutine scope`);
    assert.ok(
      acquire > launch,
      `${name} must not acquire the process-wide gate before a cancelled lifecycle scope can start`
    );
  }
});

test('API 24 instrumentation enforces a hard wall-clock timeout instead of a detached watchdog', () => {
  const instrumentation = blockBetween(
    workflowSource,
    'if [[ "$API_LEVEL" == "24" ]]; then',
    '          else\n            timeout --signal=INT --kill-after=30s 900s'
  );

  assert.match(
    instrumentation,
    /timeout --signal=TERM --kill-after=30s 900s \\\n\s+adb shell am instrument -w -r "\$INSTRUMENTATION_RUNNER" > "\$INSTRUMENTATION_LOG_RAW" 2>&1/,
    'API 24 instrumentation must be bounded by a hard timeout owned by the foreground shell'
  );
  assert.doesNotMatch(instrumentation, /setsid adb shell am instrument/);
  assert.doesNotMatch(instrumentation, /INSTRUMENTATION_WATCHDOG_PID/);
});

test('legacy Android instrumentation keeps a bounded 30 minute connected-test watchdog', () => {
  assert.match(
    instrumentationWorkflowSource,
    /timeout --signal=INT --kill-after=30s 1800s \\\n\s+\.\/gradlew connectedDebugAndroidTest --stacktrace --no-daemon/,
    'legacy instrumentation must allow the cold connected-test lane to finish without removing its hard timeout'
  );
});

test('legacy Android instrumentation API 24 keeps a bounded 30 minute adb watchdog', () => {
  const api24 = blockBetween(
    instrumentationWorkflowSource,
    'if [[ "$API_LEVEL" == "24" ]]; then',
    '          else\n            timeout --signal=INT --kill-after=30s 1800s'
  );
  assert.match(
    api24,
    /timeout --signal=INT --kill-after=30s 1800s \\\n\s+adb shell am instrument -w -r "\$INSTRUMENTATION_RUNNER"/,
    'API 24 legacy instrumentation must retain a hard 30 minute adb watchdog'
  );
});

test('API 24 APK installs are hard-bounded and use non-streaming transport in both instrumentation workflows', () => {
  for (const [name, source] of [
    ['Phone Core qualification', workflowSource],
    ['Android instrumentation', instrumentationWorkflowSource]
  ]) {
    const api24 = blockBetween(
      source,
      'if [[ "$API_LEVEL" == "24" ]]; then',
      `          else\n            timeout --signal=INT --kill-after=30s ${name === 'Android instrumentation' ? 1800 : 900}s`
    );

    for (const apkVariable of ['APP_APK', 'TEST_APK']) {
      assert.match(
        api24,
        new RegExp(`timeout --signal=TERM --kill-after=30s 120s adb install --no-streaming -r "\\$${apkVariable}"`),
        `${name} must hard-bound ${apkVariable} installation and disable streamed ADB install on API 24`
      );
      assert.doesNotMatch(
        api24,
        new RegExp(`\\n\\s*adb install(?: --no-streaming)? -r "\\$${apkVariable}"`),
        `${name} must not leave ${apkVariable} installation unbounded`
      );
    }
  }
});

test('runtime APK installation is bounded and non-streaming on API 24 so a wedged ADB transport cannot consume the whole emulator job', () => {
  const runtime = blockBetween(
    workflowSource,
    '      - name: Run emulator application/runtime qualification',
    '      - name: Collect qualification evidence even after failure'
  );

  assert.match(
    runtime,
    /timeout --signal=TERM --kill-after=30s 120s adb install --no-streaming -r "\$APK_PATH"/,
    'runtime APK installation must have its own hard foreground timeout and legacy-safe API 24 transport'
  );
  assert.doesNotMatch(
    runtime,
    /\n\s*adb install(?: --no-streaming)? -r "\$APK_PATH"/,
    'runtime APK installation must never run unbounded'
  );
});
