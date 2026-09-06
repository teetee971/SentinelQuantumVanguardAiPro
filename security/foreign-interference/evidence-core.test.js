import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildInterferenceAssessment,
  canAutonomouslyAttribute,
  canAutonomouslySanction,
  hashEvidenceContent,
  validateEvidenceRecord,
  validateHumanAttributionGate
} from './evidence-core.js';

function evidence(overrides = {}) {
  return {
    evidence_id: 'ev-1',
    source_id: 'source-official-1',
    source_type: 'PUBLIC_OFFICIAL',
    source_uri: 'https://example.invalid/source',
    observed_at: '2026-09-06T10:00:00.000Z',
    collected_at: '2026-09-06T10:05:00.000Z',
    content_hash: hashEvidenceContent('source material'),
    legally_accessible: true,
    ...overrides
  };
}

test('accepts a valid legally accessible evidence record', () => {
  assert.deepEqual(validateEvidenceRecord(evidence()), {
    valid: true,
    reason: 'EVIDENCE_VALID'
  });
});

test('fails closed when evidence provenance is incomplete or unauthorized', () => {
  assert.equal(validateEvidenceRecord(evidence({ source_uri: '' })).valid, false);
  assert.equal(validateEvidenceRecord(evidence({ legally_accessible: false })).reason, 'EVIDENCE_SOURCE_NOT_AUTHORIZED');
  assert.equal(validateEvidenceRecord(evidence({ content_hash: 'not-a-sha256' })).valid, false);
});

test('allows observation and correlation without an attribution gate', () => {
  const result = buildInterferenceAssessment({
    assessment_id: 'assessment-1',
    subject: 'campaign-cluster-1',
    level: 'CORRELATION',
    confidence: 0.72,
    evidence: [evidence()],
    relations: [{
      relation_id: 'rel-1',
      from_entity: 'domain:example.invalid',
      to_entity: 'campaign:cluster-1',
      relation_type: 'OBSERVED_ASSOCIATION',
      evidence_ids: ['ev-1'],
      confidence: 0.72
    }],
    summary: 'Correlated infrastructure observations.'
  });

  assert.equal(result.ok, true);
  assert.equal(result.assessment.level, 'CORRELATION');
  assert.equal(result.assessment.attribution, null);
  assert.equal(result.assessment.autonomous_enforcement_allowed, false);
});

test('rejects attribution without explicit human authority validation', () => {
  const result = buildInterferenceAssessment({
    assessment_id: 'assessment-2',
    subject: 'state-attribution-subject',
    level: 'ATTRIBUTION',
    confidence: 0.95,
    evidence: [evidence()]
  });

  assert.equal(result.ok, false);
  assert.equal(result.reason, 'HUMAN_ATTRIBUTION_GATE_REQUIRED');
});

test('accepts attribution only with explicit human validation metadata', () => {
  const gate = {
    human_validated: true,
    validated_by: 'authorized-human-reviewer',
    validated_at: '2026-09-06T11:00:00.000Z',
    authority_basis: 'documented institutional mandate',
    decision_reference: 'decision-2026-001'
  };

  assert.equal(validateHumanAttributionGate(gate).allowed, true);

  const result = buildInterferenceAssessment({
    assessment_id: 'assessment-3',
    subject: 'attribution-subject',
    level: 'ATTRIBUTION',
    confidence: 0.91,
    evidence: [evidence()],
    attribution_gate: gate
  });

  assert.equal(result.ok, true);
  assert.equal(result.assessment.attribution.human_validated, true);
  assert.equal(result.assessment.autonomous_enforcement_allowed, false);
});

test('relations cannot cite unknown evidence', () => {
  const result = buildInterferenceAssessment({
    assessment_id: 'assessment-4',
    subject: 'campaign-cluster-2',
    level: 'CORRELATION',
    confidence: 0.5,
    evidence: [evidence()],
    relations: [{
      relation_id: 'rel-2',
      from_entity: 'entity-a',
      to_entity: 'entity-b',
      relation_type: 'CORRELATED_WITH',
      evidence_ids: ['missing-evidence'],
      confidence: 0.5
    }]
  });

  assert.equal(result.ok, false);
  assert.equal(result.reason, 'RELATION_EVIDENCE_UNKNOWN');
});

test('autonomous attribution and sanction are impossible by invariant', () => {
  assert.equal(canAutonomouslyAttribute(), false);
  assert.equal(canAutonomouslySanction(), false);
});
