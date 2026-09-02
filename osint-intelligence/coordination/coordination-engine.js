const clamp01 = (value) => Math.max(0, Math.min(1, Number(value) || 0));

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

function findClusters(events, threshold = 0.72) {
  const parent = new Map(events.map((event) => [event.entity?.public_id, event.entity?.public_id]));
  const find = (x) => {
    let root = x;
    while (parent.get(root) !== root) root = parent.get(root);
    while (parent.get(x) !== x) { const next = parent.get(x); parent.set(x, root); x = next; }
    return root;
  };
  const union = (a, b) => { const ra = find(a); const rb = find(b); if (ra !== rb) parent.set(ra, rb); };

  const pairScores = [];
  for (let i = 0; i < events.length; i += 1) {
    for (let j = i + 1; j < events.length; j += 1) {
      const result = scorePair(events[i], events[j]);
      pairScores.push({ a: events[i].entity?.public_id, b: events[j].entity?.public_id, ...result });
      if (result.score >= threshold) union(events[i].entity?.public_id, events[j].entity?.public_id);
    }
  }

  const clusters = new Map();
  for (const event of events) {
    const id = event.entity?.public_id;
    if (!id) continue;
    const root = find(id);
    if (!clusters.has(root)) clusters.set(root, []);
    clusters.get(root).push(id);
  }

  return { threshold, clusters: [...clusters.values()].filter((cluster) => cluster.length > 1), pair_scores: pairScores };
}

module.exports = { scorePair, findClusters };
