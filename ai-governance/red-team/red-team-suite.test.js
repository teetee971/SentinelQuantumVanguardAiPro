import assert from 'node:assert/strict';
import test from 'node:test';
import { evaluateRedTeamCase, getRedTeamCases, runRedTeamSuite } from './red-team-suite.js';

test('red team suite is deterministic and fully passes', () => {
  const result = runRedTeamSuite();
  assert.equal(result.failed, 0);
  assert.equal(result.passed, result.total);
});

test('prompt injection is classified as untrusted', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-01');
  assert.equal(evaluateRedTeamCase(item).passed, true);
});

test('sensitive action without human validation is denied', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-03');
  assert.equal(evaluateRedTeamCase(item).reason, 'SENSITIVE_ACTION_DENIED');
});

test('oversized input is rejected', () => {
  const item = getRedTeamCases().find((testCase) => testCase.id === 'RT-05');
  assert.equal(evaluateRedTeamCase(item).passed, true);
});

test('unknown test cases fail closed', () => {
  assert.equal(evaluateRedTeamCase({ id: 'RT-X', dimension: 'unknown' }).passed, false);
});
