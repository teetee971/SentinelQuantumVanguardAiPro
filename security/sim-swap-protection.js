import { createPublicKey } from 'node:crypto';
import { verifyProofAuthenticity } from '../decision-plane/policy/proof-authenticity.js';
import { validateProofWindow } from '../decision-plane/policy/proof-freshness.js';

const MAX_SIGNAL_AGE_MS = 24 * 60 * 60 * 1000;
const MAX_FUTURE_SKEW_MS = 5 * 60 * 1000;
const MAX_ID_LENGTH = 256;
const SIM_SWAP_PROOF_TYPE = 'sim-swap-signal';

const SIGNAL_WEIGHTS = Object.freeze({
  simChanged: 45,
  numberPorted: 50,
  carrierChanged: 20,
  deviceChanged: 25,
  mfaReset: 40,
  recoveryChanged: 40,
});

const LEVELS = Object.freeze({ low: 0, elevated: 30, high: 60, critical: 90 });
const VERIFIED_ASSESSMENTS = new WeakSet();

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function nonEmptyString(value, maxLength = MAX_ID_LENGTH) {
  return typeof value === 'string' && value.trim().length > 0 && value.trim().length <= maxLength;
}

function failClosed(reason) {
  return { accepted: false, risk: 'critical', score: 100, reason, factors: [] };
}

function validateObservedAt(observedAt, now) {
  if (typeof observedAt !== 'string' || observedAt.trim() === '') return { valid: false };
  const timestamp = Date.parse(observedAt);
  if (!Number.isFinite(timestamp)) return { valid: false };
  if (timestamp > now + MAX_FUTURE_SKEW_MS) return { valid: false };
  if (now - timestamp > MAX_SIGNAL_AGE_MS) return { valid: false };
  return { valid: true, timestamp };
}

function classifyRisk(score) {
  if (score >= LEVELS.critical) return 'critical';
  if (score >= LEVELS.high) return 'high';
  if (score >= LEVELS.elevated) return 'elevated';
  return 'low';
}

function normalizedSignalValues(signals) {
  return Object.fromEntries(Object.keys(SIGNAL_WEIGHTS).map((name) => [name, signals[name] === true]));
}

function validateEvidenceBinding(signals, evidence) {
  if (!isPlainObject(evidence)) return { valid: false, reason: 'SIGNED_EVIDENCE_REQUIRED' };
  if (!nonEmptyString(signals.subjectId) || evidence.subject_id !== signals.subjectId.trim()) {
    return { valid: false, reason: 'EVIDENCE_SUBJECT_MISMATCH' };
  }
  if (evidence.observed_at !== signals.observedAt) {
    return { valid: false, reason: 'EVIDENCE_OBSERVATION_MISMATCH' };
  }
  if (!isPlainObject(evidence.signal_values)) {
    return { valid: false, reason: 'EVIDENCE_SIGNAL_VALUES_REQUIRED' };
  }

  const expected = normalizedSignalValues(signals);
  const evidenceKeys = Object.keys(evidence.signal_values).sort();
  const expectedKeys = Object.keys(expected).sort();
  if (evidenceKeys.length !== expectedKeys.length
      || evidenceKeys.some((key, index) => key !== expectedKeys[index])
      || expectedKeys.some((key) => typeof evidence.signal_values[key] !== 'boolean'
        || evidence.signal_values[key] !== expected[key])) {
    return { valid: false, reason: 'EVIDENCE_SIGNAL_BINDING_MISMATCH' };
  }
  return { valid: true, reason: 'EVIDENCE_BINDING_VALID' };
}

