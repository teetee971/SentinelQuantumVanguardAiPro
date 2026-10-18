#!/usr/bin/env node
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const file = path.resolve(__dirname, '../docs/telecom/regulator-sources.v1.json');
const registry = JSON.parse(fs.readFileSync(file, 'utf8'));
function assert(condition, message) { if (!condition) throw new Error(message); }
assert(registry.schema_version === 1, 'Unexpected registry schema');
assert(registry.mode === 'REFERENCE_ONLY', 'Registry must remain read-only');
assert(registry.enforcement_eligibility === 'NONE', 'Registry cannot authorize screening');
assert(Array.isArray(registry.sources) && registry.sources.length >= 10, 'Missing source directory');
const ids = new Set();
for (const source of registry.sources) {
  assert(/^[A-Z0-9_]+$/.test(source.id), 'Invalid source id');
  assert(!ids.has(source.id), 'Duplicate source id: ' + source.id);
  ids.add(source.id);
  assert(/^[A-Z]{2,3}$/.test(source.jurisdiction), 'Invalid jurisdiction');
  const url = new URL(source.official_url);
  assert(url.protocol === 'https:', 'Non-HTTPS source: ' + source.id);
  assert(source.connector_status === 'NOT_IMPLEMENTED', 'Do not misstate connector readiness');
  assert(source.last_successful_sync === null, 'Cannot claim unobserved sync');
  assert(source.license_review === 'PENDING', 'Licensing must be reviewed separately');
  assert(source.enforcement_eligibility === 'NONE', 'Source cannot block calls');
}
console.log('Global regulator registry: ' + registry.sources.length + ' reference sources; 0 active connectors; enforcement disabled.');
