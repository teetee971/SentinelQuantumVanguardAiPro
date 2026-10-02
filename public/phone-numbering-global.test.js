import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createGlobalNumberingLookup } from './phone-numbering-global.js';
import { buildLookupManifest } from '../scripts/generate-numbering-lookup-manifest.js';
const manifest = JSON.parse(readFileSync(new URL('./data/numbering-lookup-manifest.json', import.meta.url)));
const lookup = createGlobalNumberingLookup(manifest);
test('local reference retains ITU provenance and incomplete coverage', () => {
  const result = lookup('+33123456789');
  assert.equal(result.candidates[0].iso2, 'FR');
  assert.equal(result.evidence.coverageComplete, false);
  assert.equal(result.evidence.referenceHash, manifest.referenceHash);
  assert.equal(result.numberPlanValidation, 'NOT_PERFORMED');
  assert.equal(result.currentCarrier, null);
  assert.equal(result.subscriberIdentity, null);
  assert.equal(result.spoofingAssessment, 'INSUFFICIENT_EVIDENCE');
});
test('NANP resolves only an explicit NPA and preserves US/Canada uncertainty', () => {
  assert.equal(lookup('+16845551234').candidates[0].iso2, 'AS');
  const result = lookup('+12125551234');
  assert.equal(result.status, 'AMBIGUOUS_SHARED_PLAN');
  assert.deepEqual(new Set(result.candidates.map(x => x.iso2)), new Set(['US', 'CA']));
});
test('shared +7 and +599 remain ambiguous', () => {
  assert.equal(lookup('+77011234567').status, 'AMBIGUOUS_SHARED_PLAN');
  assert.equal(lookup('+59991234567').status, 'AMBIGUOUS_SHARED_PLAN');
});
test('international network references are separate and unlisted services are unknown', () => {
  assert.equal(lookup('+882161234567').candidates[0].resourceScope, 'SHARED_NETWORK');
  assert.equal(lookup('+80012345678').status, 'REFERENCE_ENTRY_NOT_FOUND');
});
test('syntax is strict without inventing national validity', () => {
  for (const value of ['0033123456789', '+0', '+33 123', '+1234567890123456', null]) {
    assert.equal(lookup(value).status, 'INVALID_E164_SYNTAX');
  }
  assert.equal(lookup('+33').status, 'PREFIX_REFERENCE_FOUND');
});
test('caller mutations cannot alter later lookup results', () => {
  const first = lookup('+33123456789');
  assert.throws(() => { first.candidates[0].iso2 = 'US'; });
  first.candidates.length = 0;
  assert.equal(lookup('+33123456789').candidates[0].iso2, 'FR');
});
test('generator is deterministic and rejects malformed or duplicate records', () => {
  const registry = { ...manifest, entries: manifest.entries };
  const bytes = JSON.stringify(registry);
  assert.deepEqual(buildLookupManifest(bytes), buildLookupManifest(bytes));
  assert.throws(() => buildLookupManifest(JSON.stringify({ ...registry, referenceHash: 'bad' })));
  assert.throws(() => buildLookupManifest(JSON.stringify({ ...registry, entries: [registry.entries[0], registry.entries[0]] })));
  assert.throws(() => createGlobalNumberingLookup({ ...manifest, entries: [{ ...manifest.entries[0], dialPrefixes: ['bad'] }] }));
});

test('numeric prefixes fail at generation and lookup construction instead of becoming unreachable map keys', () => {
  for (const prefix of [33, null, true, { toString: () => '33' }]) {
    const changed = { ...manifest, entries: [{ ...manifest.entries.find(e => e.iso2 === 'FR'), dialPrefixes: [prefix] }] };
    assert.throws(() => createGlobalNumberingLookup(changed), /Invalid dial prefix/);
    assert.throws(() => buildLookupManifest(JSON.stringify(changed)), /Invalid registry area/);
  }
});
