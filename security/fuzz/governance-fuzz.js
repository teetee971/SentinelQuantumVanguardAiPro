import assert from 'node:assert/strict';
import { evaluateActionGate } from '../../decision-plane/safety/action-gate.js';
import { isModelEligible } from '../../ai-governance/model-registry/model-policy.js';
import { verifyEvidenceChain } from '../../ai-governance/evidence-provenance/chain.js';
import { isSensitiveAction, normalizeOperation } from '../../decision-plane/policy/action-catalog.js';
import { validateAuthorizationRecord } from '../../decision-plane/policy/authorization-record.js';
import { validateHumanApprovalRecord } from '../../decision-plane/policy/human-approval-record.js';
import { validateSimulationRecord } from '../../decision-plane/policy/simulation-record.js';

const SEED = 0x51_7e_11;
const CASES = 500;

function next(seed) {
  return (Math.imul(seed, 1664525) + 1013904223) >>> 0;
}

function value(seed, depth = 0) {
  seed = next(seed);
  const mode = seed % 8;
  if (depth >= 3 || mode < 3) return [seed, [null, true, false, '', '\u0000', Number.NaN, Number.POSITIVE_INFINITY][seed % 7]];
  if (mode === 3) return [seed, { __proto__: 'pollution', constructor: { prototype: { polluted: true } } }];
  if (mode === 4) return [seed, Array.from({ length: seed % 25 }, (_, i) => i)];
  const object = {};
  let current = seed;
  for (let i = 0; i < seed % 8; i += 1) {
    [current, object[`k${i}`]] = value(current, depth + 1);
  }
  return [current, object];
}

function assertAllowedResultIsFullyBound(input, result) {
  if (!result?.allowed) return;
  const action = normalizeOperation(input?.action);
  if (!isSensitiveAction(action)) return;

  const now = Number.isFinite(input?.now) ? input.now : Date.now();
  const authorization = validateAuthorizationRecord(input.authorization, now);
  assert.equal(authorization.valid, true, 'sensitive allow requires valid authorization proof');
  assert.equal(input.authorization.action, action);
  assert.equal(input.authorization.target_id, input.targetId);
  assert.equal(input.authorization.policy_version, input.policyVersion);

  const approval = validateHumanApprovalRecord(input.humanApproval, {
    action,
    target_id: input.targetId,
    policy_version: input.policyVersion,
  }, now);
  assert.equal(approval.valid, true, 'sensitive allow requires valid human approval proof');

  const simulation = validateSimulationRecord(input.simulation, {
    action,
    action_id: input.actionId,
    target_id: input.targetId,
    policy_version: input.policyVersion,
  }, now);
  assert.equal(simulation.valid, true, 'sensitive allow requires valid simulation proof');
}

function runAdversarialPositiveCases() {
  const common = {
    policyDecision: 'allow',
    evidenceIntegrity: true,
    trust: { score: 1, uncertainty: 0 },
    now: Date.parse('2026-09-03T12:00:00.000Z'),
    action: 'block',
    actionId: 'action-fuzz-001',
    targetId: 'target-fuzz-001',
    policyVersion: 'policy-fuzz-1',
    targetAuthorized: true,
    humanValidated: true,
    simulation: { safe: true },
  };
  const result = evaluateActionGate(common);
  assert.equal(result.allowed, false, 'positive booleans must never authorize a sensitive action');
}

export function runGovernanceFuzz({ cases = CASES, seed = SEED } = {}) {
  assert(Number.isInteger(cases) && cases >= 1 && cases <= 5000);
  runAdversarialPositiveCases();

  let state = seed >>> 0;
  let crashes = 0;
  let securityViolations = 0;
  const failures = [];

  for (let i = 0; i < cases; i += 1) {
    let input;
    [state, input] = value(state);
    const before = structuredClone(input);
    try {
      const modelResult = isModelEligible(input, input);
      const gateResult = evaluateActionGate(input);
      verifyEvidenceChain(input);

      try {
        assertAllowedResultIsFullyBound(input, gateResult);
      } catch (error) {
        securityViolations += 1;
        failures.push({ index: i, name: error?.message ?? 'FUZZ_SECURITY_VIOLATION_GATE_PROOF' });
      }
      try {
        assert.deepStrictEqual(input, before);
      } catch {
        securityViolations += 1;
        failures.push({ index: i, name: 'FUZZ_SECURITY_VIOLATION_INPUT_MUTATION' });
      }
      if (modelResult?.allowed === true && (!input || typeof input !== 'object')) {
        securityViolations += 1;
        failures.push({ index: i, name: 'FUZZ_SECURITY_VIOLATION_MODEL_APPROVAL' });
      }
    } catch (error) {
      crashes += 1;
      failures.push({ index: i, name: error?.name ?? 'Error' });
    }
  }

  return { seed, cases, crashes, securityViolations, passed: crashes === 0 && securityViolations === 0, failures };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const result = runGovernanceFuzz();
  console.log(JSON.stringify(result));
  if (!result.passed) process.exitCode = 1;
}
