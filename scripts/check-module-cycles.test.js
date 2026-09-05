import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { buildLocalModuleGraph, detectModuleCycles, validateModuleCycles } from './check-module-cycles.js';

function fixture(files) {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-module-cycles-'));
  for (const [path, content] of Object.entries(files)) {
    const full = join(root, path);
    mkdirSync(join(full, '..'), { recursive: true });
    writeFileSync(full, content);
  }
  return root;
}

test('accepts an acyclic local module graph', () => {
  const root = fixture({
    'a.js': "import { b } from './b.js'; export const a = b + 1;\n",
    'b.js': "import { c } from './c.js'; export const b = c + 1;\n",
    'c.js': 'export const c = 1;\n',
  });
  const result = validateModuleCycles(root);
  assert.equal(result.modules, 3);
  assert.deepEqual(result.cycles, []);
});

test('detects a direct two-module cycle', () => {
  const root = fixture({
    'a.js': "import { b } from './b.js'; export const a = b;\n",
    'b.js': "import { a } from './a.js'; export const b = a;\n",
  });
  const result = validateModuleCycles(root);
  assert.equal(result.cycles.length, 1);
  assert.match(result.cycles[0], /a\.js/);
  assert.match(result.cycles[0], /b\.js/);
});

test('detects longer cycles and ignores test-only modules', () => {
  const root = fixture({
    'a.js': "import './b.js';\n",
    'b.js': "import './c.js';\n",
    'c.js': "import './a.js';\n",
    'ignored.test.js': "import './ignored-helper.js';\n",
    'ignored-helper.js': "import './ignored.test.js';\n",
  });
  const result = validateModuleCycles(root);
  assert.equal(result.cycles.length, 1);
  assert.doesNotMatch(result.cycles[0], /ignored/);
});

test('deduplicates the same cycle discovered from multiple traversal points', () => {
  const root = fixture({
    'a.js': "import './b.js';\n",
    'b.js': "import './c.js';\n",
    'c.js': "import './a.js';\n",
  });
  const graph = buildLocalModuleGraph(root);
  const cycles = detectModuleCycles(graph);
  assert.equal(cycles.length, 1);
});
