import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

for (const [name, size, density, target, status] of [
  ['standard override', 'Physical size: 1440x3120\nOverride size: 1080x2400', 'Physical density: 480\nOverride density: 420', ['1080x2400', '420'], 0],
  ['native S24 equivalent without a redundant override', 'Physical size: 1440x3120', 'Physical density: 480', ['1440x3120', '480'], 0],
  ['captured WMS clamp cannot qualify', 'Physical size: 320x640\nOverride size: 1080x1920', 'Physical density: 160\nOverride density: 420', ['1080x2400', '420'], 1],
  ['physical size cannot hide a conflicting override', 'Physical size: 1440x3120\nOverride size: 1080x2400', 'Physical density: 480', ['1440x3120', '480'], 1],
  ['duplicate overrides cannot qualify', 'Physical size: 1440x3120\nOverride size: 1080x2400\nOverride size: 1080x2400', 'Physical density: 480', ['1080x2400', '480'], 1],
  ['malformed override cannot fall back to physical', 'Physical size: 1440x3120\nOverride size: unavailable', 'Physical density: 480', ['1440x3120', '480'], 1],
  ['missing physical metadata cannot qualify', 'adb: device offline', 'Physical density: 480', ['1440x3120', '480'], 1]
]) {
  test(`effective viewport: ${name}`, () => {
    const dir = mkdtempSync(join(tmpdir(), 'sentinel-wm-state-'));
    try {
      writeFileSync(join(dir, 'size.txt'), size);
      writeFileSync(join(dir, 'density.txt'), density);
      const result = spawnSync('python3', [new URL('./android-viewport-state.py', import.meta.url).pathname,
        join(dir, 'size.txt'), join(dir, 'density.txt'), ...target], { encoding: 'utf8' });
      assert.equal(result.status, status, result.stderr);
      if (status === 0) assert.equal(JSON.parse(result.stdout).effective_size, target[0]);
      else assert.ok(result.stderr.trim());
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}
