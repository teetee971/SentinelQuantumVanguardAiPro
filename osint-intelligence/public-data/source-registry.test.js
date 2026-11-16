import assert from 'node:assert/strict';
import test from 'node:test';

import {
  assertApprovedPublicDataUrl,
  getPublicDataSource,
  publicDataSourceIds
} from './source-registry.js';

test('registers the requested French public-data sources', () => {
  assert.deepEqual(publicDataSourceIds(), ['annuaire_entreprises', 'data_gouv_recent', 'pci_cadastre']);
  assert.equal(getPublicDataSource('annuaire_entreprises').source_kind, 'business_search');
  assert.equal(getPublicDataSource('pci_cadastre').ingestion_mode, 'metadata_and_explicit_download');
});

test('uses the cadastre latest alias instead of pinning a stale monthly snapshot', () => {
  assert.match(getPublicDataSource('pci_cadastre').latest_download_base, /\/latest\/edigeo\/departements\/$/);
});

test('only allows explicitly approved HTTPS public-data hosts', () => {
  assert.match(assertApprovedPublicDataUrl('https://www.data.gouv.fr/api/1/datasets/recent.atom'), /^https:/);
  assert.throws(() => assertApprovedPublicDataUrl('http://www.data.gouv.fr/api/1/datasets/recent.atom'), /PUBLIC_DATA_SOURCE_NOT_APPROVED/);
  assert.throws(() => assertApprovedPublicDataUrl('https://example.com/feed'), /PUBLIC_DATA_SOURCE_NOT_APPROVED/);
});

test('unknown sources fail closed', () => {
  assert.equal(getPublicDataSource('unknown'), null);
});
