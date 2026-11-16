import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { signingPayload, verifyProofAuthenticity } from './proof-authenticity.js';

const { publicKey, privateKey } = generateKeyPairSync('ed25519');
const { publicKey: otherPublicKey, privateKey: otherPrivateKey } = generateKeyPairSync('ed25519');

function signedRecord(overrides = {}, signer = privateKey) {
  const record = {
    authorization_id: 'auth-1',
    actor_id: 'operator-1',
    issued_at: '2026-09-03T11:00:00.000Z',
    expires_at: '2026-09-03T13:00:00.000Z',
    action: 'block',
    target_id: 'target-1',
    scope: { environment: 'security-test' },
    policy_version: 'policy-1',
    source: 'operator',
    issuer_id: 'auth-service',
    key_id: 'auth-key-1',
    signature_alg: 'ed25519',
    ...overrides,
  };
  record.signature = sign(null, signingPayload(record, 'authorization'), signer).toString('base64');
  return record;
}

function trust(overrides = {}) {
  return {
    authorizedIssuers: { authorization: new Set(['auth-service']) },
    revokedKeyIds: new Set(),
    resolvePublicKey: ({ keyId }) => keyId === 'auth-key-1' ? publicKey : null,
    ...overrides,
  };
}

test('accepts a valid Ed25519 proof from an authorized issuer', () => {
  assert.equal(verifyProofAuthenticity(signedRecord(), 'authorization', trust()).valid, true);
});

test('rejects a mutated signed field', () => {
  const record = signedRecord();
  record.target_id = 'target-attacker';
  const result = verifyProofAuthenticity(record, 'authorization', trust());
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_SIGNATURE_INVALID');
});

test('rejects an unauthorized issuer even with a valid signature', () => {
  const record = signedRecord({ issuer_id: 'attacker-service' });
  const result = verifyProofAuthenticity(record, 'authorization', trust());
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_ISSUER_UNAUTHORIZED');
});

test('rejects a revoked key', () => {
  const result = verifyProofAuthenticity(
    signedRecord(),
    'authorization',
    trust({ revokedKeyIds: new Set(['auth-key-1']) }),
  );
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_KEY_REVOKED');
});

test('rejects a forged signature from another private key', () => {
  const record = signedRecord({}, otherPrivateKey);
  const result = verifyProofAuthenticity(record, 'authorization', trust());
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_SIGNATURE_INVALID');
  assert.notEqual(publicKey, otherPublicKey);
});

test('fails closed without a trust configuration', () => {
  const result = verifyProofAuthenticity(signedRecord(), 'authorization', null);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_TRUST_REQUIRED');
});

test('domain separation prevents cross-proof-type reuse', () => {
  const record = signedRecord();
  const result = verifyProofAuthenticity(record, 'approval', {
    ...trust(),
    authorizedIssuers: { approval: new Set(['auth-service']) },
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'PROOF_SIGNATURE_INVALID');
});
