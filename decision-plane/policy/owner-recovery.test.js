import assert from 'node:assert/strict';
import test from 'node:test';
import {
  evaluateOwnerRecoveryRequest,
  evaluateLostDeviceRevocation,
  evaluateReplacementDeviceEnrollment,
} from './owner-recovery.js';

test('hardware security key can start recovery for a lost device', () => {
  const result = evaluateOwnerRecoveryRequest({
    factor: 'hardware_security_key',
    authenticated: true,
    factorVerified: true,
    lostDeviceId: 'device-old',
  });
  assert.equal(result.allowed, true);
  assert.equal(result.nextRequiredStep, 'REVOKE_LOST_DEVICE');
});

test('offline break-glass can start recovery but still requires lost-device revocation', () => {
  const result = evaluateOwnerRecoveryRequest({
    factor: 'offline_break_glass',
    authenticated: true,
    factorVerified: true,
    lostDeviceId: 'device-old',
  });
  assert.equal(result.allowed, true);
  assert.equal(result.nextRequiredStep, 'REVOKE_LOST_DEVICE');
});

test('IMEI, phone number and username alone are rejected as recovery factors', () => {
  for (const factor of ['imei', 'phone_number', 'username_only']) {
    const result = evaluateOwnerRecoveryRequest({ factor, authenticated: true, factorVerified: true, lostDeviceId: 'device-old' });
    assert.equal(result.allowed, false, factor);
    assert.equal(result.reason, 'OWNER_RECOVERY_WEAK_FACTOR_REJECTED');
  }
});

test('lost device must be revoked and audited before replacement enrollment', () => {
  assert.equal(evaluateLostDeviceRevocation({ lostDeviceId: 'device-old', revoked: false, auditRecorded: true }).allowed, false);
  assert.equal(evaluateLostDeviceRevocation({ lostDeviceId: 'device-old', revoked: true, auditRecorded: false }).allowed, false);

  const revoked = evaluateLostDeviceRevocation({ lostDeviceId: 'device-old', revoked: true, auditRecorded: true });
  assert.equal(revoked.allowed, true);
  assert.equal(revoked.nextRequiredStep, 'ENROLL_REPLACEMENT_DEVICE');
});

test('replacement device enrollment fails closed without strong auth and key binding', () => {
  assert.equal(evaluateReplacementDeviceEnrollment({ lostDeviceRevoked: true, strongAuthVerified: false, newDeviceKeyBound: true }).allowed, false);
  assert.equal(evaluateReplacementDeviceEnrollment({ lostDeviceRevoked: true, strongAuthVerified: true, newDeviceKeyBound: false }).allowed, false);

  const result = evaluateReplacementDeviceEnrollment({
    lostDeviceRevoked: true,
    strongAuthVerified: true,
    newDeviceKeyBound: true,
  });
  assert.equal(result.allowed, true);
  assert.equal(result.reason, 'OWNER_REPLACEMENT_DEVICE_ENROLLED');
});
