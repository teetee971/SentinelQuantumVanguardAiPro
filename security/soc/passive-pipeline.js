import { createHash } from 'node:crypto';
import { verifyProofAuthenticity } from '../decision-plane/policy/proof-authenticity.js';
import { validateProofWindow } from '../decision-plane/policy/proof-freshness.js';

const MAX_EVENTS = 100;
const MAX_PAYLOAD_BYTES = 32_768;
const EVENT_TYPES = new Set(['indicator_observed', 'alert_raised', 'asset_state_changed']);

function fail(reason) {
  return { valid: false, reason, side_effect_performed: false };
}

function text(value, max) {
  if (typeof value !== 'string') return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}

function canonical(value) {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`;
}

function normalizeEvent(event, now) {
  if (!event || typeof event !== 'object' || Array.isArray(event)) return fail('INVALID_EVENT');
  const eventId = text(event.event_id, 256);
  const eventType = text(event.event_type, 64)?.toLowerCase();
  const sourceId = text(event.source_id, 256);
  const subject = text(event.subject, 2048);
  if (!eventId || !eventType || !sourceId || !subject) return fail('EVENT_FIELDS_REQUIRED');
  if (!EVENT_TYPES.has(eventType)) return fail('EVENT_TYPE_UNSUPPORTED');
  if (!event.payload || typeof event.payload !== 'object' || Array.isArray(event.payload)) {
    return fail('EVENT_PAYLOAD_INVALID');
  }
  if (Buffer.byteLength(canonical(event.payload), 'utf8') > MAX_PAYLOAD_BYTES) {
    return fail('EVENT_PAYLOAD_TOO_LARGE');
  }
  const window = validateProofWindow({ issuedAt: event.issued_at, expiresAt: event.expires_at, now });
  if (!window.valid) return fail(`EVENT_WINDOW_${window.reason}`);

  return {
    valid: true,
    event: Object.freeze({
      ...event,
      event_id: eventId,
      event_type: eventType,
      source_id: sourceId,
      subject,
    }),
  };
}

function processPassiveSocEvents(events, { trust, replayGuard, now = Date.now() } = {}) {
  if (!Array.isArray(events) || events.length === 0 || events.length > MAX_EVENTS) {
    return fail('EVENT_BATCH_INVALID');
  }
  if (!replayGuard || typeof replayGuard.consumeAtomically !== 'function') {
    return fail('ANTI_REPLAY_GUARD_REQUIRED');
  }

  const accepted = [];
  const rejected = [];
  const semantic = new Map();

  for (const candidate of events) {
    const normalized = normalizeEvent(candidate, now);
    if (!normalized.valid) {
      rejected.push({ event_id: candidate?.event_id ?? null, reason: normalized.reason });
      continue;
    }
    const event = normalized.event;
    const authenticity = verifyProofAuthenticity(event, 'soc_event', trust);
    if (!authenticity.valid) {
      rejected.push({ event_id: event.event_id, reason: authenticity.reason });
      continue;
    }
    const replay = replayGuard.consumeAtomically(`soc_event:${event.event_id}`);
    if (!replay.valid) {
      rejected.push({ event_id: event.event_id, reason: replay.reason });
      continue;
    }

    const key = createHash('sha256')
      .update(`${event.event_type}\n${event.subject}\n${canonical(event.payload)}`)
      .digest('hex');
    const existing = semantic.get(key);
    if (existing) {
      existing.source_ids.add(event.source_id);
      existing.event_ids.push(event.event_id);
      continue;
    }
    semantic.set(key, {
      fingerprint: key,
      event_type: event.event_type,
      subject: event.subject,
      payload: event.payload,
      source_ids: new Set([event.source_id]),
      event_ids: [event.event_id],
    });
    accepted.push(event.event_id);
  }

  const cases = [...semantic.values()].map((item) => Object.freeze({
    case_id: `soc-${item.fingerprint.slice(0, 24)}`,
    event_type: item.event_type,
    subject: item.subject,
    source_count: item.source_ids.size,
    source_ids: [...item.source_ids].sort(),
    event_ids: [...item.event_ids].sort(),
    confidence_class: item.source_ids.size >= 2 ? 'MULTI_SOURCE' : 'SINGLE_SOURCE',
    proposed_playbook: null,
    autonomous_action: false,
  }));

  return Object.freeze({
    valid: rejected.length === 0,
    reason: rejected.length === 0 ? 'PASSIVE_PIPELINE_COMPLETE' : 'PASSIVE_PIPELINE_PARTIAL',
    accepted_event_ids: accepted.sort(),
    rejected: Object.freeze(rejected),
    cases: Object.freeze(cases),
    privileged_action_requested: false,
    side_effect_performed: false,
  });
}

export { EVENT_TYPES, MAX_EVENTS, MAX_PAYLOAD_BYTES, processPassiveSocEvents };
