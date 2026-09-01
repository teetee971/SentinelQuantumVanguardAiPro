/** Defensive parser for untrusted AsyncStorage call-history data. */
import type { StoredCallEvent } from './CallHistoryStorage';

const MAX_ENTRIES = 1000;
const TYPES = new Set(['INCOMING', 'OUTGOING', 'MISSED', 'BLOCKED']);
const ACTIONS = new Set(['ALLOWED', 'BLOCKED', 'FLAGGED', 'AI_HANDLED']);
const RISKS = new Set(['SAFE', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStoredCallEvent(value: unknown): value is StoredCallEvent {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === 'string' &&
    typeof value.phoneNumber === 'string' &&
    typeof value.timestamp === 'number' && Number.isFinite(value.timestamp) &&
    TYPES.has(String(value.type)) &&
    typeof value.duration === 'number' && Number.isFinite(value.duration) &&
    ACTIONS.has(String(value.action)) &&
    RISKS.has(String(value.riskLevel)) &&
    (value.country === undefined || typeof value.country === 'string') &&
    (value.notes === undefined || typeof value.notes === 'string')
  );
}

export function parseStoredCallHistory(data: string): StoredCallEvent[] {
  if (typeof data !== 'string' || data.length === 0) return [];
  try {
    const parsed: unknown = JSON.parse(data);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isStoredCallEvent).slice(0, MAX_ENTRIES);
  } catch {
    return [];
  }
}
