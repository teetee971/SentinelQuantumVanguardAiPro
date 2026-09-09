import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzeSms, buildEnterpriseSearchUrl, findArcepAllocation, findArcepAllocationsByPrefix, normalizeArcepPrefix, normalizePhone, numberSummary, parseEnterpriseProfile, sanitizeState, toArcepNationalNumber } from './phone-intelligence.js';
import { createTranslator, normalizeLocale, resolveLocale } from './phone-intelligence-i18n.js';

test('normalizes supported national and international numbers', () => {
  assert.equal(normalizePhone('06 12 34 56 78', 'FR'), '+33612345678');
  assert.equal(normalizePhone('+32 470 12 34 56', 'BE'), '+32470123456');
  assert.equal(normalizePhone('079 123 45 67', 'CH'), '+41791234567');
  assert.equal(normalizePhone('(514) 555-0123', 'CA'), '+15145550123');
});
test('rejects a country mismatch and invalid length', () => {
  assert.equal(normalizePhone('+41 79 123 45 67', 'FR'), null);
  assert.equal(normalizePhone('123', 'CA'), null);
});
test('SMS analysis exposes evidence and does not assert fraud', () => {
  const result = analyzeSms('URGENT : colis bloqué. Payez sur bit.ly/exemple avec votre carte bancaire.');
  assert.equal(result.level, 'élevé');
  assert.ok(result.signals.some((item) => item.id === 'short-link'));
  assert.ok(result.signals.some((item) => item.id === 'credentials'));
});
test('state sanitation rejects malformed numbers and bounds notes', () => {
  const state = sanitizeState({ reports: [{ number: 'invalid', country: 'FR' }, { number: '0612345678', country: 'FR', note: 'x'.repeat(900) }] });
  assert.equal(state.reports.length, 1);
  assert.equal(state.reports[0].note.length, 500);
});
test('summary keeps allow/block/report evidence separate', () => {
  const number = '+33612345678';
  const summary = numberSummary({ allowlist: [{ number, country: 'FR' }], reports: [{ number, country: 'FR', operator: 'déclaré' }] }, number);
  assert.deepEqual({ allowed: summary.allowed, blocked: summary.blocked, reportCount: summary.reportCount }, { allowed: true, blocked: false, reportCount: 1 });
});
test('phone intelligence locale resolver is bounded to supported locales', () => {
  assert.equal(normalizeLocale('en-GB'), 'en');
  assert.equal(normalizeLocale('de-DE'), 'fr');
  assert.equal(resolveLocale({ storedLocale: 'en', browserLocale: 'fr-FR' }), 'en');
  assert.equal(resolveLocale({ storedLocale: 'de', browserLocale: 'en-US' }), 'en');
});
test('translator falls back deterministically and interpolates runtime values', () => {
  const en = createTranslator('en');
  assert.equal(en('runtime.reportCount', { count: 3 }), '3 report(s) on this device.');
  assert.equal(en('signal.credentials'), 'Possible request for credentials or banking data');
  assert.equal(en('missing.key'), 'missing.key');
});
test('ARCEP lookup resolves a French allocation without claiming current operator', () => {
  const directory = {
    schemaVersion: 2,
    operators: {
      ONE: ['Operator One', '11111111111111', 'Paris', '1 rue One', true, '01/01/2018'],
      TWO: ['Operator Two', '82902219300025', 'Metz', '4 rue Marconi', true, '14/04/2017']
    },
    entries: [
      ['0100000000', '0100999999', 'ONE', 'Métropole', '01/01/2020'],
      ['0612000000', '0612999999', 'TWO', 'Guadeloupe', '02/02/2021']
    ]
  };
  assert.equal(toArcepNationalNumber('+33612345678'), '0612345678');
  assert.deepEqual(findArcepAllocation(directory, '+33612345678'), {
    start: '0612000000',
    end: '0612999999',
    operatorCode: 'TWO',
    attributedOperator: 'Operator Two',
    territory: 'Guadeloupe',
    allocationDate: '02/02/2021',
    businessIdentifier: '82902219300025',
    rcs: 'Metz',
    address: '4 rue Marconi',
    canReceiveNumbering: true,
    declarationDate: '14/04/2017'
  });
  assert.equal(findArcepAllocation(directory, '+32470123456'), null);
  assert.equal(normalizeArcepPrefix('04 24 11'), '042411');
  assert.equal(normalizeArcepPrefix('+33 4 24 11'), '042411');
  assert.equal(normalizeArcepPrefix('424'), null);
  assert.deepEqual(findArcepAllocationsByPrefix(directory, '06 12'), [{
    start: '0612000000',
    end: '0612999999',
    operatorCode: 'TWO',
    attributedOperator: 'Operator Two',
    territory: 'Guadeloupe',
    allocationDate: '02/02/2021',
    businessIdentifier: '82902219300025',
    rcs: 'Metz',
    address: '4 rue Marconi',
    canReceiveNumbering: true,
    declarationDate: '14/04/2017'
  }]);
});

test('enterprise lookup is exact, bounded and tied to the requested SIRET', () => {
  assert.equal(buildEnterpriseSearchUrl('829 022 193 00025'),
    'https://recherche-entreprises.api.gouv.fr/search?q=82902219300025&per_page=1');
  assert.equal(buildEnterpriseSearchUrl('829022193'),
    'https://recherche-entreprises.api.gouv.fr/search?q=829022193&per_page=1');
  assert.equal(buildEnterpriseSearchUrl('82902219'), null);
  const payload = {
    total_results: 1,
    results: [{
      siren: '829022193',
      nom_complet: 'GET & GO TELECOM',
      activite_principale: '82.20Z',
      date_creation: '2017-04-10',
      etat_administratif: 'A',
      nombre_etablissements: 2,
      nombre_etablissements_ouverts: 1,
      siege: { siret: '82902219300025', adresse: '4 RUE MARCONI 57070 METZ', date_creation: '2023-04-18', date_fermeture: null },
      matching_etablissements: []
    }]
  };
  assert.deepEqual(parseEnterpriseProfile(payload, '82902219300025'), {
    siren: '829022193',
    name: 'GET & GO TELECOM',
    activityCode: '82.20Z',
    createdAt: '2017-04-10',
    active: true,
    totalEstablishments: 2,
    openEstablishments: 1,
    headOfficeSiret: '82902219300025',
    headOfficeAddress: '4 RUE MARCONI 57070 METZ',
    establishmentCreatedAt: '2023-04-18',
    establishmentClosedAt: null,
    directoryUrl: 'https://annuaire-entreprises.data.gouv.fr/entreprise/829022193'
  });
  assert.equal(parseEnterpriseProfile(payload, '11111111111111'), null);
});
