import { createHash } from 'node:crypto';

function canonicalize(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`).join(',')}}`;
}

const REQUIRED = Object.freeze([
  'trace_id',
  'decision_id',
  'evidence_refs',
  'model_id',
  'model_version',
  'policy_version',
  'action',
  'result',
  'timestamp',
]);

export function hashAuditEvent(event, previousHash = '') {
  const payload = `${previousHash}:${canonicalize(event)}`;
  return createHash('sha256').update(payload, 'utf8').digest('hex');
}

export function appendAuditEvent(chain, event) {
  if (!Array.isArray(chain)) throw new TypeError('AUDIT_CHAIN_REQUIRED');
  const missing = REQUIRED.filter((field) => !(field in (event ?? {})));
  if (missing.length) throw new TypeError(`AUDIT_EVENT_MISSING_${missing[0].toUpperCase()}`);
  if (!Array.isArray(event.evidence_refs)) throw new TypeError('AUDIT_EVIDENCE_REFS_REQUIRED');

  const previousHash = chain.length ? chain.at(-1).hash : '';
  const entry = { ...event, previous_hash: previousHash };
  return [...chain, { ...entry, hash: hashAuditEvent(entry, previousHash) }];
}

export function verifyAuditChain(chain) {
  if (!Array.isArray(chain)) return { valid: false, reason: 'AUDIT_CHAIN_REQUIRED' };
  let previousHash = '';
  for (const entry of chain) {
    if (!entry || typeof entry !== 'object' || typeof entry.hash !== 'string') {
      return { valid: false, reason: 'AUDIT_ENTRY_INVALID' };
    }
    const { hash, ...event } = entry;
    if (event.previous_hash !== previousHash) return { valid: false, reason: 'AUDIT_CHAIN_LINK_INVALID' };
    if (hash !== hashAuditEvent(event, previousHash)) return { valid: false, reason: 'AUDIT_HASH_INVALID' };
    previousHash = hash;
  }
  return { valid: true, reason: 'AUDIT_CHAIN_VALID' };
}
