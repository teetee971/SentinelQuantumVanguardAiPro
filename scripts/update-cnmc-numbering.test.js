import assert from 'node:assert/strict';
import test from 'node:test';
import {
  assertSafeCnmcRefresh,
  buildCnmcDirectory,
  parseCnmcFiles
} from './update-cnmc-numbering.js';

const GEOGRAPHIC = Buffer.from([
  '851#94#Málaga#Asignado#ALHAMBRA SYSTEMS, S.A.#02/09/2021',
  '851#94#Málaga#Subasignado 02#INSTALACION Y ASESORAMIENTO, S.L.#21/11/2022',
  '960#295#Valencia#Asignado#OPERADOR C, S.L.#06/08/2026'
].join('\n'), 'latin1');

const MOBILE = Buffer.from([
  '600##GSM/DCS#Asignado#VODAFONE ESPAÑA, S.A.U.#19/11/1998',
  '601#0##Compartido#TENARIA, S.A.#19/10/2007',
  '601#6#GSM#Subasignado 0,1,2#LEAST COST ROUTING TELECOM, S.L.#22/02/2012'
].join('\n'), 'latin1');

function files() {
  return { 'geograficos.txt': GEOGRAPHIC, 'moviles.txt': MOBILE };
}

test('normalizes geographic assignment and subassignment as longest-prefix records', () => {
  const rows = parseCnmcFiles(files());
  const assigned = rows.find((row) => row.operator.startsWith('ALHAMBRA'));
  const sub = rows.find((row) => row.operator.startsWith('INSTALACION'));
  assert.equal(assigned.prefix, '85194');
  assert.equal(assigned.assignmentKind, 'assigned');
  assert.equal(sub.prefix, '8519402');
  assert.equal(sub.assignmentKind, 'subassigned');
  assert.equal(sub.province, 'Málaga');
});

test('normalizes mobile assignments, shared records and explicit subassignment suffixes', () => {
  const rows = parseCnmcFiles(files()).filter((row) => row.resourceType === 'mobile');
  assert.ok(rows.some((row) => row.prefix === '600' && row.assignmentKind === 'assigned'));
  assert.ok(rows.some((row) => row.prefix === '6010' && row.assignmentKind === 'shared'));
  assert.deepEqual(
    rows.filter((row) => row.assignmentKind === 'subassigned').map((row) => row.prefix),
    ['60160', '60161', '60162']
  );
});

test('rejects unknown status rather than guessing', () => {
  const bad = Buffer.from('600##GSM/DCS#Reservado#OPERADOR#19/11/1998', 'latin1');
  assert.throws(() => parseCnmcFiles({ ...files(), 'moviles.txt': bad }), /CNMC_STATUS_UNKNOWN/);
});

test('rejects malformed row width and impossible date', () => {
  const narrow = Buffer.from('600##GSM/DCS#Asignado#OPERADOR', 'latin1');
  assert.throws(() => parseCnmcFiles({ ...files(), 'moviles.txt': narrow }), /CNMC_ROW_WIDTH/);
  const badDate = Buffer.from('600##GSM/DCS#Asignado#OPERADOR#31/02/2026', 'latin1');
  assert.throws(() => parseCnmcFiles({ ...files(), 'moviles.txt': badDate }), /CNMC_DATE_INVALID/);
});

test('builds a CC BY-SA provenance-preserving directory', () => {
  const directory = buildCnmcDirectory(files(), Buffer.from('PK\u0003\u0004synthetic-archive'), {
    fetchedAt: '2026-10-01T20:00:00.000Z'
  });
  assert.equal(directory.country, 'ES');
  assert.equal(directory.countryCallingCode, '+34');
  assert.equal(directory.license, 'CC BY-SA 4.0');
  assert.equal(directory.recordCount, 8);
  assert.equal(directory.maxDecisionDate, '2026-08-06');
  assert.deepEqual(directory.coverage, ['geographic', 'mobile']);
  assert.ok(directory.excludes.includes('portability-current-carrier'));
  assert.match(directory.semantics, /not necessarily the current serving operator/i);
  assert.equal(directory.sourceIntegrity.files['geograficos.txt'].sha256.length, 64);
});

test('rejects decision-date rollback when source content changes', () => {
  assert.throws(() => assertSafeCnmcRefresh({
    fetchedAt: '2026-10-01T00:00:00.000Z',
    maxDecisionDate: '2026-09-25',
    recordCount: 100,
    sourceIntegrity: { files: {
      'geograficos.txt': { sha256: 'old-g' },
      'moviles.txt': { sha256: 'old-m' }
    } }
  }, {
    fetchedAt: '2026-10-02T00:00:00.000Z',
    maxDecisionDate: '2026-09-20',
    recordCount: 100,
    sourceIntegrity: { files: {
      'geograficos.txt': { sha256: 'new-g' },
      'moviles.txt': { sha256: 'new-m' }
    } }
  }), /CNMC_DECISION_DATE_ROLLBACK/);
});

test('rejects anomalous record collapse', () => {
  assert.throws(() => assertSafeCnmcRefresh({
    fetchedAt: '2026-10-01T00:00:00.000Z',
    maxDecisionDate: '2026-09-25',
    recordCount: 100,
    sourceIntegrity: { files: {
      'geograficos.txt': { sha256: 'old-g' },
      'moviles.txt': { sha256: 'old-m' }
    } }
  }, {
    fetchedAt: '2026-10-02T00:00:00.000Z',
    maxDecisionDate: '2026-09-26',
    recordCount: 70,
    sourceIntegrity: { files: {
      'geograficos.txt': { sha256: 'new-g' },
      'moviles.txt': { sha256: 'new-m' }
    } }
  }), /CNMC_SUSPICIOUS_ROW_DROP/);
});
