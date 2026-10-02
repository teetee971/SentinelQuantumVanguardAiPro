/** Local prefix evidence only. No network request, subscriber identity or carrier inference. */
export function createGlobalNumberingLookup(manifest) {
  if (manifest?.schemaVersion !== 1 || !Array.isArray(manifest.entries) || !manifest.entries.length ||
      !/^[a-f0-9]{64}$/.test(manifest.referenceHash) || !/^[a-f0-9]{64}$/.test(manifest.registryHash)) {
    throw new Error('Invalid numbering manifest');
  }
  const prefixes = new Map();
  const ids = new Set();
  for (const entry of manifest.entries) {
    if (!entry.id || ids.has(entry.id) || !Array.isArray(entry.dialPrefixes) || !entry.dialPrefixes.length ||
        !['COUNTRY', 'TERRITORY', 'SHARED_NETWORK', 'GLOBAL_SERVICE'].includes(entry.resourceScope)) {
      throw new Error('Invalid numbering area');
    }
    ids.add(entry.id);
    for (const prefix of entry.dialPrefixes) {
      if (!/^[1-9]\d{0,6}$/.test(prefix)) throw new Error('Invalid dial prefix');
      if (!prefixes.has(prefix)) prefixes.set(prefix, []);
      prefixes.get(prefix).push(Object.freeze({ ...entry, dialPrefixes: Object.freeze([...entry.dialPrefixes]) }));
    }
  }
  const evidence = Object.freeze({ referenceScope: manifest.referenceScope,
    referenceUrl: manifest.referenceUrl, referenceHash: manifest.referenceHash,
    registryHash: manifest.registryHash, referenceFetchedAt: manifest.referenceFetchedAt,
    coverageComplete: manifest.coverageComplete === true });
  return function lookup(e164) {
    if (typeof e164 !== 'string' || !/^\+[1-9]\d{1,14}$/.test(e164)) {
      return { status: 'INVALID_E164_SYNTAX', candidates: [], evidence };
    }
    const digits = e164.slice(1);
    let candidates = [];
    for (let length = Math.min(7, digits.length); length > 0; length--) {
      if (prefixes.has(digits.slice(0, length))) {
        candidates = [...prefixes.get(digits.slice(0, length))];
        break;
      }
    }
    return { status: candidates.length === 0 ? 'REFERENCE_ENTRY_NOT_FOUND' :
      candidates.length > 1 ? 'AMBIGUOUS_SHARED_PLAN' : 'PREFIX_REFERENCE_FOUND',
      candidates, evidence, numberPlanValidation: 'NOT_PERFORMED',
      regulatoryAssignment: null, currentCarrier: null, subscriberIdentity: null,
      spoofingAssessment: 'INSUFFICIENT_EVIDENCE' };
  };
}
