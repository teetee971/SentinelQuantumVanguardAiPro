import assert from 'node:assert/strict';
import test from 'node:test';
import { generateKeyPairSync, sign } from 'node:crypto';
import { signingPayload } from '../decision-plane/policy/proof-authenticity.js';
import { createInMemoryReplayGuard } from '../decision-plane/action-verification/anti-replay.js';
import {
  buildSimSwapEvidenceTrust,
  buildSimSwapResponsePlan,
  evaluateRecoveryPolicy,
  evaluateSimSwapRisk,
  SIM_SWAP_LIMITS,
} from './sim-swap-protection.js';

const now = Date.parse('2026-09-03T20:00:00.000Z');
const observedAt = '2026-09-03T19:30:00.000Z';
const { publicKey, privateKey } = generateKeyPairSync('ed25519');
const { privateKey: attackerPrivateKey } = generateKeyPairSync('ed25519');
let evidenceCounter = 0;

function pem(key) {
  return key.export({ type: 'spki', format: 'pem' }).toString();
}

function trustConfig(overrides = {}) {
  return {
    issuers: [{
      issuer_id: 'carrier-adapter',
      keys: [{ key_id: 'carrier-key-1', public_key_pem: pem(publicKey) }],
    }],
    revoked_key_ids: [],
    ...overrides,
  };
}

function signalValues(signals) {
  return {
    simChanged: signals.simChanged === true,
    numberPorted: signals.numberPorted === true,
    carrierChanged: signals.carrierChanged === true,
    deviceChanged: signals.deviceChanged === true,
    mfaReset: signals.mfaReset === true,
    recoveryChanged: signals.recoveryChanged === true,
  };
}

function signedEvidence(signals, overrides = {}, signer = privateKey) {
  evidenceCounter += 1;
  const record = {
    evidence_id: `sim-evidence-${evidenceCounter}`,
    subject_id: signals.subjectId,
    observed_at: signals.observedAt,
    signal_values: signalValues(signals),
    issued_at: '2026-09-03T19:31:00.000Z',
    expires_at: '2026-09-03T20:30:00.000Z',
    issuer_id: 'carrier-adapter',
    key_id: 'carrier-key-1',
    signature_alg: 'ed25519',
    ...overrides,
  };
  record.signature = sign(null, signingPayload(record, SIM_SWAP_LIMITS.proofType), signer).toString('base64');
  return record;
}

function assessed(values = {}) {
  const signals = { subjectId: 'account-7', observedAt, ...values };
  signals.evidence = signedEvidence(signals);
  return evaluateSimSwapRisk(signals, {
    now,
    trust: buildSimSwapEvidenceTrust(trustConfig()),
    replayGuard: createInMemoryReplayGuard(),
  });
}

