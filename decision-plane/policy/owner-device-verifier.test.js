import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import test from 'node:test';

import { createInMemoryReplayGuard } from '../action-verification/anti-replay.js';
import {
  serializeOwnerDeviceChallenge,
  verifyOwnerDeviceChallenge,
} from './owner-device-verifier.js';

function fixture(overrides = {}) {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const nowMs = Date.parse('2026-09-06T15:00:00.000Z');
  const device = {
    device_id: 'device-owner-001',
    owner_subject: 'owner:primary',
    state: 'ACTIVE',
    public_key_spki_base64: publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
    ...overrides.device,
  };
  const challenge = {
    challenge_id: 'challenge-001',
    device_id: device.device_id,
    owner_subject: device.owner_subject,
    nonce_base64: Buffer.alloc(32, 7).toString('base64'),
    issued_at: new Date(nowMs - 1_000).toISOString(),
    expires_at: new Date(nowMs + 60_000).toISOString(),
    operation_context: 'owner-admin-session',
    ...overrides.challenge,
  };
  const signature_base64 = sign(
    'sha256',
    serializeOwnerDeviceChallenge(challenge),
    privateKey,
  ).toString('base64');

  return { device, challenge, signature_base64, nowMs, privateKey };
}

test('verifies a fresh challenge signed by an active enrolled device', () => {
  const input = fixture();
  const result = verifyOwnerDeviceChallenge({
    ...input,
    replayGuard: createInMemoryReplayGuard(),
  });

  assert.deepEqual(result, {
    valid: true,
    reason: 'OWNER_DEVICE_CHALLENGE_VERIFIED',
    owner_subject: 'owner:primary',
    device_id: 'device-owner-001',
    challenge_id: 'challenge-001',
  });
});

test('rejects a revoked device before signature acceptance', () => {
  const input = fixture({ device: { state: 'REVOKED' } });
  const result = verifyOwnerDeviceChallenge({
    ...input,
    replayGuard: createInMemoryReplayGuard(),
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OWNER_DEVICE_NOT_ACTIVE');
});

test('rejects device and owner binding mismatches', () => {
  const deviceMismatch = fixture({ challenge: { device_id: 'different-device' } });
  assert.equal(
    verifyOwnerDeviceChallenge({ ...deviceMismatch, replayGuard: createInMemoryReplayGuard() }).reason,
    'OWNER_DEVICE_BINDING_MISMATCH',
  );

  const ownerMismatch = fixture({ challenge: { owner_subject: 'owner:other' } });
  assert.equal(
    verifyOwnerDeviceChallenge({ ...ownerMismatch, replayGuard: createInMemoryReplayGuard() }).reason,
    'OWNER_SUBJECT_BINDING_MISMATCH',
  );
});

test('rejects expired and overlong validity windows', () => {
  const expired = fixture({
    challenge: {
      issued_at: '2026-09-06T14:50:00.000Z',
      expires_at: '2026-09-06T14:55:00.000Z',
    },
  });
  assert.equal(
    verifyOwnerDeviceChallenge({ ...expired, replayGuard: createInMemoryReplayGuard() }).reason,
    'OWNER_DEVICE_CHALLENGE_EXPIRED',
  );

  const overlong = fixture({
    challenge: {
      issued_at: '2026-09-06T14:59:00.000Z',
      expires_at: '2026-09-06T15:10:00.000Z',
    },
  });
  assert.equal(
    verifyOwnerDeviceChallenge({ ...overlong, replayGuard: createInMemoryReplayGuard() }).reason,
    'OWNER_DEVICE_CHALLENGE_WINDOW_INVALID',
  );
});

test('rejects tampered challenge signatures', () => {
  const input = fixture();
  const tamperedChallenge = { ...input.challenge, operation_context: 'different-operation' };
  const result = verifyOwnerDeviceChallenge({
    ...input,
    challenge: tamperedChallenge,
    replayGuard: createInMemoryReplayGuard(),
  });
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OWNER_DEVICE_SIGNATURE_INVALID');
});

test('consumes a verified challenge exactly once', () => {
  const input = fixture();
  const replayGuard = createInMemoryReplayGuard();
  const first = verifyOwnerDeviceChallenge({ ...input, replayGuard });
  const second = verifyOwnerDeviceChallenge({ ...input, replayGuard });
  assert.equal(first.valid, true);
  assert.equal(second.valid, false);
  assert.equal(second.reason, 'REPLAY_DETECTED');
});

test('does not consume replay key when signature verification fails', () => {
  const input = fixture();
  const replayGuard = createInMemoryReplayGuard();
  const invalid = verifyOwnerDeviceChallenge({
    ...input,
    signature_base64: Buffer.from('invalid').toString('base64'),
    replayGuard,
  });
  assert.equal(invalid.valid, false);
  assert.equal(replayGuard.hasConsumed(`owner-device-challenge:${input.challenge.challenge_id}`), false);

  const valid = verifyOwnerDeviceChallenge({ ...input, replayGuard });
  assert.equal(valid.valid, true);
});

test('requires an anti-replay guard', () => {
  const input = fixture();
  const result = verifyOwnerDeviceChallenge(input);
  assert.equal(result.valid, false);
  assert.equal(result.reason, 'OWNER_DEVICE_REPLAY_GUARD_REQUIRED');
});
