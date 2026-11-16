import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildFsmaIndex,
  extractMessageDomains,
  matchFsmaDomains,
  validateFsmaSnapshot,
} from './fsma-regulatory.js';

const snapshot = {
  schemaVersion: 1,
  generatedAt: '2026-10-17T00:00:00.000Z',
  advisoryOnly: true,
  source: { id: 'fsma-belgium-warnings' },
  warnings: [
    {
      title: 'Warning',
      company: 'Example SA',
      warningDate: '2026-08-20',
      domains: ['fraud.example'],
      sourceId: 'fsma-belgium-warnings',
      sourceUrl: 'https://www.fsma.be/en/warnings/companies-operating-unlawfully-in-belgium',
      license: 'CC BY 4.0',
      autoBlock: false,
    },
  ],
};

test('FSMA snapshot must remain advisory-only and bounded', () => {
  assert.equal(validateFsmaSnapshot(snapshot), snapshot);
  assert.equal(validateFsmaSnapshot({ ...snapshot, advisoryOnly: false }), null);
  assert.equal(validateFsmaSnapshot({
    ...snapshot,
    warnings: [{ ...snapshot.warnings[0], autoBlock: true }],
  }), null);
});

test('SMS domain extraction never fetches or follows message links', () => {
  const domains = extractMessageDomains('Urgent https://sub.fraud.example/pay et www.safe.test/a');
  assert.deepEqual(domains, ['sub.fraud.example', 'safe.test']);
});

test('FSMA matching supports warned parent domains and stays advisory', () => {
  const index = buildFsmaIndex(snapshot);
  const matches = matchFsmaDomains(index, ['sub.fraud.example']);
  assert.equal(matches.length, 1);
  assert.equal(matches[0].matchedDomain, 'fraud.example');
  assert.equal(matches[0].warnings[0].autoBlock, false);
});
