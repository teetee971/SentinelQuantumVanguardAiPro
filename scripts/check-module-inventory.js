#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { classifyModuleUsage, rootDir } from './report-module-usage.js';

const ALLOWED_STATUSES = new Set([
  'DORMANT_LIBRARY',
  'INTENTIONALLY_DORMANT',
  'CI_TOOLING',
  'TOOLING_ENTRYPOINT',
  'DEAD_CODE_CANDIDATE',
]);

export function validateInventory(baseDir = rootDir, inventoryPath = join(baseDir, 'config', 'module-continuity-inventory.json')) {
  const records = classifyModuleUsage(baseDir);
  const orphanPaths = new Set(
    records.filter((record) => record.classification === 'ORPHAN_CANDIDATE').map((record) => record.path),
  );
  const inventory = JSON.parse(readFileSync(inventoryPath, 'utf8'));
  const entries = inventory?.entries && typeof inventory.entries === 'object' ? inventory.entries : {};
  const errors = [];

  for (const orphanPath of orphanPaths) {
    const entry = entries[orphanPath];
    if (!entry) {
      errors.push(`unclassified orphan candidate: ${orphanPath}`);
      continue;
    }
    if (!ALLOWED_STATUSES.has(entry.status)) {
      errors.push(`invalid status for ${orphanPath}: ${entry.status ?? '<missing>'}`);
    }
    if (typeof entry.owner !== 'string' || !entry.owner.trim()) {
      errors.push(`missing owner for ${orphanPath}`);
    }
    if (typeof entry.reason !== 'string' || entry.reason.trim().length < 12) {
      errors.push(`missing or insufficient reason for ${orphanPath}`);
    }
  }

  for (const inventoryPathKey of Object.keys(entries)) {
    if (!orphanPaths.has(inventoryPathKey)) {
      errors.push(`stale inventory entry is no longer an orphan candidate: ${inventoryPathKey}`);
    }
  }

  return { orphanCount: orphanPaths.size, inventoryCount: Object.keys(entries).length, errors };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = validateInventory();
    if (result.errors.length) {
      console.error('Module continuity inventory validation failed:');
      for (const error of result.errors) console.error(`- ${error}`);
      process.exit(1);
    }
    console.log(`Module continuity inventory valid (${result.orphanCount} orphan candidates explicitly classified).`);
  } catch (error) {
    console.error(`Module continuity inventory validation failed: ${error.message}`);
    process.exit(1);
  }
}
