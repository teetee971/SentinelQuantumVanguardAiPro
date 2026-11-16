import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assertNotOlder,
  buildOfcomDirectory,
  discoverOfcomCsvUrls,
  parseCommaCsv,
  parseOfcomNumberingCsv
} from './update-ofcom-numbering.js';

const legacy = `SABC,D/DE,Status,Date,Communications Provider,Use,Notes,Change
7700,09,Allocated,N/A,Provider X,Mobile Services,10 Digit Numbers,01/2015
7700,1,Allocated,N/A,Provider Y,Mobile Services,10 Digit Numbers,02/2016
`;

const modern = `SABC Code,Range,SubRange,Communications Provider,Status
020,7946,0,BT,Reserved (Drama)
020,7946,1,BT,Allocated
`;

function allFiles(csv = legacy) {
  return Object.fromEntries(['s1.csv','s3.csv','s5.csv','s7.csv','s8.csv','s9.csv'].map((name) => [name, Buffer.from(csv)]));
}

test('parses quoted comma CSV safely', () => {
  assert.deepEqual(parseCommaCsv('a,b\n"x,y","z""q"\n'), [['a','b'], ['x,y','z"q']]);
});

test('parses legacy Ofcom SABC and D/DE schema into national prefixes', () => {
  const rows = parseOfcomNumberingCsv(legacy, 's7.csv');
  assert.equal(rows[0].start, '0770009');
  assert.equal(rows[0].end, '0770009');
  assert.equal(rows[0].provider, 'Provider X');
  assert.equal(rows[0].use, 'Mobile Services');
});

test('accepts current-style SABC Code / Range / SubRange aliases', () => {
  const rows = parseOfcomNumberingCsv(modern, 's1.csv');
  assert.equal(rows[0].start, '02079460');
  assert.equal(rows[0].status, 'Reserved (Drama)');
  assert.equal(rows[1].start, '02079461');
});

test('expands bounded D/DE ranges without inventing individual subscribers', () => {
  const csv = `SABC,D/DE,Status,Date,Communications Provider,Use,Notes,Change\n7790,0-9,Allocated,N/A,Provider Z,Mobile Services,,08/1999\n`;
  const [row] = parseOfcomNumberingCsv(csv, 's7.csv');
  assert.equal(row.start, '077900');
  assert.equal(row.end, '077909');
});

test('rejects schema drift instead of guessing', () => {
  assert.throws(
    () => parseOfcomNumberingCsv('prefix,owner\n0207,BT\n', 's1.csv'),
    /OFCOM_SCHEMA_COLUMN_MISSING/
  );
});

test('discovers only canonical telephone-number CSVs and captures page publication date', () => {
  const names = ['s1.csv','s3.csv','s5.csv','s7.csv','s8.csv','s9.csv'];
  const links = names.map((name, i) =>
    `<a href="/siteassets/resources/documents/phones-telecoms-and-internet/information-for-industry/numbering/regular-updates/telephone-numbers/${name}?v=${i}">${name}</a>`
  ).join('');
  const page = `<p>Current files publish date: 30 September 2026</p>${links}<a href="https://evil.example/s1.csv">bad</a>`;
  const result = discoverOfcomCsvUrls(page);
  assert.equal(result.sourcePublishedAt, '2026-09-30');
  assert.equal(result.links.size, 6);
  assert.match(result.links.get('s1.csv'), /^https:\/\/www\.ofcom\.org\.uk\//);
});

test('requires all six canonical data files', () => {
  assert.throws(
    () => discoverOfcomCsvUrls('<p>Current files publish date: 30 September 2026</p>'),
    /OFCOM_REQUIRED_FILE_MISSING/
  );
});

test('builds a compact provenance-preserving directory', () => {
  const directory = buildOfcomDirectory(allFiles(), {
    sourcePublishedAt: '2026-09-30',
    generatedAt: '2026-10-01T00:00:00.000Z'
  });
  assert.equal(directory.country, 'GB');
  assert.equal(directory.countryCallingCode, '+44');
  assert.equal(directory.recordCount, 12);
  assert.equal(directory.holders.length, 2);
  assert.equal(directory.statuses.length, 1);
  assert.equal(Object.keys(directory.sources).length, 6);
  assert.match(directory.semantics, /not necessarily the current provider/i);
});

test('rejects stale source publication rollback', () => {
  assert.throws(
    () => assertNotOlder({ sourcePublishedAt: '2026-09-30' }, '2026-09-23'),
    /OFCOM_ROLLBACK_REJECTED/
  );
  assert.doesNotThrow(() => assertNotOlder({ sourcePublishedAt: '2026-09-23' }, '2026-09-30'));
});
