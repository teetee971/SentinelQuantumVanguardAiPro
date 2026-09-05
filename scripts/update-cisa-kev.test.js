import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import {
  DEFAULT_CISA_KEV_URL,
  assertOfficialCisaUrl,
  loadKevCatalogFromFile,
  normalizeKevCatalog,
  writeCatalogAtomically
} from './update-cisa-kev.js';

function validPayload() {
  return {
    title: 'CISA Known Exploited Vulnerabilities Catalog',
    catalogVersion: 'test-version',
    dateReleased: '2026.01.01',
    count: 2,
    vulnerabilities: [
      {
        cveID: 'CVE-2026-12345',
        vendorProject: 'Vendor B',
        product: 'Product B',
        vulnerabilityName: 'Example B',
        dateAdded: '2026-01-02',
        shortDescription: 'Example description B',
        requiredAction: 'Apply mitigations per vendor instructions.',
        dueDate: '2026-01-20',
        knownRansomwareCampaignUse: 'Unknown',
        notes: '',
        cwes: ['CWE-79', 'CWE-79']
      },
      {
        cveID: 'CVE-2025-9999',
        vendorProject: 'Vendor A',
        product: 'Product A',
        vulnerabilityName: 'Example A',
        dateAdded: '2026-01-01',
        shortDescription: 'Example description A',
        requiredAction: 'Apply updates.',
        dueDate: '2026-01-15',
        knownRansomwareCampaignUse: 'Known',
        notes: 'Reference note.'
      }
    ]
  };
}

test('normalizes a valid catalog deterministically', () => {
  const result = normalizeKevCatalog(validPayload());
  assert.equal(result.source, DEFAULT_CISA_KEV_URL);
  assert.equal(result.count, 2);
  assert.deepEqual(result.vulnerabilities.map((entry) => entry.cveID), ['CVE-2025-9999', 'CVE-2026-12345']);
  assert.deepEqual(result.vulnerabilities[1].cwes, ['CWE-79']);
});

test('rejects non-official source URLs', () => {
  assert.throws(() => assertOfficialCisaUrl('https://example.com/known_exploited_vulnerabilities.json'), /KEV_SOURCE_NOT_ALLOWED/);
  assert.throws(() => assertOfficialCisaUrl('http://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json'), /KEV_SOURCE_NOT_ALLOWED/);
});

test('rejects duplicate CVEs', () => {
  const payload = validPayload();
  payload.vulnerabilities[1].cveID = payload.vulnerabilities[0].cveID;
  assert.throws(() => normalizeKevCatalog(payload), /KEV_DUPLICATE_CVE/);
});

test('rejects count mismatch', () => {
  const payload = validPayload();
  payload.count = 999;
  assert.throws(() => normalizeKevCatalog(payload), /KEV_COUNT_MISMATCH/);
});

test('rejects malformed CVE and dates', () => {
  const malformedCve = validPayload();
  malformedCve.vulnerabilities[0].cveID = 'not-a-cve';
  assert.throws(() => normalizeKevCatalog(malformedCve), /KEV_INVALID_CVE/);

  const malformedDate = validPayload();
  malformedDate.vulnerabilities[0].dateAdded = '01/02/2026';
  assert.throws(() => normalizeKevCatalog(malformedDate), /KEV_INVALID_DATE/);
});

test('file ingestion and atomic output work without network access', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'sentinel-kev-'));
  const input = join(dir, 'input.json');
  const output = join(dir, 'output.json');
  await writeFile(input, JSON.stringify(validPayload()), 'utf8');

  const catalog = await loadKevCatalogFromFile(input);
  await writeCatalogAtomically(output, catalog);
  const persisted = JSON.parse(await readFile(output, 'utf8'));

  assert.equal(persisted.count, 2);
  assert.equal(persisted.source, DEFAULT_CISA_KEV_URL);
  assert.equal(persisted.vulnerabilities[0].cveID, 'CVE-2025-9999');
});
