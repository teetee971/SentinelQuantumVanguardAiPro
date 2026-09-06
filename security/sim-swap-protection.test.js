import assert from 'node:assert/strict';
import test from 'node:test';
import {
  buildSimSwapResponsePlan,
  evaluateRecoveryPolicy,
  evaluateSimSwapRisk,
} from './sim-swap-protection.js';

const now = Date.parse('2026-09-03T20:00:00.000Z');

const trustedEvidence = Object.freeze({ id: 'trusted-test-evidence' });

function assessed(signals) {
  return evaluateSimSwapRisk(
    { ...signals, evidence: trustedEvidence, observedAt: '2026-09-03T19:30:00.000Z' },
    { now, verifyEvidence: (evidence) => evidence === trustedEvidence },
  );
}

test('no risk signals remain low', () => {
  const result = assessed({});
  assert.equal(result.accepted, true);
  assert.equal(result.risk, 'low');
  assert.equal(result.score, 0);
});

test('SIM change raises risk and requires an independent authenticator', () => {
  const result = assessed({ simChanged: true });
  assert.equal(result.risk, 'elevated');
  assert.equal(result.independentAuthenticatorRequired, true);
});

test('SIM change plus new device is high risk', () => {
  const result = assessed({ simChanged: true, deviceChanged: true });
  assert.equal(result.risk, 'high');
  assert.equal(result.recoveryRestricted, true);
});

test('SIM change plus MFA and recovery changes is critical', () => {
  const result = assessed({ simChanged: true, mfaReset: true, recoveryChanged: true });
  assert.equal(result.risk, 'critical');
  assert.equal(result.score, 100);
});

test('high-risk recovery cannot fall back to SMS or voice', () => {
  const riskAssessment = assessed({ simChanged: true, deviceChanged: true });
  assert.deepEqual(
    evaluateRecoveryPolicy({ riskAssessment, method: 'sms' }),
    { allowed: false, reason: 'PSTN_RECOVERY_BLOCKED_AFTER_HIGH_RISK_EVENT' },
  );
  assert.deepEqual(
    evaluateRecoveryPolicy({ riskAssessment, method: 'voice' }),
    { allowed: false, reason: 'PSTN_RECOVERY_BLOCKED_AFTER_HIGH_RISK_EVENT' },
  );
});

test('independent authenticator clears the elevated recovery requirement', () => {
  const riskAssessment = assessed({ simChanged: true });
  const result = evaluateRecoveryPolicy({
    riskAssessment,
    method: 'webauthn',
    independentAuthenticatorVerified: true,
  });
  assert.deepEqual(result, { allowed: true, reason: 'RECOVERY_POLICY_ALLOW' });
});

test('stale and future signals fail closed', () => {
  const stale = evaluateSimSwapRisk(
    { simChanged: true, observedAt: '2026-09-01T20:00:00.000Z' },
    { now },
  );
  const future = evaluateSimSwapRisk(
    { simChanged: true, observedAt: '2026-09-04T20:00:00.000Z' },
    { now },
  );
  assert.equal(stale.accepted, false);
  assert.equal(stale.reason, 'STALE_OR_FUTURE_SIGNAL');
  assert.equal(future.accepted, false);
  assert.equal(future.reason, 'STALE_OR_FUTURE_SIGNAL');
});

test('malformed input fails closed without throwing', () => {
  assert.equal(evaluateSimSwapRisk(null).accepted, false);
  assert.equal(evaluateSimSwapRisk([]).accepted, false);
  assert.equal(evaluateSimSwapRisk({ observedAt: 'not-a-date' }).accepted, false);
  assert.equal(
    evaluateRecoveryPolicy({ riskAssessment: null, method: 'webauthn' }).allowed,
    false,
  );
});

test('positive SIM-swap signals fail closed without trusted evidence verification', () => {
  const result = evaluateSimSwapRisk(
    { simChanged: true, evidence: { id: 'self-asserted' }, observedAt: '2026-09-03T19:30:00.000Z' },
    { now },
  );
  assert.equal(result.accepted, false);
  assert.equal(result.reason, 'UNVERIFIED_POSITIVE_SIGNAL');
});

test('critical response plan is advisory and blocks PSTN recovery', () => {
  const riskAssessment = assessed({ simChanged: true, mfaReset: true, recoveryChanged: true });
  const plan = buildSimSwapResponsePlan(riskAssessment);
  assert.equal(plan.accepted, true);
  assert.equal(plan.advisoryOnly, true);
  assert.ok(plan.actions.includes('FREEZE_ACCOUNT_RECOVERY'));
  assert.ok(plan.actions.includes('BLOCK_PSTN_RECOVERY'));
  assert.ok(Object.isFrozen(plan.actions));
});
