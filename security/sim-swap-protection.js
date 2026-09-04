const MAX_SIGNAL_AGE_MS = 24 * 60 * 60 * 1000;
const MAX_FUTURE_SKEW_MS = 5 * 60 * 1000;

const SIGNAL_WEIGHTS = Object.freeze({
  simChanged: 45,
  numberPorted: 50,
  carrierChanged: 20,
  deviceChanged: 25,
  mfaReset: 40,
  recoveryChanged: 40,
});

const LEVELS = Object.freeze({ low: 0, elevated: 30, high: 60, critical: 90 });

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasBooleanSignal(signals, name) {
  return signals[name] === true;
}

function validateObservedAt(observedAt, now) {
  if (observedAt === undefined) return { valid: true, stale: false };
  if (typeof observedAt !== 'string') return { valid: false, stale: true };
  const timestamp = Date.parse(observedAt);
  if (!Number.isFinite(timestamp)) return { valid: false, stale: true };
  if (timestamp > now + MAX_FUTURE_SKEW_MS) return { valid: false, stale: true };
  return { valid: true, stale: now - timestamp > MAX_SIGNAL_AGE_MS };
}

function classifyRisk(score) {
  if (score >= LEVELS.critical) return 'critical';
  if (score >= LEVELS.high) return 'high';
  if (score >= LEVELS.elevated) return 'elevated';
  return 'low';
}

/**
 * Evaluates SIM-swap-related risk signals. It does not claim to detect a SIM
 * change from browser APIs and it never contacts a carrier. Authoritative
 * signals must be verified by a trusted server, identity provider, carrier,
 * or device-security service before they are treated as evidence.
 */
export function evaluateSimSwapRisk(signals = {}, { now = Date.now() } = {}) {
  if (!isPlainObject(signals)) {
    return { accepted: false, risk: 'critical', score: 100, reason: 'INVALID_SIGNALS', factors: [] };
  }
  if (!Number.isFinite(now)) {
    return { accepted: false, risk: 'critical', score: 100, reason: 'INVALID_CLOCK', factors: [] };
  }

  const freshness = validateObservedAt(signals.observedAt, now);
  if (!freshness.valid || freshness.stale) {
    return {
      accepted: false,
      risk: 'critical',
      score: 100,
      reason: freshness.stale ? 'STALE_OR_FUTURE_SIGNAL' : 'INVALID_TIMESTAMP',
      factors: [],
    };
  }

  let score = 0;
  const factors = [];
  for (const [name, weight] of Object.entries(SIGNAL_WEIGHTS)) {
    if (hasBooleanSignal(signals, name)) {
      score += weight;
      factors.push(name);
    }
  }

  // Correlated recovery activity after a SIM/number event is materially more
  // dangerous than the individual signals in isolation.
  const simOrPort = signals.simChanged === true || signals.numberPorted === true;
  if (simOrPort && signals.mfaReset === true) score += 15;
  if (simOrPort && signals.recoveryChanged === true) score += 15;
  if (signals.deviceChanged === true && signals.mfaReset === true) score += 10;

  score = Math.min(score, 100);
  return {
    accepted: true,
    risk: classifyRisk(score),
    score,
    factors,
    recoveryRestricted: score >= LEVELS.high,
    independentAuthenticatorRequired: score >= LEVELS.elevated,
  };
}

/**
 * Applies the anti-bypass rule for account recovery. SMS/voice alone cannot
 * clear a high-risk SIM/number event. An independent enrolled authenticator
 * (preferably a phishing-resistant cryptographic authenticator) is required.
 */
export function evaluateRecoveryPolicy({ riskAssessment, method, independentAuthenticatorVerified = false } = {}) {
  if (!isPlainObject(riskAssessment) || riskAssessment.accepted !== true) {
    return { allowed: false, reason: 'VALID_RISK_ASSESSMENT_REQUIRED' };
  }

  const normalizedMethod = typeof method === 'string' ? method.trim().toLowerCase() : '';
  if (!normalizedMethod) return { allowed: false, reason: 'INVALID_RECOVERY_METHOD' };

  const smsLike = normalizedMethod === 'sms' || normalizedMethod === 'voice';
  if (riskAssessment.recoveryRestricted && smsLike) {
    return { allowed: false, reason: 'PSTN_RECOVERY_BLOCKED_AFTER_HIGH_RISK_EVENT' };
  }
  if (riskAssessment.independentAuthenticatorRequired && independentAuthenticatorVerified !== true) {
    return { allowed: false, reason: 'INDEPENDENT_AUTHENTICATOR_REQUIRED' };
  }

  return { allowed: true, reason: 'RECOVERY_POLICY_ALLOW' };
}

export const SIM_SWAP_LIMITS = Object.freeze({
  maxSignalAgeMs: MAX_SIGNAL_AGE_MS,
  maxFutureSkewMs: MAX_FUTURE_SKEW_MS,
});
