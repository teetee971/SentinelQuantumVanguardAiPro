import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateAndroidStartupContracts, validateWifiLocationContract } from './android-startup-contracts.js';
const base = 'native-android-app/app/src/main/java/com/sentinel/quantum/';
const app = readFileSync(base + 'SentinelApplication.kt', 'utf8');
const service = readFileSync(base + 'security/SentinelCallScreeningService.kt', 'utf8');
const scanner = readFileSync(base + 'security/WifiScanner.kt', 'utf8');
const declarations = ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION'].map(name => ({ name, attributes: {} }));
test('current cold-start contract preserves memory-only screening and post-response HMAC loading', () => {
  assert.deepEqual(validateAndroidStartupContracts(app, service), []);
});
for (const io of ['KeyStore.getInstance("AndroidKeyStore")', 'getSharedPreferences("rules", MODE_PRIVATE)', 'launchForegroundMaintenance()']) {
  test(`rejects Application cold-start work: ${io}`, () => {
    const changed = app.replace('super.onCreate()', `super.onCreate()\n        ${io}`);
    assert.ok(validateAndroidStartupContracts(changed, service).length > 0);
  });
}
test('rejects maintenance moved before the Telecom response', () => {
  const changed = service.replace('responseBoundary.submitPostResponse', 'preResponseWorker').replace('respondToCall(callDetails, response.build())', 'runPostResponseMaintenance()\n            respondToCall(callDetails, response.build())');
  assert.ok(validateAndroidStartupContracts(app, changed).length > 0);
});
test('rejects direct key access on the screening callback', () => {
  assert.ok(validateAndroidStartupContracts(app, service.replace('val rawCallerNumber =', 'KeyStore.getInstance("AndroidKeyStore")\n        val rawCallerNumber =')).length > 0);
});
test('paired runtime requests remain available on API 33+ as well as Android 12', () => {
  assert.deepEqual(validateWifiLocationContract(declarations, scanner), []);
});
test('rejects a coarse permission capped at 32 which breaks the mandatory pair on newer SDKs', () => {
  const changed = declarations.map(d => d.name === 'ACCESS_COARSE_LOCATION' ? { ...d, attributes: { 'android:maxSdkVersion': '32' } } : d);
  assert.ok(validateWifiLocationContract(changed, scanner).length > 0);
});
test('rejects missing effective runtime permission checks', () => {
  assert.ok(validateWifiLocationContract(declarations, scanner.replace('mandatoryPermissions.all', 'mandatoryPermissions.any')).length > 0);
});
