import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { assertCoverage, buildRegistry, extractItuAreas, coverageReport } from './numbering-sources/common/itu-registry.js';
const load = path => JSON.parse(readFileSync(path, 'utf8'));
const reference = load('config/numbering/itu-national-numbering-plans-reference.json');
const registry = load('config/global-numbering-source-registry.json');
const mapping = load('config/numbering/itu-iso-mapping.json');
const qualifications = load('config/numbering/source-qualifications.json');
const change = callback => { const copy = structuredClone(registry); callback(copy); return copy; };

test('every official NNP entry and prefix is represented, without a fixed country count', () => {
  assert.equal(assertCoverage(reference, registry), true);
  assert.deepEqual(buildRegistry(reference, mapping, qualifications), registry);
});
test('NANP countries keep their own NPAs and +1 never means US alone', () => {
  const jamaica = registry.entries.find(e => e.iso2 === 'JM');
  assert.equal(jamaica.countryCallingCode, '1');
  assert.deepEqual(jamaica.npaCodes, ['876','658']);
  const members = registry.entries.filter(e => e.nanpMember);
  assert.ok(members.some(e => e.iso2 === 'CA'));
  assert.ok(members.some(e => e.iso2 === 'AS'));
  assert.ok(members.some(e => e.iso2 === 'SX'));
  assert.equal(members.every(e => e.sharedCallingCode === '1'), true);
});
test('shared +7, +599 and international networks remain distinct', () => {
  for (const code of ['7','599']) {
    const entries = registry.entries.filter(e => e.countryCallingCode === code);
    assert.ok(entries.length > 1);
    assert.ok(entries.every(e => e.sharedCallingCode === code));
  }
  const iridium = registry.entries.find(e => e.ituDisplayName === 'Iridium');
  assert.equal(iridium.resourceScope, 'SHARED_NETWORK');
  assert.deepEqual(iridium.networkCodes, ['6','7']);
  assert.equal(iridium.iso2, null);
});
test('a missing territory, duplicate area, incorrect prefix or lost shared code fails', () => {
  for (const mutate of [r=>r.entries.pop(), r=>r.entries[0]=r.entries[1], r=>r.entries[0].countryCallingCode='999', r=>r.entries.find(e=>e.iso2==='CA').sharedCallingCode=null]) {
    assert.throws(() => assertCoverage(reference, change(mutate)));
  }
});
test('missing status, field, blocker, importer, test or integrated snapshot fails', () => {
  for (const mutate of [r=>delete r.entries[0].integrationStatus,r=>delete r.entries[0].regulatorName,r=>r.entries[0].blocker=null,r=>r.entries.find(e=>e.iso2==='FR').importerPath='missing.js',r=>r.entries.find(e=>e.iso2==='FR').importerTestPath='missing.test.js',r=>r.entries.find(e=>e.iso2==='FR').datasetPath='missing.json']) {
    assert.throws(() => assertCoverage(reference, change(mutate)));
  }
});
test('an unlinked, renamed or malformed source entry fails closed', () => {
  for (const mutate of [r=>r.areaLinks.pop(),r=>r.areaLinks[0].text='Invented',r=>r.areaLinks[0].url='https://evil.test/?parent=T0202000001',r=>r.catalogueText=r.catalogueText.replace('(+93)','(+000)')]) {
    const copy=structuredClone(reference);mutate(copy);
    assert.throws(()=>extractItuAreas(copy));
  }
});
test('original ITU names survive ISO enrichment and unrelated labels do not collide', () => {
  for (const name of ['Guinea','Equatorial Guinea','American Samoa','Samoa','Palestine, State of','Taiwan, China','Kosovo*']) {
    assert.equal(registry.entries.filter(e=>e.ituDisplayName===name).length,1);
  }
});
test('no worldwide percentage or false complete flag before remaining references are reconciled', () => {
  assert.equal(coverageReport(registry).globalCoveragePercentage, null);
  assert.equal(registry.coverageComplete, false);
  assert.throws(()=>assertCoverage(reference,change(r=>r.coverageComplete=true)),/ITU_FALSE_GLOBAL_COVERAGE/);
  assert.throws(()=>assertCoverage(reference,change(r=>r.referenceHash='0'.repeat(64))),/ITU_REFERENCE_PROVENANCE_CHANGED/);
});
test('coverage dashboard is regenerated exactly from the registry', () => {
  assert.deepEqual(coverageReport(registry),load('docs/data/global-numbering-coverage.json'));
});

test('distinct IDs cannot bind to the same existing ITU label', () => {
  const copy=structuredClone(reference);
  copy.areaLinks[1].text=copy.areaLinks[0].text;
  assert.throws(()=>extractItuAreas(copy),/ITU_LABEL_BINDING_DUPLICATED/);
});
test('qualifications cannot overwrite identity or provenance from ITU', () => {
  for (const key of ['iso2','countryCallingCode','officialNumberingPlanUrl','referenceHash','referenceFetchedAt']) {
    const copy=structuredClone(qualifications);copy.FR[key]='tampered';
    assert.throws(()=>buildRegistry(reference,mapping,copy),/ITU_QUALIFICATION_OVERRIDE/);
  }
  for (const key of ['officialNumberingPlanUrl','referenceHash','referenceFetchedAt']) {
    assert.throws(()=>assertCoverage(reference,change(r=>r.entries.find(e=>e.iso2==='FR')[key]='tampered')),/ITU_ENTRY_PROVENANCE_CHANGED/);
  }
});
test('Saint Pierre and Miquelon preserves technical territory scope', () => {
  assert.equal(registry.entries.find(e=>e.iso2==='PM').resourceScope,'TERRITORY');
});
