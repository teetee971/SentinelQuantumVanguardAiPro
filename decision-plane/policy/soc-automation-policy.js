import { validateHumanApprovalRecord } from './human-approval-record.js';

const PASSIVE_ACTIONS = Object.freeze([
  'normalize_event',
  'deduplicate_event',
  'enrich_alert',
  'create_case',
  'propose_playbook',
]);

const HIGH_IMPACT_ACTIONS = Object.freeze([
  'contain_endpoint',
  'block_indicator',
  'quarantine_artifact',
  'disable_account',
]);

const FORBIDDEN_ACTIONS = Object.freeze([
  'exploit_target',
  'credential_access',
  'establish_persistence',
  'exfiltrate_data',
  'disrupt_service',
]);

const PASSIVE_SET = new Set(PASSIVE_ACTIONS);
const HIGH_IMPACT_SET = new Set(HIGH_IMPACT_ACTIONS);
const FORBIDDEN_SET = new Set(FORBIDDEN_ACTIONS);
const MODES = new Set(['DRY_RUN', 'EXECUTE']);

function nonEmpty(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function normalize(value) {
  return nonEmpty(value) ? value.trim().toLowerCase() : null;
}

function deny(reason, details = {}) {
  return {
    eligible: false,
    reason,
    side_effect_performed: false,
    ...details,
  };
}

/**
 * Fail-closed SOC automation policy boundary.
 *
 * This function never executes an action. Passive processing may be declared
 * eligible for a downstream deterministic worker. High-impact operations only
 * receive a hand-off decision to the existing cryptographically bound
 * execution boundary after dual human control and safety prerequisites.
 */
function assessSocAutomationRequest(request, now = Date.now()) {
  if (!request || typeof request !== 'object' || Array.isArray(request)) {
    return deny('INVALID_REQUEST');
  }

  const action = normalize(request.action);
  const mode = typeof request.mode === 'string' ? request.mode.trim().toUpperCase() : null;

  if (!action || !MODES.has(mode)) return deny('INVALID_ACTION_OR_MODE');
  if (FORBIDDEN_SET.has(action)) return deny('OFFENSIVE_ACTION_FORBIDDEN', { action, mode });
  if (!PASSIVE_SET.has(action) && !HIGH_IMPACT_SET.has(action)) {
    return deny('UNKNOWN_ACTION', { action, mode });
  }

  if (!nonEmpty(request.policy_version) || !nonEmpty(request.target_id)) {
    return deny('POLICY_AND_TARGET_REQUIRED', { action, mode });
  }
  if (request.source_trusted !== true) {
    return deny('TRUSTED_SOURCE_REQUIRED', { action, mode });
  }

  if (PASSIVE_SET.has(action)) {
    return {
      eligible: true,
      reason: mode === 'DRY_RUN' ? 'PASSIVE_DRY_RUN_ELIGIBLE' : 'PASSIVE_PROCESSING_ELIGIBLE',
      action,
      mode,
      next_boundary: 'DETERMINISTIC_WORKER',
      side_effect_performed: false,
    };
  }

  if (mode === 'DRY_RUN') {
    return {
      eligible: true,
      reason: 'HIGH_IMPACT_SIMULATION_ONLY',
      action,
      mode,
      next_boundary: 'SIMULATION',
      side_effect_performed: false,
    };
  }

  if (request.authorized_target !== true) return deny('AUTHORIZED_TARGET_REQUIRED', { action, mode });
  if (request.simulation_safe !== true) return deny('SAFE_SIMULATION_REQUIRED', { action, mode });
  if (!nonEmpty(request.rollback_reference)) return deny('ROLLBACK_REFERENCE_REQUIRED', { action, mode });
  if (request.approvals_authenticated !== true) {
    return deny('AUTHENTICATED_APPROVALS_REQUIRED', { action, mode });
  }

  if (!Array.isArray(request.approvals) || request.approvals.length < 2) {
    return deny('TWO_HUMAN_APPROVALS_REQUIRED', { action, mode });
  }

  const expected = {
    action,
    target_id: request.target_id,
    policy_version: request.policy_version,
  };
  const actors = new Set();

  for (const approval of request.approvals) {
    const result = validateHumanApprovalRecord(approval, expected, now);
    if (!result.valid) return deny(`INVALID_HUMAN_APPROVAL:${result.reason}`, { action, mode });
    actors.add(approval.actor_id);
  }

  if (actors.size < 2) return deny('DISTINCT_APPROVERS_REQUIRED', { action, mode });

  return {
    eligible: true,
    reason: 'BOUND_AUTHORIZATION_REQUIRED',
    action,
    mode,
    next_boundary: 'AUTHORIZE_BOUNDED_OPERATION',
    required_approvers: 2,
    side_effect_performed: false,
  };
}

export {
  PASSIVE_ACTIONS,
  HIGH_IMPACT_ACTIONS,
  FORBIDDEN_ACTIONS,
  assessSocAutomationRequest,
};
