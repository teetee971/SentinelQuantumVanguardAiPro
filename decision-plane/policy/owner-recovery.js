const OWNER_RECOVERY_FACTORS = Object.freeze([
  'registered_device',
  'hardware_security_key',
  'offline_break_glass',
]);

const DISALLOWED_SINGLE_FACTORS = Object.freeze([
  'imei',
  'phone_number',
  'email_only',
  'username_only',
]);

function normalize(value) {
  return typeof value === 'string' ? value.trim().toLowerCase() : null;
}

function evaluateOwnerRecoveryRequest({ factor, authenticated = false, factorVerified = false, lostDeviceId = null } = {}) {
  const normalizedFactor = normalize(factor);

  if (!authenticated) {
    return { allowed: false, reason: 'OWNER_RECOVERY_AUTHENTICATION_REQUIRED' };
  }
  if (!factorVerified) {
    return { allowed: false, reason: 'OWNER_RECOVERY_FACTOR_VERIFICATION_REQUIRED' };
  }
  if (DISALLOWED_SINGLE_FACTORS.includes(normalizedFactor)) {
    return { allowed: false, reason: 'OWNER_RECOVERY_WEAK_FACTOR_REJECTED' };
  }
  if (!OWNER_RECOVERY_FACTORS.includes(normalizedFactor)) {
    return { allowed: false, reason: 'OWNER_RECOVERY_FACTOR_UNKNOWN' };
  }
  if (normalizedFactor !== 'registered_device' && !normalize(lostDeviceId)) {
    return { allowed: false, reason: 'OWNER_RECOVERY_LOST_DEVICE_REQUIRED' };
  }

  return {
    allowed: true,
    reason: 'OWNER_RECOVERY_FACTOR_ACCEPTED',
    factor: normalizedFactor,
    nextRequiredStep: normalizedFactor === 'registered_device' ? 'COMPLETE_OWNER_AUTHENTICATION' : 'REVOKE_LOST_DEVICE',
  };
}

function evaluateLostDeviceRevocation({ lostDeviceId, revoked = false, auditRecorded = false } = {}) {
  if (!normalize(lostDeviceId)) {
    return { allowed: false, reason: 'LOST_DEVICE_ID_REQUIRED' };
  }
  if (!revoked) {
    return { allowed: false, reason: 'LOST_DEVICE_REVOCATION_REQUIRED' };
  }
  if (!auditRecorded) {
    return { allowed: false, reason: 'LOST_DEVICE_REVOCATION_AUDIT_REQUIRED' };
  }

  return { allowed: true, reason: 'LOST_DEVICE_REVOKED', nextRequiredStep: 'ENROLL_REPLACEMENT_DEVICE' };
}

function evaluateReplacementDeviceEnrollment({ lostDeviceRevoked = false, newDeviceKeyBound = false, strongAuthVerified = false } = {}) {
  if (!lostDeviceRevoked) {
    return { allowed: false, reason: 'LOST_DEVICE_MUST_BE_REVOKED_FIRST' };
  }
  if (!strongAuthVerified) {
    return { allowed: false, reason: 'STRONG_AUTH_REQUIRED' };
  }
  if (!newDeviceKeyBound) {
    return { allowed: false, reason: 'NEW_DEVICE_KEY_BINDING_REQUIRED' };
  }

  return { allowed: true, reason: 'OWNER_REPLACEMENT_DEVICE_ENROLLED' };
}

export {
  OWNER_RECOVERY_FACTORS,
  DISALLOWED_SINGLE_FACTORS,
  evaluateOwnerRecoveryRequest,
  evaluateLostDeviceRevocation,
  evaluateReplacementDeviceEnrollment,
};
