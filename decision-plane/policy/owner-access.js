const OWNER_ROLE = 'owner';

const OWNER_ADMIN_CAPABILITIES = Object.freeze([
  'system.configure',
  'system.diagnose',
  'system.inspect',
  'system.recover',
  'system.feature.manage',
  'system.policy.manage',
  'system.audit.read',
  'system.audit.export',
  'system.user.manage',
  'simulation.manage',
  'deployment.manage',
]);

const OWNER_ADMIN_CAPABILITY_SET = new Set(OWNER_ADMIN_CAPABILITIES);

const NON_BYPASSABLE_OPERATIONAL_CONTROLS = Object.freeze([
  'target_authorization',
  'human_validation_for_sensitive_actions',
  'evidence_integrity',
  'trust_thresholds',
  'safe_simulation_or_authorized_execution_boundary',
  'proof_authenticity',
  'proof_freshness',
  'anti_replay',
  'audit_trail',
  'kill_switch',
]);

function normalize(value) {
  return typeof value === 'string' ? value.trim().toLowerCase() : null;
}

/**
 * Grants the product owner complete administrative control over Sentinel itself.
 * This is intentionally separate from authorization to act on external targets.
 * Authentication and MFA are required because OWNER is the highest product role.
 */
function evaluateOwnerAdministrativeAccess({ role, capability, authenticated = false, mfaVerified = false } = {}) {
  if (normalize(role) !== OWNER_ROLE) {
    return { allowed: false, reason: 'OWNER_ROLE_REQUIRED' };
  }
  if (authenticated !== true) {
    return { allowed: false, reason: 'OWNER_AUTHENTICATION_REQUIRED' };
  }
  if (mfaVerified !== true) {
    return { allowed: false, reason: 'OWNER_MFA_REQUIRED' };
  }

  const normalizedCapability = normalize(capability);
  if (!normalizedCapability || !OWNER_ADMIN_CAPABILITY_SET.has(normalizedCapability)) {
    return { allowed: false, reason: 'UNKNOWN_ADMIN_CAPABILITY' };
  }

  return {
    allowed: true,
    reason: 'OWNER_ADMIN_ALLOW',
    role: OWNER_ROLE,
    capability: normalizedCapability,
  };
}

function canOwnerBypassOperationalAuthorization() {
  return false;
}

export {
  OWNER_ROLE,
  OWNER_ADMIN_CAPABILITIES,
  NON_BYPASSABLE_OPERATIONAL_CONTROLS,
  evaluateOwnerAdministrativeAccess,
  canOwnerBypassOperationalAuthorization,
};
