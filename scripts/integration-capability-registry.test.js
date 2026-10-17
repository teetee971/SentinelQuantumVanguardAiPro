import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const registryPath = path.resolve('config/integration-capabilities.json');
const REQUIRED_FAMILIES = [
  'dialer',
  'incall',
  'call_screening',
  'caller_id_local',
  'caller_reputation_remote',
  'community_phone_reports',
  'sms',
  'mms',
  'contacts',
  'wifi_observation',
  'bluetooth_observation',
  'network_behavior',
  'vpn_runtime',
  'vpn_control_plane',
  'mesh_runtime',
  'wearable_runtime',
  'digital_exposure',
  'threat_intelligence',
  'regulatory_intelligence',
  'soc_decision_plane',
  'osint_analyst_tooling',
  'device_utilities',
  'voice_rtc_ptt',
  'first_run_activation',
  'system_doctor_release_evidence'
];
const VALID_STATES = new Set([
  'CONNECTED',
  'LOCKED_EXTERNAL_DEPENDENCY',
  'PHYSICAL_REQUIRED',
  'INTENTIONALLY_DISABLED',
  'CI_ONLY'
]);

function readRegistry() {
  return JSON.parse(fs.readFileSync(registryPath, 'utf8'));
}

test('integration capability registry exists with schema version 1', () => {
  assert.equal(fs.existsSync(registryPath), true, 'config/integration-capabilities.json must exist');
  assert.equal(readRegistry().schema_version, 1);
});

test('capability ids are unique and every entry has owner, surface and valid state', () => {
  const registry = readRegistry();
  assert.ok(Array.isArray(registry.capabilities));
  const ids = registry.capabilities.map((entry) => entry.id);
  assert.equal(new Set(ids).size, ids.length, 'capability ids must be unique');
  for (const entry of registry.capabilities) {
    assert.match(entry.id, /^[a-z0-9_]{2,64}$/);
    assert.equal(typeof entry.owner, 'string');
    assert.ok(entry.owner.trim().length > 0);
    assert.equal(typeof entry.surface, 'string');
    assert.ok(entry.surface.trim().length > 0);
    assert.equal(VALID_STATES.has(entry.state), true, `invalid state for ${entry.id}`);
    if (entry.state !== 'CONNECTED') {
      assert.equal(typeof entry.diagnostic_reason, 'string');
      assert.ok(entry.diagnostic_reason.trim().length > 0, `${entry.id} needs a diagnostic reason`);
    }
  }
});

test('registry diagnostic reasons stay aligned with the Android truth enum', () => {
  const stateSource = fs.readFileSync(
    'native-android-app/app/src/main/java/com/sentinel/quantum/integration/IntegrationState.kt',
    'utf8'
  );
  const enumMatch = stateSource.match(/enum class IntegrationDiagnosticReason\s*\{([\s\S]*?)\n\}/);
  assert.ok(enumMatch, 'IntegrationDiagnosticReason enum must remain parseable');
  const knownReasons = new Set(
    enumMatch[1]
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
  );

  for (const capability of readRegistry().capabilities) {
    if (capability.state === 'CONNECTED') continue;
    assert.equal(
      knownReasons.has(capability.diagnostic_reason),
      true,
      `${capability.id}: diagnostic reason is missing from IntegrationDiagnosticReason`
    );
  }
});

test('registry covers all 25 capability families from the approved design', () => {
  const ids = new Set(readRegistry().capabilities.map((entry) => entry.id));
  for (const required of REQUIRED_FAMILIES) {
    assert.equal(ids.has(required), true, `missing capability family: ${required}`);
  }
});

