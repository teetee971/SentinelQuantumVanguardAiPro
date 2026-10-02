import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const STATUSES = Object.freeze(['INTEGRATED','IMPORTER_READY','STRUCTURED_SOURCE_FOUND','OFFICIAL_SOURCE_FOUND','PLAN_ONLY','MANUAL_ONLY','RESTRICTED_ACCESS','NO_PUBLIC_BULK_DATA','SOURCE_NOT_YET_VERIFIED']);
const FIELDS = ['iso2','iso3','countryName','ituDisplayName','countryCallingCode','sharedCallingCode','nanpMember','regulatorName','regulatorUrl','numberingAuthorityName','numberingAuthorityUrl','officialNumberingPlanUrl','bulkDataUrl','apiUrl','sourceType','sourceFormat','license','portabilityAvailable','portabilitySource','allocationHolderAvailable','allocationDateAvailable','territoryAvailable','operatorCodeAvailable','refreshPolicy','lastSourceVerificationAt','integrationStatus','blocker','notes'];

export function extractItuAreas(reference) {
  if (reference.schemaVersion !== 1 || reference.referenceScope !== 'ITU_NATIONAL_NUMBERING_PLANS' || !/^[a-f0-9]{64}$/.test(reference.sourceHash)) throw new Error('ITU_REFERENCE_INVALID');
  const gn = reference.catalogueText.indexOf('- GN -');
  if (gn < 0 || !Array.isArray(reference.areaLinks) || !reference.areaLinks.length) throw new Error('ITU_REFERENCE_STRUCTURE');
  const seen = new Set();
  const result = reference.areaLinks.map(link => {
    const url = new URL(link.url);
    const id = url.searchParams.get('parent');
    if (url.origin !== 'https://www.itu.int' || !/^T0202[0-9A-F]{6}$/.test(id ?? '') || !link.text || seen.has(id)) throw new Error('ITU_REFERENCE_AREA_INVALID');
    seen.add(id);
    const marker = `${link.text} (+`;
    const positions = [];
    let cursor = 0;
    while (true) {
      const found = reference.catalogueText.indexOf(marker, cursor);
      if (found < 0) break;
      const before = reference.catalogueText.slice(0, found).trimEnd();
      if (before.endsWith(')') || /- [A-Z]+ -$/.test(before)) positions.push(found);
      cursor = found + marker.length;
    }
    if (positions.length !== 1) throw new Error('ITU_LABEL_AMBIGUOUS');
    const start = positions[0];
    const remainder = reference.catalogueText.slice(start + link.text.length + 2);
    const match = /^(\+[1-9][0-9 ]*(?:, \+[1-9][0-9 ]*)*)\)/.exec(remainder);
    if (!match) throw new Error('ITU_CALLING_PREFIX_INVALID');
    const codes = match[1].split(', ').map(code => code.slice(1).trim());
    const base = codes[0].split(' ')[0];
    if (!/^[1-9][0-9]{0,2}$/.test(base) || codes.some(code => code.split(' ')[0] !== base)) throw new Error('ITU_CALLING_CODE_INCONSISTENT');
    return { id, ituDisplayName: link.text, officialNumberingPlanUrl: link.url, countryCallingCode: base,
      dialPrefixes: codes.map(code => code.replaceAll(' ', '')), nanpMember: base === '1' && start < gn,
      npaCodes: base === '1' ? codes.filter(code => code.includes(' ')).map(code => code.split(' ')[1]) : [],
      networkCodes: start > gn ? codes.filter(code => code.includes(' ')).map(code => code.split(' ').slice(1).join('')) : [],
      globalNetwork: start > gn };
  });
  // Every labelled prefix in the source must have one official record link.
  const labels = [...reference.catalogueText.matchAll(/\(\+[1-9][0-9 ]*(?:, \+[1-9][0-9 ]*)*\)/g)];
  if (labels.length !== result.length) throw new Error('ITU_UNLINKED_AREA');
  return result;
}

