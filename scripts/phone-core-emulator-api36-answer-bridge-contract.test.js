import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const flow = readFileSync(new URL('./phone-core-emulator-flow.sh', import.meta.url), 'utf8');
const helper = readFileSync(new URL('./phone-core-emulator-api37-answer-bridge.py', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = flow.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  const end = flow.indexOf('\n}', start);
  assert.notEqual(end, -1, `unterminated shell function ${name}`);
  return flow.slice(start, end + 2);
}

test('incoming-answer modem bridge is enabled for API 36+ and receives the actual API', () => {
  const bridge = shellFunction('start_api37_incoming_answer_transport_bridge');
  assert.match(
    bridge,
    /if \[\[ "\$FLOW_API" -lt 36 \]\]/,
    'API 36 exhibits the same synthetic GSM answer race and must use the causal transport bridge'
  );
  assert.match(
    bridge,
    /phone-core-emulator-api37-answer-bridge\.py[\s\S]*--api "\$FLOW_API"/,
    'the helper must receive the actual emulator API instead of hard-coding API 37 provenance'
  );
});

test('bridge helper records dynamic API provenance and rejects every unsupported API', () => {
  assert.match(
    helper,
    /add_argument\("--api", type=int, choices=\(36, 37\), required=True\)/,
    'the helper must whitelist only the qualified Android 16/17 API set'
  );
  assert.match(helper, /api=\{api\}/, 'transport evidence must use the API passed through the validated boundary');
  assert.doesNotMatch(
    helper,
    /api=37\b/,
    'transport evidence must never claim API 37 when the tested emulator is API 36'
  );
});

test('API 36 extension preserves strict causal answer qualification and private app truth', () => {
  for (const marker of [
    'ANSWER_REQUEST_MARKER',
    'REQUEST_ACCEPT_RE',
    'ANSWERED_RE',
    'ACTIVE_RE',
    'answered_same_call',
    'active_same_call',
  ]) {
    assert.match(helper, new RegExp(marker));
  }

  const startBridge = flow.indexOf('start_api37_incoming_answer_transport_bridge');
  const tapAnswer = flow.indexOf('tap_text "phone_core_answer"', startBridge);
  const waitBridge = flow.indexOf('wait_api37_incoming_answer_transport_bridge', tapAnswer);
  const privateActive = flow.indexOf('wait_private_timeline_event "INCOMING" "INCALL_ACTIVE"', waitBridge);

  assert.ok(startBridge >= 0 && startBridge < tapAnswer, 'bridge must be armed before Sentinel answers');
  assert.ok(tapAnswer < waitBridge, 'bridge completion must be checked only after Sentinel answer action');
  assert.ok(waitBridge < privateActive, 'transport ACTIVE must not replace Sentinel private INCALL_ACTIVE evidence');
});
