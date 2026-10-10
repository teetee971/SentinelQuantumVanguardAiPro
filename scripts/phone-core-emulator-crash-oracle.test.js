import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `${name} exists`);
  return revocation.slice(start, revocation.indexOf('\n}', start) + 2);
}

function runAssertNoCrash({ logcatStatus = 0, logcatOutput = '' } = {}) {
  const fixture = `
set -euo pipefail
adb() {
  if [[ "$*" == "logcat -d -v brief" ]]; then
    printf '%s' ${JSON.stringify(logcatOutput)}
    return ${logcatStatus}
  fi
  if [[ "$*" == "logcat -d -v time" ]]; then
    printf '%s' ${JSON.stringify(logcatOutput)}
    return ${logcatStatus}
  fi
  return 99
}
${shellFunction('assert_no_crash')}
assert_no_crash
`;
  return spawnSync('bash', ['-c', fixture], { encoding: 'utf8' });
}

test('assert_no_crash accepts a readable clean logcat', () => {
  const result = runAssertNoCrash({ logcatOutput: 'SentinelLifecycle: clean\n' });
  assert.equal(result.status, 0, result.stderr);
});

test('assert_no_crash rejects an observed crash', () => {
  const result = runAssertNoCrash({
    logcatOutput: 'E/AndroidRuntime: FATAL EXCEPTION: main\nProcess: com.sentinel.quantum\n'
  });
  assert.notEqual(result.status, 0);
});

test('assert_no_crash rejects a crash in a Sentinel secondary Android process', () => {
  const result = runAssertNoCrash({
    logcatOutput: 'E/AndroidRuntime: FATAL EXCEPTION: worker\nProcess: com.sentinel.quantum:isolated_worker\n'
  });
  assert.notEqual(
    result.status,
    0,
    'A package-owned secondary process crash must not be misclassified as an unrelated Android process'
  );
});

test('assert_no_crash rejects an ANR in a Sentinel secondary Android process', () => {
  const result = runAssertNoCrash({
    logcatOutput: 'E/ActivityManager: ANR in com.sentinel.quantum:telecom\n'
  });
  assert.notEqual(
    result.status,
    0,
    'A package-owned secondary process ANR must fail the runtime qualification gate'
  );
});

test('assert_no_crash rejects an unreadable logcat oracle', () => {
  const result = runAssertNoCrash({ logcatStatus: 2, logcatOutput: '' });
  assert.notEqual(
    result.status,
    0,
    'A failed logcat read is UNKNOWN evidence and must never qualify as crash-free'
  );
});
