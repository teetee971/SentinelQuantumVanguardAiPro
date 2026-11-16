import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, cpSync, existsSync, rmSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { includePublicArtifact } from './static-artifact-filter.js';

test('static copy preserves runtime modules and nested assets but excludes Node test programs', () => {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-static-copy-'));
  try {
    const source = join(root, 'public'); const dest = join(root, 'dist');
    mkdirSync(join(source, 'nested'), { recursive: true });
    writeFileSync(join(source, 'runtime.js'), 'export const ready = true;');
    writeFileSync(join(source, 'runtime.test.js'), 'import test from "node:test";');
    writeFileSync(join(source, 'nested', 'client.test.js'), 'test code');
    writeFileSync(join(source, 'nested', 'client.js'), 'runtime code');
    cpSync(source, dest, { recursive: true, filter: includePublicArtifact });
    assert.equal(readFileSync(join(dest, 'runtime.js'), 'utf8'), 'export const ready = true;');
    assert.equal(readFileSync(join(dest, 'nested', 'client.js'), 'utf8'), 'runtime code');
    assert.equal(existsSync(join(dest, 'runtime.test.js')), false);
    assert.equal(existsSync(join(dest, 'nested', 'client.test.js')), false);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
