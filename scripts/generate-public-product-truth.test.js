import test from 'node:test';
import assert from 'node:assert/strict';
import { buildPublicProductTruth, serializePublicProductTruth } from './generate-public-product-truth.js';
import { renderCapabilityConsumers } from './render-product-capabilities.js';
import { readFileSync } from 'node:fs';

const payload = buildPublicProductTruth();
const registry = JSON.parse(readFileSync(new URL('../config/product-capabilities.json', import.meta.url), 'utf8'));

test('public snapshot carries the canonical commerce switches', () => {
  assert.equal(payload.commerce.checkout_enabled, false);
  assert.equal(payload.commerce.trial_enrollment_enabled, false);
  assert.equal(payload.commerce.trial_duration_days, 7);
});

test('public snapshot contains PTT and API Pro without opening them for sale', () => {
  const ptt = payload.modules.find((module) => module.id === 'sentinel_ptt');
  const api = payload.modules.find((module) => module.id === 'sentinel_api_pro');
  assert.equal(ptt?.tier, 'PREMIUM_INDIVIDUAL');
  assert.equal(ptt?.offer, 'NOT_FOR_SALE');
  assert.equal(api?.tier, 'PRO');
  assert.equal(api?.offer, 'NOT_FOR_SALE');
});

test('snapshot never exports internal blockers, evidence or source scope', () => {
  const serialized = serializePublicProductTruth(payload);
  assert.doesNotMatch(serialized, /"blockers"/);
  assert.doesNotMatch(serialized, /"evidence"/);
  assert.doesNotMatch(serialized, /"source_scope"/);
  assert.doesNotMatch(serialized, /verification_evidence/);
});

test('customer availability and effective evidence expiry mirror canonical consumers', () => {
  const renderedConsumer = JSON.parse(
    renderCapabilityConsumers(registry)['public/product-capabilities.json']
  );
  const renderedById = new Map(
    renderedConsumer.capabilities.map((capability) => [capability.id, capability])
  );

  for (const item of payload.capabilities) {
    const canonical = registry.capabilities.find((capability) => capability.id === item.id);
    const rendered = renderedById.get(item.id);
    assert.ok(canonical, `missing canonical capability ${item.id}`);
    assert.ok(rendered, `missing generated capability ${item.id}`);
    assert.equal(item.customer_available, canonical.customer_available === true);
    assert.equal(item.expires_at_ms, rendered.expires_at_ms);
  }
});

test('public runtime fails closed on expired capability evidence and uses canonical SaaS identity id', () => {
  const runtime = readFileSync(new URL('../public/product-truth.js', import.meta.url), 'utf8');
  assert.match(runtime, /capability\?\.customer_available === true/);
  assert.match(runtime, /Number\.isSafeInteger\(deadline\)/);
  assert.match(runtime, /deadline > now/);
  assert.match(runtime, /'\/public\/espace-client\.html': \{ capabilities: \['saas_identity'\] \}/);
  assert.doesNotMatch(runtime, /saas_customer_identity/);
});

test('committed product status page matches its canonical renderer', () => {
  const expected = renderCapabilityConsumers(registry)['public/product-status.html'];
  const actual = readFileSync(new URL('../public/product-status.html', import.meta.url), 'utf8');
  assert.equal(actual, expected);
});

test('public renderer is wired into truth-sensitive pages', () => {
  for (const page of ['../public/pricing.html', '../public/product-status.html', '../public/roadmap.html']) {
    const html = readFileSync(new URL(page, import.meta.url), 'utf8');
    assert.match(html, /product-truth\.js/);
  }
});
