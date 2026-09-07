import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLegalManifest } from './check-legal-readiness.js';

const valid = () => ({
  schema_version: 1,
  publisher: {
    legal_name: 'Sentinel Publisher SAS',
    publication_director: 'Verified Director',
    contact_email: 'security@sentinel.test',
    contact_address: '1 Security Avenue, 75000 Paris',
    country: 'France'
  },
  hosting: {
    provider: 'Cloudflare Pages',
    public_information_url: 'https://www.cloudflare.com/'
  },
  reviewed_at: '2026-09-07T00:00:00Z',
  reviewed_by: 'release-reviewer'
});

test('accepts a complete, non-placeholder legal manifest', () => {
  assert.deepEqual(validateLegalManifest(valid()), { ok: true, errors: [] });
});

test('fails closed on missing legal identity fields', () => {
  const manifest = valid();
  delete manifest.publisher.publication_director;
  const result = validateLegalManifest(manifest);
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes('publisher.publication_director: missing'));
});

test('rejects placeholder identity data and insecure hosting URL', () => {
  const manifest = valid();
  manifest.publisher.legal_name = 'TODO';
  manifest.hosting.public_information_url = 'http://example.invalid/hosting';
  const result = validateLegalManifest(manifest);
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((item) => item.includes('placeholder value rejected')));
  assert.ok(result.errors.includes('hosting.public_information_url: HTTPS required'));
});

test('rejects malformed email and review timestamp', () => {
  const manifest = valid();
  manifest.publisher.contact_email = 'not-an-email';
  manifest.reviewed_at = 'not-a-date';
  const result = validateLegalManifest(manifest);
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes('publisher.contact_email: invalid email'));
  assert.ok(result.errors.includes('reviewed_at: invalid timestamp'));
});
