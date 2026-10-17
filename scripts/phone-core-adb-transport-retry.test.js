import test from 'node:test';
import assert from 'node:assert/strict';
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const revocation = readFileSync(new URL('./phone-core-emulator-revocation-flow.sh', import.meta.url), 'utf8');

function shellFunction(name) {
  const start = revocation.indexOf(`${name}() {`);
  assert.notEqual(start, -1, `${name} must exist in the revocation harness`);
  const next = revocation.indexOf('\n}', start);
  assert.ok(next > start, `${name} must have a complete shell body`);
  return revocation.slice(start, next + 2);
}

function runRetryFixture(mode) {
  const dir = mkdtempSync(join(tmpdir(), 'sentinel-adb-retry-'));
  try {
    const binDir = join(dir, 'bin');
    mkdirSync(binDir);
    const adbPath = join(binDir, 'adb');
    writeFileSync(adbPath, `#!/usr/bin/env bash
set -u
OUT_DIR="\${SENTINEL_TEST_OUT_DIR:?}"
MODE="\${SENTINEL_TEST_MODE:?}"
increment() {
  local file="$1"
  local value=0
  if [[ -f "$file" ]]; then value="$(cat "$file")"; fi
  value=$((value + 1))
  printf '%s\\n' "$value" > "$file"
  printf '%s\\n' "$value"
}
if [[ "\${1:-}" == "get-state" ]]; then
  increment "$OUT_DIR/get-state-attempts.txt" >/dev/null
  printf 'device\\n'
  exit 0
fi
attempt="$(increment "$OUT_DIR/target-attempts.txt")"
case "$MODE" in
  transient)
    if [[ "$attempt" -eq 1 ]]; then
      printf 'error: device offline\\n' >&2
      exit 1
    fi
    printf 'role command completed\\n'
    exit 0
    ;;
  semantic)
    printf 'Security exception: role change rejected\\n' >&2
    exit 1
    ;;
  persistent)
    printf 'connection terminated: write failed\\n' >&2
    exit 1
    ;;
esac
`);
    chmodSync(adbPath, 0o755);

    const script = `
set -u
OUT_DIR="$1"
${shellFunction('adb_transport_ready')}
${shellFunction('adb_retry_transport')}
adb_retry_transport shell cmd role remove-role-holder --user 0 android.app.role.SMS com.sentinel.quantum
status=$?
TARGET_ATTEMPTS="$(cat "$OUT_DIR/target-attempts.txt" 2>/dev/null || printf '0')"
GET_STATE_ATTEMPTS="$(cat "$OUT_DIR/get-state-attempts.txt" 2>/dev/null || printf '0')"
printf 'status=%s target_attempts=%s get_state_attempts=%s\n' "$status" "$TARGET_ATTEMPTS" "$GET_STATE_ATTEMPTS"
exit "$status"
`;
    return spawnSync('bash', ['-c', script, 'test', dir], {
      encoding: 'utf8',
      env: {
        ...process.env,
        PATH: `${binDir}:${process.env.PATH}`,
        SENTINEL_TEST_OUT_DIR: dir,
        SENTINEL_TEST_MODE: mode,
        SENTINEL_ADB_RETRY_ATTEMPTS: '3',
        SENTINEL_ADB_READY_ATTEMPTS: '2',
        SENTINEL_ADB_RETRY_DELAY_SECONDS: '0'
      }
    });
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('transient device-offline transport loss retries the interrupted ADB command', () => {
  const result = runRetryFixture('transient');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /role command completed/);
  assert.match(result.stdout, /target_attempts=2/);
});

test('semantic ADB failure is fatal and is not retried', () => {
  const result = runRetryFixture('semantic');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Security exception/);
  assert.match(result.stdout, /target_attempts=1/);
});

test('persistent transport loss fails closed after the bounded retry budget', () => {
  const result = runRetryFixture('persistent');
  assert.equal(result.status, 1);
  assert.match(result.stderr, /connection terminated: write failed/);
  assert.match(result.stdout, /target_attempts=3/);
});

test('role and permission transition commands use the bounded transport retry', () => {
  assert.match(revocation, /adb_retry_transport shell pm grant \"\$PACKAGE\" android\.permission\.SEND_SMS/);
  assert.match(revocation, /adb_retry_transport shell cmd role remove-role-holder --user 0 android\.app\.role\.SMS \"\$PACKAGE\"/);
  assert.match(revocation, /adb_retry_transport shell cmd role remove-role-holder --user 0 android\.app\.role\.DIALER \"\$PACKAGE\"/);
});

test('ADB transport retry hard-bounds both readiness probes and target commands', () => {
  const ready = shellFunction('adb_transport_ready');
  const retry = shellFunction('adb_retry_transport');

  assert.match(
    ready,
    /timeout --signal=TERM --kill-after=1s 3s adb get-state/,
    'adb get-state must have its own hard timeout so one wedged readiness probe cannot consume the whole script watchdog'
  );
  assert.match(
    retry,
    /timeout --signal=TERM --kill-after=5s 20s adb \"\$@\"/,
    'each retried ADB command must be individually bounded in addition to the retry-count budget'
  );
  assert.doesNotMatch(
    ready,
    /\$\(adb get-state/,
    'readiness checks must not call adb get-state unbounded'
  );
  assert.doesNotMatch(
    retry,
    /\n\s*if adb \"\$@\"/,
    'retry attempts must not call the target ADB command unbounded'
  );
});
