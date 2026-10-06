import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, readFileSync, rmSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

// ADB transport fixtures exercise the actual collector, never product health.
for (const [mode, expected, reads] of [['success', 0, 1], ['transient', 0, 2], ['offline', 0, 2], ['not-booted', 1, 1], ['flapping-boot', 0, 2], ['always-aborted', 1, 3],
  ['syntax-error', 1, 1], ['partial-product-crash', 1, 1], ['empty-success', 1, 1]]) {
  test(`logcat collector: ${mode}`, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-logcat-read-'));
    try {
      writeFileSync(join(dir, 'adb'), `#!/usr/bin/env bash\nset -eu\nif [[ "$*" == wait-for-device ]]; then exit 0; fi\nif [[ "$*" == 'shell getprop sys.boot_completed' ]]; then\n if [[ "$COLLECT_MODE" == not-booted ]]; then echo 0; exit 0; fi\n if [[ "$COLLECT_MODE" == flapping-boot ]]; then boot_count=0; if [[ -f "$COLLECT_FIXTURE/boot-count" ]]; then read -r boot_count < "$COLLECT_FIXTURE/boot-count"; fi; boot_count=$((boot_count + 1)); echo "$boot_count" > "$COLLECT_FIXTURE/boot-count"; if [[ "$boot_count" == 2 ]]; then echo 0; else echo 1; fi; exit 0; fi\n echo 1; exit 0; fi\nif [[ "$*" != 'logcat -d -v time' ]]; then exit 99; fi\ncount=0\nif [[ -f "$COLLECT_FIXTURE/count" ]]; then read -r count < "$COLLECT_FIXTURE/count"; fi\ncount=$((count + 1)); printf '%s\\n' "$count" > "$COLLECT_FIXTURE/count"\ncase "$COLLECT_MODE" in\n not-booted) echo 'I/System( 7): partial'; exit 255;;\n flapping-boot) if [[ "$count" == 1 ]]; then echo 'I/System( 7): partial'; exit 255; fi;;\n offline) if [[ "$count" == 1 ]]; then echo 'error: device offline' >&2; exit 1; fi;;\n empty-success) exit 0;;\n syntax-error) echo 'unknown command' >&2; exit 1;;\n partial-product-crash) printf 'E/AndroidRuntime( 42): FATAL EXCEPTION: main\\nE/AndroidRuntime( 42): Process: com.sentinel.quantum, PID: 42\\n'; exit 255;;\n always-aborted) echo 'I/System( 7): partial buffer'; exit 255;;\n transient) if [[ "$count" == 1 ]]; then echo 'I/System( 7): truncated read'; echo 'stream aborted' >&2; exit 255; fi;;\nesac\necho 'I/ActivityManager( 7): healthy transport fixture only'\n`, { mode: 0o700 });
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
      if (mode === 'transient') {
        assert.match(readFileSync(output + '.attempt-1.txt', 'utf8'), /truncated/);
        assert.equal(readFileSync(output + '.attempt-1.status', 'utf8').trim(), '255');
        assert.match(readFileSync(output, 'utf8'), /healthy transport/);
      }
      if (mode === 'partial-product-crash') {
        assert.match(readFileSync(output, 'utf8'), /FATAL EXCEPTION/);
        assert.equal(JSON.parse(readFileSync(output + '.attempt-1.txt.analysis.json', 'utf8')).result, 'FAIL');
      }
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}
