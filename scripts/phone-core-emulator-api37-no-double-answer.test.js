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

test('API 37 does not race the app answer with a host gsm accept', () => {
  const requestAcceptBlock = blockBetween(
    bridgeSource,
    'if answer_transaction is not None and not accept_requested:',
    'if accept_requested and not answered and call_id is not None:'
  );

  assert.match(requestAcceptBlock, /if api == 36:[\s\S]*send_console_accept/);
  assert.doesNotMatch(
    requestAcceptBlock,
    /api == 37[\s\S]*send_console_accept/,
    'API 37 must let Telecom execute the causal answer without a parallel host accept'
  );
});

test('the bridge requires ACTIVE on the same Telecom call that accepted the answer', () => {
  assert.match(bridgeSource, /call_id = accept_match\.group\(1\)/);
  assert.match(bridgeSource, /active_match\.group\(1\) == call_id/);
  assert.match(bridgeSource, /disconnected before ACTIVE/);
});
