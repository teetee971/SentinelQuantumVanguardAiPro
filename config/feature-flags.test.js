import test from 'node:test';
import assert from 'node:assert/strict';

import * as featureFlags from './feature-flags.js';

const {
  FEATURE_FLAGS,
  emergencyShutdown,
  getSystemStatus,
  isBackendReadOnly,
  isFeatureEnabled,
  verifyZeroTrustCompliance
} = featureFlags;

test('conservative defaults keep sensitive capabilities disabled', () => {
  assert.equal(FEATURE_FLAGS.FEATURE_BACKEND, false);
  assert.equal(FEATURE_FLAGS.FEATURE_BACKEND_WRITE, false);
  assert.equal(FEATURE_FLAGS.FEATURE_LOGS_LIVE, false);
  assert.equal(FEATURE_FLAGS.FEATURE_ANDROID_AUTO_UPDATE, false);
  assert.equal(FEATURE_FLAGS.FEATURE_THREAT_SCANNER, false);
  assert.equal(isBackendReadOnly(), true);
  assert.equal(isFeatureEnabled('FEATURE_BACKEND_WRITE'), false);
});

test('exported feature flags cannot be mutated through the module object', () => {
  assert.equal(Object.isFrozen(FEATURE_FLAGS), true);
  assert.throws(() => {
    FEATURE_FLAGS.FEATURE_BACKEND_WRITE = true;
  }, TypeError);
  assert.equal(isFeatureEnabled('FEATURE_BACKEND_WRITE'), false);
});

test('zero-trust baseline is compliant before emergency shutdown', () => {
  const result = verifyZeroTrustCompliance();
  assert.equal(result.compliant, true);
  assert.equal(result.riskLevel, 'CONTROLLED');
});

test('emergency shutdown leaves only the audit log enabled', () => {
  const result = emergencyShutdown();
  assert.equal(result.success, true);
  assert.equal(result.auditLogActive, true);
  assert.equal(isFeatureEnabled('FEATURE_AUDIT_LOG'), true);
  assert.equal(isFeatureEnabled('FEATURE_BACKEND_READ_ONLY'), false);
  assert.equal(getSystemStatus().emergencyShutdown, true);
  assert.equal(getSystemStatus().killSwitchActive, true);

  // Recovery must happen through a controlled deployment/configuration path,
  // not through a browser-exposed recovery function.
  assert.equal('restoreFromEmergency' in featureFlags, false);
});
