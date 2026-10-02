import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

export function buildLookupManifest(registryBytes) {
  const registry = JSON.parse(registryBytes);
  if (registry.schemaVersion !== 1 || !Array.isArray(registry.entries) || !registry.entries.length ||
      !/^[a-f0-9]{64}$/.test(registry.referenceHash) || !registry.referenceUrl?.startsWith('https://www.itu.int/')) {
    throw new Error('Invalid ITU registry provenance');
  }
  const ids = new Set();
  const entries = registry.entries.map(entry => {
    if (!entry.id || ids.has(entry.id) || !Array.isArray(entry.dialPrefixes) || !entry.dialPrefixes.length ||
        entry.dialPrefixes.some(prefix => typeof prefix !== 'string' || !/^[1-9]\d{0,6}$/.test(prefix)) ||
        !['COUNTRY', 'TERRITORY', 'SHARED_NETWORK', 'GLOBAL_SERVICE'].includes(entry.resourceScope)) {
      throw new Error('Invalid registry area');
    }
    ids.add(entry.id);
    return { id: entry.id, iso2: entry.iso2, ituDisplayName: entry.ituDisplayName,
      countryCallingCode: entry.countryCallingCode, dialPrefixes: entry.dialPrefixes,
      resourceScope: entry.resourceScope, officialNumberingPlanUrl: entry.officialNumberingPlanUrl };
  });
  return { schemaVersion: 1, referenceScope: registry.referenceScope,
    referenceUrl: registry.referenceUrl, referenceHash: registry.referenceHash,
    referenceFetchedAt: registry.referenceFetchedAt,
    registryHash: createHash('sha256').update(registryBytes).digest('hex'),
    coverageComplete: registry.coverageComplete === true,
    outstandingReferenceScopes: registry.outstandingReferenceScopes, entries };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.length !== 4) throw new Error('Usage: node scripts/generate-numbering-lookup-manifest.js registry.json output.json');
  writeFileSync(process.argv[3], JSON.stringify(buildLookupManifest(readFileSync(process.argv[2], 'utf8')), null, 2) + '\n');
}
