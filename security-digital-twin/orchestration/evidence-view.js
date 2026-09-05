import { createTwin, addEntity, addEvidence } from '../core/digital-twin.js';
import { createEvidenceGraph, addNode, addEdge, traceEvidence } from '../core/evidence-graph.js';

const DEFAULT_LIMITS = Object.freeze({
  max_entities: 1000,
  max_evidence: 2000,
  max_relations: 5000,
  max_trace_depth: 8,
});

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function boundedInteger(value, fallback, max) {
  if (!Number.isInteger(value) || value < 0) return fallback;
  return Math.min(value, max);
}

/**
 * Builds one immutable defensive digital-twin snapshot and an evidence graph
 * from already-observed, explicitly supplied records. It performs no discovery,
 * attribution, enforcement, blocking, remediation, or external side effect.
 */
export function buildDigitalTwinEvidenceView({
  entities = [],
  evidence = [],
  relations = [],
  focusNodeId = null,
  limits = {},
} = {}) {
  if (!Array.isArray(entities) || !Array.isArray(evidence) || !Array.isArray(relations) || !isPlainObject(limits)) {
    return { allowed: false, reason: 'DIGITAL_TWIN_INPUT_INVALID', side_effect_performed: false };
  }

  const effectiveLimits = {
    max_entities: boundedInteger(limits.max_entities, DEFAULT_LIMITS.max_entities, DEFAULT_LIMITS.max_entities),
    max_evidence: boundedInteger(limits.max_evidence, DEFAULT_LIMITS.max_evidence, DEFAULT_LIMITS.max_evidence),
    max_relations: boundedInteger(limits.max_relations, DEFAULT_LIMITS.max_relations, DEFAULT_LIMITS.max_relations),
    max_trace_depth: boundedInteger(limits.max_trace_depth, DEFAULT_LIMITS.max_trace_depth, DEFAULT_LIMITS.max_trace_depth),
  };

  if (entities.length > effectiveLimits.max_entities) {
    return { allowed: false, reason: 'DIGITAL_TWIN_ENTITY_LIMIT_EXCEEDED', side_effect_performed: false };
  }
  if (evidence.length > effectiveLimits.max_evidence) {
    return { allowed: false, reason: 'DIGITAL_TWIN_EVIDENCE_LIMIT_EXCEEDED', side_effect_performed: false };
  }
  if (relations.length > effectiveLimits.max_relations) {
    return { allowed: false, reason: 'DIGITAL_TWIN_RELATION_LIMIT_EXCEEDED', side_effect_performed: false };
  }

  let twin = createTwin();
  let graph = createEvidenceGraph();

  try {
    for (const entity of entities) {
      twin = addEntity(twin, entity);
      graph = addNode(graph, {
        id: entity.entity_id,
        kind: 'entity',
        entity_type: entity.entity_type,
      });
    }

    for (const item of evidence) {
      twin = addEvidence(twin, item);
      graph = addNode(graph, {
        id: item.evidence_id,
        kind: 'evidence',
        evidence_kind: item.kind,
      });
    }

    for (const relation of relations) {
      if (!isPlainObject(relation)) throw new Error('invalid_relation');
      graph = addEdge(
        graph,
        relation.from,
        relation.relation,
        relation.to,
        relation.confidence ?? 1,
      );
    }
  } catch (error) {
    return {
      allowed: false,
      reason: 'DIGITAL_TWIN_BUILD_REJECTED',
      error_code: error instanceof Error ? error.message : 'UNKNOWN_ERROR',
      side_effect_performed: false,
    };
  }

  let trace = null;
  if (focusNodeId !== null) {
    if (typeof focusNodeId !== 'string' || !graph.nodes.has(focusNodeId)) {
      return { allowed: false, reason: 'DIGITAL_TWIN_FOCUS_NODE_UNKNOWN', side_effect_performed: false };
    }
    trace = traceEvidence(graph, focusNodeId, effectiveLimits.max_trace_depth);
  }

  return {
    allowed: true,
    reason: 'DIGITAL_TWIN_EVIDENCE_VIEW_READY',
    twin,
    graph,
    trace,
    limits: effectiveLimits,
    side_effect_performed: false,
  };
}

export { DEFAULT_LIMITS };
