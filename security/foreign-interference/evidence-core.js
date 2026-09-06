import { createHash } from 'node:crypto';

export const ASSESSMENT_LEVELS = Object.freeze([
  'OBSERVATION',
  'CORRELATION',
  'HYPOTHESIS',
  'CHARACTERIZATION',
  'ATTRIBUTION'
]);

const ATTRIBUTION_LEVEL = 'ATTRIBUTION';
const SHA256_HEX = /^[a-f0-9]{64}$/i;

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function isIsoDate(value) {
  return isNonEmptyString(value) && Number.isFinite(Date.parse(value));
}

function normalizeConfidence(value) {
  if (!Number.isFinite(value)) return null;
  if (value < 0 || value > 1) return null;
  return value;
}

export function hashEvidenceContent(content) {
  return createHash('sha256')
    .update(typeof content === 'string' ? content : JSON.stringify(content), 'utf8')
    .digest('hex');
}

export function validateEvidenceRecord(record) {
  if (!record || typeof record !== 'object') {
    return { valid: false, reason: 'EVIDENCE_RECORD_REQUIRED' };
  }

  const requiredStrings = ['evidence_id', 'source_id', 'source_type', 'source_uri'];
  for (const field of requiredStrings) {
    if (!isNonEmptyString(record[field])) {
      return { valid: false, reason: `EVIDENCE_${field.toUpperCase()}_REQUIRED` };
    }
  }

  if (!isIsoDate(record.observed_at)) {
    return { valid: false, reason: 'EVIDENCE_OBSERVED_AT_INVALID' };
  }

  if (!isIsoDate(record.collected_at)) {
    return { valid: false, reason: 'EVIDENCE_COLLECTED_AT_INVALID' };
  }

  if (!SHA256_HEX.test(record.content_hash || '')) {
    return { valid: false, reason: 'EVIDENCE_CONTENT_HASH_INVALID' };
  }

  if (record.legally_accessible !== true) {
    return { valid: false, reason: 'EVIDENCE_SOURCE_NOT_AUTHORIZED' };
  }

  return { valid: true, reason: 'EVIDENCE_VALID' };
}

export function validateRelation(relation, evidenceById = new Map()) {
  if (!relation || typeof relation !== 'object') {
    return { valid: false, reason: 'RELATION_REQUIRED' };
  }

  for (const field of ['relation_id', 'from_entity', 'to_entity', 'relation_type']) {
    if (!isNonEmptyString(relation[field])) {
      return { valid: false, reason: `RELATION_${field.toUpperCase()}_REQUIRED` };
    }
  }

  if (!Array.isArray(relation.evidence_ids) || relation.evidence_ids.length === 0) {
    return { valid: false, reason: 'RELATION_EVIDENCE_REQUIRED' };
  }

  if (normalizeConfidence(relation.confidence) === null) {
    return { valid: false, reason: 'RELATION_CONFIDENCE_INVALID' };
  }

  for (const evidenceId of relation.evidence_ids) {
    if (!evidenceById.has(evidenceId)) {
      return { valid: false, reason: 'RELATION_EVIDENCE_UNKNOWN' };
    }
  }

  return { valid: true, reason: 'RELATION_VALID' };
}

export function validateHumanAttributionGate(gate) {
  if (!gate || typeof gate !== 'object') {
    return { allowed: false, reason: 'HUMAN_ATTRIBUTION_GATE_REQUIRED' };
  }

  if (gate.human_validated !== true) {
    return { allowed: false, reason: 'HUMAN_VALIDATION_REQUIRED' };
  }

  for (const field of ['validated_by', 'validated_at', 'authority_basis', 'decision_reference']) {
    if (!isNonEmptyString(gate[field])) {
      return { allowed: false, reason: `ATTRIBUTION_${field.toUpperCase()}_REQUIRED` };
    }
  }

  if (!isIsoDate(gate.validated_at)) {
    return { allowed: false, reason: 'ATTRIBUTION_VALIDATED_AT_INVALID' };
  }

  return { allowed: true, reason: 'HUMAN_ATTRIBUTION_GATE_VALID' };
}

export function buildInterferenceAssessment({
  assessment_id,
  subject,
  level,
  confidence,
  evidence = [],
  relations = [],
  attribution_gate = null,
  summary = ''
}) {
  if (!isNonEmptyString(assessment_id)) {
    return { ok: false, reason: 'ASSESSMENT_ID_REQUIRED' };
  }

  if (!isNonEmptyString(subject)) {
    return { ok: false, reason: 'ASSESSMENT_SUBJECT_REQUIRED' };
  }

  if (!ASSESSMENT_LEVELS.includes(level)) {
    return { ok: false, reason: 'ASSESSMENT_LEVEL_INVALID' };
  }

  const normalizedConfidence = normalizeConfidence(confidence);
  if (normalizedConfidence === null) {
    return { ok: false, reason: 'ASSESSMENT_CONFIDENCE_INVALID' };
  }

  if (!Array.isArray(evidence) || evidence.length === 0) {
    return { ok: false, reason: 'ASSESSMENT_EVIDENCE_REQUIRED' };
  }

  const evidenceById = new Map();
  for (const record of evidence) {
    const validation = validateEvidenceRecord(record);
    if (!validation.valid) return { ok: false, reason: validation.reason };
    if (evidenceById.has(record.evidence_id)) {
      return { ok: false, reason: 'EVIDENCE_ID_DUPLICATE' };
    }
    evidenceById.set(record.evidence_id, record);
  }

  for (const relation of relations) {
    const validation = validateRelation(relation, evidenceById);
    if (!validation.valid) return { ok: false, reason: validation.reason };
  }

  if (level === ATTRIBUTION_LEVEL) {
    const gate = validateHumanAttributionGate(attribution_gate);
    if (!gate.allowed) return { ok: false, reason: gate.reason };
  }

  return {
    ok: true,
    assessment: {
      assessment_id,
      subject,
      level,
      confidence: normalizedConfidence,
      summary: String(summary || ''),
      evidence_ids: evidence.map((record) => record.evidence_id),
      relation_ids: relations.map((relation) => relation.relation_id),
      attribution: level === ATTRIBUTION_LEVEL
        ? {
            human_validated: true,
            validated_by: attribution_gate.validated_by,
            validated_at: attribution_gate.validated_at,
            authority_basis: attribution_gate.authority_basis,
            decision_reference: attribution_gate.decision_reference
          }
        : null,
      autonomous_enforcement_allowed: false
    }
  };
}

export function canAutonomouslyAttribute() {
  return false;
}

export function canAutonomouslySanction() {
  return false;
}
