import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const phoneListenerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelWearListenerService.kt';
const probeRegistryPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsPlatformProbeRegistry.kt';
const watchListenerPath =
  'native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/SentinelWearListenerService.kt';

test('phone owns a pong listener and one-shot correlated probe registry', () => {
  assert.equal(fs.existsSync(phoneListenerPath), true, 'phone WearableListenerService is missing');
  assert.equal(fs.existsSync(probeRegistryPath), true, 'phone probe registry is missing');

  const listener = fs.readFileSync(phoneListenerPath, 'utf8');
  const registry = fs.readFileSync(probeRegistryPath, 'utf8');

  assert.match(listener, /WearableListenerService/);
  assert.match(listener, /PLATFORM_PONG_PATH/);
  assert.match(listener, /acceptPong\(/);
  assert.match(registry, /SecureRandom/);
  assert.match(registry, /MessageDigest\.isEqual/);
  assert.match(registry, /expiresAtMs/);
  assert.match(registry, /pending\.remove/);
});

test('platform pong remains a non-privileged reachability proof', () => {
  assert.equal(fs.existsSync(watchListenerPath), true, 'watch listener is missing');
  const watchListener = fs.readFileSync(watchListenerPath, 'utf8');
  assert.match(watchListener, /PLATFORM_PING_PATH/);
  assert.match(watchListener, /PLATFORM_PONG_PATH/);
  assert.doesNotMatch(watchListener, /observeAuthenticatedChannel|publishConnected|WearableChannelProof/);
});
