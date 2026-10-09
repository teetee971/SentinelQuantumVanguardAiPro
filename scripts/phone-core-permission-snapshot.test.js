import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

const flow = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = flow.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  const end = flow.indexOf('\n}', start);
  assert.notEqual(end, -1, `unterminated shell function ${name}`);
  return flow.slice(start, end + 2);
}

test('contradictory SEND_SMS package snapshots fail closed across every runtime permission oracle', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-send-sms-snapshot-'));
  try {
    const script = `PACKAGE=com.sentinel.quantum\nOUT_DIR="$1"\nsleep(){ :; }\nadb(){\n  if [[ "$*" == 'shell dumpsys package com.sentinel.quantum' ]]; then\n    printf '  android.permission.SEND_SMS: granted=true\\n  android.permission.SEND_SMS: granted=false\\n'\n    return 0\n  fi\n  return 1\n}\n${shellFunction('permission_granted')}\n${shellFunction('assert_send_sms_runtime_permission_granted')}\n${shellFunction('archive_send_sms_runtime_permission_state')}\n${shellFunction('assert_send_sms_runtime_permission_denied')}\n${shellFunction('wait_send_sms_runtime_permission_denied')}\nfor fn in assert_send_sms_runtime_permission_granted archive_send_sms_runtime_permission_state assert_send_sms_runtime_permission_denied wait_send_sms_runtime_permission_denied; do\n  if "$fn" "$fn.txt"; then printf '%s=accepted\\n' "$fn"; else printf '%s=rejected\\n' "$fn"; fi\ndone`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.doesNotMatch(result.stdout, /=accepted/, result.stdout);
    for (const fn of [
      'assert_send_sms_runtime_permission_granted',
      'archive_send_sms_runtime_permission_state',
      'assert_send_sms_runtime_permission_denied',
      'wait_send_sms_runtime_permission_denied'
    ]) {
      assert.match(result.stdout, new RegExp(`${fn}=rejected`));
    }
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
