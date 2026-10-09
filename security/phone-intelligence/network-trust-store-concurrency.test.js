import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const store = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/NetworkTrustStore.kt',
  'utf8'
);
const screen = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt',
  'utf8'
);

test('NetworkTrustStore serializes and durably commits trust transitions', () => {
  assert.match(
    store,
    /private inline fun <T> withStoreLock\(block: \(\) -> T\): T = synchronized\(STORE_LOCK\)/,
    'allow/block/clear transitions need one process-wide lock'
  );
  assert.match(
    store,
    /private val STORE_LOCK = Any\(\)/,
    'the lock must be shared across store instances'
  );
  for (const method of ['isAllowed', 'isBlocked', 'allow', 'block', 'clear']) {
    assert.match(
      store,
      new RegExp(`fun ${method}\\b[\\s\\S]*?withStoreLock`),
      `${method} must use the shared trust-store lock`
    );
  }
  assert.doesNotMatch(store, /\.apply\(\)/, 'trust transitions must not be falsely reported before durable commit');
  assert.match(store, /\.commit\(\)/, 'trust transitions must commit synchronously');
});

test('network trust actions expose persistence failure instead of claiming a scan refresh', () => {
  assert.match(
    screen,
    /onAllow = \{[\s\S]*if \(trustStore\.allow\(network\.bssid\)\)/,
    'the allow action must branch on the durable store result'
  );
  assert.match(
    screen,
    /onBlock = \{[\s\S]*if \(trustStore\.block\(network\.bssid\)\)/,
    'the block action must branch on the durable store result'
  );
  assert.doesNotMatch(
    screen,
    /trustStore\.(allow|block)\(network\.bssid\)\s*\n\s*runWifiScan\(\)/,
    'a failed trust write must not be followed by an unconditional refresh'
  );
});
