import { createHash } from 'node:crypto';

function canonicalize(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(',')}}`;
}

export function hashEvidence(record, previousHash = '') {
  const payload = `${previousHash}:${canonicalize(record)}`;
  return createHash('sha256').update(payload, 'utf8').digest('hex');
}

export function appendEvidence(chain, record) {
  if (!Array.isArray(chain)) throw new TypeError('EVIDENCE_CHAIN_REQUIRED');
  const previousHash = chain.length ? chain.at(-1).hash : '';
  const entry = {
    ...record,
    previous_hash: previousHash,
  };
  return [...chain, { ...entry, hash: hashEvidence(entry, previousHash) }];
}

export function verifyEvidenceChain(chain) {
  if (!Array.isArray(chain)) return { valid: false, reason: 'EVIDENCE_CHAIN_REQUIRED' };
  let previousHash = '';
  for (const entry of chain) {
    const { hash, ...record } = entry;
    if (record.previous_hash !== previousHash) return { valid: false, reason: 'CHAIN_LINK_INVALID' };
    if (hash !== hashEvidence(record, previousHash)) return { valid: false, reason: 'EVIDENCE_HASH_INVALID' };
    previousHash = hash;
  }
  return { valid: true, reason: 'CHAIN_VALID' };
}
