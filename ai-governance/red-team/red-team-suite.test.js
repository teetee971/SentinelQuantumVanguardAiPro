import assert from 'node:assert/strict';
import test from 'node:test';
import { evaluateRedTeamCase, getRedTeamCases, runRedTeamSuite } from './red-team-suite.js';

test('red team suite is deterministic and fully passes', () => {
  const result = runRedTeamSuite();
  assert.equal(result.failed, 0);
  assert.equal(result.passed, result.total);
  assert.equal(result.suite_version, '1.2.0');
});

test('prompt injection is classified as untrusted', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-01');
  assert.equal(evaluateRedTeamCase(item).passed, true);
});

test('sensitive action without structured proof is denied', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-03');
  assert.equal(evaluateRedTeamCase(item).reason, 'SENSITIVE_ACTION_DENIED');
});

test('positive legacy booleans do not count as authorization', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-07');
  assert.equal(evaluateRedTeamCase(item).passed, true);
  assert.equal(evaluateRedTeamCase(item).reason, 'SENSITIVE_ACTION_DENIED');
});

test('authorization target mismatch is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-08');
  assert.equal(evaluateRedTeamCase(item).passed, true);
  assert.equal(evaluateRedTeamCase(item).reason, 'MISMATCH_REJECTED');
});

test('human approval target mismatch is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-09');
  assert.equal(evaluateRedTeamCase(item).passed, true);
  assert.equal(evaluateRedTeamCase(item).reason, 'MISMATCH_REJECTED');
});

test('simulation action mismatch is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-10');
  assert.equal(evaluateRedTeamCase(item).passed, true);
  assert.equal(evaluateRedTeamCase(item).reason, 'MISMATCH_REJECTED');
});

test('expired authorization is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-11');
  assert.equal(evaluateRedTeamCase(item).passed, true);
  assert.equal(evaluateRedTeamCase(item).reason, 'EXPIRED_PROOF_REJECTED');
});

test('future human approval is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-12');
  assert.equal(evaluateRedTeamCase(item).passed, true);
  assert.equal(evaluateRedTeamCase(item).reason, 'FUTURE_PROOF_REJECTED');
});

test('oversized input is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-05');
  assert.equal(evaluateRedTeamCase(item).passed, true);
});

test('unknown test cases fail closed', () => {
  assert.equal(evaluateRedTeamCase({ id: 'RT-X', dimension: 'unknown' }).passed, false);
});
