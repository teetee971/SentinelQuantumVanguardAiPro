import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');
const workflow = readFileSync(new URL('../.github/workflows/android-emulation-qualification.yml', import.meta.url), 'utf8');

test('revocation flow falls back to a stable non-role-managed permission when SEND_SMS is restored', () => {
  assert.match(revocation, /use_read_phone_state_runtime_permission_probe\(\)/);
  assert.match(revocation, /READ_PHONE_STATE_RUNTIME_PERMISSION_REVOKED/);
  assert.match(revocation, /android\.permission\.READ_PHONE_STATE/);
  assert.match(revocation, /read-phone-state-runtime-permission-denied-after-launch\.txt/);
  assert.match(revocation, /assert_sms_role_held/);
});

test('qualification accepts the stable READ_PHONE_STATE denial only with persisted package evidence', () => {
  assert.match(workflow, /effective_permission_probe === 'READ_PHONE_STATE_RUNTIME_PERMISSION_REVOKED'/);
  assert.match(workflow, /read-phone-state-runtime-permission-denied-after-launch\.txt/);
  assert.match(workflow, /android\\\.permission\\\.READ_PHONE_STATE:\\s\+granted=false/);
});
