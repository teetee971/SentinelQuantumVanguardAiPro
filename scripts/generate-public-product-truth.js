#!/usr/bin/env node
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { renderCapabilityConsumers } from './render-product-capabilities.js';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outputPath = join(rootDir, 'public', 'product-truth.generated.json');

function readJson(relativePath) {
  return JSON.parse(readFileSync(join(rootDir, relativePath), 'utf8'));
}

function capabilityState(capability) {
  if (capability.customer_available === true) return 'AVAILABLE';
  if (capability.implemented !== true) return 'PLANNED';
  if (capability.requires_deployment && capability.deployed !== true) return 'INFRASTRUCTURE';
  if (capability.requires_physical_validation && capability.physically_validated !== true) return 'VALIDATION';
  if (capability.requires_signed_release && capability.release_signed !== true) return 'VALIDATION';
  return 'VALIDATION';
}

function publicCapability(capability, expiresAtMs) {
  return {
    id: capability.id,
    surface: capability.surface,
    state: capabilityState(capability),
    implemented: capability.implemented === true,
    configured: capability.configured === true,
    deployed: capability.deployed === true,
    runtime_verified: capability.runtime_verified === true,
    physically_validated: capability.physically_validated === true,
    release_signed: capability.release_signed === true,
    customer_available: capability.customer_available === true,
    expires_at_ms: Number.isSafeInteger(expiresAtMs) ? expiresAtMs : null,
  };
}

function publicModule(module) {
  return {
    id: module.id,
    name: module.name,
    tier: module.tier,
    offer: module.offer,
    permanent_free: module.permanent_free === true,
    trial_candidate: module.trial_candidate === true,
    trial_eligible: module.trial_eligible === true,
    cost_class: module.cost_class ?? null,
    billable_remote_workload: module.billable_remote_workload === true,
    required_capabilities: Array.isArray(module.required_capabilities) ? module.required_capabilities : [],
    fallback: module.fallback ?? null,
  };
}

export function buildPublicProductTruth() {
  const capabilities = readJson('config/product-capabilities.json');
  const catalog = readJson('config/commercial-catalog.json');
  const proExtension = readJson('config/commercial-catalog-pro-extension.json');
  const modules = [...(catalog.modules ?? []), ...(proExtension.modules ?? [])].map(publicModule);
  const generatedCapabilityConsumer = JSON.parse(
    renderCapabilityConsumers(capabilities, rootDir)['public/product-capabilities.json']
  );
  const expiryByCapabilityId = new Map(
    (generatedCapabilityConsumer.capabilities ?? []).map((capability) => [capability.id, capability.expires_at_ms])
  );

  return {
    schema_version: 1,
    generated_from: {
      product_capabilities_updated_at: capabilities.updated_at ?? null,
      commercial_catalog_updated_at: catalog.updated_at ?? null,
      commercial_pro_extension_updated_at: proExtension.updated_at ?? null,
    },
    commerce: {
      checkout_enabled: catalog.commerce?.checkout_enabled === true,
      trial_enrollment_enabled: catalog.commerce?.trial_enrollment_enabled === true,
      trial_duration_days: catalog.commerce?.trial_duration_days ?? null,
      entitlement_provider: catalog.commerce?.entitlement_provider ?? 'UNSELECTED',
      canonical_price: catalog.commerce?.canonical_price ?? null,
    },
    tiers: Array.isArray(catalog.tiers) ? catalog.tiers : [],
    modules,
    capabilities: (capabilities.capabilities ?? []).map((capability) =>
      publicCapability(capability, expiryByCapabilityId.get(capability.id))
    ),
  };
}

export function serializePublicProductTruth(payload = buildPublicProductTruth()) {
  return `${JSON.stringify(payload, null, 2)}\n`;
}

export function writePublicProductTruth() {
  const serialized = serializePublicProductTruth();
  writeFileSync(outputPath, serialized, 'utf8');
  return serialized;
}

export function checkPublicProductTruth() {
  const expected = serializePublicProductTruth();
  const actual = existsSync(outputPath) ? readFileSync(outputPath, 'utf8') : '';
  if (actual !== expected) throw new Error('public/product-truth.generated.json is stale; run node scripts/generate-public-product-truth.js');
}

const invoked = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invoked) {
  if (process.argv.includes('--check')) {
    checkPublicProductTruth();
    console.log('Public product truth snapshot is current.');
  } else {
    writePublicProductTruth();
    console.log('Generated public/product-truth.generated.json from canonical registries.');
  }
}
