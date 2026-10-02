import assert from 'node:assert/strict';
import test from 'node:test';

import {
  mapCisaKevCatalogToObservations,
  mapCisaKevEntryToObservation
} from './cisa-kev-adapter.js';

const entry = () => ({
  cveID: 'CVE-2026-12345',
  vendorProject: 'Vendor',
  product: 'Product',
  vulnerabilityName: 'Example vulnerability',
  dateAdded: '2026-10-01',
  shortDescription: 'Observed exploitation.',
  requiredAction: 'Apply mitigations.',
  dueDate: '2026-10-05',
  knownRansomwareCampaignUse: 'Known',
  cwes: ['CWE-287']
});

test('maps KEV membership as exploited-vulnerability evidence, not malware verdict', () => {
  const result = mapCisaKevEntryToObservation(entry(), {
    retrievedAt: '2026-10-02T10:00:00Z'
  });

  assert.equal(result.indicator_type, 'cve');
  assert.equal(result.indicator_value, 'CVE-2026-12345');
  assert.equal(result.source_verdict, 'unknown');
  assert.equal(result.exploit_status, 'known_exploited');
  assert.equal(result.ransomware_use, 'Known');
  assert.equal(result.confidence, 100);
  assert.ok(result.tags.includes('known-exploited'));
  assert.ok(result.tags.includes('known-ransomware-use'));
});

test('maps catalogs deterministically and rejects count mismatch', () => {
  const catalog = {
    count: 2,
    vulnerabilities: [
      { ...entry(), cveID: 'CVE-2026-99999' },
      entry()
    ]
  };
  const observations = mapCisaKevCatalogToObservations(catalog, {
    retrievedAt: '2026-10-02T10:00:00Z'
  });

  assert.deepEqual(
    observations.map(item => item.indicator_value),
    ['CVE-2026-12345', 'CVE-2026-99999']
  );

  assert.throws(
    () => mapCisaKevCatalogToObservations(
      { ...catalog, count: 99 },
      { retrievedAt: '2026-10-02T10:00:00Z' }
    ),
    /CISA_KEV_COUNT_MISMATCH/
  );
});
