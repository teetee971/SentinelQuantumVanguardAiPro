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

export function deterministicUnitInterval(...parts) {
  return stableHash32(...parts) / 0x100000000;
}

export function deterministicInt(maxExclusive, ...parts) {
  if (!Number.isInteger(maxExclusive) || maxExclusive <= 0) return 0;
  return Math.floor(deterministicUnitInterval(...parts) * maxExclusive);
}

export function deterministicHex(length, ...parts) {
  if (!Number.isInteger(length) || length <= 0) return '';

  let output = '';
  let counter = 0;
  while (output.length < length) {
    output += stableHash32(...parts, counter).toString(16).padStart(8, '0');
    counter += 1;
  }

  return output.slice(0, length);
}

export function deterministicSyntheticIocs(techniqueId, ...context) {
  const normalizedTechniqueId = String(techniqueId || 'unknown');
  const seed = [normalizedTechniqueId, ...context];
  const iocTypes = ['ip', 'domain', 'hash', 'process', 'registry'];
  const selectedTypes = iocTypes.slice(
    0,
    deterministicInt(3, 'ioc-count', ...seed) + 1
  );

  return selectedTypes.map((type) => {
    switch (type) {
      case 'ip':
        return `IOC-IP: 203.0.113.${deterministicInt(256, 'ioc-ip', ...seed)}`;
      case 'domain':
        return `IOC-DOMAIN: ${deterministicHex(12, 'ioc-domain', ...seed)}.malicious.example`;
      case 'hash':
        return `IOC-HASH-SHA256: ${deterministicHex(64, 'ioc-hash', ...seed)}`;
      case 'process':
        return `IOC-PROCESS: suspicious_${deterministicHex(12, 'ioc-process', ...seed)}.exe`;
      case 'registry':
        return `IOC-REGISTRY: HKLM\\Software\\${deterministicHex(12, 'ioc-registry', ...seed)}`;
      default:
        return 'IOC-UNKNOWN';
    }
  });
}

export function safePercentage(numerator, denominator) {
  if (!Number.isFinite(numerator) || !Number.isFinite(denominator) || denominator <= 0) {
    return 0;
  }

  return (numerator / denominator) * 100;
}

export function referenceCoverage(coveredCount, referenceCount) {
  return Math.min(100, Math.max(0, safePercentage(coveredCount, referenceCount)));
}

export function deterministicDetectionDecision(probability, ...parts) {
  if (!Number.isFinite(probability) || probability <= 0) return false;
  if (probability >= 1) return true;
  return deterministicUnitInterval('detection', ...parts) < probability;
}

export function deterministicDetectionLatencyMs(...parts) {
  return 1000 + deterministicInt(4001, 'mttd-ms', ...parts);
}
