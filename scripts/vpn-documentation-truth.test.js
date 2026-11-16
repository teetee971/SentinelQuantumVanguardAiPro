import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const architecture = readFileSync(resolve('docs/security/SENTINEL-VPN-ARCHITECTURE.md'), 'utf8');
const multiRegion = readFileSync(resolve('docs/security/SENTINEL-VPN-MULTI-REGION.md'), 'utf8');
const controller = readFileSync(
  resolve('native-android-app/app/src/main/java/com/sentinel/quantum/security/SentinelVpnController.kt'),
  'utf8'
);
const screen = readFileSync(
  resolve('native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/VpnScreen.kt'),
  'utf8'
);

test('VPN architecture does not deny the implemented Android WireGuard client', () => {
  assert.match(controller, /class SentinelVpnController/);
  assert.match(controller, /GoBackend/);
  assert.match(screen, /Client WireGuard intégré/);

  assert.doesNotMatch(architecture, /does \*\*not\*\* currently contain a VPN implementation/i);
  assert.doesNotMatch(architecture, /future `SentinelVpnController`/i);
  assert.doesNotMatch(architecture, /status as `NOT_IMPLEMENTED`/i);

  assert.match(architecture, /Android WireGuard client integrated/i);
  assert.match(architecture, /Sentinel exit gateway\/service not provisioned/i);
});

test('multi-region documentation separates implemented client foundations from production infrastructure', () => {
  assert.doesNotMatch(multiRegion, /Status: \*\*design foundation only\*\*/i);
  assert.doesNotMatch(multiRegion, /- `NOT_IMPLEMENTED`/);
  assert.match(multiRegion, /client\/control-plane foundation implemented/i);
  assert.match(multiRegion, /production exit infrastructure unavailable/i);
  assert.match(multiRegion, /Still required before a country becomes connectable/i);
});
