import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');

test('incoming emulator call is fully torn down before the outgoing probe starts', () => {
  const cancelIndex = flow.indexOf('adb emu gsm cancel "$FLOW_NUMBER"');
  const endedIndex = flow.indexOf('wait_text "Appel terminé"', cancelIndex);
  const endedCaptureIndex = flow.indexOf('capture 04-ended-call', endedIndex);
  const outgoingIndex = flow.indexOf('android.intent.action.DIAL -d tel:5550101', endedCaptureIndex);

  assert.ok(cancelIndex >= 0, 'incoming modem cancellation must be present');
  assert.ok(endedIndex > cancelIndex, 'the flow must observe the ended-call UI after cancellation');
  assert.ok(endedCaptureIndex > endedIndex, 'ended-call evidence must be captured');
  assert.ok(outgoingIndex > endedCaptureIndex, 'outgoing qualification must start only after teardown evidence');

  const transition = flow.slice(cancelIndex, outgoingIndex);
  assert.doesNotMatch(
    transition,
    /adb emu gsm cancel \"\$FLOW_NUMBER\"\s*\nsleep 1\b/,
    'a fixed sleep is not a valid Telecom teardown oracle'
  );
});
