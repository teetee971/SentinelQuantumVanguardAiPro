import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzeSms, normalizePhone, numberSummary, sanitizeState } from './phone-intelligence.js';

test('normalizes supported national and international numbers', () => {
  assert.equal(normalizePhone('06 12 34 56 78', 'FR'), '+33612345678');
  assert.equal(normalizePhone('+32 470 12 34 56', 'BE'), '+32470123456');
  assert.equal(normalizePhone('079 123 45 67', 'CH'), '+41791234567');
  assert.equal(normalizePhone('(514) 555-0123', 'CA'), '+15145550123');
});
test('rejects a country mismatch and invalid length', () => {
  assert.equal(normalizePhone('+41 79 123 45 67', 'FR'), null);
  assert.equal(normalizePhone('123', 'CA'), null);
});
test('SMS analysis exposes evidence and does not assert fraud', () => {
  const result = analyzeSms('URGENT : colis bloqué. Payez sur bit.ly/exemple avec votre carte bancaire.');
  assert.equal(result.level, 'élevé');
  assert.ok(result.signals.some((item) => item.id === 'short-link'));
  assert.ok(result.signals.some((item) => item.id === 'credentials'));
});
test('state sanitation rejects malformed numbers and bounds notes', () => {
  const state = sanitizeState({ reports: [{ number: 'invalid', country: 'FR' }, { number: '0612345678', country: 'FR', note: 'x'.repeat(900) }] });
  assert.equal(state.reports.length, 1);
  assert.equal(state.reports[0].note.length, 500);
});
test('summary keeps allow/block/report evidence separate', () => {
  const number = '+33612345678';
  const summary = numberSummary({ allowlist: [{ number, country: 'FR' }], reports: [{ number, country: 'FR', operator: 'déclaré' }] }, number);
  assert.deepEqual({ allowed: summary.allowed, blocked: summary.blocked, reportCount: summary.reportCount }, { allowed: true, blocked: false, reportCount: 1 });
});
