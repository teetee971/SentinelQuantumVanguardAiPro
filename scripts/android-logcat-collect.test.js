import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

// ADB transport fixtures exercise the actual collector, never product health.
const adbFixture = `#!/usr/bin/env bash
set -eu
crash() {
  printf 'E/AndroidRuntime( 42): FATAL EXCEPTION: main\\nE/AndroidRuntime( 42): Process: com.sentinel.quantum, PID: 42\\n'
}
if [[ "$*" == wait-for-device ]]; then exit 0; fi
if [[ "$*" == 'shell getprop sys.boot_completed' ]]; then
  if [[ "$COLLECT_MODE" == not-booted ]]; then echo 0; exit 0; fi
  if [[ "$COLLECT_MODE" == flapping-boot ]]; then
    boot_count=0
    if [[ -f "$COLLECT_FIXTURE/boot-count" ]]; then read -r boot_count < "$COLLECT_FIXTURE/boot-count"; fi
    boot_count=$((boot_count + 1)); echo "$boot_count" > "$COLLECT_FIXTURE/boot-count"
    if [[ "$boot_count" == 2 ]]; then echo 0; else echo 1; fi
    exit 0
  fi
  echo 1; exit 0
fi
if [[ "$1" == pull ]]; then
  if [[ "$COLLECT_MODE" == pull-error ]]; then echo 'permission denied' >&2; exit 1; fi
  if [[ "$COLLECT_MODE" == partial-sync-product-crash ]]; then crash > "$3"; exit 255; fi
  cp "$COLLECT_FIXTURE/device-buffer" "$3"; exit 0
fi
count=0
if [[ -f "$COLLECT_FIXTURE/count" ]]; then read -r count < "$COLLECT_FIXTURE/count"; fi
count=$((count + 1)); echo "$count" > "$COLLECT_FIXTURE/count"
if [[ "$1" == shell ]]; then
  [[ "$2" == 'logcat -d -v time > /data/local/tmp/sentinel-qualification-logcat.txt' ]] || exit 99
  if [[ "$COLLECT_MODE" == snapshot-product-crash ]]; then crash > "$COLLECT_FIXTURE/device-buffer"; exit 0; fi
  if [[ "$COLLECT_MODE" == empty-snapshot ]]; then : > "$COLLECT_FIXTURE/device-buffer"; exit 0; fi
  echo 'I/ActivityManager( 7): healthy transport fixture only' > "$COLLECT_FIXTURE/device-buffer"
  if [[ "$COLLECT_MODE" == capture-error-stale-file ]]; then echo 'snapshot failed' >&2; exit 1; fi
  if [[ "$COLLECT_MODE" == always-aborted ]]; then exit 255; fi
  exit 0
fi
[[ "$*" == 'logcat -d -v time' ]] || exit 99
case "$COLLECT_MODE" in
  success) echo 'I/ActivityManager( 7): healthy transport fixture only'; exit 0;;
  offline) echo 'error: device offline' >&2; exit 1;;
  empty-success) exit 0;;
  syntax-error) echo 'unknown command' >&2; exit 1;;
  partial-product-crash) crash; exit 255;;
esac
echo 'I/System( 7): truncated read'; echo 'stream aborted' >&2; exit 255
`;

for (const [mode, expected, reads] of [
  ['success', 0, 1], ['transient', 0, 2], ['offline', 0, 2], ['not-booted', 1, 1],
  ['flapping-boot', 0, 2], ['always-aborted', 1, 3], ['syntax-error', 1, 1],
  ['partial-product-crash', 1, 1], ['empty-success', 1, 1],
  ['snapshot-product-crash', 1, 2], ['partial-sync-product-crash', 1, 2],
  ['capture-error-stale-file', 1, 2], ['pull-error', 1, 2], ['empty-snapshot', 1, 2]
]) {
  test(`logcat collector: ${mode}`, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-logcat-read-'));
    try {
      writeFileSync(join(dir, 'adb'), adbFixture, { mode: 0o700 });
      writeFileSync(join(dir, 'sleep'), '#!/usr/bin/env bash\nexit 0\n', { mode: 0o700 });
      const output = join(dir, 'logcat.txt');
      const result = spawnSync('bash', [new URL('./android-logcat-collect.sh', import.meta.url).pathname, output], {
        encoding: 'utf8', env: { ...process.env, PATH: dir + ':' + process.env.PATH, COLLECT_FIXTURE: dir, COLLECT_MODE: mode }
      });
      assert.equal(result.status, expected, result.stderr);
      assert.equal(Number(readFileSync(join(dir, 'count'), 'utf8')), reads);
      assert.ok(existsSync(output + '.attempt-1.status'));
      assert.ok(existsSync(output + '.attempt-1.txt.analysis.json'));
      if (mode === 'flapping-boot') assert.equal(Number(readFileSync(join(dir, 'boot-count'), 'utf8')), 4);
      if (mode === 'not-booted') assert.match(result.stderr, /stable booted shell/);
      if (reads > 1) {
        assert.equal(readFileSync(output + '.attempt-2.mode', 'utf8').trim(), 'device-file-sync');
        assert.ok(existsSync(output + '.attempt-2.txt.capture.status'));
        assert.ok(existsSync(output + '.attempt-2.txt.pull.status'));
      }
      if (mode === 'transient') {
        assert.match(readFileSync(output + '.attempt-1.txt', 'utf8'), /truncated/);
        assert.equal(readFileSync(output + '.attempt-1.status', 'utf8').trim(), '255');
        assert.match(readFileSync(output, 'utf8'), /healthy transport/);
      }
      if (mode.endsWith('product-crash')) {
        assert.match(readFileSync(output, 'utf8'), /FATAL EXCEPTION/);
        assert.equal(JSON.parse(readFileSync(output + `.attempt-${reads}.txt.analysis.json`, 'utf8')).result, 'FAIL');
      }
      if (mode === 'capture-error-stale-file') {
        assert.match(readFileSync(output, 'utf8'), /healthy transport/);
        assert.equal(readFileSync(output + '.attempt-2.txt.capture.status', 'utf8').trim(), '1');
        assert.equal(readFileSync(output + '.attempt-2.txt.pull.status', 'utf8').trim(), '0');
      }
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}
