import assert from 'node:assert/strict';
import test from 'node:test';
import { resolve } from 'node:path';

import { auditPublicDisclosure } from './check-public-disclosure-boundary.js';

test('rejects named threat-intelligence feeds in public files', () => {
  const errors = auditPublicDisclosure(
    resolve('public/example.html'),
    '<p>ThreatFox and URLhaus are our feeds</p>'
  );
  assert.ok(errors.some(error => error.includes('threat-intelligence source identity exposed')));
});

test('rejects exact runtime and ledger internals in marketing pages', () => {
  const errors = auditPublicDisclosure(
    resolve('public/roadmap.html'),
    'Redis snapshot_hash sentinel-moteur-api.onrender.com'
  );
  assert.ok(errors.length >= 3);
});

test('allows generic defensive wording', () => {
  const errors = auditPublicDisclosure(
    resolve('public/threat-intelligence.html'),
    'Sentinel corrèle plusieurs catégories de renseignement défensif indépendantes.'
  );
  assert.deepEqual(errors, []);
});

test('legal pages may disclose infrastructure recipients when legally required', () => {
  const errors = auditPublicDisclosure(
    resolve('public/privacy.html'),
    'Cloud hosting and Redis may be disclosed for transparency.'
  );
  assert.deepEqual(errors, []);
});
