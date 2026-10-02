import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test('Phone Core roadmap and diagnostic copy match certification schema v5', () => {
  const source = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt'),
    'utf8'
  );
  const diagnostic = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreDiagnosticActivity.kt'),
    'utf8'
  );
  const uiStateTest = readFileSync(
    resolve('native-android-app/app/src/test/java/com/sentinel/quantum/ui/design/PhoneCoreUiStateTest.kt'),
    'utf8'
  );
  const roadmap = readFileSync(resolve('docs/ROADMAP.md'), 'utf8');
  const publicRoadmap = readFileSync(resolve('public/roadmap.html'), 'utf8');

  assert.match(source, /CERTIFICATION_SCHEMA_VERSION\s*=\s*5/);
  const required = source.match(/requiredCount:\s*Int\s*get\(\)\s*=\s*(\d+)/)?.[1];
  assert.equal(required, '14');

  assert.match(roadmap, /Phone Core v5 structurée en 14 preuves/);
  assert.match(source, /"outgoing_mms_sent"/);
  assert.match(roadmap, /MMS sortant/);
  assert.match(roadmap, /Wi-Fi[^\n]*diagnostic réseau séparé/);
  assert.match(roadmap, /ne compte pas dans le certificat Phone Core/);

  assert.match(publicRoadmap, /certificat Phone Core v5 porte sur 14 preuves distinctes/);
  assert.match(publicRoadmap, /scanner Wi-Fi reste un diagnostic réseau séparé/);
  assert.match(publicRoadmap, /Validation physique 14\/14 requise/);
  assert.doesNotMatch(publicRoadmap, /--progress:\d+%/);

  assert.match(diagnostic, /n’est pas une certification Phone Core 14\/14/);
  assert.match(uiStateTest, /onlyFourteenOfFourteenIsValidated/);
  assert.match(uiStateTest, /thirteenOfFourteenCannotClaimValidated/);
  assert.doesNotMatch(uiStateTest, /onlyThirteenOfThirteen|twelveOfThirteen/);

  assert.doesNotMatch(source, /wifiScanFresh|SIGNAL_WIFI_SCAN_FRESH|WIFI_SCAN_FRESH|Kind\.WIFI/);
});


test('Phone Core readiness no longer owns the Wi-Fi scanner capability', () => {
  const diagnostics = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCoreDiagnostics.kt'),
    'utf8'
  );
  const activation = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt'),
    'utf8'
  );
  const labels = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCoreFrenchLabels.kt'),
    'utf8'
  );
  const wifiScanner = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/WifiScanner.kt'),
    'utf8'
  );

  assert.doesNotMatch(diagnostics, /"WIFI_SCAN"/);
  assert.doesNotMatch(diagnostics, /wifiScanServiceAvailable|wifiScanPermissionGranted|locationEnabledForWifiScan/);
  assert.doesNotMatch(activation, /wifiScanServiceAvailable\s*=|wifiScanPermissionGranted\s*=|locationEnabledForWifiScan\s*=|wifiScanFresh|Réseau Wi‑Fi/);
  assert.doesNotMatch(labels, /"WIFI_SCAN"\s*->/);
  assert.match(wifiScanner, /SIGNAL_WIFI_SCAN_FRESH\s*=\s*"WIFI_SCAN_FRESH"/);
  assert.doesNotMatch(wifiScanner, /PhoneCorePhysicalValidation\.SIGNAL_WIFI_SCAN_FRESH/);
});