function validateSignedEvidence(signals, { now, trust, replayGuard }) {
  const evidence = signals.evidence;
  if (!isPlainObject(evidence)) return { valid: false, reason: 'SIGNED_EVIDENCE_REQUIRED' };
  if (!nonEmptyString(evidence.evidence_id)) {
    return { valid: false, reason: 'EVIDENCE_ID_REQUIRED' };
  }

  const authenticity = verifyProofAuthenticity(evidence, SIM_SWAP_PROOF_TYPE, trust);
  if (!authenticity.valid) return { valid: false, reason: authenticity.reason };

  const window = validateProofWindow({
    issuedAt: evidence.issued_at,
    expiresAt: evidence.expires_at,
    now,
    maxClockSkewMs: MAX_FUTURE_SKEW_MS,
  });
  if (!window.valid) return { valid: false, reason: window.reason };

  const binding = validateEvidenceBinding(signals, evidence);
  if (!binding.valid) return binding;

  if (!replayGuard || typeof replayGuard.consumeAtomically !== 'function') {
    return { valid: false, reason: 'SIM_SWAP_REPLAY_GUARD_REQUIRED' };
  }
  const issuerId = evidence.issuer_id.trim();
  const evidenceId = evidence.evidence_id.trim();
  const replay = replayGuard.consumeAtomically(`${SIM_SWAP_PROOF_TYPE}:${issuerId}:${evidenceId}`);
  if (!replay || replay.valid !== true) {
    return { valid: false, reason: replay?.reason ?? 'SIM_SWAP_REPLAY_GUARD_FAILED' };
  }
  return { valid: true, reason: 'SIGNED_EVIDENCE_VALID', evidenceId };
}

/**
 * Evaluates authenticated SIM-swap evidence. Every accepted result, including
 * a low-risk result, requires a fresh Ed25519 proof bound to the subject,
 * observation time, and complete boolean signal set, plus atomic anti-replay.
 */
export function evaluateSimSwapRisk(signals = {}, {
  now = Date.now(),
  trust,
  replayGuard,
} = {}) {
  if (!isPlainObject(signals)) return failClosed('INVALID_SIGNALS');
  if (!Number.isFinite(now)) return failClosed('INVALID_CLOCK');

  const observation = validateObservedAt(signals.observedAt, now);
  if (!observation.valid) return failClosed('STALE_FUTURE_OR_INVALID_SIGNAL');

  const evidenceResult = validateSignedEvidence(signals, { now, trust, replayGuard });
  if (!evidenceResult.valid) return failClosed(evidenceResult.reason);

  let score = 0;
  const factors = [];
  for (const [name, weight] of Object.entries(SIGNAL_WEIGHTS)) {
    if (signals[name] === true) {
      score += weight;
      factors.push(name);
    }
  }

  const simOrPort = signals.simChanged === true || signals.numberPorted === true;
  if (simOrPort && signals.mfaReset === true) score += 15;
  if (simOrPort && signals.recoveryChanged === true) score += 15;
  if (signals.deviceChanged === true && signals.mfaReset === true) score += 10;

  score = Math.min(score, 100);
  const assessment = Object.freeze({
    accepted: true,
    evidenceVerified: true,
    risk: classifyRisk(score),
    score,
    factors: Object.freeze([...factors]),
    recoveryRestricted: score >= LEVELS.high,
    independentAuthenticatorRequired: score >= LEVELS.elevated,
  });
  VERIFIED_ASSESSMENTS.add(assessment);
  return assessment;
}

/**
 * Builds a deployment trust object from public Ed25519 keys only. Runtime
 * provisioning, private-key custody and durable replay storage remain external.
 */
