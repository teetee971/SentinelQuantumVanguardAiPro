import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { validateProductCapabilities } from './check-product-capabilities.js';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registry = JSON.parse(
  fs.readFileSync(path.join(rootDir, 'config', 'product-capabilities.json'), 'utf8')
);

test('canonical product capability registry satisfies truth invariants', () => {
  assert.deepEqual(validateProductCapabilities(registry, rootDir), []);
});

test('customer availability cannot bypass physical validation or signed release', () => {
  const copy = structuredClone(registry);
  const phoneCore = copy.capabilities.find((item) => item.id === 'phone_core_android');
  phoneCore.customer_available = true;
  phoneCore.runtime_verified = true;
  phoneCore.blockers = [];

  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(errors.some((error) => error.includes('physical validation')));
  assert.ok(errors.some((error) => error.includes('signed release')));
});

test('deployment cannot be asserted without implementation and configuration', () => {
  const copy = structuredClone(registry);
  const vpn = copy.capabilities.find((item) => item.id === 'sentinel_vpn_service');
  vpn.implemented = false;
  vpn.configured = false;
  vpn.deployed = true;

  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(errors.some((error) => error.includes('deployed requires implemented and configured')));
});

test('evidence paths must exist', () => {
  const copy = structuredClone(registry);
  copy.capabilities[0].evidence = ['does/not/exist'];
  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(errors.some((error) => error.includes('evidence path does not exist')));
});
