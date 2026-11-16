import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, copyFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

function fixture(run) {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-ui-import-'));
  try {
    mkdirSync(join(root, 'scripts'));
    const script = join(root, 'scripts', 'guard.mjs');
    copyFileSync(new URL('./check-android-ui-consistency.js', import.meta.url), script);
    run(script);
  } finally { rmSync(root, { recursive: true, force: true }); }
}

test('importing the validator must not terminate the caller for an unrelated missing Android tree', () => fixture((script) => {
  const result = spawnSync(process.execPath, ['--input-type=module', '-e',
    `import { validateSharedComponents } from ${JSON.stringify(pathToFileURL(script).href)}; console.log(validateSharedComponents('', '').length);`
  ], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), '4');
}));

test('the same guard run as CLI still rejects a missing Android tree', () => fixture((script) => {
  const result = spawnSync(process.execPath, [script], { encoding: 'utf8' });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Android screen directory missing/);
}));
