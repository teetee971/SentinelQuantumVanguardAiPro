import test from 'node:test';
import assert from 'node:assert/strict';
import {
  canonicalizeOperation,
  computeOperationDigest,
  verifyOperationDigest,
} from './operation-digest.js';

test('canonicalization makes object key order irrelevant', () => {
  const left = computeOperationDigest({ action: 'block', target: { id: 'x' }, policy: 'p1' });
  const right = computeOperationDigest({ policy: 'p1', target: { id: 'x' }, action: 'block' });
  assert.equal(left.valid, true);
  assert.equal(left.digest, right.digest);
});

test('digest changes when a security-relevant field changes', () => {
  const original = { action_id: 'a1', action: 'block', target_id: 'safe', policy_version: 'p1', input_hash: 'h1' };
  const digest = computeOperationDigest(original);
  assert.equal(verifyOperationDigest(original, digest.digest).valid, true);
  assert.equal(verifyOperationDigest({ ...original, target_id: 'critical' }, digest.digest).valid, false);
  assert.equal(verifyOperationDigest({ ...original, policy_version: 'p2' }, digest.digest).valid, false);
  assert.equal(verifyOperationDigest({ ...original, input_hash: 'h2' }, digest.digest).valid, false);
});

test('rejects malformed and oversized operations', () => {
  assert.equal(canonicalizeOperation(null).valid, false);
  assert.equal(verifyOperationDigest({}, 'not-a-sha256').valid, false);
  assert.equal(computeOperationDigest({ payload: 'x'.repeat(20000) }).valid, false);
});
