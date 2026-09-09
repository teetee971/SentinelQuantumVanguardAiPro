import test from 'node:test';
import assert from 'node:assert/strict';
import { buildArcepDirectory, parseSemicolonCsv } from './update-arcep-numbering.js';

const NUMBERS = [
  'EZABPQM;Tranche_Debut;Tranche_Fin;Mnémo;Territoire;Date_Attribution',
  '01056;0105600000;0105699999;FRTE;Métropole;01/01/2017',
  '3999;3999;3999;TEST;National;22/09/2025'
].join('\n');
const OPERATORS = [
  'IDENTITE_OPERATEUR;CODE_OPERATEUR;SIRET_ACTEUR',
  '"Orange; France";FRTE;123',
  'Test;TEST;456'
].join('\n');

test('semicolon parser preserves quoted delimiters', () => {
  assert.equal(parseSemicolonCsv(OPERATORS)[1][0], 'Orange; France');
});

test('directory joins operator names and preserves ARCEP limitations', () => {
  const result = buildArcepDirectory(NUMBERS, OPERATORS, { generatedAt: '2026-09-09T00:00:00.000Z' });
  assert.equal(result.entries.length, 2);
  assert.equal(result.entries[0][3], 'Orange; France');
  assert.match(result.semantics, /portabilité/);
});

test('directory rejects malformed ranges', () => {
  const malformed = NUMBERS.replace('0105600000;0105699999', 'abc;0105699999');
  assert.throws(() => buildArcepDirectory(malformed, OPERATORS), /ARCEP_INVALID_RANGE/);
});
