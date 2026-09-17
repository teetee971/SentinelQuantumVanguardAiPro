import test from 'node:test';
import assert from 'node:assert/strict';
import { buildArcepDirectory, hasSameArcepDirectoryContent, parseSemicolonCsv } from './update-arcep-numbering.js';

const NUMBERS = [
  'EZABPQM;Tranche_Debut;Tranche_Fin;Mnémo;Territoire;Date_Attribution',
  '01056;0105600000;0105699999;FRTE;Métropole;01/01/2017',
  '3999;3999;3999;TEST;National;22/09/2025'
].join('\n');
const OPERATORS = [
  'IDENTITE_OPERATEUR;CODE_OPERATEUR;SIRET_ACTEUR;RCS_ACTEUR;ADRESSE_COMPLETE_ACTEUR;ATTRIB_RESS_NUM;DATE_DECLARATION_OPERATEUR',
  '"Orange; France";FRTE;12345678901234;Paris;1 rue Test;1;10/01/2018',
  'Test;TEST;;Lyon;2 rue Test;0;11/02/2019'
].join('\n');

test('semicolon parser preserves quoted delimiters', () => {
  assert.equal(parseSemicolonCsv(OPERATORS)[1][0], 'Orange; France');
});

test('directory joins operator names and preserves ARCEP limitations', () => {
  const result = buildArcepDirectory(NUMBERS, OPERATORS, { generatedAt: '2026-09-09T00:00:00.000Z' });
  assert.equal(result.entries.length, 2);
  assert.equal(result.schemaVersion, 2);
  assert.deepEqual(result.operators.FRTE, ['Orange; France', '12345678901234', 'Paris', '1 rue Test', true, '10/01/2018']);
  assert.equal(result.entries[0][2], 'FRTE');
  assert.equal(result.sourceIntegrity.algorithm, 'sha256');
  assert.match(result.sourceIntegrity.numbering, /^[a-f0-9]{64}$/);
  assert.match(result.sourceIntegrity.operators, /^[a-f0-9]{64}$/);
  assert.match(result.semantics, /portabilité/);
  assert.match(result.semantics, /identité de l’appelant/);
});

test('directory rejects malformed ranges', () => {
  const malformed = NUMBERS.replace('0105600000;0105699999', 'abc;0105699999');
  assert.throws(() => buildArcepDirectory(malformed, OPERATORS), /ARCEP_INVALID_RANGE/);
});


test('content comparison ignores generation time but detects source changes', () => {
  const first = buildArcepDirectory(NUMBERS, OPERATORS, { generatedAt: '2026-09-14T07:00:00.000Z' });
  const second = buildArcepDirectory(NUMBERS, OPERATORS, { generatedAt: '2026-09-15T07:00:00.000Z' });
  assert.equal(hasSameArcepDirectoryContent(first, second), true);

  const changedNumbers = NUMBERS.replace('0105699999', '0105689999');
  const changed = buildArcepDirectory(changedNumbers, OPERATORS, { generatedAt: '2026-09-15T07:00:00.000Z' });
  assert.equal(hasSameArcepDirectoryContent(first, changed), false);
  assert.notEqual(first.sourceIntegrity.numbering, changed.sourceIntegrity.numbering);
});
