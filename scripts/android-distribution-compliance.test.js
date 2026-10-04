import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { validateProductCapabilities } from './check-product-capabilities.js';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registry = JSON.parse(
  fs.readFileSync(path.join(rootDir, 'config', 'product-capabilities.json'), 'utf8')
);

test('public Android release is structurally gated by exact-release distribution compliance', () => {
  const compliance = registry.capabilities.find(item => item.id === 'android_distribution_compliance');
  const release = registry.capabilities.find(item => item.id === 'public_android_release');

  assert.ok(compliance, 'android_distribution_compliance capability must be registered');
  assert.ok(release, 'public_android_release capability must be registered');
  assert.equal(compliance.configured, false);
  assert.equal(compliance.runtime_verified, false);
  assert.equal(compliance.customer_available, false);

  const dependency = release.dependencies.find(item => item.id === 'android_distribution_compliance');
  assert.deepEqual(dependency?.required_stages, ['runtime_verified']);

  for (const source of [
    'native-android-app/app/src/main/AndroidManifest.xml',
    'native-android-app/app/build.gradle',
    'docs/PLAY-DATA-SAFETY.md',
    'native-android-app/PLAY_STORE_LISTING.md',
    'public/privacy.html',
    'public/terms.html',
    'public/cgv.html',
    'public/legal.html',
    'public/pricing.html',
    'RELEASE_CHECKLIST.md',
  ]) {
    assert.ok(compliance.source_scope.includes(source), `distribution compliance must invalidate on ${source}`);
  }
});

test('a signed and published Android release cannot bypass absent distribution compliance', () => {
  const copy = structuredClone(registry);
  const release = copy.capabilities.find(item => item.id === 'public_android_release');
  release.configured = true;
  release.deployed = true;
  release.runtime_verified = true;
  release.physically_validated = true;
  release.release_signed = true;
  release.blockers = [];

  const errors = validateProductCapabilities(copy, rootDir);
  assert.ok(
    errors.some(error => error.includes('dependency android_distribution_compliance requires runtime_verified')),
    'public Android readiness must remain blocked until distribution compliance is externally verified'
  );
});

test('repository legal and Play preparation copy cannot masquerade as completed compliance', () => {
  const dataSafety = fs.readFileSync(path.join(rootDir, 'docs/PLAY-DATA-SAFETY.md'), 'utf8');
  const listing = fs.readFileSync(path.join(rootDir, 'native-android-app/PLAY_STORE_LISTING.md'), 'utf8');
  const legal = fs.readFileSync(path.join(rootDir, 'public/legal.html'), 'utf8');
  const pricing = fs.readFileSync(path.join(rootDir, 'public/pricing.html'), 'utf8');

  assert.match(dataSafety, /document de préparation interne/);
  assert.match(dataSafety, /n’est pas une déclaration Play Console soumise/);
  assert.match(listing, /None of these may be advertised as a distributed production service/);
  assert.match(legal, /\[NOM_ÉDITEUR — à configurer\]/);
  assert.match(pricing, /Prix non contractuels/);
  assert.match(pricing, /Aucun paiement n’est traité par cette vitrine statique aujourd’hui/);
});
