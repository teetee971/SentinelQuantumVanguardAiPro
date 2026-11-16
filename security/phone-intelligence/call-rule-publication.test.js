import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import {
  buildPayload,
  normalizePrefix,
  signCallRulePackage,
  verifyCallRulePackage,
} from './call-rule-publication.js';

const now = Date.parse('2026-09-06T12:00:00Z');
const pair = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const trusted = new Map([['key-1', pair.publicKey]]);

function signed(overrides = {}) {
  return signCallRulePackage({
    privateKey: pair.privateKey,
    sequence: 7,
    issuedAtMs: now - 1_000,
    expiresAtMs: now + 24 * 60 * 60 * 1000,
    issuerId: 'sentinel-phone-publication',
    keyId: 'key-1',
    silencePrefixes: ['+33187', '+33948'],
    ...overrides,
  }).envelope;
}

test('normalization matches Android call-rule prefix semantics', () => {
  assert.equal(normalizePrefix('01 87'), '+33187');
  assert.equal(normalizePrefix('0033948'), '+33948');
  assert.equal(normalizePrefix('+33 1 87'), '+33187');
  assert.equal(normalizePrefix('bad-prefix'), null);
});

test('produces a signed package accepted by the bounded verifier', () => {
  const result = verifyCallRulePackage(signed(), {
    trustedKeys: trusted,
    expectedIssuerId: 'sentinel-phone-publication',
    highestAcceptedSequence: 6,
    now,
  });
  assert.equal(result.accepted, true);
  assert.equal(result.rulePackage.sequence, 7);
  assert.deepEqual([...result.rulePackage.silencePrefixes], ['+33187', '+33948']);
});

test('signature tampering and replay are rejected', () => {
  const envelope = signed();
  const tampered = envelope.replace('payload_hex=', 'payload_hex=00');
  assert.equal(verifyCallRulePackage(tampered, {
    trustedKeys: trusted,
    expectedIssuerId: 'sentinel-phone-publication',
    highestAcceptedSequence: 6,
    now,
  }).accepted, false);

  const replay = verifyCallRulePackage(envelope, {
    trustedKeys: trusted,
    expectedIssuerId: 'sentinel-phone-publication',
    highestAcceptedSequence: 7,
    now,
  });
  assert.equal(replay.reason, 'SIGNED_RULE_ROLLBACK_REJECTED');
});

test('expired and wrong-issuer packages are rejected', () => {
  const expired = signed({ issuedAtMs: now - 20_000, expiresAtMs: now - 1 });
  assert.equal(verifyCallRulePackage(expired, {
    trustedKeys: trusted,
    expectedIssuerId: 'sentinel-phone-publication',
    highestAcceptedSequence: 0,
    now,
  }).reason, 'SIGNED_RULE_EXPIRED');

  assert.equal(verifyCallRulePackage(signed(), {
    trustedKeys: trusted,
    expectedIssuerId: 'other-issuer',
    highestAcceptedSequence: 0,
    now,
  }).reason, 'SIGNED_RULE_ISSUER_INVALID');
});

test('payload generation rejects overly broad or duplicate reputation prefixes', () => {
  assert.throws(() => buildPayload({
    sequence: 1,
    issuedAtMs: now,
    expiresAtMs: now + 1_000,
    issuerId: 'sentinel-phone-publication',
    keyId: 'key-1',
    silencePrefixes: ['+331'],
  }), /CALL_RULE_PREFIX_INVALID/);

  assert.throws(() => buildPayload({
    sequence: 1,
    issuedAtMs: now,
    expiresAtMs: now + 1_000,
    issuerId: 'sentinel-phone-publication',
    keyId: 'key-1',
    silencePrefixes: ['+33187', '+33187'],
  }), /CALL_RULE_PREFIX_DUPLICATE/);
});
