import type { StoredCallEvent } from './CallHistoryStorage';

const MAX_ENTRIES = 1000;
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
  if (typeof event.id !== 'string' || event.id.length === 0) return false;
  if (typeof event.phoneNumber !== 'string') return false;
  if (!Number.isSafeInteger(event.timestamp) || event.timestamp < 0) return false;
  if (typeof event.type !== 'string' || !CALL_TYPES.has(event.type as StoredCallEvent['type'])) return false;
  if (!Number.isFinite(event.duration) || (event.duration as number) < 0) return false;
  if (typeof event.action !== 'string' || !ACTIONS.has(event.action as StoredCallEvent['action'])) return false;
  if (typeof event.riskLevel !== 'string' || !RISK_LEVELS.has(event.riskLevel as StoredCallEvent['riskLevel'])) return false;
  if (event.country !== undefined && typeof event.country !== 'string') return false;
  if (event.notes !== undefined && typeof event.notes !== 'string') return false;

  return true;
}

/**
 * Parse untrusted local call-history data defensively.
 * Invalid JSON, non-array roots and schema-invalid entries become an empty
 * result or are discarded. The input array is bounded before validation.
 */
export function parseStoredCallHistory(serialized: string): StoredCallEvent[] {
  if (typeof serialized !== 'string' || serialized.length === 0) return [];

  try {
    const parsed: unknown = JSON.parse(serialized);
    if (!Array.isArray(parsed)) return [];

    return parsed.slice(0, MAX_ENTRIES).filter(isStoredCallEvent);
  } catch {
    return [];
  }
}
