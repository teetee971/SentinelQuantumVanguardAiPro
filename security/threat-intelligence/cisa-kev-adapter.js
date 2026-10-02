import { normalizeThreatObservation } from './normalization.js';

export const CISA_KEV_SOURCE_URI =
  'https://www.cisa.gov/known-exploited-vulnerabilities-catalog';

function dateAtUtcStart(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new TypeError('CISA_KEV_DATE_INVALID');
  }
  return `${value}T00:00:00.000Z`;
}

export function mapCisaKevEntryToObservation(entry, {
  retrievedAt,
  sourceUri = CISA_KEV_SOURCE_URI
} = {}) {
  if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
    throw new TypeError('CISA_KEV_ENTRY_REQUIRED');
  }
  if (typeof retrievedAt !== 'string' || !Number.isFinite(Date.parse(retrievedAt))) {
    throw new TypeError('CISA_KEV_RETRIEVED_AT_REQUIRED');
  }

  const cve = String(entry.cveID ?? '').trim().toUpperCase();
  const dateAdded = dateAtUtcStart(String(entry.dateAdded ?? '').trim());
  const dueDate = dateAtUtcStart(String(entry.dueDate ?? '').trim());
  const cwes = Array.isArray(entry.cwes) ? entry.cwes : [];

  return normalizeThreatObservation({
    observation_id: `cisa-kev:${cve}`,
    indicator_type: 'cve',
    indicator_value: cve,
    source_id: 'cisa_kev',
    source_kind: 'vulnerability_catalog',
    source_uri: sourceUri,
    source_verdict: 'unknown',
    confidence: 100,
    observed_at: dateAdded,
    retrieved_at: retrievedAt,
    legally_accessible: true,
    threat_type: 'known_exploited_vulnerability',
    vendor: entry.vendorProject,
    product: entry.product,
    exploit_status: 'known_exploited',
    ransomware_use: entry.knownRansomwareCampaignUse || 'Unknown',
    first_seen: dateAdded,
    due_date: dueDate,
    cwes,
    references: [sourceUri],
    tags: [
      'cisa-kev',
      'known-exploited',
      ...(entry.knownRansomwareCampaignUse === 'Known' ? ['known-ransomware-use'] : [])
    ]
  });
}

export function mapCisaKevCatalogToObservations(catalog, {
  retrievedAt = new Date().toISOString()
} = {}) {
  if (!catalog || typeof catalog !== 'object' || Array.isArray(catalog)) {
    throw new TypeError('CISA_KEV_CATALOG_REQUIRED');
  }
  if (!Array.isArray(catalog.vulnerabilities) || catalog.vulnerabilities.length === 0) {
    throw new TypeError('CISA_KEV_VULNERABILITIES_REQUIRED');
  }
  if (catalog.count !== undefined && catalog.count !== catalog.vulnerabilities.length) {
    throw new TypeError('CISA_KEV_COUNT_MISMATCH');
  }

  return Object.freeze(
    catalog.vulnerabilities
      .map(entry => mapCisaKevEntryToObservation(entry, { retrievedAt }))
      .sort((a, b) => a.indicator_value.localeCompare(b.indicator_value))
  );
}
