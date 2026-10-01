import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test('Phone Core roadmap matches certification schema v4 and keeps Wi-Fi diagnostic-only', () => {
  const source = readFileSync(
    resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/PhoneCorePhysicalValidation.kt'),
    'utf8'
  );
  const roadmap = readFileSync(resolve('docs/ROADMAP.md'), 'utf8');
  const publicRoadmap = readFileSync(resolve('public/roadmap.html'), 'utf8');

  assert.match(source, /CERTIFICATION_SCHEMA_VERSION\s*=\s*4/);
  const required = source.match(/requiredCount:\s*Int\s*get\(\)\s*=\s*(\d+)/)?.[1];
  assert.equal(required, '13');

  assert.match(roadmap, /Phone Core v4 structurée en 13 preuves/);
  assert.doesNotMatch(roadmap, /Phone Core[^\n]*14 preuves|Validation physique locale structurée en 14 preuves/);
  assert.match(roadmap, /Wi-Fi[^\n]*diagnostic réseau séparé/);
  assert.match(roadmap, /ne compte pas dans le certificat Phone Core/);

  assert.match(publicRoadmap, /certificat Phone Core v4 porte sur 13 preuves distinctes/);
  assert.match(publicRoadmap, /scanner Wi-Fi reste un diagnostic réseau séparé/);
  assert.match(publicRoadmap, /Validation physique 13\/13 requise/);
  assert.doesNotMatch(publicRoadmap, /--progress:\\d+%/);

  assert.doesNotMatch(source, /if\s*\(!wifiScanFresh\)\s*add\("wifi_scan_fresh"\)/);
  assert.match(source, /freshWifiEvidenceIsDiagnosticOnlyInPhoneCoreSchemaV4|wifiScanFresh/);
});
