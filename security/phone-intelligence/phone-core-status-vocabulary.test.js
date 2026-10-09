import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const state = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelState.kt',
  'utf8'
);
const dialer = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelDialerActivity.kt',
  'utf8'
);
const callerId = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/CallerIdActivity.kt',
  'utf8'
);

test('Phone Core exposes only READY, LIMITED and LOCKED statuses', () => {
  assert.match(state, /enum class SentinelState\s*\{\s*READY,\s*LIMITED,\s*LOCKED\s*\}/s);
  assert.doesNotMatch(state, /TO_CONFIGURE|TO_TEST|VALIDATED|PARTIAL|DEGRADED|BLOCKED|UNKNOWN|UNAVAILABLE/);
  assert.match(state, /physicalDeviceValidated[\s\S]*operationalEnvironmentReady/);
});

test('Phone Core status consumers do not reintroduce legacy state values', () => {
  for (const [name, source] of [['dialer', dialer], ['caller ID', callerId]]) {
    assert.doesNotMatch(
      source,
      /SentinelState\.(TO_CONFIGURE|TO_TEST|VALIDATED|PARTIAL|DEGRADED|BLOCKED|UNKNOWN|UNAVAILABLE)/,
      `${name} must use the strict Phone Core status vocabulary`
    );
  }
});
