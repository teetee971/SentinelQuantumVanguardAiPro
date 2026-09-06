import assert from 'node:assert/strict';
import test from 'node:test';
import {
  OWNER_ADMIN_CAPABILITIES,
  NON_BYPASSABLE_OPERATIONAL_CONTROLS,
  evaluateOwnerAdministrativeAccess,
  canOwnerBypassOperationalAuthorization,
} from './owner-access.js';

test('owner receives every declared administrative capability with strong authentication', () => {
  for (const capability of OWNER_ADMIN_CAPABILITIES) {
    const result = evaluateOwnerAdministrativeAccess({
      role: ' OWNER ',
      capability: ` ${capability.toUpperCase()} `,
      authenticated: true,
      mfaVerified: true,
    });
    assert.equal(result.allowed, true, capability);
    assert.equal(result.reason, 'OWNER_ADMIN_ALLOW');
  }
});

test('owner role requires authentication and MFA', () => {
  assert.equal(evaluateOwnerAdministrativeAccess({ role: 'owner', capability: 'system.inspect' }).allowed, false);
  assert.equal(evaluateOwnerAdministrativeAccess({ role: 'owner', capability: 'system.inspect', authenticated: true }).allowed, false);
});

test('unknown administrative capability fails closed', () => {
  const result = evaluateOwnerAdministrativeAccess({
    role: 'owner',
    capability: 'operational.authorization.bypass',
    authenticated: true,
    mfaVerified: true,
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'UNKNOWN_ADMIN_CAPABILITY');
});

test('non-owner cannot acquire owner administration', () => {
  const result = evaluateOwnerAdministrativeAccess({
    role: 'admin',
    capability: 'system.configure',
    authenticated: true,
    mfaVerified: true,
  });
  assert.equal(result.allowed, false);
  assert.equal(result.reason, 'OWNER_ROLE_REQUIRED');
});

test('owner never bypasses operational authorization controls', () => {
  assert.equal(canOwnerBypassOperationalAuthorization(), false);
  assert.ok(NON_BYPASSABLE_OPERATIONAL_CONTROLS.includes('target_authorization'));
  assert.ok(NON_BYPASSABLE_OPERATIONAL_CONTROLS.includes('anti_replay'));
  assert.ok(NON_BYPASSABLE_OPERATIONAL_CONTROLS.includes('kill_switch'));
});
