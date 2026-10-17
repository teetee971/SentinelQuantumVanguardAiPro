import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const phoneListenerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelWearListenerService.kt';
const probeRegistryPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsPlatformProbeRegistry.kt';
const adapterPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsDataLayerAdapter.kt';
const phoneManifestPath = 'native-android-app/app/src/main/AndroidManifest.xml';
const watchListenerPath =
  'native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/SentinelWearListenerService.kt';
const contractPath =
  'native-android-app/wearable-contract/src/main/kotlin/com/sentinel/quantum/wearable/contract/WearableProtocolV2.kt';
const phoneGradlePath = 'native-android-app/app/build.gradle';
const watchGradlePath = 'native-android-app/wear-app/build.gradle';

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

test('phone emits the platform ping and manifest wires Sentinel Data Layer messages', () => {
  const adapter = fs.readFileSync(adapterPath, 'utf8');
  const manifest = fs.readFileSync(phoneManifestPath, 'utf8');

  assert.match(adapter, /Wearable\.getMessageClient\(appContext\)/);
  assert.match(adapter, /WearOsPlatformProbeRegistry\.issue/);
  assert.match(adapter, /PLATFORM_PING_PATH/);
  assert.match(adapter, /sendMessage\(/);
  assert.match(manifest, /\.security\.SentinelWearListenerService/);
  assert.match(manifest, /com\.google\.android\.gms\.wearable\.MESSAGE_RECEIVED/);
  assert.match(manifest, /android:pathPrefix="\/sentinel\/"/);
});

test('platform pong remains a non-privileged reachability proof', () => {
  assert.equal(fs.existsSync(watchListenerPath), true, 'watch listener is missing');
  const watchListener = fs.readFileSync(watchListenerPath, 'utf8');
  assert.match(watchListener, /PLATFORM_PING_PATH/);
  assert.match(watchListener, /PLATFORM_PONG_PATH/);
  assert.doesNotMatch(watchListener, /observeAuthenticatedChannel|publishConnected|WearableChannelProof/);
});

test('both apps share one bounded secure protocol-v2 contract', () => {
  assert.equal(fs.existsSync(contractPath), true, 'shared wearable protocol v2 contract is missing');
  const contract = fs.readFileSync(contractPath, 'utf8');
  const phoneGradle = fs.readFileSync(phoneGradlePath, 'utf8');
  const watchGradle = fs.readFileSync(watchGradlePath, 'utf8');

  assert.match(phoneGradle, /implementation project\(':wearable-contract'\)/);
  assert.match(watchGradle, /implementation project\(':wearable-contract'\)/);
  for (const suffix of ['challenge', 'handshake', 'liveness', 'message']) {
    assert.match(contract, new RegExp(`/sentinel/v2/secure/${suffix}`));
  }
  assert.match(contract, /SCHEMA_VERSION\s*=\s*2/);
  assert.match(contract, /MAX_FRAME_BYTES/);
  assert.match(contract, /payload\.size\s*>\s*MAX_FRAME_BYTES/);
});

test('Data Layer listeners route bounded secure frames without promoting product truth', () => {
  const phoneListener = fs.readFileSync(phoneListenerPath, 'utf8');
  const watchListener = fs.readFileSync(watchListenerPath, 'utf8');

  for (const listener of [phoneListener, watchListener]) {
    assert.match(listener, /WearableProtocolV2/);
    assert.match(listener, /decodeFrame/);
    assert.doesNotMatch(listener, /publishConnected/);
    assert.doesNotMatch(listener, /observeAuthenticatedChannel/);
  }
});
