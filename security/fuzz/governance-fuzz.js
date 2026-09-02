import assert from 'node:assert/strict';
import { evaluateActionGate } from '../../decision-plane/safety/action-gate.js';
import { isModelEligible } from '../../ai-governance/model-registry/model-policy.js';
import { verifyEvidenceChain } from '../../ai-governance/evidence-provenance/chain.js';

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

function isValidGateApproval(input, result) {
  if (!result?.allowed) return true;
  return Boolean(
    input && typeof input.action === 'string' && input.policyDecision === 'allow'
      && input.evidenceIntegrity === true
      && input.trust && typeof input.trust.score === 'number'
      && Number.isFinite(input.trust.score) && input.trust.score >= 0.7
      && typeof input.trust.uncertainty === 'number'
      && Number.isFinite(input.trust.uncertainty) && input.trust.uncertainty <= 0.3
      && input.simulation && input.simulation.safe === true
  );
}

export function runGovernanceFuzz({ cases = CASES, seed = SEED } = {}) {
  assert(Number.isInteger(cases) && cases >= 1 && cases <= 5000);
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

      if (!isValidGateApproval(input, gateResult)) {
        securityViolations += 1;
        failures.push({ index: i, name: 'FUZZ_SECURITY_VIOLATION_GATE_APPROVAL' });
      }
      if (!assert.deepStrictEqual(input, before)) {
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
