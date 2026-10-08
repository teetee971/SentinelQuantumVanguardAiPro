import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const flow = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = flow.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `missing shell function ${name}`);
  const end = flow.indexOf('\n}', start);
  assert.notEqual(end, -1, `unterminated shell function ${name}`);
  return flow.slice(start, end + 2);
}

test('SEND_SMS revoke probe refuses to issue pm revoke without an observed granted baseline', () => {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-send-sms-baseline-'));
  try {
    const script = `
set -euo pipefail
PACKAGE=com.sentinel.quantum
OUT_DIR="$1"
SEND_SMS_PM_REVOCATION_OBSERVABLE=true
sleep(){ :; }
permission_granted(){ return 1; }
adb(){
  if [[ "$*" == 'shell pm revoke com.sentinel.quantum android.permission.SEND_SMS' ]]; then
    printf 'called\n' > "$OUT_DIR/revoke-called.txt"
    return 0
  fi
  return 0
}
${shellFunction('probe_pm_revoke_send_sms')}
probe_pm_revoke_send_sms
printf 'observable=%s\n' "$SEND_SMS_PM_REVOCATION_OBSERVABLE"
`;
    const result = spawnSync('bash', ['-c', script, 'test', dir], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr || result.stdout);
    assert.equal(existsSync(join(dir, 'revoke-called.txt')), false, 'pm revoke must not run until a granted baseline is proven inside the probe');
    assert.match(result.stdout, /observable=false/);
    const evidence = readFileSync(join(dir, 'send-sms-pm-revoke-observation.txt'), 'utf8');
    assert.match(evidence, /baseline_granted=false/);
    assert.match(evidence, /reason=baseline_grant_not_proven/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
