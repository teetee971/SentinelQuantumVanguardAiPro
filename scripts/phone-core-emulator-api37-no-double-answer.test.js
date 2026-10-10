import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const source = fs.readFileSync('scripts/phone-core-emulator-api37-answer-bridge.py', 'utf8');

function blockBetween(startNeedle, endNeedle) {
  const start = source.indexOf(startNeedle);
  const end = source.indexOf(endNeedle, start);
  assert.ok(start >= 0, `missing start marker: ${startNeedle}`);
  assert.ok(end > start, `missing end marker: ${endNeedle}`);
  return source.slice(start, end);
}

test('API 37 never races the app answer with a host gsm accept at answer-transaction start', () => {
  const answerRequestBlock = blockBetween(
    'if answer_transaction is None and ANSWER_REQUEST_MARKER in line:',
    'if answer_transaction is not None and not accept_requested:',
  );

  assert.doesNotMatch(
    answerRequestBlock,
    /api == 37[\s\S]*send_console_accept/,
    'API 37 must let Telephony execute the causal answer; forcing gsm accept here can disconnect TC@1 and recreate transport as TC@2',
  );
  assert.doesNotMatch(answerRequestBlock, /transport_pre_sync=/);
});

test('API 36 keeps its explicit emulator synchronization only after causal REQUEST_ACCEPT', () => {
  const requestAcceptBlock = blockBetween(
    'if answer_transaction is not None and not accept_requested:',
    'if accept_requested and not answered and call_id is not None:',
  );

  assert.match(requestAcceptBlock, /if api == 36:[\s\S]*send_console_accept/);
  assert.match(requestAcceptBlock, /transport_sync=/);
});