export function buildRegistry(reference, mapping, qualifications = {}) {
  const areas = extractItuAreas(reference);
  const codes = new Map();
  for (const area of areas) codes.set(area.countryCallingCode, (codes.get(area.countryCallingCode) ?? 0) + 1);
  const entries = areas.map(area => {
    const identity = mapping.entries[area.id];
    if (!identity) throw new Error('ITU_ISO_MAPPING_MISSING');
    const q = qualifications[identity.iso2] ?? {};
    return { id: area.id, resourceScope: area.globalNetwork ? 'SHARED_NETWORK' : identity.resourceScope,
      iso2: identity.iso2, iso3: identity.iso3, countryName: area.ituDisplayName, ituDisplayName: area.ituDisplayName,
      countryCallingCode: area.countryCallingCode, sharedCallingCode: codes.get(area.countryCallingCode) > 1 || area.nanpMember ? area.countryCallingCode : null,
      dialPrefixes: area.dialPrefixes, npaCodes: area.npaCodes, networkCodes: area.networkCodes, nanpMember: area.nanpMember,
      regulatorName: null, regulatorUrl: null, numberingAuthorityName: null, numberingAuthorityUrl: null,
      officialNumberingPlanUrl: area.officialNumberingPlanUrl, bulkDataUrl: null, apiUrl: null,
      sourceType: 'ITU_PLAN_REFERENCE', sourceFormat: 'HTML_REFERENCE_ONLY', license: null,
      portabilityAvailable: null, portabilitySource: null, portabilityAccessStatus: null,
      portabilityVerificationStatus: 'SOURCE_NOT_YET_VERIFIED', allocationHolderAvailable: null,
      allocationDateAvailable: null, territoryAvailable: null, operatorCodeAvailable: null,
      refreshPolicy: { mode: 'DISCOVERY_PENDING', cadence: null }, lastSourceVerificationAt: null,
      integrationStatus: 'SOURCE_NOT_YET_VERIFIED',
      blocker: 'Official ITU plan reference found. National authority, source freshness, bulk availability, reuse rights and portability still require verification.',
      notes: ['Original ITU technical designation preserved; no sovereignty assertion.', identity.mappingMethod],
      referenceHash: reference.sourceHash, referenceFetchedAt: reference.fetchedAt, ...q };
  });
  return { schemaVersion: 1, referenceScope: reference.referenceScope,
    coverageQualification: reference.coverageQualification, referenceUrl: reference.sourceUrl,
    referenceHash: reference.sourceHash, referenceFetchedAt: reference.fetchedAt,
    outstandingReferenceScopes: ['E164_ASSIGNED_CODES_AND_BULLETIN_AMENDMENTS','GLOBAL_SERVICES','SHARED_PLAN_SUBAREAS'],
    coverageComplete: false, entries };
}

