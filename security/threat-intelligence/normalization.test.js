import assert from 'node:assert/strict';
import test from 'node:test';

import {
  THREAT_INTELLIGENCE_BOUNDARY,
  THREAT_VERDICTS,
  buildGlobalThreatSituation,
  buildThreatConsensus,
  canAutomaticallyDownloadMalwareSamples,
  canInferActorFromObservedLocation,
  normalizeThreatObservation
} from './normalization.js';

function observation(overrides = {}) {
  return {
    observation_id: 'obs-1',
    indicator_type: 'sha256',
    indicator_value: 'a'.repeat(64),
    source_id: 'source-a',
    source_kind: 'malware_repository',
    source_uri: 'https://example.test/report/a',
    source_verdict: 'malicious',
    confidence: 90,
    observed_at: '2026-09-06T10:00:00Z',
    retrieved_at: '2026-09-06T10:01:00Z',
    legally_accessible: true,
    tags: ['trojan', 'windows'],
    ...overrides
  };
}

test('normalizes metadata-only threat observations', () => {
  const result = normalizeThreatObservation(observation({
    observed_source_location: { country_code: 'fr', asn: 64500 },
    observed_target_location: { country_code: 'de' }
  }));

  assert.equal(result.indicator_type, 'sha256');
  assert.equal(result.indicator_value, 'a'.repeat(64));
  assert.equal(result.observed_source_location.country_code, 'FR');
  assert.equal(result.observed_target_location.country_code, 'DE');
  assert.equal(result.sample_downloaded, false);
  assert.equal(result.attribution, null);
});

test('rejects records without explicit legal-access confirmation', () => {
  assert.throws(
    () => normalizeThreatObservation(observation({ legally_accessible: false })),
    /LEGAL_ACCESS_CONFIRMATION_REQUIRED/
  );
});

test('rejects automated actor or country attribution fields', () => {
  assert.throws(
    () => normalizeThreatObservation(observation({ attributed_country: 'XX' })),
    /AUTOMATED_ATTRIBUTION_FIELD_FORBIDDEN/
  );
  assert.throws(
    () => normalizeThreatObservation(observation({ responsible_actor: 'actor-x' })),
    /AUTOMATED_ATTRIBUTION_FIELD_FORBIDDEN/
  );
});

test('validates cryptographic hashes and CVE identifiers', () => {
  assert.throws(
    () => normalizeThreatObservation(observation({ indicator_value: 'not-a-hash' })),
    /INVALID_SHA256/
  );

  const cve = normalizeThreatObservation(observation({
    indicator_type: 'cve',
    indicator_value: 'cve-2026-12345',
    source_kind: 'vulnerability_catalog',
    source_verdict: 'unknown'
  }));
  assert.equal(cve.indicator_value, 'CVE-2026-12345');
});

test('requires retrieval time to be at or after observation time', () => {
  assert.throws(
    () => normalizeThreatObservation(observation({
      observed_at: '2026-09-06T10:00:00Z',
      retrieved_at: '2026-09-06T09:59:59Z'
    })),
    /RETRIEVED_BEFORE_OBSERVED/
  );
});

test('requires multiple independent malicious sources for KNOWN_MALWARE', () => {
  const consensus = buildThreatConsensus([
    observation({ source_id: 'source-a', confidence: 95 }),
    observation({ observation_id: 'obs-2', source_id: 'source-b', confidence: 85 })
  ]);

  assert.equal(consensus.verdict, THREAT_VERDICTS.KNOWN_MALWARE);
  assert.deepEqual(consensus.sources, ['source-a', 'source-b']);
  assert.equal(consensus.attribution, null);
  assert.equal(consensus.enforcement_allowed, false);
});

test('does not count duplicate records from one source as independent consensus', () => {
  const consensus = buildThreatConsensus([
    observation({ source_id: 'source-a', confidence: 95 }),
    observation({ observation_id: 'obs-2', source_id: 'source-a', confidence: 90 })
  ]);

  assert.equal(consensus.verdict, THREAT_VERDICTS.LIKELY_MALICIOUS);
  assert.deepEqual(consensus.sources, ['source-a']);
});

test('surfaces conflicting intelligence instead of collapsing disagreement', () => {
  const consensus = buildThreatConsensus([
    observation({ source_id: 'source-a', source_verdict: 'malicious', confidence: 95 }),
    observation({ observation_id: 'obs-2', source_id: 'source-b', source_verdict: 'benign', confidence: 80 })
  ]);

  assert.equal(consensus.verdict, THREAT_VERDICTS.CONFLICTING_INTELLIGENCE);
  assert.match(consensus.reasons[0], /CONFLICT/);
});

test('rejects consensus across different indicators', () => {
  assert.throws(
    () => buildThreatConsensus([
      observation(),
      observation({ observation_id: 'obs-2', indicator_value: 'b'.repeat(64), source_id: 'source-b' })
    ]),
    /CONSENSUS_REQUIRES_SINGLE_INDICATOR/
  );
});

test('builds a global situation summary without actor attribution', () => {
  const summary = buildGlobalThreatSituation([
    observation({
      observed_source_location: { country_code: 'FR' },
      observed_target_location: { country_code: 'DE' }
    }),
    observation({
      observation_id: 'obs-2',
      source_id: 'source-b',
      source_kind: 'threat_map',
      indicator_type: 'ip',
      indicator_value: '192.0.2.10',
      source_verdict: 'suspicious',
      observed_source_location: { country_code: 'US' },
      observed_target_location: { country_code: 'DE' }
    })
  ]);

  assert.equal(summary.observations, 2);
  assert.deepEqual(summary.observed_source_countries, { FR: 1, US: 1 });
  assert.deepEqual(summary.observed_target_countries, { DE: 2 });
  assert.equal(summary.actor_attribution, null);
  assert.match(summary.caveat, /not evidence of actor nationality/);
});

test('hard-codes sample download and location attribution boundaries', () => {
  assert.equal(canAutomaticallyDownloadMalwareSamples(), false);
  assert.equal(canInferActorFromObservedLocation(), false);
  assert.equal(THREAT_INTELLIGENCE_BOUNDARY.metadata_only, true);
  assert.equal(THREAT_INTELLIGENCE_BOUNDARY.automatic_sample_download, false);
  assert.equal(THREAT_INTELLIGENCE_BOUNDARY.autonomous_actor_attribution, false);
  assert.equal(THREAT_INTELLIGENCE_BOUNDARY.autonomous_enforcement, false);
});
