import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assertSafeCtuRefresh,
  buildCtuDirectory,
  parseCtuCsv,
  parseCtuMetadata,
  validateCtuSchema
} from './update-ctu-numbering.js';

const HEADER = 'Číslo/Číselný rozsah;Počet čísel;Typ čísla;Podnikatel;IČO;Přiděleno od;Přiděleno do;Číslo reference';
const CSV = [
  HEADER,
  '14 112;1;14 – Služby;O2 Czech Republic a.s.;60193336;2003-01-23;2027-12-31;ČTÚ-45 112/2022-610',
  '800 333 318 až 800 333 319;2;800 – Přístup ke službám;O2 Czech Republic a.s.;60193336;2006-08-15;2030-12-31;ČTÚ-26 387/2026-610'
].join('\n');

const SCHEMA = JSON.stringify({
  tableSchema: {
    columns: [
      { titles: 'Číslo/Číselný rozsah', datatype: 'string', required: true },
      { titles: 'Počet čísel', datatype: 'integer', required: true },
      { titles: 'Typ čísla', datatype: 'string', required: true },
      { titles: 'Podnikatel', datatype: 'string', required: false },
      { titles: 'IČO', datatype: 'string', required: false },
      { titles: 'Přiděleno od', datatype: 'date', required: true },
      { titles: 'Přiděleno do', datatype: 'date', required: true },
      { titles: 'Číslo reference', datatype: 'string', required: true }
    ]
  }
});

const META = '<html><body><div>Datum aktualizace:</div><div>01.10.2026 - 04:40</div></body></html>';

test('validates the exact official eight-column CSVW schema', () => {
  assert.doesNotThrow(() => validateCtuSchema(SCHEMA));
  const changed = JSON.parse(SCHEMA);
  changed.tableSchema.columns[2].datatype = 'integer';
  assert.throws(() => validateCtuSchema(JSON.stringify(changed)), /CTU_SCHEMA_COLUMNS_MISMATCH/);
});

test('parses single numbers and Czech až ranges with count verification', () => {
  const rows = parseCtuCsv(CSV);
  assert.equal(rows.length, 2);
  assert.equal(rows[0].start, '14112');
  assert.equal(rows[0].end, '14112');
  assert.equal(rows[1].start, '800333318');
  assert.equal(rows[1].end, '800333319');
  assert.equal(rows[1].count, 2);
  assert.equal(rows[1].ico, '60193336');
});

test('rejects a declared count inconsistent with the published range', () => {
  assert.throws(() => parseCtuCsv(CSV.replace(';2;800 –', ';3;800 –')), /CTU_RANGE_COUNT_MISMATCH/);
});

test('rejects header drift', () => {
  assert.throws(() => parseCtuCsv(CSV.replace('Typ čísla', 'Typ')), /CTU_SCHEMA_HEADERS_MISMATCH/);
});

test('extracts source update time without pretending it is UTC', () => {
  const metadata = parseCtuMetadata(META);
  assert.deepEqual(metadata, {
    sourceUpdatedLocal: '2026-10-01T04:40:00',
    sourceTimeZone: 'Europe/Prague'
  });
});

test('builds a provenance-preserving Czech directory', () => {
  const directory = buildCtuDirectory(Buffer.from(CSV), Buffer.from(SCHEMA), Buffer.from(META), {
    fetchedAt: '2026-10-01T12:00:00.000Z'
  });
  assert.equal(directory.country, 'CZ');
  assert.equal(directory.countryCallingCode, '+420');
  assert.equal(directory.recordCount, 2);
  assert.equal(directory.holders.length, 1);
  assert.equal(directory.types.length, 2);
  assert.equal(directory.sourceUpdatedLocal, '2026-10-01T04:40:00');
  assert.equal(directory.sourceIntegrity.csv.length, 64);
  assert.match(directory.semantics, /not proof of current carrier/i);
});

test('rejects source timestamp rollback', () => {
  assert.throws(() => assertSafeCtuRefresh({
    sourceUpdatedLocal: '2026-10-01T04:40:00',
    sourceIntegrity: { csv: 'old' },
    recordCount: 100
  }, {
    sourceUpdatedLocal: '2026-09-30T04:40:00',
    sourceIntegrity: { csv: 'new' },
    recordCount: 100
  }), /CTU_SOURCE_ROLLBACK/);
});

test('rejects changed content under an unchanged source timestamp', () => {
  assert.throws(() => assertSafeCtuRefresh({
    sourceUpdatedLocal: '2026-10-01T04:40:00',
    sourceIntegrity: { csv: 'old' },
    recordCount: 100
  }, {
    sourceUpdatedLocal: '2026-10-01T04:40:00',
    sourceIntegrity: { csv: 'new' },
    recordCount: 100
  }), /CTU_SAME_TIMESTAMP_CONTENT_CHANGE/);
});

test('rejects a changed source with an anomalous row-count collapse', () => {
  assert.throws(() => assertSafeCtuRefresh({
    sourceUpdatedLocal: '2026-10-01T04:40:00',
    sourceIntegrity: { csv: 'old' },
    recordCount: 100
  }, {
    sourceUpdatedLocal: '2026-10-02T04:40:00',
    sourceIntegrity: { csv: 'new' },
    recordCount: 70
  }), /CTU_SUSPICIOUS_ROW_DROP/);
});

test('rejects nonexistent allocation calendar dates', () => {
  assert.throws(
    () => parseCtuCsv(CSV.replace('2003-01-23', '2026-02-31')),
    /CTU_ALLOCATED_FROM_INVALID/
  );
  assert.throws(
    () => parseCtuCsv(CSV.replace('2027-12-31', '2026-02-31')),
    /CTU_ALLOCATED_UNTIL_INVALID/
  );
});

test('rejects nonexistent metadata update dates and invalid local times', () => {
  assert.throws(
    () => parseCtuMetadata(META.replace('01.10.2026 - 04:40', '31.02.2026 - 04:40')),
    /CTU_SOURCE_UPDATED_AT_INVALID/
  );
  assert.throws(
    () => parseCtuMetadata(META.replace('01.10.2026 - 04:40', '01.10.2026 - 24:40')),
    /CTU_SOURCE_UPDATED_AT_INVALID/
  );
});
test('parses official hyphen-delimited CTU ranges', () => {
  const csv = [
    HEADER,
    '7-15;9;7 – Služby;O2 Czech Republic a.s.;60193336;2003-01-23;2027-12-31;ČTÚ-7/15'
  ].join('\n');
  const rows = parseCtuCsv(csv);
  assert.equal(rows[0].start, '7');
  assert.equal(rows[0].end, '15');
  assert.equal(rows[0].count, 9);
});
