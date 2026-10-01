import assert from 'node:assert/strict';
import test from 'node:test';
import { assertSafeNkomRefresh, buildNkomDirectory, parseNkomCsv } from './update-nkom-numbering.js';

const CSV = [
  '"Fra","Til","Tilbyder","Status","Kommentar","Antall","Kategori","Punktkode"',
  '"21 00 00 00","21 09 99 99","TELIA NORGE AS","Tildelt",,"100,000","Fastnettnummer",',
  '"21 10 00 00","21 30 79 99"," ","Ledig",,"208,000","Fastnettnummer",',
  '"20 00 00 00","20 99 99 99","Nasjonal kommunikasjonsmyndighet","Blokkert","administrativ","1,000,000","Fastnettnummer","47-1"'
].join('\n');

test('parses the official eight-column Nkom CSV shape', () => {
  const rows = parseNkomCsv(CSV);
  assert.equal(rows.length, 3);
  assert.deepEqual(rows[0], {
    start: '21000000',
    end: '21099999',
    holder: 'TELIA NORGE AS',
    status: 'allocated',
    sourceStatus: 'Tildelt',
    comment: '',
    count: 100000,
    category: 'Fastnettnummer',
    pointCode: ''
  });
  assert.equal(rows[1].status, 'available');
  assert.equal(rows[1].holder, '');
  assert.equal(rows[2].status, 'blocked');
});

test('rejects count and numeric range disagreement', () => {
  assert.throws(
    () => parseNkomCsv(CSV.replace('"100,000"', '"99,999"')),
    /NKOM_RANGE_COUNT_MISMATCH/
  );
});

test('rejects unknown status and schema drift', () => {
  assert.throws(() => parseNkomCsv(CSV.replace('"Tildelt"', '"Reservert"')), /NKOM_STATUS_UNKNOWN/);
  assert.throws(() => parseNkomCsv(CSV.replace('"Punktkode"', '"Kode"')), /NKOM_SCHEMA_HEADERS_MISMATCH/);
});

test('rejects allocated rows with no published holder', () => {
  const bad = CSV.replace('"TELIA NORGE AS","Tildelt"', '" ","Tildelt"');
  assert.throws(() => parseNkomCsv(bad), /NKOM_ALLOCATED_WITHOUT_HOLDER/);
});

test('builds an NLOD-attributed provenance directory', () => {
  const directory = buildNkomDirectory(Buffer.from(CSV), { fetchedAt: '2026-10-01T20:00:00.000Z' });
  assert.equal(directory.country, 'NO');
  assert.equal(directory.countryCallingCode, '+47');
  assert.equal(directory.license, 'NLOD 2.0');
  assert.equal(directory.recordCount, 3);
  assert.equal(directory.sourceIntegrity.csv.length, 64);
  assert.match(directory.attribution, /Nasjonal kommunikasjonsmyndighet/);
  assert.match(directory.semantics, /not proof of the current serving provider/i);
});

test('rejects fetch-time rollback', () => {
  assert.throws(() => assertSafeNkomRefresh({
    fetchedAt: '2026-10-02T00:00:00.000Z',
    recordCount: 100,
    sourceIntegrity: { csv: 'old' }
  }, {
    fetchedAt: '2026-10-01T00:00:00.000Z',
    recordCount: 100,
    sourceIntegrity: { csv: 'new' }
  }), /NKOM_FETCH_TIME_ROLLBACK/);
});

test('rejects anomalous row collapse when source content changes', () => {
  assert.throws(() => assertSafeNkomRefresh({
    fetchedAt: '2026-10-01T00:00:00.000Z',
    recordCount: 100,
    sourceIntegrity: { csv: 'old' }
  }, {
    fetchedAt: '2026-10-02T00:00:00.000Z',
    recordCount: 70,
    sourceIntegrity: { csv: 'new' }
  }), /NKOM_SUSPICIOUS_ROW_DROP/);
});
