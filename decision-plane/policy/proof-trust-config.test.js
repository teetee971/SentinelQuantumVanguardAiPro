import assert from 'node:assert/strict';
import test from 'node:test';
import { generateKeyPairSync, sign } from 'node:crypto';

import { signingPayload, verifyProofAuthenticity } from './proof-authenticity.js';
import { buildProofTrustConfig } from './proof-trust-config.js';

function pem(key) {
  return key.export({ type: 'spki', format: 'pem' }).toString();
}

function privatePem(key) {
  return key.export({ type: 'pkcs8', format: 'pem' }).toString();
}

function fixture() {
  const auth = generateKeyPairSync('ed25519');
  const approval = generateKeyPairSync('ed25519');
  const simulation = generateKeyPairSync('ed25519');
  return {
    keys: { auth, approval, simulation },
    config: {
      issuers: {
        authorization: [{ issuer_id: 'auth-service', keys: [{ key_id: 'auth-k1', public_key_pem: pem(auth.publicKey) }] }],
        approval: [{ issuer_id: 'human-approval-service', keys: [{ key_id: 'approval-k1', public_key_pem: pem(approval.publicKey) }] }],
        simulation: [{ issuer_id: 'simulator-service', keys: [{ key_id: 'simulation-k1', public_key_pem: pem(simulation.publicKey) }] }],
      },
      revoked_key_ids: [],
    },
  };
}

function signedAuthorization(privateKey) {
  const record = {
    authorization_id: 'authz-1',
    issuer_id: 'auth-service',
    key_id: 'auth-k1',
    signature_alg: 'ed25519',
    action: 'contain',
    target_id: 'host-1',
    policy_version: 'policy-v1',
    issued_at: '2026-09-05T20:00:00.000Z',
    expires_at: '2026-09-05T20:10:00.000Z',
  };
  record.signature = sign(null, signingPayload(record, 'authorization'), privateKey).toString('base64');
  return record;
}

test('builds a trust object that verifies a correctly bound Ed25519 issuer', () => {
  const { keys, config } = fixture();
  const trust = buildProofTrustConfig(config);
  const result = verifyProofAuthenticity(signedAuthorization(keys.auth.privateKey), 'authorization', trust);
  assert.deepEqual(result, { valid: true, reason: 'PROOF_AUTHENTICITY_VALID' });
});

test('requires every proof type used by the final execution boundary', () => {
  const { config } = fixture();
  delete config.issuers.simulation;
  assert.throws(() => buildProofTrustConfig(config), /TRUST_CONFIG_ISSUER_REQUIRED:simulation/);
});

test('rejects private key material in deployment trust configuration', () => {
  const { keys, config } = fixture();
  config.issuers.authorization[0].keys[0].public_key_pem = privatePem(keys.auth.privateKey);
  assert.throws(() => buildProofTrustConfig(config), /TRUST_CONFIG_PRIVATE_KEY_FORBIDDEN/);
});

test('rejects globally duplicate key ids to keep revocation unambiguous', () => {
  const { config } = fixture();
  config.issuers.approval[0].keys[0].key_id = 'auth-k1';
  assert.throws(() => buildProofTrustConfig(config), /TRUST_CONFIG_DUPLICATE_KEY_ID/);
});

test('rejects revocations that do not map to a configured key', () => {
  const { config } = fixture();
  config.revoked_key_ids = ['missing-key'];
  assert.throws(() => buildProofTrustConfig(config), /TRUST_CONFIG_REVOKED_KEY_UNKNOWN/);
});

test('configured revocation is enforced by the authenticity verifier', () => {
  const { keys, config } = fixture();
  config.revoked_key_ids = ['auth-k1'];
  const trust = buildProofTrustConfig(config);
  const result = verifyProofAuthenticity(signedAuthorization(keys.auth.privateKey), 'authorization', trust);
  assert.deepEqual(result, { valid: false, reason: 'PROOF_KEY_REVOKED' });
});