export function buildSimSwapEvidenceTrust(config) {
  if (!isPlainObject(config) || !Array.isArray(config.issuers) || config.issuers.length === 0) {
    throw new Error('SIM_SWAP_TRUST_ISSUERS_REQUIRED');
  }
  const authorized = new Set();
  const keys = new Map();
  const keyIds = new Set();

  for (const issuer of config.issuers) {
    if (!isPlainObject(issuer) || !nonEmptyString(issuer.issuer_id)
        || !Array.isArray(issuer.keys) || issuer.keys.length === 0) {
      throw new Error('SIM_SWAP_TRUST_ISSUER_INVALID');
    }
    const issuerId = issuer.issuer_id.trim();
    if (authorized.has(issuerId)) throw new Error('SIM_SWAP_TRUST_DUPLICATE_ISSUER');
    authorized.add(issuerId);
    for (const entry of issuer.keys) {
      if (!isPlainObject(entry) || !nonEmptyString(entry.key_id)
          || !nonEmptyString(entry.public_key_pem, 16_384)) {
        throw new Error('SIM_SWAP_TRUST_KEY_INVALID');
      }
      const keyId = entry.key_id.trim();
      if (keyIds.has(keyId)) throw new Error('SIM_SWAP_TRUST_DUPLICATE_KEY_ID');
      if (/PRIVATE KEY/.test(entry.public_key_pem)) throw new Error('SIM_SWAP_TRUST_PRIVATE_KEY_FORBIDDEN');
      let publicKey;
      try {
        publicKey = createPublicKey(entry.public_key_pem);
      } catch {
        throw new Error('SIM_SWAP_TRUST_PUBLIC_KEY_INVALID');
      }
      if (publicKey.asymmetricKeyType !== 'ed25519') {
        throw new Error('SIM_SWAP_TRUST_KEY_ALGORITHM_UNSUPPORTED');
      }
      keyIds.add(keyId);
      keys.set(`${issuerId}\u0000${keyId}`, publicKey);
    }
  }

  const revokedKeyIds = new Set();
  const revoked = config.revoked_key_ids ?? [];
  if (!Array.isArray(revoked)) throw new Error('SIM_SWAP_TRUST_REVOCATIONS_INVALID');
  for (const value of revoked) {
    if (!nonEmptyString(value) || !keyIds.has(value.trim())) {
      throw new Error('SIM_SWAP_TRUST_REVOKED_KEY_UNKNOWN');
    }
    revokedKeyIds.add(value.trim());
  }

  return Object.freeze({
    authorizedIssuers: Object.freeze({ [SIM_SWAP_PROOF_TYPE]: authorized }),
    revokedKeyIds,
    resolvePublicKey({ issuerId, keyId, proofType } = {}) {
      if (proofType !== SIM_SWAP_PROOF_TYPE) return null;
      return keys.get(`${issuerId}\u0000${keyId}`) ?? null;
    },
  });
}

export function evaluateRecoveryPolicy({ riskAssessment, method, independentAuthenticatorVerified = false } = {}) {
  if (!isPlainObject(riskAssessment) || !VERIFIED_ASSESSMENTS.has(riskAssessment)) {
    return { allowed: false, reason: 'VERIFIED_RISK_ASSESSMENT_REQUIRED' };
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

export function buildSimSwapResponsePlan(riskAssessment) {
  if (!isPlainObject(riskAssessment) || !VERIFIED_ASSESSMENTS.has(riskAssessment)) {
    return { accepted: false, reason: 'VERIFIED_RISK_ASSESSMENT_REQUIRED', actions: [] };
  }
  const actionsByRisk = {
    low: ['CONTINUE_MONITORING'],
    elevated: ['REQUIRE_INDEPENDENT_AUTHENTICATOR', 'NOTIFY_OWNER'],
    high: ['BLOCK_PSTN_RECOVERY', 'REQUIRE_INDEPENDENT_AUTHENTICATOR', 'CHALLENGE_ACTIVE_SESSIONS', 'NOTIFY_OWNER'],
    critical: ['FREEZE_ACCOUNT_RECOVERY', 'BLOCK_PSTN_RECOVERY', 'REVOKE_UNTRUSTED_SESSIONS', 'REQUIRE_HUMAN_REVIEW', 'NOTIFY_OWNER'],
  };
  const actions = actionsByRisk[riskAssessment.risk];
  if (!actions) return { accepted: false, reason: 'INVALID_RISK_LEVEL', actions: [] };
  return {
    accepted: true,
    reason: 'SIM_SWAP_RESPONSE_PLAN_CREATED',
    risk: riskAssessment.risk,
    actions: Object.freeze([...actions]),
    advisoryOnly: true,
  };
}

export const SIM_SWAP_LIMITS = Object.freeze({
  maxSignalAgeMs: MAX_SIGNAL_AGE_MS,
  maxFutureSkewMs: MAX_FUTURE_SKEW_MS,
  proofType: SIM_SWAP_PROOF_TYPE,
});
