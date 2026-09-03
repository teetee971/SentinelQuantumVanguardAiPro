import assert from 'node:assert/strict';
import test from 'node:test';
import { validateProofWindow } from './proof-freshness.js';

const NOW = Date.parse('2026-09-03T12:00:00.000Z');
const WINDOW = {
  issuedAt: '2026-09-03T11:59:00.000Z',
  expiresAt: '2026-09-03T13:00:00.000Z',
};

test('accepts a currently valid proof window', () => {
  assert.equal(validateProofWindow({ ...WINDOW, now: NOW }).valid, true);
});

test('rejects an expired proof', () => {
  const result = validateProofWindow({ ...WINDOW, now: Date.parse('2026-09-03T13:00:00.000Z') });
  assert.equal(result.reason, 'PROOF_EXPIRED');
});

test('rejects a proof issued materially in the future', () => {
  const result = validateProofWindow({
    ...WINDOW,
    issuedAt: '2026-09-03T12:01:00.000Z',
    now: NOW,
  });
  assert.equal(result.reason, 'PROOF_ISSUED_IN_FUTURE');
});

test('rejects an invalid proof window', () => {
  const result = validateProofWindow({
    issuedAt: WINDOW.expiresAt,
    expiresAt: WINDOW.issuedAt,
    now: NOW,
  });
  assert.equal(result.reason, 'INVALID_PROOF_WINDOW');
});

test('rejects an invalid clock-skew configuration', () => {
  const result = validateProofWindow({ ...WINDOW, now: NOW, maxClockSkewMs: -1 });
  assert.equal(result.reason, 'INVALID_TIME_REFERENCE');
});
