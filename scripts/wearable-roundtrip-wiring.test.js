import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const phoneListenerPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelWearListenerService.kt';
const phoneSecureRuntimePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelWearableSecureHandshakeRuntime.kt';
const phoneLivenessRuntimePath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneWearableLivenessRuntime.kt';
const probeRegistryPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsPlatformProbeRegistry.kt';
const adapterPath =
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsDataLayerAdapter.kt';
const phoneManifestPath = 'native-android-app/app/src/main/AndroidManifest.xml';
const watchListenerPath =
  'native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/SentinelWearListenerService.kt';
const watchChallengeHandlerPath =
  'native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/WearableSecureChallengeFrameHandler.kt';
const watchLivenessHandlerPath =
  'native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/WearableWatchLivenessFrameHandler.kt';
const watchSessionRegistryPath =
  'native-android-app/wear-app/src/main/java/com/sentinel/quantum/wear/WearableWatchSecureSessionRegistry.kt';
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

test('watch installs real challenge and authenticated liveness handlers with memory-only HPKE sessions', () => {
  assert.equal(fs.existsSync(watchChallengeHandlerPath), true, 'watch secure challenge handler is missing');
  assert.equal(fs.existsSync(watchLivenessHandlerPath), true, 'watch secure liveness handler is missing');
  assert.equal(fs.existsSync(watchSessionRegistryPath), true, 'watch secure session registry is missing');
  const listener = fs.readFileSync(watchListenerPath, 'utf8');
  const challengeHandler = fs.readFileSync(watchChallengeHandlerPath, 'utf8');
  const livenessHandler = fs.readFileSync(watchLivenessHandlerPath, 'utf8');
  const registry = fs.readFileSync(watchSessionRegistryPath, 'utf8');

  assert.match(listener, /WearableSecureChallengeFrameHandler/);
  assert.match(listener, /WearableWatchLivenessFrameHandler/);
  assert.match(listener, /WearableProtocolV2\.LIVENESS_PATH/);
  assert.match(listener, /WearableSecureTransportInbox\.install/);
  assert.match(challengeHandler, /decodeChallengeFrame/);
  assert.match(challengeHandler, /HANDSHAKE_PATH/);
  assert.match(challengeHandler, /encodeHandshake/);
  assert.match(livenessHandler, /decryptFromPhone/);
  assert.match(livenessHandler, /acceptLivenessSequence/);
  assert.match(livenessHandler, /encryptForPhone/);
  assert.match(registry, /WearableHpkeDuplexSession/);
  assert.match(registry, /expiresAtMs\s*=\s*refreshedExpiry/);
  assert.doesNotMatch(registry, /SharedPreferences|Room|DataStore/);
});

test('phone installs secure handshake plus HPKE liveness and starts challenge for connected nodes', () => {
  assert.equal(fs.existsSync(phoneSecureRuntimePath), true, 'phone secure handshake composition is missing');
  assert.equal(fs.existsSync(phoneLivenessRuntimePath), true, 'phone secure liveness runtime is missing');
  const runtime = fs.readFileSync(phoneSecureRuntimePath, 'utf8');
  const liveness = fs.readFileSync(phoneLivenessRuntimePath, 'utf8');
  const adapter = fs.readFileSync(adapterPath, 'utf8');

  assert.match(runtime, /WearableSecureTransportInbox\.install/);
  assert.match(runtime, /WearableProtocolV2\.HANDSHAKE_PATH/);
  assert.match(runtime, /WearableProtocolV2\.LIVENESS_PATH/);
  assert.match(runtime, /PhoneWearableSecureHandshakeRuntime/);
  assert.match(runtime, /PhoneWearableLivenessRuntime/);
  assert.match(runtime, /pendingConfirmation/);
  assert.match(runtime, /authenticatedTraffic/);
  assert.match(liveness, /decryptFromWearable/);
  assert.match(liveness, /MessageDigest\.isEqual/);
  assert.match(liveness, /revokeAuthenticatedChannel/);
  assert.match(adapter, /SentinelWearableSecureHandshakeRuntime\.start/);
});

test('phone Data Layer disconnect revokes pending and active secure state immediately', () => {
  const listener = fs.readFileSync(phoneListenerPath, 'utf8');
  const runtime = fs.readFileSync(phoneSecureRuntimePath, 'utf8');
  assert.match(listener, /onPeerDisconnected/);
  assert.match(listener, /SentinelWearableSecureHandshakeRuntime\.disconnect/);
  assert.match(runtime, /runtime\?\.disconnect\(nodeId\)/);
  assert.match(runtime, /live\?\.disconnect\(nodeId\)/);
});
