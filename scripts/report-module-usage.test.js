import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { classifyModuleUsage } from './report-module-usage.js';

function fixture() {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-module-usage-'));
  mkdirSync(join(root, 'src'), { recursive: true });
  mkdirSync(join(root, '.github', 'workflows'), { recursive: true });
  writeFileSync(join(root, 'package.json'), JSON.stringify({
    type: 'module',
    scripts: { start: 'node src/entry.js' },
  }));
  writeFileSync(join(root, '.github', 'workflows', 'ci.yml'), 'steps:\n  - run: node src/worker.js\n');
  writeFileSync(join(root, 'src', 'entry.js'), "import './used.js';\n");
  writeFileSync(join(root, 'src', 'used.js'), 'export const used = true;\n');
  writeFileSync(join(root, 'src', 'worker.js'), 'export const worker = true;\n');
  writeFileSync(join(root, 'src', 'orphan.js'), 'export const orphan = true;\n');
  writeFileSync(join(root, 'src', 'fake.test.js'), "import './missing.js';\n");
  return root;
}

test('classifies explicit runtime entrypoints, imported modules, and orphan candidates', () => {
  const records = classifyModuleUsage(fixture());
  const byPath = new Map(records.map((record) => [record.path, record]));

  assert.equal(byPath.get('src/entry.js').classification, 'ENTRYPOINT');
  assert.equal(byPath.get('src/worker.js').classification, 'ENTRYPOINT');
  assert.equal(byPath.get('src/used.js').classification, 'IMPORTED');
  assert.equal(byPath.get('src/orphan.js').classification, 'ORPHAN_CANDIDATE');
  assert.equal(byPath.has('src/fake.test.js'), false);
});
