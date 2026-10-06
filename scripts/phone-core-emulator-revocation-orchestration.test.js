import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const flow = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

test('hardened preflight is mandatory before delegated revocation scenario', () => {
  assert.match(flow, /PRECHECK=.*phone-core-emulator-sms-denial-preflight\.sh/);
  assert.match(flow, /IMPLEMENTATION=.*phone-core-emulator-revocation-impl\.sh/);
  const contract = flow.indexOf('validate_delegated_contract');
  const preflight = flow.indexOf('bash "$PRECHECK" "$OUT_DIR"');
  const proof = flow.indexOf('test -s "$OUT_DIR/sms-denial-transition-proof.json"');
  const implementation = flow.indexOf('bash "$IMPLEMENTATION" "$OUT_DIR"');
  const summary = flow.indexOf('test -s "$OUT_DIR/revocation-summary.json"');
  assert.ok(contract >= 0 && contract < preflight);
  assert.ok(preflight >= 0 && preflight < proof);
  assert.ok(proof < implementation);
  assert.ok(implementation < summary);
});

test('delegated implementation contract is validated instead of trusted by comments', () => {
  assert.match(flow, /grep -Fq "\$marker" "\$IMPLEMENTATION"/);
  for (const marker of [
    'remove-role-holder',
    'assert_send_sms_appop_denied',
    'assert_action_disabled "phone_core_sms_send"',
    'wait_role_absent android.app.role.CALL_SCREENING'
  ]) assert.ok(flow.includes(marker), `missing delegated contract marker: ${marker}`);
});
