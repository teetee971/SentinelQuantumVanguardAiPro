import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

const gradle = fs.readFileSync('native-android-app/app/build.gradle', 'utf8');
const adapter = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearOsDataLayerAdapter.kt',
  'utf8'
);
const coordinator = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/security/WearableRuntimeCoordinator.kt',
  'utf8'
);
const application = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt',
  'utf8'
);
const registry = JSON.parse(fs.readFileSync('config/integration-capabilities.json', 'utf8'));

test('Wear OS uses the documented Google Play services Data Layer dependency', () => {
  assert.match(gradle, /com\.google\.android\.gms:play-services-wearable:20\.0\.1/);
  assert.match(adapter, /Wearable\.getNodeClient\(appContext\)/);
  assert.match(adapter, /nodeClient\.connectedNodes/);
});

test('platform node identity is pseudonymized and cannot authenticate Sentinel by itself', () => {
  assert.match(adapter, /MessageDigest\.getInstance\("SHA-256"\)/);
  assert.match(adapter, /sentinel-wearos-node-v1:/);
  assert.doesNotMatch(adapter, /observeAuthenticatedChannel\(/);
  assert.match(adapter, /observedCapabilities = setOf\(WearableCapability\.CONNECTION_STATE\)/);
  assert.match(coordinator, /internal fun observeAuthenticatedChannel\(/);
});

test('Wear OS observation runs only from foreground maintenance, not Application.onCreate', () => {
  const onCreate = application
    .split('override fun onCreate()')[1]
    .split('\n    }')[0];
  const foreground = application
    .split('fun launchForegroundMaintenance() {')[1]
    .split('\n    }')[0];

  assert.doesNotMatch(onCreate, /WearOsDataLayerAdapter|launchWearableObservation/);
  assert.match(foreground, /launchWearableObservation\(\)/);
  assert.match(application, /WearOsDataLayerAdapter\(applicationContext\)\.observeConnectedNodes/);
});

test('wearable truth stays locked on identity binding until secure channel evidence exists', () => {
  const wearable = registry.capabilities.find((entry) => entry.id === 'wearable_runtime');
  assert.ok(wearable, 'wearable_runtime capability must exist');
  assert.equal(wearable.state, 'LOCKED_EXTERNAL_DEPENDENCY');
  assert.equal(wearable.diagnostic_reason, 'IDENTITY_BINDING_MISSING');
  assert.match(coordinator, /IntegrationCapabilityId\.WEARABLE_RUNTIME/);
  assert.match(coordinator, /publishConnected/);
  assert.match(coordinator, /IDENTITY_BINDING_MISSING/);
});
