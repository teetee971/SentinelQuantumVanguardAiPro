import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
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

test('repository MIT license is complete and never a placeholder', () => {
  const license = readFileSync(resolve('LICENSE'), 'utf8');
  assert.match(license, /^MIT License\s*$/m);
  assert.match(license, /Permission is hereby granted, free of charge, to any person obtaining a copy/);
  assert.match(license, /THE SOFTWARE IS PROVIDED "AS IS"/);
  assert.doesNotMatch(license, /placeholder/i);
});


test('public licensing copy distinguishes MIT source from future commercial services', () => {
  const legal = readFileSync(resolve('public/legal.html'), 'utf8');
  const strings = readFileSync(resolve('native-android-app/app/src/main/res/values/strings.xml'), 'utf8');
  assert.match(legal, /code source de ce dépôt est publié sur GitHub sous licence MIT/i);
  assert.match(legal, /Aucun APK officiel n’est distribué à ce stade/i);
  assert.match(legal, /services, accès serveur ou contrats de support/i);
  assert.match(strings, /Code source du dépôt : licence MIT/);
  assert.doesNotMatch(legal, /logiciel local est distribué sous licence via l'espace client/i);
});
