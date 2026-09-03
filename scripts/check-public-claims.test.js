import assert from 'node:assert/strict';
import test from 'node:test';
import { findUnsupportedClaims } from './check-public-claims.js';

test('rejects unsupported operational claims', () => {
  const html = '<html><h1>SOC Live Fonctionnel</h1><p>Protection active et surveillance en temps réel.</p></html>';
  const findings = findUnsupportedClaims(html);
  assert.equal(findings.length, 3);
});

test('checks every occurrence of a risky claim', () => {
  const html = '<p>Protection active.</p><p>Protection active.</p>';
  assert.equal(findUnsupportedClaims(html).length, 2);
});

test('does not let a later sentence hide an earlier claim', () => {
  const html = '<p>Protection active. Aucune collecte de données.</p>';
  assert.equal(findUnsupportedClaims(html).length, 1);
});

test('allows explicit conceptual or negative wording', () => {
  const html = '<html><p>Pas de protection active. Les agents autonomes restent conceptuels.</p></html>';
  assert.deepEqual(findUnsupportedClaims(html), []);
});

test('ignores risky wording inside scripts and styles', () => {
  const html = '<html><style>.x{content:"protection active"}</style><script>const x = "SOC Fonctionnel";</script><p>Module de visualisation.</p></html>';
  assert.deepEqual(findUnsupportedClaims(html), []);
});
