import assert from 'node:assert/strict';
import test from 'node:test';

import {
  classifyRegistryEvent,
  REGISTRY_FILTER_BOUNDARY,
  shouldEscalateRegistryEvent
} from './registry-filter.js';

test('escalates persistence-sensitive registry paths', () => {
  const result = classifyRegistryEvent({
    operation: 'set_value',
    path: 'HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run\\SentinelTest'
  });
  assert.equal(result.accepted, true);
  assert.equal(result.monitor, true);
  assert.equal(result.category, 'persistence');
  assert.equal(shouldEscalateRegistryEvent({ operation: 'set_value', path: 'HKLM\\SYSTEM\\CurrentControlSet\\Services\\Example' }), true);
});

test('keeps ordinary registry changes informational', () => {
  const result = classifyRegistryEvent({ operation: 'set_value', path: 'HKCU\\Software\\Example\\Theme' });
  assert.equal(result.monitor, false);
  assert.equal(result.severity, 'info');
});

test('rejects malformed events', () => {
  assert.equal(classifyRegistryEvent({ operation: 'execute', path: 'HKLM\\Software' }).accepted, false);
  assert.equal(classifyRegistryEvent({ operation: 'set_value', path: '' }).accepted, false);
});

test('registry filter remains telemetry-only and non-destructive', () => {
  assert.equal(REGISTRY_FILTER_BOUNDARY.telemetry_only, true);
  assert.equal(REGISTRY_FILTER_BOUNDARY.kernel_driver_included, false);
  assert.equal(REGISTRY_FILTER_BOUNDARY.automatic_registry_blocking, false);
  assert.equal(REGISTRY_FILTER_BOUNDARY.automatic_registry_modification, false);
});
