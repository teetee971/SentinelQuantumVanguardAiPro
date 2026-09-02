import type { StoredCallEvent } from './CallHistoryStorage';

const MAX_ENTRIES = 1000;
const MAX_SERIALIZED_BYTES = 512 * 1024;
const CALL_TYPES = new Set<StoredCallEvent['type']>([
  'INCOMING',
  'OUTGOING',
  'MISSED',
  'BLOCKED',
]);
const ACTIONS = new Set<StoredCallEvent['action']>([
  'ALLOWED',
  'BLOCKED',
  'FLAGGED',
  'AI_HANDLED',
]);
const RISK_LEVELS = new Set<StoredCallEvent['riskLevel']>([
  'SAFE',
  'LOW',
  'MEDIUM',
  'HIGH',
  'CRITICAL',
]);

function isStoredCallEvent(value: unknown): value is StoredCallEvent {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return false;

  const event = value as Record<string, unknown>;
  if (typeof event.id !== 'string' || event.id.length === 0 || event.id.length > 256) return false;
  if (typeof event.phoneNumber !== 'string' || event.phoneNumber.length > 128) return false;
  if (!Number.isSafeInteger(event.timestamp) || event.timestamp < 0) return false;
  if (typeof event.type !== 'string' || !CALL_TYPES.has(event.type as StoredCallEvent['type'])) return false;
  if (!Number.isFinite(event.duration) || (event.duration as number) < 0 || (event.duration as number) > 86_400) return false;
  if (typeof event.action !== 'string' || !ACTIONS.has(event.action as StoredCallEvent['action'])) return false;
  if (typeof event.riskLevel !== 'string' || !RISK_LEVELS.has(event.riskLevel as StoredCallEvent['riskLevel'])) return false;
  if (event.country !== undefined && (typeof event.country !== 'string' || event.country.length > 128)) return false;
  if (event.notes !== undefined && (typeof event.notes !== 'string' || event.notes.length > 4096)) return false;

  return true;
}

/**
 * Parse untrusted local call-history data defensively.
 * Invalid JSON, non-array roots and schema-invalid entries become an empty
 * result or are discarded. Input size and result cardinality are bounded
 * before/while validation to limit memory and CPU consumption.
 */
export function parseStoredCallHistory(serialized: string): StoredCallEvent[] {
  if (typeof serialized !== 'string' || serialized.length === 0) return [];
  if (serialized.length > MAX_SERIALIZED_BYTES) return [];

  try {
    const parsed: unknown = JSON.parse(serialized);
    if (!Array.isArray(parsed)) return [];

    return parsed.slice(0, MAX_ENTRIES).filter(isStoredCallEvent);
  } catch {
    return [];
  }
}
