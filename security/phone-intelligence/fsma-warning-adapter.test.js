import test from 'node:test';
import assert from 'node:assert/strict';

import { authorizeSource, createSourceRegistry } from './source-registry.js';
import {
  FSMA_SOURCE_ID,
  buildFsmaDomainIndex,
  extractDomains,
  fsmaSourceRecord,
  matchFsmaDomain,
  normalizeDomain,
  normalizeFsmaCsv,
  normalizeWarningDate,
  parseDelimitedCsv,
} from './fsma-warning-adapter.js';

test('parses bounded semicolon CSV with quoted commas and newlines', () => {
  const rows = parseDelimitedCsv(
    'Titre;"Nom de la société";Website;"Type de fraude";Date\r\n' +
    '"Alerte, importante";"Société A";"https://example.test, https://sub.example.test";Autre;03/02/2025\r\n'
  );
  assert.equal(rows.length, 2);
  assert.equal(rows[1][0], 'Alerte, importante');
  assert.equal(rows[1][1], 'Société A');
});

test('normalizes representative FSMA domains and rejects malformed guesses', () => {
  assert.equal(normalizeDomain('https://ProtectionLine.NET/'), 'protectionline.net');
  assert.equal(normalizeDomain(' https:// artosnomics.com '), 'artosnomics.com');
  assert.equal(normalizeDomain('https://www.quantexbelgica.com/path'), 'quantexbelgica.com');
  assert.equal(normalizeDomain('http://(client.)golden-currencies.net/'), null);
  assert.equal(normalizeDomain('https://user:pass@example.test/'), null);
});

test('extracts and deduplicates many websites from one warning', () => {
  assert.deepEqual(
    extractDomains('https://222-t.com, https://aliviofinances.com, https://www.222-t.com/path'),
    ['222-t.com', 'aliviofinances.com']
  );
});

test('accepts French FSMA export headings and preserves advisory provenance', () => {
  const csv =
    'Titre;"Nom de la société";Website;"Type de fraude";Date\n' +
    '"Des escrocs usurpent le nom de la FSMA";Protectionline;"https://protectionline.net/, https://regulatory-legal.systems/";Autre;03/02/2025\n' +
    '"25 nouvelles plateformes frauduleuses dans la liste de la FSMA";"Argentis Capora, Bitelity";"https://argentis-capora.com, https://bitelity.com";"Plateformes frauduleuses de trading en ligne";30/06/2026\n';

  const warnings = normalizeFsmaCsv(csv);
  assert.equal(warnings.length, 2);
  assert.equal(warnings[0].warningDate, '2026-06-30');
  assert.equal(warnings[0].sourceId, FSMA_SOURCE_ID);
  assert.equal(warnings[0].license, 'CC BY 4.0');
  assert.equal(warnings[0].autoBlock, false);
  assert.deepEqual(warnings[1].domains, ['protectionline.net', 'regulatory-legal.systems']);
});

test('supports English and Dutch header aliases without changing semantics', () => {
  const english =
    'Title;"Company name";Website;"Type of fraude";Date\n' +
    'Warning;Example;https://example.test;Others;20/08/2026\n';
  const dutch =
    'Titel;"Naam van de vennootschap";Website;"Type fraude";Datum\n' +
    'Waarschuwing;Voorbeeld;https://voorbeeld.test;Andere;20/08/2026\n';
  assert.equal(normalizeFsmaCsv(english)[0].domains[0], 'example.test');
  assert.equal(normalizeFsmaCsv(dutch)[0].domains[0], 'voorbeeld.test');
});

test('matches exact domains and subdomains but never turns a warning into auto-block', () => {
  const warnings = normalizeFsmaCsv(
    'Title;"Company name";Website;"Type of fraude";Date\n' +
    'Warning;Example;https://example.test;Others;20/08/2026\n'
  );
  const index = buildFsmaDomainIndex(warnings);
  const exact = matchFsmaDomain(index, 'https://example.test/login');
  const subdomain = matchFsmaDomain(index, 'https://secure.example.test/login');
  assert.equal(exact.matchedDomain, 'example.test');
  assert.equal(subdomain.matchedDomain, 'example.test');
  assert.equal(subdomain.advisoryOnly, true);
  assert.equal(subdomain.warning.autoBlock, false);
  assert.equal(matchFsmaDomain(index, 'https://example.test.attacker.invalid'), null);
});

test('validates calendar dates instead of accepting impossible CSV dates', () => {
  assert.equal(normalizeWarningDate('29/02/2024'), '2024-02-29');
  assert.equal(normalizeWarningDate('31/02/2024'), null);
  assert.equal(normalizeWarningDate('2026-08-20'), null);
});

test('builds a source-registry record only with explicit review evidence', () => {
  const record = fsmaSourceRecord({
    reviewedBy: 'compliance-review',
    reviewedAt: '2026-09-23T00:00:00Z',
    validUntil: '2027-09-23T00:00:00Z',
  });
  const registry = createSourceRegistry([record]);
  const authorization = authorizeSource(registry, {
    sourceId: FSMA_SOURCE_ID,
    scope: 'regulatory-domain-warning',
    at: Date.parse('2026-09-23T12:00:00Z'),
  });
  assert.equal(authorization.allowed, true);
  assert.equal(authorization.source.authorization_type, 'CC BY 4.0 public-information reuse');
  assert.throws(() => fsmaSourceRecord({}), /FSMA_REVIEWER_REQUIRED/);
});

test('fails closed on oversized, unterminated or structurally invalid CSV', () => {
  assert.throws(() => parseDelimitedCsv('"unterminated'), /FSMA_CSV_UNTERMINATED_QUOTE/);
  assert.throws(
    () => normalizeFsmaCsv('Title;Website;Date\nWarning;https://example.test;20/08/2026\n'),
    /FSMA_HEADER_MISSING_COMPANY/
  );
});
