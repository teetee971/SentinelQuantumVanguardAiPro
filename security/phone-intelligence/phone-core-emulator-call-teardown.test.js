import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const flow = fs.readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');

test('incoming emulator call is fully torn down before the outgoing probe starts', () => {
  const cancelIndex = flow.indexOf('adb emu gsm cancel "$FLOW_NUMBER"');
  const absentIndex = flow.indexOf('wait_emulator_call_absent "$FLOW_NUMBER"', cancelIndex);
  const endedCaptureIndex = flow.indexOf('capture 04-ended-call', absentIndex);
  const outgoingIndex = flow.indexOf('FLOW_OUTGOING_ACTIVE=0', endedCaptureIndex);

  assert.ok(cancelIndex >= 0, 'incoming modem cancellation must be present');
  assert.ok(absentIndex > cancelIndex, 'the emulator modem must prove the incoming call is absent');
  assert.ok(endedCaptureIndex > absentIndex, 'post-teardown visual evidence must be captured');
  assert.ok(outgoingIndex > endedCaptureIndex, 'outgoing qualification must start only after teardown evidence');

  const transition = flow.slice(cancelIndex, outgoingIndex);
  assert.doesNotMatch(
    transition,
    /wait_text \"Appel terminé\"/,
    'a localized UI label is not a Telecom teardown oracle'
  );
  assert.doesNotMatch(
    transition,
    /adb emu gsm cancel \"\$FLOW_NUMBER\"\s*\nsleep 1\b/,
    'a fixed sleep is not a valid Telecom teardown oracle'
  );
});

test('outgoing emulator qualification preserves fail-closed app policy and waits for runtime truth', () => {
  const retryIndex = flow.indexOf('FLOW_OUTGOING_ACTIVE=0');
  const dialIndex = flow.indexOf('android.intent.action.DIAL -d tel:5550101', retryIndex);
  const activeIndex = flow.indexOf('private_timeline_has_event "OUTGOING" "INCALL_ACTIVE"', dialIndex);
  const uiEvidenceIndex = flow.indexOf('wait_private_timeline_event "LOCAL" "INCALL_UI_SHOWN"', activeIndex);
  const hangupIndex = flow.indexOf('tap_text "Raccrocher"', uiEvidenceIndex);
  const modemEndedIndex = flow.indexOf('wait_emulator_call_absent 5550101', hangupIndex);

  assert.ok(retryIndex >= 0, 'outgoing recovery window must be explicit and bounded');
  assert.ok(dialIndex > retryIndex, 'Sentinel must originate the outgoing probe from its dialer');
  assert.ok(activeIndex > dialIndex, 'connected-state proof must come from the private runtime timeline');
  assert.ok(uiEvidenceIndex > activeIndex, 'app-owned in-call UI proof must remain independently required');
  assert.ok(hangupIndex > uiEvidenceIndex, 'the Sentinel hang-up control must still be exercised');
  assert.ok(modemEndedIndex > hangupIndex, 'the modem must prove that Sentinel hang-up ended the call');

  const outgoingSection = flow.slice(retryIndex, modemEndedIndex);
  assert.doesNotMatch(
    outgoingSection,
    /wait_text \"Composition\"|wait_text \"En communication\"/,
    'localized transient call-state labels must not gate functional qualification'
  );
});
