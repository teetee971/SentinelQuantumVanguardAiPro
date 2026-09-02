const clamp01 = (value) => Math.max(0, Math.min(1, Number(value) || 0));

const DEFAULT_LIMITS = Object.freeze({
  max_events: 750,
  max_pair_evaluations: 250000,
  include_pair_scores: true
});

/**
 * Scores coordination between already-observed public events.
 * This is a behavioral indicator, not an identity or maliciousness classifier.
 */
function scorePair(a, b) {
  if (!a || !b || a.entity?.public_id === b.entity?.public_id) {
    return { score: 0, confidence: 0, reasons: [] };
  }

  const features = a.coordination_features || {};
  const other = b.coordination_features || {};
  const text = clamp01((features.text_similarity + other.text_similarity) / 2);
  const time = clamp01((features.time_similarity + other.time_similarity) / 2);
  const url = clamp01((features.url_overlap + other.url_overlap) / 2);
  const hash = clamp01((features.hashtag_overlap + other.hashtag_overlap) / 2);

  const score = clamp01((text * 0.35) + (time * 0.30) + (url * 0.20) + (hash * 0.15));
  const reasons = [];
  if (text >= 0.8) reasons.push('forte_similarite_textuelle');
  if (time >= 0.8) reasons.push('forte_synchronisation_temporelle');
  if (url >= 0.7) reasons.push('fort_recouvrement_urls');
  if (hash >= 0.7) reasons.push('fort_recouvrement_hashtags');

  return {
    score: Number(score.toFixed(4)),
    confidence: Number(Math.min(1, reasons.length / 4).toFixed(4)),
    reasons
  };
}

/**
 * Finds behavioral clusters with hard resource bounds.
 * If a bound is reached, the result is marked truncated and must not be
 * treated as a complete clustering result. This is intentionally fail-safe.
 */
function findClusters(events = [], threshold = 0.72, options = {}) {
  const limits = { ...DEFAULT_LIMITS, ...options };
  const maxEvents = Math.max(1, Math.floor(Number(limits.max_events) || DEFAULT_LIMITS.max_events));
  const maxPairs = Math.max(1, Math.floor(Number(limits.max_pair_evaluations) || DEFAULT_LIMITS.max_pair_evaluations));
  const includePairScores = limits.include_pair_scores !== false;

  const uniqueEvents = [];
  const seenIds = new Set();
  for (const event of events) {
    const id = event?.entity?.public_id;
    if (!id || seenIds.has(id)) continue;
    seenIds.add(id);
    uniqueEvents.push(event);
  }

  if (uniqueEvents.length > maxEvents) {
    return {
      threshold,
      clusters: [],
      pair_scores: [],
      truncated: true,
      reason: 'event_limit_exceeded',
      input_event_count: events.length,
      evaluated_event_count: 0,
      pair_evaluations: 0,
      limits: { max_events: maxEvents, max_pair_evaluations: maxPairs }
    };
  }

  const parent = new Map(uniqueEvents.map((event) => [event.entity.public_id, event.entity.public_id]));
  const find = (x) => {
    let root = x;
    while (parent.get(root) !== root) root = parent.get(root);
    while (parent.get(x) !== x) { const next = parent.get(x); parent.set(x, root); x = next; }
    return root;
  };
  const union = (a, b) => { const ra = find(a); const rb = find(b); if (ra !== rb) parent.set(ra, rb); };

  const pairScores = [];
  let pairEvaluations = 0;
  let truncated = false;
  for (let i = 0; i < uniqueEvents.length && !truncated; i += 1) {
    for (let j = i + 1; j < uniqueEvents.length; j += 1) {
      if (pairEvaluations >= maxPairs) {
        truncated = true;
        break;
      }
      const result = scorePair(uniqueEvents[i], uniqueEvents[j]);
      pairEvaluations += 1;
      if (includePairScores) {
        pairScores.push({ a: uniqueEvents[i].entity.public_id, b: uniqueEvents[j].entity.public_id, ...result });
      }
      if (result.score >= threshold) union(uniqueEvents[i].entity.public_id, uniqueEvents[j].entity.public_id);
    }
  }

  if (truncated) {
    return {
      threshold,
      clusters: [],
      pair_scores: pairScores,
      truncated: true,
      reason: 'pair_evaluation_limit_exceeded',
      input_event_count: events.length,
      evaluated_event_count: uniqueEvents.length,
      pair_evaluations: pairEvaluations,
      limits: { max_events: maxEvents, max_pair_evaluations: maxPairs }
    };
  }

  const clusters = new Map();
  for (const event of uniqueEvents) {
    const id = event.entity.public_id;
    const root = find(id);
    if (!clusters.has(root)) clusters.set(root, []);
    clusters.get(root).push(id);
  }

  return {
    threshold,
    clusters: [...clusters.values()].filter((cluster) => cluster.length > 1),
    pair_scores: pairScores,
    truncated: false,
    reason: null,
    input_event_count: events.length,
    evaluated_event_count: uniqueEvents.length,
    pair_evaluations: pairEvaluations,
    limits: { max_events: maxEvents, max_pair_evaluations: maxPairs }
  };
}

export { scorePair, findClusters, DEFAULT_LIMITS };
