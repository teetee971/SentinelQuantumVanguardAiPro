import test from 'node:test';
import assert from 'node:assert/strict';
import {
  capabilityMap,
  evaluateCommercialAccess,
  loadJson,
  validateCommercialCatalog
} from './check-commercial-catalog.js';

const catalog = loadJson('config/commercial-catalog.json');
const capabilities = loadJson('config/product-capabilities.json');

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

test('current canonical commercial catalog is internally valid', () => {
  assert.deepEqual(validateCommercialCatalog(catalog, capabilities), []);
});

test('permanent free baseline cannot become a paid or trial-gated module', () => {
  const mutated = clone(catalog);
  const phoneCore = mutated.modules.find((module) => module.id === 'phone_core');
  phoneCore.tier = 'PREMIUM_INDIVIDUAL';
  phoneCore.offer = 'ADDON';
  phoneCore.trial_candidate = true;

  const errors = validateCommercialCatalog(mutated, capabilities).join('\n');
  assert.match(errors, /permanent_free requires FREE tier/);
  assert.match(errors, /permanent_free module cannot be sold/);
  assert.match(errors, /permanent_free module cannot be trial-gated/);
});

test('checkout switch cannot make an unavailable technical capability sellable', () => {
  const mutated = clone(catalog);
  mutated.commerce.checkout_enabled = true;
  mutated.commerce.entitlement_provider = 'TEST_PROVIDER';

  const vpn = mutated.modules.find((module) => module.id === 'vpn_premium');
  vpn.offer = 'ADDON';

  const access = evaluateCommercialAccess(vpn, mutated, capabilityMap(capabilities));
  assert.equal(access.customerOfferable, false);
  assert.equal(access.reason, 'TECHNICAL_GATE');
});

test('trial cannot bypass unavailable capability evidence', () => {
  const mutated = clone(catalog);
  mutated.commerce.checkout_enabled = true;
  mutated.commerce.trial_enrollment_enabled = true;
  mutated.commerce.entitlement_provider = 'TEST_PROVIDER';

  const voice = mutated.modules.find((module) => module.id === 'voice_transform');
  voice.offer = 'TRIAL_BUNDLE';
  voice.trial_eligible = true;

  const errors = validateCommercialCatalog(mutated, capabilities).join('\n');
  assert.match(errors, /trial cannot bypass technical capability readiness/);
});

test('unknown capability dependency fails closed', () => {
  const mutated = clone(catalog);
  const module = mutated.modules.find((entry) => entry.id === 'system_doctor_pro');
  module.required_capabilities = ['does_not_exist'];

  const errors = validateCommercialCatalog(mutated, capabilities).join('\n');
  assert.match(errors, /unknown required capability does_not_exist/);
});

test('trial candidates are Premium only and are not currently eligible', () => {
  const candidates = catalog.modules.filter((module) => module.trial_candidate);
  assert.ok(candidates.length > 0);
  assert.ok(candidates.every((module) => module.tier === 'PREMIUM_INDIVIDUAL'));
  assert.ok(candidates.every((module) => module.trial_eligible === false));
  assert.equal(catalog.commerce.trial_enrollment_enabled, false);
});

test('critical local safety surfaces remain in permanent FREE', () => {
  const free = new Set(
    catalog.modules.filter((module) => module.permanent_free).map((module) => module.id)
  );

  for (const id of [
    'phone_core',
    'system_doctor_basic',
    'security_audit_basic',
    'app_permission_analyzer_basic',
    'sms_link_analysis_basic',
    'mail_shield_basic',
    'network_visibility_basic'
  ]) {
    assert.ok(free.has(id), `${id} must remain permanently free`);
  }
});

test('current catalog does not declare any paid module customer-offerable', () => {
  const capabilityIndex = capabilityMap(capabilities);
  const offerablePaid = catalog.modules.filter((module) =>
    !module.permanent_free && evaluateCommercialAccess(module, catalog, capabilityIndex).customerOfferable
  );
  assert.deepEqual(offerablePaid, []);
});

test('legacy fixed pricing is not embedded in module catalog', () => {
  for (const module of catalog.modules) {
    for (const key of Object.keys(module)) {
      assert.doesNotMatch(key, /(?:price|amount|monthly|annual|mrr|tcv)/i);
    }
  }
  assert.equal(catalog.commerce.canonical_price, null);
});
