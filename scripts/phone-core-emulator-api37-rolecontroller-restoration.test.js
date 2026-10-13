import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');
const workflow = readFileSync(new URL('../.github/workflows/android-emulation-qualification.yml', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  return revocation.slice(start, revocation.indexOf('\n}', start) + 2);
}

test('API 37 classifier accepts only a proven RoleController restoration of an established SEND_SMS denial', () => {
  const classifier = shellFunction('api37_role_controller_restored_send_sms_appop');
  const cases = [
    { api: '37', before: 'Uid mode: SEND_SMS: ignore\nSEND_SMS: allow\n', after: 'SEND_SMS: allow\n', held: 0, expected: 0 },
    { api: '36', before: 'Uid mode: SEND_SMS: ignore\nSEND_SMS: allow\n', after: 'SEND_SMS: allow\n', held: 0, expected: 1 },
    { api: '37', before: 'SEND_SMS: allow\n', after: 'SEND_SMS: allow\n', held: 0, expected: 1 },
    { api: '37', before: 'Uid mode: SEND_SMS: ignore\n', after: 'Uid mode: SEND_SMS: ignore\nSEND_SMS: allow\n', held: 0, expected: 1 },
    { api: '37', before: 'Uid mode: SEND_SMS: ignore\n', after: 'SEND_SMS: allow\n', held: 1, expected: 1 }
  ];

  for (const entry of cases) {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-api37-rolecontroller-'));
    try {
      writeFileSync(join(dir, 'before.txt'), entry.before);
      writeFileSync(join(dir, 'after.txt'), entry.after);
      const script = `API_LEVEL=${entry.api}\nOUT_DIR="$1"\nassert_sms_role_held() { return ${entry.held}; }\n${classifier}\napi37_role_controller_restored_send_sms_appop before.txt after.txt`;
      const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
      assert.equal(result.status, entry.expected, result.stderr || result.stdout);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  }
});

test('API 37 runtime evidence records the platform limitation but still requires real role-revocation proof', () => {
  assert.match(revocation, /EFFECTIVE_PERMISSION_DENIAL_EMULATOR_LIMITED=false/);
  assert.match(revocation, /effective_permission_denial_emulator_limited/);
  assert.match(revocation, /API37_ROLE_CONTROLLER_RESTORED_SEND_SMS/);
  assert.match(revocation, /ROLE_REVOCATION_PROVEN=true/);

  assert.match(workflow, /api37_effective_sms_denial_emulator_limitation/);
  assert.match(workflow, /api37_role_controller_restored_send_sms_appop/);
  assert.match(workflow, /role_revocation_fail_closed/);
  assert.match(workflow, /api37_role_controller_restored_send_sms_appop/);
  assert.match(workflow, /result: evidenceFailures\.length > 0 \? 'FAIL' : limitations\.length > 0 \? 'LIMITED' : 'PASS'/);
});
