import { buildPropagationGraph, propagationTimeline } from '../graph/propagation-graph.js';
import { correlateCandidateCampaigns } from '../correlation/campaign-correlation-engine.js';

const DEFAULT_MAX_EVENTS = 750;

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/**
 * Bounded defensive OSINT orchestration.
 *
 * Connects propagation-graph construction to candidate campaign correlation for
 * already-observed public/authorized events. The result is an analyst-review aid
 * only: it performs no attribution, identity inference, guilt determination,
 * blocking, takedown, or other external side effect.
 */
export function analyzeObservedCampaigns({ events, correlationOptions = {}, maxEvents = DEFAULT_MAX_EVENTS } = {}) {
  if (!Array.isArray(events)) {
    return { valid: false, reason: 'OSINT_EVENTS_REQUIRED', side_effect_performed: false };
  }
  if (!isPlainObject(correlationOptions)) {
    return { valid: false, reason: 'OSINT_CORRELATION_OPTIONS_INVALID', side_effect_performed: false };
  }

  const boundedMaxEvents = Number.isInteger(maxEvents) && maxEvents > 0
    ? Math.min(maxEvents, DEFAULT_MAX_EVENTS)
    : DEFAULT_MAX_EVENTS;

  if (events.length > boundedMaxEvents) {
    return {
      valid: false,
      reason: 'OSINT_EVENT_LIMIT_EXCEEDED',
      input_event_count: events.length,
      max_events: boundedMaxEvents,
      side_effect_performed: false,
    };
  }

  if (events.some((event) => !isPlainObject(event))) {
    return { valid: false, reason: 'OSINT_EVENT_INVALID', side_effect_performed: false };
  }

  const graph = buildPropagationGraph(events);
  const timeline = propagationTimeline(graph);
  const candidates = correlateCandidateCampaigns(events, correlationOptions);

  return {
    valid: true,
    reason: 'OSINT_ANALYSIS_READY_FOR_REVIEW',
    graph,
    timeline,
    candidates,
    input_event_count: events.length,
    analyst_review_required: true,
    attribution_performed: false,
    side_effect_performed: false,
  };
}

export { DEFAULT_MAX_EVENTS };
