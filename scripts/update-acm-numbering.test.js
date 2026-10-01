import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assertSafeRefresh,
  buildAcmDirectory,
  parseAcmCsv
} from './update-acm-numbering.js';

const CSV = [
  'Beginnummer;Eindnummer;Bestemming;Status;Nummerhouder;Datum beschikking',
  '0644800000;0644899999;Mobiele telefonie;Toegekend;Provider A B.V.;30-09-2026',
  '0207000000;0207099999;Geografische nummers;Afkoelen;;29/09/2026',
  '0881000000;0881099999;Bedrijfsnummers;Geblokkeerd;Organisatie B;2026-09-28'
].join('\n');

test('parses current register semantics without carrier inference', () => {
  const rows = parseAcmCsv(CSV);
  assert.equal(rows.length, 3);
  assert.deepEqual(rows[0], {
    start: '0644800000',
    end: '0644899999',
    status: 'allocated',
    destination: 'Mobiele telefonie',
    holder: 'Provider A B.V.',
    decisionDate: '2026-09-30'
  });
  assert.equal(rows[1].status, 'cooling_off');
  assert.equal(rows[2].status, 'blocked');
});

test('accepts a single range column and formatted Dutch phone numbers', () => {
  const csv = [
    'Reeks;Bestemming;Status;Nummerhouder;Datum beschikking',
    '064-4800000 t/m 064-4899999;Mobiele telefonie;Toegekend;Provider A;30-09-2026'
  ].join('\n');
  const [row] = parseAcmCsv(csv);
  assert.equal(row.start, '0644800000');
  assert.equal(row.end, '0644899999');
});

test('rejects unknown status instead of silently reclassifying it', () => {
  const csv = CSV.replace('Toegekend', 'NieuwStatus');
  assert.throws(() => parseAcmCsv(csv), /ACM_STATUS_UNKNOWN/);
});

test('rejects missing schema fields', () => {
  assert.throws(
    () => parseAcmCsv('Beginnummer;Eindnummer;Status\n0610000000;0619999999;Toegekend\n'),
    /ACM_SCHEMA_COLUMN_MISSING/
  );
});

test('builds a compact hash-provenance directory', () => {
  const csv = Buffer.from(CSV);
  const archive = Buffer.from('PK\u0003\u0004synthetic-test-archive');
  const directory = buildAcmDirectory(csv, archive, { fetchedAt: '2026-10-01T20:00:00.000Z' });
  assert.equal(directory.country, 'NL');
  assert.equal(directory.countryCallingCode, '+31');
  assert.equal(directory.recordCount, 3);
  assert.equal(directory.holders.length, 2);
  assert.equal(directory.destinations.length, 3);
  assert.equal(directory.sourcePublishedAt, null);
  assert.match(directory.semantics, /not necessarily the current telecom provider/i);
  assert.equal(directory.sourceIntegrity.csv.length, 64);
  assert.equal(directory.sourceIntegrity.archive.length, 64);
});

test('same archive hash may refresh fetch time without creating rollback semantics', () => {
  const incoming = {
    fetchedAt: '2026-10-02T00:00:00.000Z',
    sourceIntegrity: { archive: 'same' },
    recordCount: 10
  };
  assert.doesNotThrow(() => assertSafeRefresh({
    fetchedAt: '2026-10-01T00:00:00.000Z',
    sourceIntegrity: { archive: 'same' },
    recordCount: 100
  }, incoming));
});

test('rejects fetch-time rollback', () => {
  assert.throws(() => assertSafeRefresh({
    fetchedAt: '2026-10-02T00:00:00.000Z',
    sourceIntegrity: { archive: 'old' },
    recordCount: 100
  }, {
    fetchedAt: '2026-10-01T00:00:00.000Z',
    sourceIntegrity: { archive: 'new' },
    recordCount: 100
  }), /ACM_FETCH_TIME_ROLLBACK/);
});

test('rejects a changed archive with an anomalous row-count collapse', () => {
  assert.throws(() => assertSafeRefresh({
    fetchedAt: '2026-10-01T00:00:00.000Z',
    sourceIntegrity: { archive: 'old' },
    recordCount: 100
  }, {
    fetchedAt: '2026-10-02T00:00:00.000Z',
    sourceIntegrity: { archive: 'new' },
    recordCount: 60
  }), /ACM_SUSPICIOUS_ROW_DROP/);
});
