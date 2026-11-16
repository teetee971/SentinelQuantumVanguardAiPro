import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const SOURCE_ROOT = resolve(
  'native-android-app/app/src/main/java/com/sentinel/quantum'
);
const REGISTRY_PATH = resolve(
  SOURCE_ROOT,
  'security/SentinelVpnRuntimeStateRegistry.kt'
);
const CONTROLLER_PATH = resolve(
  SOURCE_ROOT,
  'security/SentinelVpnController.kt'
);
const DOCTOR_PATH = resolve(
  SOURCE_ROOT,
  'security/SentinelSystemDoctor.kt'
);
const VPN_SCREEN_PATH = resolve(
  SOURCE_ROOT,
  'ui/screens/VpnScreen.kt'
);

test('VPN runtime state is shared with diagnostics and the VPN surface', () => {
  assert.equal(
    existsSync(REGISTRY_PATH),
    true,
    'the VPN runtime observation registry must exist'
  );

  const registry = readFileSync(REGISTRY_PATH, 'utf8');
  const controller = readFileSync(CONTROLLER_PATH, 'utf8');
  const doctor = readFileSync(DOCTOR_PATH, 'utf8');
  const vpnScreen = readFileSync(VPN_SCREEN_PATH, 'utf8');

  assert.match(registry, /data class Observation\s*\(/);
  assert.match(registry, /fun current\(/);
  assert.match(registry, /maxAgeMs/);
  assert.match(registry, /PROTECTED/);
  assert.match(controller, /SentinelVpnRuntimeStateRegistry\.publish/);
  assert.match(doctor, /SentinelVpnRuntimeStateRegistry\.current/);
  assert.match(vpnScreen, /SentinelVpnRuntimeStateRegistry\.current/);
  assert.match(vpnScreen, /NetworkVpnPresentation\.vpnSurface/);
  assert.doesNotMatch(
    vpnScreen,
    /val runtimeState = SentinelVpnController\.RuntimeState\.READY_NO_GATEWAY/,
    'the VPN surface must not hardcode one runtime state'
  );
});