test('mesh runtime diagnosis reflects the implemented WireGuard transport', () => {
  const meshController = fs.readFileSync(
    'native-android-app/app/src/main/java/com/sentinel/quantum/security/MeshTunnelController.kt',
    'utf8'
  );
  assert.match(meshController, /backend\.setState\(tunnel,\s*Tunnel\.State\.UP/);
  assert.match(meshController, /RuntimeState\.CONNECTED/);

  const mesh = readRegistry().capabilities.find((entry) => entry.id === 'mesh_runtime');
  assert.ok(mesh, 'mesh_runtime capability must exist');
  assert.notEqual(
    mesh.diagnostic_reason,
    'TRANSPORT_NOT_IMPLEMENTED',
    'implemented Mesh WireGuard transport must not be diagnosed as missing transport'
  );
});

test('Render-declared phone backends are not diagnosed as undeployed', () => {
  const render = fs.readFileSync('render.yaml', 'utf8');
  assert.match(render, /name:\s*sentinel-moteur-api/);
  assert.match(render, /healthCheckPath:\s*\/health\/ready/);

  for (const id of ['caller_reputation_remote', 'community_phone_reports']) {
    const capability = readRegistry().capabilities.find((entry) => entry.id === id);
    assert.ok(capability, `${id} capability must exist`);
    assert.notEqual(
      capability.diagnostic_reason,
      'BACKEND_NOT_DEPLOYED',
      `${id} backend is declared for deployment and must use a runtime/configuration diagnostic instead`
    );
  }
});

test('Digital exposure diagnoses missing user-safe identity binding rather than an absent backend', () => {
  const exposureBackend = fs.readFileSync('backend/wangiri-api/collective_exposure.py', 'utf8');
  const app = fs.readFileSync('backend/wangiri-api/app_redis.py', 'utf8');
  assert.match(exposureBackend, /prefix="\/v1\/intelligence\/exposures"/);
  assert.match(exposureBackend, /X-Exposure-Key|x_exposure_key/);
  assert.match(app, /create_collective_exposure_router\(\)/);

  const capability = readRegistry().capabilities.find((entry) => entry.id === 'digital_exposure');
  assert.ok(capability, 'digital_exposure capability must exist');
  assert.equal(capability.state, 'LOCKED_EXTERNAL_DEPENDENCY');
  assert.equal(capability.diagnostic_reason, 'IDENTITY_BINDING_MISSING');
});

test('VPN truth reflects a missing privileged gateway host rather than missing control-plane code', () => {
  const provisioningServer = fs.readFileSync('ops/vpn-gateway/provisioning-server.js', 'utf8');
  const peerRuntime = fs.readFileSync('ops/vpn-gateway/peer-runtime.js', 'utf8');
  const managePeer = fs.readFileSync('ops/vpn-gateway/manage-peer.sh', 'utf8');
  assert.match(provisioningServer, /createVpnProvisioningServer/);
  assert.match(provisioningServer, /VpnGatewayPeerRuntime required/);
  assert.match(peerRuntime, /manage-peer\.sh/);
  assert.match(managePeer, /Run as root\./);
  assert.match(managePeer, /command -v wg/);
  assert.match(managePeer, /WireGuard interface is not active/);

  for (const id of ['vpn_runtime', 'vpn_control_plane']) {
    const capability = readRegistry().capabilities.find((entry) => entry.id === id);
    assert.ok(capability, `${id} capability must exist`);
    assert.equal(capability.state, 'LOCKED_EXTERNAL_DEPENDENCY');
    assert.equal(capability.diagnostic_reason, 'GATEWAY_NOT_AVAILABLE');
  }
});

test('Threat intelligence distinguishes active public sources from credential-gated adapters', () => {
  const frontend = fs.readFileSync('public/threat-intelligence.js', 'utf8');
  const continuity = JSON.parse(fs.readFileSync('config/module-continuity-inventory.json', 'utf8'));
  for (const marker of [
    'api.github.com/advisories',
    'services.nvd.nist.gov',
    'known_exploited_vulnerabilities.json',
    'api.osv.dev/v1/query'
  ]) {
    assert.equal(frontend.includes(marker), true, `missing free public threat-intel source: ${marker}`);
  }
  assert.equal(
    continuity.entries['security/threat-intelligence/malwarebazaar-adapter.js'].status,
    'DORMANT_LIBRARY'
  );
  assert.equal(
    continuity.entries['security/threat-intelligence/virusshare-adapter.js'].status,
    'DORMANT_LIBRARY'
  );

  const threat = readRegistry().capabilities.find((entry) => entry.id === 'threat_intelligence');
  assert.ok(threat, 'threat_intelligence capability must exist');
  assert.equal(threat.state, 'LOCKED_EXTERNAL_DEPENDENCY');
  assert.equal(threat.diagnostic_reason, 'PARTIAL_PUBLIC_SOURCES_ONLY');
});

test('Voice truth reflects the deployed-but-disabled Render service and readiness health gate', () => {
  const render = fs.readFileSync('render.yaml', 'utf8');
  const voiceService = render.slice(render.indexOf('name: sentinel-voice-api'));
  assert.match(voiceService, /healthCheckPath:\s*\/health\/ready/);
  assert.match(voiceService, /key:\s*VOICE_SERVICE_ENABLED[\s\S]*?value:\s*"false"/);

  const voice = readRegistry().capabilities.find((entry) => entry.id === 'voice_rtc_ptt');
  assert.ok(voice, 'voice_rtc_ptt capability must exist');
  assert.equal(voice.state, 'LOCKED_EXTERNAL_DEPENDENCY');
  assert.equal(voice.diagnostic_reason, 'SERVICE_DISABLED_BY_CONFIGURATION');
});
