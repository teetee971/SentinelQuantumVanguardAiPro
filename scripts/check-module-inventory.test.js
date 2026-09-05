import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateInventory } from './check-module-inventory.js';

function fixture(inventoryEntries = {}) {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-module-inventory-'));
  mkdirSync(join(root, 'src'), { recursive: true });
  mkdirSync(join(root, 'config'), { recursive: true });
  writeFileSync(join(root, 'package.json'), JSON.stringify({
    type: 'module',
    scripts: { start: 'node src/entry.js' },
  }));
  writeFileSync(join(root, 'src', 'entry.js'), "import './used.js';\n");
  writeFileSync(join(root, 'src', 'used.js'), 'export const used = true;\n');
  writeFileSync(join(root, 'src', 'orphan.js'), 'export const orphan = true;\n');
  const inventoryPath = join(root, 'config', 'module-continuity-inventory.json');
  writeFileSync(inventoryPath, JSON.stringify({ schema_version: 1, entries: inventoryEntries }));
  return { root, inventoryPath };
}

function withFixture(entries, assertion) {
  const { root, inventoryPath } = fixture(entries);
  try {
    assertion(validateInventory(root, inventoryPath));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('accepts an explicitly classified orphan candidate', () => {
  withFixture({
    'src/orphan.js': {
      status: 'DORMANT_LIBRARY',
      owner: 'test-owner',
      reason: 'Deliberately unreferenced reusable fixture module.',
    },
  }, (result) => {
    assert.equal(result.orphanCount, 1);
    assert.equal(result.inventoryCount, 1);
    assert.deepEqual(result.errors, []);
  });
});

test('fails closed for an unclassified orphan candidate', () => {
  withFixture({}, (result) => {
    assert.deepEqual(result.errors, ['unclassified orphan candidate: src/orphan.js']);
  });
});

test('rejects stale inventory entries', () => {
  withFixture({
    'src/orphan.js': {
      status: 'INTENTIONALLY_DORMANT',
      owner: 'test-owner',
      reason: 'Explicitly retained for a bounded future integration.',
    },
    'src/used.js': {
      status: 'DORMANT_LIBRARY',
      owner: 'test-owner',
      reason: 'This entry is intentionally stale for the regression test.',
    },
  }, (result) => {
    assert.equal(result.errors.includes('stale inventory entry is no longer an orphan candidate: src/used.js'), true);
  });
});

test('rejects invalid status, missing owner, and insufficient reason', () => {
  withFixture({
    'src/orphan.js': {
      status: 'ACTIVE_RUNTIME',
      owner: '   ',
      reason: 'too short',
    },
  }, (result) => {
    assert.equal(result.errors.includes('invalid status for src/orphan.js: ACTIVE_RUNTIME'), true);
    assert.equal(result.errors.includes('missing owner for src/orphan.js'), true);
    assert.equal(result.errors.includes('missing or insufficient reason for src/orphan.js'), true);
  });
});
