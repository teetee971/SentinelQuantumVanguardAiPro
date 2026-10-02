import assert from 'node:assert/strict';
import test from 'node:test';

import {
  CURRENT_THREAT_PRIORITIES,
  CURRENT_THREAT_PRIORITIES_AS_OF,
  currentThreatDetectionTargets
} from './current-threat-priorities.js';

test('current threat priorities are dated, sourced and non-enforcing', () => {
  assert.equal(CURRENT_THREAT_PRIORITIES_AS_OF, '2026-10-02');
  assert.ok(CURRENT_THREAT_PRIORITIES.length >= 6);
  for (const threat of CURRENT_THREAT_PRIORITIES) {
    assert.match(threat.source_uri, /^https:\/\//);
    assert.ok(threat.behaviors.length > 0);
    assert.ok(threat.sentinel_detection_targets.length > 0);
    assert.equal(threat.autonomous_blocking, false);
  }
});

test('includes current Android takeover and supply-chain detection targets', () => {
  const targets = currentThreatDetectionTargets();
  for (const expected of [
    'accessibility_abuse',
    'unknown_source_install',
    'known_hash_reputation',
    'ci_dependency_integrity',
    'oauth_device_code_warning'
  ]) {
    assert.ok(targets.includes(expected), expected);
  }
});