test('fresh signed negative evidence can produce a low-risk result', () => {
  const result = assessed();
  assert.equal(result.accepted, true);
  assert.equal(result.evidenceVerified, true);
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

test('unsigned low and positive claims both fail closed', () => {
  for (const values of [{}, { simChanged: true }]) {
    const result = evaluateSimSwapRisk({ subjectId: 'account-7', observedAt, ...values }, { now });
    assert.equal(result.accepted, false);
    assert.equal(result.reason, 'SIGNED_EVIDENCE_REQUIRED');
  }
});

test('a caller-provided always-true callback cannot bypass signed evidence', () => {
  const result = evaluateSimSwapRisk(
    { subjectId: 'account-7', observedAt, simChanged: true, evidence: { selfAsserted: true } },
    { now, verifyEvidence: () => true },
  );
  assert.equal(result.accepted, false);
  assert.equal(result.reason, 'EVIDENCE_ID_REQUIRED');
});

test('evidence is bound to the full signal set', () => {
  const signals = { subjectId: 'account-7', observedAt, simChanged: true };
  const evidence = signedEvidence(signals);
  const mutated = { ...signals, deviceChanged: true, evidence };
  const result = evaluateSimSwapRisk(mutated, {
    now,
    trust: buildSimSwapEvidenceTrust(trustConfig()),
    replayGuard: createInMemoryReplayGuard(),
  });
  assert.equal(result.accepted, false);
  assert.equal(result.reason, 'EVIDENCE_SIGNAL_BINDING_MISMATCH');
});

test('a forged Ed25519 signature is rejected', () => {
  const signals = { subjectId: 'account-7', observedAt, simChanged: true };
  signals.evidence = signedEvidence(signals, {}, attackerPrivateKey);
  const result = evaluateSimSwapRisk(signals, {
    now,
    trust: buildSimSwapEvidenceTrust(trustConfig()),
    replayGuard: createInMemoryReplayGuard(),
  });
  assert.equal(result.reason, 'PROOF_SIGNATURE_INVALID');
});

test('the same signed evidence cannot be replayed', () => {
  const signals = { subjectId: 'account-7', observedAt, simChanged: true };
  signals.evidence = signedEvidence(signals);
  const options = {
    now,
    trust: buildSimSwapEvidenceTrust(trustConfig()),
    replayGuard: createInMemoryReplayGuard(),
  };
  assert.equal(evaluateSimSwapRisk(signals, options).accepted, true);
  const replay = evaluateSimSwapRisk(signals, options);
  assert.equal(replay.accepted, false);
  assert.equal(replay.reason, 'REPLAY_DETECTED');
});

test('evidence fails closed without atomic replay protection', () => {
  const signals = { subjectId: 'account-7', observedAt };
  signals.evidence = signedEvidence(signals);
  const result = evaluateSimSwapRisk(signals, {
    now,
    trust: buildSimSwapEvidenceTrust(trustConfig()),
  });
  assert.equal(result.reason, 'SIM_SWAP_REPLAY_GUARD_REQUIRED');
});

test('stale, future and malformed observations fail closed', () => {
  for (const value of ['2026-09-01T20:00:00.000Z', '2026-09-04T20:00:00.000Z', 'not-a-date']) {
    assert.equal(evaluateSimSwapRisk({ subjectId: 'account-7', observedAt: value }, { now }).accepted, false);
  }
});

test('trust config rejects private, non-Ed25519, duplicate and unknown revoked keys', () => {
  const privatePem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  assert.throws(
    () => buildSimSwapEvidenceTrust(trustConfig({
      issuers: [{ issuer_id: 'carrier-adapter', keys: [{ key_id: 'k', public_key_pem: privatePem }] }],
    })),
    /SIM_SWAP_TRUST_PRIVATE_KEY_FORBIDDEN/,
  );
  assert.throws(
    () => buildSimSwapEvidenceTrust(trustConfig({ revoked_key_ids: ['unknown'] })),
    /SIM_SWAP_TRUST_REVOKED_KEY_UNKNOWN/,
  );
  assert.throws(
    () => buildSimSwapEvidenceTrust({
      issuers: [
        ...trustConfig().issuers,
        { issuer_id: 'second', keys: [{ key_id: 'carrier-key-1', public_key_pem: pem(publicKey) }] },
      ],
    }),
    /SIM_SWAP_TRUST_DUPLICATE_KEY_ID/,
  );
});

test('revoked carrier evidence key is rejected', () => {
  const signals = { subjectId: 'account-7', observedAt, simChanged: true };
  signals.evidence = signedEvidence(signals);
  const result = evaluateSimSwapRisk(signals, {
    now,
    trust: buildSimSwapEvidenceTrust(trustConfig({ revoked_key_ids: ['carrier-key-1'] })),
    replayGuard: createInMemoryReplayGuard(),
  });
  assert.equal(result.reason, 'PROOF_KEY_REVOKED');
});

test('high-risk recovery cannot fall back to SMS or voice', () => {
  const riskAssessment = assessed({ simChanged: true, deviceChanged: true });
  for (const method of ['sms', 'voice']) {
    assert.deepEqual(evaluateRecoveryPolicy({ riskAssessment, method }), {
      allowed: false,
      reason: 'PSTN_RECOVERY_BLOCKED_AFTER_HIGH_RISK_EVENT',
    });
  }
});

test('independent authenticator clears the elevated recovery requirement', () => {
  const riskAssessment = assessed({ simChanged: true });
  assert.deepEqual(evaluateRecoveryPolicy({
    riskAssessment,
    method: 'webauthn',
    independentAuthenticatorVerified: true,
  }), { allowed: true, reason: 'RECOVERY_POLICY_ALLOW' });
});

test('unverified assessment cannot authorize recovery or a response plan', () => {
  const fake = { accepted: true, risk: 'low', evidenceVerified: true };
  assert.equal(evaluateRecoveryPolicy({ riskAssessment: fake, method: 'webauthn' }).allowed, false);
  assert.equal(buildSimSwapResponsePlan(fake).accepted, false);
});

test('critical response plan is advisory and blocks PSTN recovery', () => {
  const plan = buildSimSwapResponsePlan(assessed({
    simChanged: true,
    mfaReset: true,
    recoveryChanged: true,
  }));
  assert.equal(plan.accepted, true);
  assert.equal(plan.advisoryOnly, true);
  assert.ok(plan.actions.includes('FREEZE_ACCOUNT_RECOVERY'));
  assert.ok(plan.actions.includes('BLOCK_PSTN_RECOVERY'));
  assert.ok(Object.isFrozen(plan.actions));
});