export function assertCoverage(reference, registry, fileExists = existsSync) {
  const areas = extractItuAreas(reference);
  if (registry.referenceHash !== reference.sourceHash || registry.referenceScope !== reference.referenceScope || !Array.isArray(registry.outstandingReferenceScopes)) throw new Error('ITU_REFERENCE_PROVENANCE_CHANGED');
  const expected = new Map(areas.map(area => [area.id, area]));
  if (!Array.isArray(registry.entries) || registry.entries.length !== areas.length) throw new Error('ITU_AREA_MISSING_OR_EXTRA');
  const seen = new Set();
  for (const entry of registry.entries) {
    const area = expected.get(entry.id);
    if (!area || seen.has(entry.id)) throw new Error('ITU_AREA_DUPLICATED_OR_UNKNOWN');
    seen.add(entry.id);
    if (entry.ituDisplayName !== area.ituDisplayName || entry.countryCallingCode !== area.countryCallingCode || JSON.stringify(entry.dialPrefixes) !== JSON.stringify(area.dialPrefixes)) throw new Error('ITU_AREA_CHANGED');
    if (FIELDS.some(field => !Object.hasOwn(entry, field)) || !STATUSES.includes(entry.integrationStatus)) throw new Error('ITU_ENTRY_STATUS_OR_FIELD_MISSING');
    if (!['COUNTRY','TERRITORY','SHARED_NETWORK','GLOBAL_SERVICE'].includes(entry.resourceScope) || area.globalNetwork !== (entry.resourceScope === 'SHARED_NETWORK')) throw new Error('ITU_RESOURCE_SCOPE_INVALID');
    if (area.nanpMember !== entry.nanpMember || JSON.stringify(area.npaCodes) !== JSON.stringify(entry.npaCodes)) throw new Error('ITU_NANP_CHANGED');
    const shared = areas.some(other => other.id !== area.id && other.countryCallingCode === area.countryCallingCode) || area.nanpMember;
    if (entry.sharedCallingCode !== (shared ? area.countryCallingCode : null)) throw new Error('ITU_SHARED_CODE_NOT_MODELLED');
    if (entry.integrationStatus === 'SOURCE_NOT_YET_VERIFIED' && !entry.blocker) throw new Error('ITU_UNKNOWN_WITHOUT_BLOCKER');
    if (['INTEGRATED','IMPORTER_READY'].includes(entry.integrationStatus) && (!entry.importerPath || !entry.importerTestPath || !fileExists(entry.importerPath) || !fileExists(entry.importerTestPath))) throw new Error('ITU_IMPORTER_OR_TEST_MISSING');
    if (entry.integrationStatus === 'INTEGRATED' && (!entry.datasetPath || !fileExists(entry.datasetPath))) throw new Error('ITU_INTEGRATED_DATASET_MISSING');
  }
  if (registry.coverageComplete && (registry.outstandingReferenceScopes.length || registry.entries.some(x => x.integrationStatus === 'SOURCE_NOT_YET_VERIFIED'))) throw new Error('ITU_FALSE_GLOBAL_COVERAGE');
  return true;
}

export function coverageReport(registry) {
  const entries = registry.entries;
  const statuses = Object.fromEntries(STATUSES.map(status => [status, entries.filter(entry => entry.integrationStatus === status).length]));
  return { referenceScope: registry.referenceScope, coverageQualification: registry.coverageQualification,
    totalReferenceEntries: entries.length, geographicalEntries: entries.filter(e => !['SHARED_NETWORK','GLOBAL_SERVICE'].includes(e.resourceScope)).length,
    internationalResourceEntries: entries.filter(e => ['SHARED_NETWORK','GLOBAL_SERVICE'].includes(e.resourceScope)).length,
    regulatorIdentified: entries.filter(e => e.regulatorName).length,
    officialSourceRecentlyVerified: entries.filter(e => e.lastSourceVerificationAt).length,
    statuses, globalCoveragePercentage: null, coverageComplete: registry.coverageComplete,
    outstandingReferenceScopes: registry.outstandingReferenceScopes,
    regionBreakdownStatus: 'UN_M49_MAPPING_NOT_YET_VERIFIED',
    note: 'Catalogue completeness and allocation-data integration are separate. No worldwide percentage until assigned-code and shared-area reconciliation is complete.' };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const load = path => JSON.parse(readFileSync(path, 'utf8'));
  const registry = buildRegistry(load('config/numbering/itu-national-numbering-plans-reference.json'), load('config/numbering/itu-iso-mapping.json'), load('config/numbering/source-qualifications.json'));
  assertCoverage(load('config/numbering/itu-national-numbering-plans-reference.json'), registry);
  writeFileSync('config/global-numbering-source-registry.json', JSON.stringify(registry, null, 2) + '\n');
  writeFileSync('docs/data/global-numbering-coverage.json', JSON.stringify(coverageReport(registry), null, 2) + '\n');
}
