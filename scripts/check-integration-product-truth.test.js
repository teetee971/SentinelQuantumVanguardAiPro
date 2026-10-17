import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { validateIntegrationCapabilities } from './check-integration-product-truth.js';

const base = {
  schema_version: 1,
  capabilities: [
    {
      id: 'dialer',
      owner: 'phone-core',
      surface: 'Phone / Dialer',
      state: 'PHYSICAL_REQUIRED',
      diagnostic_reason: 'PHYSICAL_VALIDATION_REQUIRED'
    }
  ]
};

function validate(capabilities) {
  return validateIntegrationCapabilities(
    { schema_version: 1, capabilities },
    { knownIds: new Set(['dialer', 'sms']) }
  );
}

test('unknown capability ids fail closed', () => {
  const result = validate([
    ...base.capabilities,
    {
      id: 'unknown_capability', owner: 'x', surface: 'x',
      state: 'LOCKED_EXTERNAL_DEPENDENCY', diagnostic_reason: 'RUNTIME_NOT_OBSERVED'
    }
  ]);
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /unknown capability/i);
});

test('duplicate ids fail validation', () => {
  const result = validate([...base.capabilities, ...base.capabilities]);
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /duplicate/i);
});

test('CONNECTED requires a complete six-link integration chain and evidence reference', () => {
  const result = validate([
    {
      id: 'dialer', owner: 'phone-core', surface: 'Phone / Dialer', state: 'CONNECTED'
    }
  ]);
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /integration chain/i);
});

test('CI_ONLY is excluded from production completion counts', () => {
  const result = validate([
    {
      id: 'dialer', owner: 'phone-core', surface: 'Phone / Dialer',
      state: 'PHYSICAL_REQUIRED', diagnostic_reason: 'PHYSICAL_VALIDATION_REQUIRED'
    },
    {
      id: 'sms', owner: 'ci', surface: 'fixture', state: 'CI_ONLY',
      diagnostic_reason: 'CI_ONLY_COMPONENT'
    }
  ]);
  assert.equal(result.ok, true);
  assert.equal(result.summary.productionTotal, 1);
  assert.equal(result.summary.ciOnlyCount, 1);
});

test('non-CONNECTED state without diagnostic reason fails', () => {
  const result = validate([
    { id: 'dialer', owner: 'phone-core', surface: 'Phone / Dialer', state: 'PHYSICAL_REQUIRED' }
  ]);
  assert.equal(result.ok, false);
  assert.match(result.errors.join('\n'), /diagnostic reason/i);
});

test('package and Product Capability Truth workflow require integration product truth', () => {
  const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
  const workflow = fs.readFileSync('.github/workflows/product-capability-truth.yml', 'utf8');
  assert.equal(typeof pkg.scripts['test:integration-product-truth'], 'string');
  assert.match(pkg.scripts['test:integration-product-truth'], /check-integration-product-truth/);
  assert.match(workflow, /npm run test:integration-product-truth/);
});
