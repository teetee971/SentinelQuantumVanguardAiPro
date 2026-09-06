import test from 'node:test';
import assert from 'node:assert/strict';

import {
  boundedCoverage,
  calculateExpectedDetectionRate,
  calculateTechniqueCoverage,
  deterministicInt,
  deterministicLatencyMs,
  safePercentage,
  stableHash32
} from './deterministicMetrics.js';

test('stable hash and deterministic latency are reproducible', () => {
  assert.equal(stableHash32('scenario-1', 'T1566'), stableHash32('scenario-1', 'T1566'));
  const first = deterministicLatencyMs('scenario-1', 'T1566', 0);
  const second = deterministicLatencyMs('scenario-1', 'T1566', 0);
  assert.equal(first, second);
  assert.ok(first >= 1000 && first <= 5000);
});

test('deterministic integer remains bounded', () => {
  const value = deterministicInt(10, 'seed');
  assert.ok(value >= 0 && value < 10);
  assert.equal(value, deterministicInt(10, 'seed'));
  assert.equal(deterministicInt(0, 'seed'), 0);
});

test('safe percentage and coverage fail closed on empty references', () => {
  assert.equal(safePercentage(1, 0), 0);
  assert.equal(safePercentage(Number.NaN, 4), 0);
  assert.equal(boundedCoverage(3, 2), 100);
  assert.equal(boundedCoverage(0, 0), 0);
});

test('MITRE coverage uses unique declared reference techniques', () => {
  const actual = calculateTechniqueCoverage(['T1', 'T1', 'T2', 'UNKNOWN'], ['T1', 'T2', 'T3']);
  const expected = (2 / 3) * 100;
  assert.ok(
    Math.abs(actual - expected) < 1e-12,
    `expected ${actual} to be within 1e-12 of ${expected}`
  );
  assert.equal(calculateTechniqueCoverage(['T1'], []), 0);
});

test('detection rate reflects expected indicator technique coverage', () => {
  assert.equal(calculateExpectedDetectionRate(['T1', 'T2'], ['T1', 'T2']), 100);
  assert.equal(calculateExpectedDetectionRate(['T1'], ['T1', 'T2']), 50);
  assert.equal(calculateExpectedDetectionRate(['T1'], []), 0);
});
