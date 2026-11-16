import test from 'node:test';
import assert from 'node:assert/strict';

import {
  deterministicDetectionDecision,
  deterministicDetectionLatencyMs,
  deterministicHex,
  deterministicInt,
  deterministicSyntheticIocs,
  deterministicUnitInterval,
  referenceCoverage,
  safePercentage,
  stableHash32
} from './deterministicSimulation.js';

test('stable hash and unit interval are deterministic', () => {
  const firstHash = stableHash32('adv-1', 'Initial Access', 'T1566');
  const secondHash = stableHash32('adv-1', 'Initial Access', 'T1566');
  assert.equal(firstHash, secondHash);

  const firstUnit = deterministicUnitInterval('adv-1', 'T1566');
  const secondUnit = deterministicUnitInterval('adv-1', 'T1566');
  assert.equal(firstUnit, secondUnit);
  assert.ok(firstUnit >= 0 && firstUnit < 1);
});

test('deterministic integer and hex helpers remain bounded and reproducible', () => {
  const firstInt = deterministicInt(256, 'seed');
  const secondInt = deterministicInt(256, 'seed');
  assert.equal(firstInt, secondInt);
  assert.ok(firstInt >= 0 && firstInt < 256);

  const firstHex = deterministicHex(64, 'seed');
  const secondHex = deterministicHex(64, 'seed');
  assert.equal(firstHex, secondHex);
  assert.match(firstHex, /^[0-9a-f]{64}$/);
});

test('synthetic IOC generation is reproducible for the same technique', () => {
  const first = deterministicSyntheticIocs('T1566');
  const second = deterministicSyntheticIocs('T1566');
  assert.deepEqual(first, second);
  assert.ok(first.length >= 1 && first.length <= 3);
});

test('detection decision is deterministic and honors hard probability bounds', () => {
  const args = ['adv-1', 'Initial Access', 'T1566', 5];
  assert.equal(
    deterministicDetectionDecision(0.42, ...args),
    deterministicDetectionDecision(0.42, ...args)
  );
  assert.equal(deterministicDetectionDecision(0, ...args), false);
  assert.equal(deterministicDetectionDecision(1, ...args), true);
});

test('detection latency is deterministic and bounded', () => {
  const first = deterministicDetectionLatencyMs('adv-1', 'Initial Access', 5);
  const second = deterministicDetectionLatencyMs('adv-1', 'Initial Access', 5);
  assert.equal(first, second);
  assert.ok(first >= 1000 && first <= 5000);
});

test('safe percentages and reference coverage fail closed on empty denominators', () => {
  assert.equal(safePercentage(1, 0), 0);
  assert.equal(safePercentage(Number.NaN, 10), 0);
  assert.equal(referenceCoverage(1, 0), 0);
  assert.equal(referenceCoverage(3, 2), 100);
  assert.equal(referenceCoverage(1, 4), 25);
});
