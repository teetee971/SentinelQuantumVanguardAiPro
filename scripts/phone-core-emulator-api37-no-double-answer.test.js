import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const flowSource = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
const bridgePath = 'scripts/phone-core-emulator-api37-answer-bridge.py';
const bridgeSource = fs.existsSync(bridgePath) ? fs.readFileSync(bridgePath, 'utf8') : '';

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

test('API 37 synchronizes the synthetic modem only after the same Telecom call is ANSWERED', () => {
  const answeredBlock = blockBetween(
    bridgeSource,
    'if accept_requested and not answered and call_id is not None:',
    'if synchronized and answered and not active and call_id is not None:'
  );

  const answeredGuard = answeredBlock.indexOf('answered_match.group(1) == call_id');
  const answeredState = answeredBlock.indexOf('answered = True');
  const api37Guard = answeredBlock.indexOf('api == 37', answeredState);
  const consoleAccept = answeredBlock.indexOf('send_console_accept', api37Guard);
  const syncMarker = answeredBlock.indexOf('transport_sync=emulator_console_gsm_accept_after_answered', consoleAccept);

  assert.ok(answeredGuard >= 0, 'ANSWERED must be correlated to the causal Telecom call');
  assert.ok(answeredState > answeredGuard, 'the bridge must observe ANSWERED before synchronizing transport');
  assert.ok(api37Guard > answeredState, 'API 37 synchronization must happen only after ANSWERED');
  assert.ok(consoleAccept > api37Guard, 'API 37 must then synchronize the emulator GSM transport');
  assert.ok(syncMarker > consoleAccept, 'the causal synchronization must be recorded as evidence');
});

test('the bridge requires ACTIVE on the same Telecom call that accepted the answer', () => {
  assert.match(bridgeSource, /call_id = accept_match\.group\(1\)/);
  assert.match(bridgeSource, /active_match\.group\(1\) == call_id/);
  assert.match(bridgeSource, /disconnected before ACTIVE/);
});
