import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  extractExportNames,
  extractLocalImportContracts,
  validateModuleInterfaceContracts,
} from './check-module-interface-contracts.js';

test('extracts declared named and default exports', () => {
  const result = extractExportNames(`
    export const alpha = 1;
    export async function beta() {}
    const gamma = 3;
    export { gamma as delta };
    export default class Example {}
  `);
  assert.deepEqual([...result.names].sort(), ['alpha', 'beta', 'delta']);
  assert.equal(result.hasDefault, true);
});

test('extracts local named/default imports and re-export requirements', () => {
  const contracts = extractLocalImportContracts(`
    import defaultThing, { alpha as localAlpha, beta } from './provider.js';
    import * as namespace from './other.js';
    export { gamma as publicGamma } from '../shared.js';
  `);
  assert.deepEqual(contracts, [
    { specifier: './provider.js', named: ['alpha', 'beta'], requiresDefault: true, kind: 'import' },
    { specifier: '../shared.js', named: ['gamma'], requiresDefault: false, kind: 're-export' },
  ]);
});

test('fails when a production consumer requests a missing named export', () => {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-interface-contract-'));
  try {
    mkdirSync(join(root, 'app'));
    writeFileSync(join(root, 'app', 'provider.js'), 'export const present = true;\n');
    writeFileSync(join(root, 'app', 'consumer.js'), "import { missing } from './provider.js';\nexport const value = missing;\n");
    const result = validateModuleInterfaceContracts(root);
    assert.equal(result.errors.length, 1);
    assert.match(result.errors[0], /requires named export missing/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('accepts matching named, aliased, and default contracts', () => {
  const root = mkdtempSync(join(tmpdir(), 'sentinel-interface-contract-'));
  try {
    mkdirSync(join(root, 'app'));
    writeFileSync(join(root, 'app', 'provider.js'), 'export default function main() {}\nexport const alpha = 1;\n');
    writeFileSync(join(root, 'app', 'consumer.js'), "import main, { alpha as localAlpha } from './provider.js';\nexport const value = [main, localAlpha];\n");
    const result = validateModuleInterfaceContracts(root);
    assert.deepEqual(result.errors, []);
    assert.equal(result.contractsChecked, 1);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
