import assert from 'node:assert/strict';
import { scoreCampaignSignal, correlateCampaign } from './campaign-correlation-engine.js';

const strong = scoreCampaignSignal({
  behavioral_anomaly: 0.95,
  temporal_sync: 0.95,
  narrative_similarity: 0.92,
  source_convergence: 0.80,
  propagation_strength: 0.85
});

assert.ok(strong.priority_score >= 0.80);
assert.ok(strong.reasons.includes('temporal_sync'));
assert.ok(strong.reasons.includes('narrative_similarity'));
assert.equal(strong.interpretation, 'revue_prioritaire');

const weak = scoreCampaignSignal({
  behavioral_anomaly: 0.10,
  temporal_sync: 0.20,
  narrative_similarity: 0.15,
  source_convergence: 0.10,
  propagation_strength: 0.20
});
assert.ok(weak.priority_score < 0.60);
assert.equal(weak.interpretation, 'surveillance_analytique');

const events = Array.from({ length: 500 }, (_, index) => ({
  campaign_id: index < 400 ? 'synthetic-coordinated-500' : `noise-${index}`,
  signals: index < 400
    ? { behavioral_anomaly: 0.90, temporal_sync: 0.95, narrative_similarity: 0.92, source_convergence: 0.80, propagation_strength: 0.85 }
    : { behavioral_anomaly: 0.10, temporal_sync: 0.10, narrative_similarity: 0.10, source_convergence: 0.10, propagation_strength: 0.10 }
}));

const campaigns = correlateCampaign(events, { threshold: 0.60 });
assert.equal(campaigns.length, 1);
assert.equal(campaigns[0].campaign_id, 'synthetic-coordinated-500');
assert.equal(campaigns[0].event_count, 400);
assert.ok(campaigns[0].priority_score >= 0.80);
assert.ok(campaigns[0].confidence > 0);

console.log('campaign-correlation-engine: OK — 500 synthetic events');
