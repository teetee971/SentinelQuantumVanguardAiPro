import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const bridgeSource = fs.readFileSync(
  'scripts/phone-core-emulator-api37-answer-bridge.py',
  'utf8'
);

function blockBetween(source, startNeedle, endNeedle) {
  const start = source.indexOf(startNeedle);
  const end = source.indexOf(endNeedle, start);
  assert.ok(start >= 0, `missing start marker: ${startNeedle}`);
  assert.ok(end > start, `missing end marker: ${endNeedle}`);
  return source.slice(start, end);
}

test('API 36 hold recovery gets a fresh bounded observation window', () => {
  const heldBlock = blockBetween(
    bridgeSource,
    'held_match = HELD_RE.search(line)',
    'if answer_transaction and accept_requested and synchronized and answered and active:'
  );

  const recoveryAccept = heldBlock.indexOf('send_console_accept(');
  const recoveryDeadline = heldBlock.indexOf('deadline = time.monotonic() + timeout_s', recoveryAccept);
  const recoveryFlag = heldBlock.indexOf('recovery_attempted = True', recoveryAccept);

  assert.ok(recoveryAccept >= 0, 'API 36 ON_HOLD recovery must issue one bounded console accept');
  assert.ok(
    recoveryDeadline > recoveryAccept,
    'a successful API 36 hold recovery must start a fresh bounded observation window for same-call ACTIVE'
  );
  assert.ok(
    recoveryFlag > recoveryDeadline,
    'the fresh deadline must be armed before the recovery attempt is marked complete'
  );
});
