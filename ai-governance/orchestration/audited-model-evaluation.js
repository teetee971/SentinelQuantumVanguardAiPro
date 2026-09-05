import { appendAuditEvent } from '../audit/audit-event.js';
import { evaluateModelForApproval } from './model-evaluation-gate.js';

function isPlainObject(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return false;
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function validAuditContext(context) {
  return isPlainObject(context)
    && typeof context.trace_id === 'string'
    && context.trace_id.length > 0
    && typeof context.decision_id === 'string'
    && context.decision_id.length > 0
    && Array.isArray(context.evidence_refs)
    && typeof context.policy_version === 'string'
    && context.policy_version.length > 0
    && typeof context.timestamp === 'string'
    && context.timestamp.length > 0;
}

/**
 * Evaluates one exact model/version and records the result in the canonical
 * tamper-evident audit chain before returning an auditable governance result.
 *
 * The audit event is local and immutable. This function performs no registry
 * mutation, model invocation, deployment, promotion, or external persistence.
 */
export function evaluateModelForApprovalWithAudit({
  model,
  evaluationInput,
  auditChain = [],
  auditContext,
} = {}) {
  if (!model?.model_id || !model?.version) {
    return {
      allowed: false,
      reason: 'MODEL_BINDING_REQUIRED',
      audit_recorded: false,
      production_side_effect_performed: false,
    };
  }
  if (!Array.isArray(auditChain)) {
    return {
      allowed: false,
      reason: 'AUDIT_CHAIN_REQUIRED',
      audit_recorded: false,
      production_side_effect_performed: false,
    };
  }
  if (!validAuditContext(auditContext)) {
    return {
      allowed: false,
      reason: 'AUDIT_CONTEXT_REQUIRED',
      audit_recorded: false,
      production_side_effect_performed: false,
    };
  }

  const governance = evaluateModelForApproval({ model, evaluationInput });
  const event = {
    trace_id: auditContext.trace_id,
    decision_id: auditContext.decision_id,
    evidence_refs: [...auditContext.evidence_refs],
    model_id: model.model_id,
    model_version: model.version,
    policy_version: auditContext.policy_version,
    action: 'MODEL_EVALUATION',
    result: governance.allowed ? 'APPROVED' : 'DENIED',
    timestamp: auditContext.timestamp,
  };

  let nextAuditChain;
  try {
    nextAuditChain = appendAuditEvent(auditChain, event);
  } catch (error) {
    return {
      allowed: false,
      reason: 'AUDIT_APPEND_FAILED',
      governance,
      audit_recorded: false,
      audit_error: error instanceof Error ? error.message : 'UNKNOWN_ERROR',
      production_side_effect_performed: false,
    };
  }

  return {
    ...governance,
    audit_chain: nextAuditChain,
    audit_recorded: true,
    production_side_effect_performed: false,
  };
}
