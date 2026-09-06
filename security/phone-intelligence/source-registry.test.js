import test from 'node:test';
import assert from 'node:assert/strict';
import { authorizeSource, createSourceRegistry } from './source-registry.js';

const record = {
  source_id: 'licensed-carrier-feed',
  name: 'Licensed carrier feed',
  status: 'authorized',
  authorization_type: 'contract',
  authorization_reference: 'contract-ref-001',
  evidence_url: 'https://example.test/evidence/contract-ref-001',
  reviewed_by: 'compliance-review',
  reviewed_at: '2026-09-01T00:00:00Z',
  valid_from: '2026-09-01T00:00:00Z',
  valid_until: '2027-09-01T00:00:00Z',
  scopes: ['reputation-publication', 'carrier-signal'],
};

test('authorizes only a registered source, valid time and explicit scope', () => {
  const registry = createSourceRegistry([record]);
  const result = authorizeSource(registry, {
    sourceId: record.source_id,
    scope: 'reputation-publication',
    at: Date.parse('2026-09-06T00:00:00Z'),
  });
  assert.equal(result.allowed, true);
  assert.equal(result.reason, 'SOURCE_AUTHORIZED');
});

test('fails closed for unregistered, suspended and unauthorized scopes', () => {
  const registry = createSourceRegistry([record]);
  assert.equal(authorizeSource(registry, {
    sourceId: 'unknown', scope: 'reputation-publication', at: Date.parse('2026-09-06T00:00:00Z'),
  }).reason, 'SOURCE_NOT_REGISTERED');
  assert.equal(authorizeSource(registry, {
    sourceId: record.source_id, scope: 'numbering-reference', at: Date.parse('2026-09-06T00:00:00Z'),
  }).reason, 'SOURCE_SCOPE_NOT_AUTHORIZED');

  const suspended = createSourceRegistry([{ ...record, status: 'suspended' }]);
  assert.equal(authorizeSource(suspended, {
    sourceId: record.source_id, scope: 'reputation-publication', at: Date.parse('2026-09-06T00:00:00Z'),
  }).reason, 'SOURCE_NOT_AUTHORIZED');
});

test('rejects expired authorization and malformed evidence configuration', () => {
  const registry = createSourceRegistry([record]);
  assert.equal(authorizeSource(registry, {
    sourceId: record.source_id,
    scope: 'reputation-publication',
    at: Date.parse('2027-09-01T00:00:00Z'),
  }).reason, 'SOURCE_AUTHORIZATION_OUTSIDE_VALIDITY');

  assert.throws(() => createSourceRegistry([{ ...record, evidence_url: 'http://insecure.test/evidence' }]), /SOURCE_EVIDENCE_URL_INVALID/);
  assert.throws(() => createSourceRegistry([{ ...record, scopes: ['reputation-publication', 'reputation-publication'] }]), /SOURCE_SCOPES_INVALID/);
});
