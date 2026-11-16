function normalizePart(value) {
  if (value === null || value === undefined) return '';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

export function stableHash32(...parts) {
  const input = parts.map(normalizePart).join('\u001f');
  let hash = 0x811c9dc5;
  for (let i = 0; i < input.length; i += 1) {
    hash ^= input.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

export function deterministicInt(maxExclusive, ...parts) {
  if (!Number.isInteger(maxExclusive) || maxExclusive <= 0) return 0;
  return Math.floor((stableHash32(...parts) / 0x100000000) * maxExclusive);
}

export function deterministicLatencyMs(...parts) {
  return 1000 + deterministicInt(4001, 'redteam-mttd', ...parts);
}

export function safePercentage(numerator, denominator) {
  if (!Number.isFinite(numerator) || !Number.isFinite(denominator) || denominator <= 0) {
    return 0;
  }
  return (numerator / denominator) * 100;
}

export function boundedCoverage(coveredCount, referenceCount) {
  return Math.min(100, Math.max(0, safePercentage(coveredCount, referenceCount)));
}

export function calculateTechniqueCoverage(eventTechniqueIds, referenceTechniqueIds) {
  const reference = new Set(
    (referenceTechniqueIds || []).filter((value) => typeof value === 'string' && value.length > 0)
  );
  if (reference.size === 0) return 0;

  const covered = new Set(
    (eventTechniqueIds || []).filter((value) => reference.has(value))
  );
  return boundedCoverage(covered.size, reference.size);
}

export function calculateExpectedDetectionRate(eventTechniqueIds, expectedTechniqueIds) {
  return calculateTechniqueCoverage(eventTechniqueIds, expectedTechniqueIds);
}
