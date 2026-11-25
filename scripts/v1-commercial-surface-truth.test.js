import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

function read(path) {
  return fs.readFileSync(path, 'utf8');
}

const excludedCapabilityIds = [
  'osint_android_feeds',
  'sentinel_vpn_service',
  'mesh_runtime',
  'voice_transform_calls',
  'collective_defense_android'
];

test('le graphe commercial V1 n’ouvre pas les surfaces exclues', () => {
  const nav = read('native-android-app/app/src/main/java/com/sentinel/quantum/navigation/CommercialPhoneCoreNavGraph.kt');
  for (const forbidden of [
    'OsintFeed',
    'VpnScreen',
    'SystemDoctor',
    'SecurityAudit',
    'AppPermissionAnalyzer',
    'NetworkSurveillance',
    'DigitalExposure',
    'CollectiveDefense',
    'EmailSecurity',
    'SmartHome',
    'SentinelAiActivity'
  ]) {
    assert.equal(nav.includes(forbidden), false, `${forbidden} est encore nommé dans le graphe commercial`);
  }
});

test('le manifeste V1 retire les entrées hors Phone Core', () => {
  const manifest = read('native-android-app/app/src/v1Commercial/AndroidManifest.xml');
  for (const name of [
    '.ai.SentinelAiActivity',
    '.VoiceStudioActivity',
    'com.wireguard.android.backend.GoBackend$VpnService',
    '.security.SentinelWearListenerService'
  ]) {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    assert.match(
      manifest,
      new RegExp(`android:name="${escaped}"[^>]*tools:node="remove"`),
      `${name} n’est pas retiré du manifeste V1`
    );
  }
});

test('le registre ne présente pas les capacités exclues comme disponibles client', () => {
  const registry = JSON.parse(read('config/product-capabilities.json'));
  const byId = new Map(registry.capabilities.map((capability) => [capability.id, capability]));
  for (const capability of registry.capabilities) {
    assert.equal(capability.customer_available, false, capability.id);
    assert.equal(typeof capability.commercial_v1_exposed, 'boolean', `${capability.id} sans commercial_v1_exposed`);
  }
  for (const id of excludedCapabilityIds) {
    const capability = byId.get(id);
    assert.ok(capability, id);
    assert.equal(capability.commercial_v1_exposed, false, id);
    assert.equal(
      capability.blockers.some((blocker) => blocker.includes('-PsentinelCommercialV1=true')),
      true,
      `${id} ne dit pas qu’il est hors du profil commercial`
    );
  }
  const phone = byId.get('phone_core_android');
  assert.equal(phone.commercial_v1_exposed, true);
  assert.equal(phone.customer_available, false);
  assert.equal(phone.runtime_verified, false);
  assert.equal(phone.physically_validated, false);
});

test('le statut généré distingue l’exclusion V1 d’un simple manque de validation', () => {
  const status = read('docs/PRODUCT_CAPABILITY_STATUS.md');
  for (const surface of [
    'Android public-source OSINT feeds',
    'Sentinel VPN',
    'Mesh control plane and relay runtime'
  ]) {
    assert.match(status, new RegExp(`\\| ${surface} \\| EXCLUDED_FROM_COMMERCIAL_V1 \\|`));
  }
  assert.equal(status.includes('| YES |'), false);
});

test('les textes publics et l’écran VPN ne disent pas que l’exclu est opérationnel', () => {
  const threat = read('public/threat-intelligence.html');
  assert.equal(threat.includes('LIVE INTELLIGENCE'), false);
  assert.match(threat, /NON QUALIFIÉ/);

  const press = read('public/conference-presse.html');
  assert.equal(press.includes('plateforme Web opérationnelle'), false);
  assert.match(press, /non qualifiée comme service opérationnel/);

  const pricing = read('public/pricing.html');
  assert.equal(pricing.includes('System Doctor manuel, Security Audit manuel'), false);
  assert.match(pricing, /hors profil commercial V1/);
  assert.match(pricing, /non qualifié sur appareil/);

  const vpn = read('native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/VpnScreen.kt');
  assert.equal(vpn.includes('Fondations déjà actives'), false);
  assert.equal(vpn.includes('Arbitrage avec Private Mesh'), false);
  assert.match(vpn, /"Client intégré"/);
  assert.match(vpn, /Non opérationnel/);

  const flags = read('config/feature-flags.js');
  assert.match(flags, /FEATURE_ANDROID_AUTO_UPDATE:\s*false/);

  const catalog = JSON.parse(read('config/commercial-catalog.json'));
  const fimi = catalog.modules.find((module) => module.id === 'foreign_interference_defense');
  assert.equal(fimi.offer, 'NOT_FOR_SALE');

  const matrix = read('audit/v1-commercial/FEATURE_MATRIX.md');
  for (const needle of ['DISARM-FR', 'Qwen', 'nettoyage', 'mise à jour automatique', 'NO-GO']) {
    assert.match(matrix, new RegExp(needle));
  }
  for (const row of matrix.split('\n')) {
    if (!row.startsWith('|') || row.includes('---') || row.includes('Commercialisable')) continue;
    assert.equal(row.includes('| GO |'), false, row);
  }
});
