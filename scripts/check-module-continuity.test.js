import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { extractLocalSpecifiers, resolveLocalModule, validateModuleContinuity } from './check-module-continuity.js';

test('extracts static, dynamic and require local references only', () => {
  const source = `
    import x from './a.js';
    export { y } from '../b.js';
    await import('./c.js');
    require('./d.cjs');
    import 'node:fs';
  `;
  assert.deepEqual(extractLocalSpecifiers(source).sort(), ['./a.js', './c.js', './d.cjs', '../b.js'].sort());
});

test('resolves extensionless and index module targets', () => {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-modules-'));
  const src = join(root, 'src');
  const lib = join(src, 'lib');
  mkdirSync(lib, { recursive: true });
  writeFileSync(join(src, 'main.js'), "import './util'; import './lib';\n");
  writeFileSync(join(src, 'util.js'), 'export const x = 1;\n');
  writeFileSync(join(lib, 'index.js'), 'export const y = 2;\n');
  assert.ok(resolveLocalModule(join(src, 'main.js'), './util'));
  assert.ok(resolveLocalModule(join(src, 'main.js'), './lib'));
});

test('reports unresolved local module references', () => {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-modules-'));
  writeFileSync(join(root, 'entry.js'), "import './missing.js';\n");
  const result = validateModuleContinuity(root);
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /entry\.js -> \.\/missing\.js/);
});
