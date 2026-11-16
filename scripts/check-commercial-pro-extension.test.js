import test from 'node:test';
import assert from 'node:assert/strict';
import { validateProExtension } from './check-commercial-pro-extension.js';

const base = { modules: [{ id: 'phone_core' }] };
const capabilities = {
  capabilities: [
    { id: 'saas_identity', customer_available: false },
    { id: 'api_ready', customer_available: true }
  ]
};

const apiModule = (overrides = {}) => ({
  id: 'sentinel_api_pro',
  name: 'Sentinel API Pro',
  tier: 'PRO',
  offer: 'NOT_FOR_SALE',
  permanent_free: false,
  trial_candidate: false,
  trial_eligible: false,
  cost_class: 'LOW_SERVER',
  billable_remote_workload: true,
  required_capabilities: ['saas_identity'],
  fallback: 'REVOKE_API_ACCESS_KEEP_FREE_CLIENT',
  ...overrides
});

const extension = (module = apiModule()) => ({ schema_version: 1, modules: [module] });

test('Sentinel API Pro starts fail-closed', () => {
  assert.deepEqual(validateProExtension(base, extension(), capabilities), []);
});

test('consumer trial cannot unlock a Pro API', () => {
  const errors = validateProExtension(base, extension(apiModule({ trial_candidate: true, trial_eligible: true })), capabilities);
  assert.ok(errors.some((error) => error.includes('cannot use consumer trial')));
});

test('API Pro cannot lose SaaS identity dependency', () => {
  const errors = validateProExtension(base, extension(apiModule({ required_capabilities: ['api_ready'] })), capabilities);
  assert.ok(errors.some((error) => error.includes('must require saas_identity')));
});

test('unavailable capability prevents contract activation', () => {
  const errors = validateProExtension(base, extension(apiModule({ tier: 'ENTERPRISE', offer: 'CONTRACT_ONLY' })), capabilities);
  assert.ok(errors.some((error) => error.includes('cannot become CONTRACT_ONLY')));
});

test('extension cannot shadow a base catalog module', () => {
  const errors = validateProExtension(base, { schema_version: 1, modules: [apiModule({ id: 'phone_core' })] }, capabilities);
  assert.ok(errors.some((error) => error.includes('duplicate id already exists')));
});
