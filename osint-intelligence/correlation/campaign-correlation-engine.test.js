const assert = require('node:assert/strict');
const {
  scoreCampaignSignal,
  correlateCampaign
} = require('./campaign-correlation-engine');

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

const campaigns = correlateCampaign([
  {
    campaign_id: 'synthetic-campaign-01',
    signals: {
      behavioral_anomaly: 0.90,
      temporal_sync: 0.95,
      narrative_similarity: 0.90,
      source_convergence: 0.80,
      propagation_strength: 0.85
    }
  },
  {
    campaign_id: 'synthetic-campaign-01',
    signals: {
      behavioral_anomaly: 0.85,
      temporal_sync: 0.90,
      narrative_similarity: 0.88,
      source_convergence: 0.75,
      propagation_strength: 0.80
    }
  },
  {
    campaign_id: 'noise-01',
    signals: {
      behavioral_anomaly: 0.10,
      temporal_sync: 0.10,
      narrative_similarity: 0.10,
      source_convergence: 0.10,
      propagation_strength: 0.10
    }
  }
], { threshold: 0.60 });

assert.equal(campaigns.length, 1);
assert.equal(campaigns[0].campaign_id, 'synthetic-campaign-01');
assert.equal(campaigns[0].event_count, 2);
assert.ok(campaigns[0].confidence > 0);

console.log('campaign-correlation-engine: OK');
