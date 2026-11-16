import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const PRODUCT_STATES = new Set([
  'CONNECTED',
  'LOCKED_EXTERNAL_DEPENDENCY',
  'PHYSICAL_REQUIRED',
  'INTENTIONALLY_DISABLED',
  'CI_ONLY'
]);

export const DEFAULT_KNOWN_IDS = new Set([
  'dialer','incall','call_screening','caller_id_local','caller_reputation_remote',
  'community_phone_reports','sms','mms','contacts','wifi_observation',
  'bluetooth_observation','network_behavior','vpn_runtime','vpn_control_plane','mesh_runtime',
  'wearable_runtime','digital_exposure','threat_intelligence','regulatory_intelligence',
  'soc_decision_plane','osint_analyst_tooling','device_utilities','voice_rtc_ptt',
  'first_run_activation','system_doctor_release_evidence'
]);

const REQUIRED_CHAIN_LINKS = ['producer', 'contract', 'sink', 'consumer', 'surface', 'evidence'];

export function validateIntegrationCapabilities(registry, { knownIds = DEFAULT_KNOWN_IDS } = {}) {
  const errors = [];
  const capabilities = Array.isArray(registry?.capabilities) ? registry.capabilities : [];
  if (registry?.schema_version !== 1) errors.push('integration registry schema_version must be 1');
  if (!Array.isArray(registry?.capabilities)) errors.push('integration registry capabilities must be an array');

  const seen = new Set();
  for (const capability of capabilities) {
    const id = capability?.id;
    if (typeof id !== 'string' || !knownIds.has(id)) errors.push(`unknown capability: ${String(id)}`);
    if (seen.has(id)) errors.push(`duplicate capability id: ${String(id)}`);
    seen.add(id);

    if (typeof capability?.owner !== 'string' || capability.owner.trim() === '') errors.push(`${id}: owner is required`);
    if (typeof capability?.surface !== 'string' || capability.surface.trim() === '') errors.push(`${id}: surface is required`);
    if (!PRODUCT_STATES.has(capability?.state)) errors.push(`${id}: invalid integration state`);

    if (capability?.state === 'CONNECTED') {
      const chain = capability.integration_chain;
      const complete = chain && REQUIRED_CHAIN_LINKS.every((link) => chain[link] === true);
      if (!complete || typeof capability.evidence_ref !== 'string' || capability.evidence_ref.trim() === '') {
        errors.push(`${id}: CONNECTED requires a complete integration chain and evidence reference`);
      }
      if (capability.diagnostic_reason != null) errors.push(`${id}: CONNECTED cannot carry a diagnostic reason`);
    } else if (PRODUCT_STATES.has(capability?.state)) {
      if (typeof capability.diagnostic_reason !== 'string' || capability.diagnostic_reason.trim() === '') {
        errors.push(`${id}: non-CONNECTED state requires a diagnostic reason`);
      }
    }
  }

  const ciOnlyCount = capabilities.filter((item) => item?.state === 'CI_ONLY').length;
  const intentionallyDisabledCount = capabilities.filter((item) => item?.state === 'INTENTIONALLY_DISABLED').length;
  const production = capabilities.filter((item) => !['CI_ONLY', 'INTENTIONALLY_DISABLED'].includes(item?.state));
  const connectedCount = production.filter((item) => item?.state === 'CONNECTED').length;

  return {
    ok: errors.length === 0,
    errors,
    summary: {
      total: capabilities.length,
      productionTotal: production.length,
      connectedCount,
      ciOnlyCount,
      intentionallyDisabledCount
    }
  };
}

export function validateIntegrationRegistryFile(registryPath = path.resolve('config/integration-capabilities.json')) {
  const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'));
  return validateIntegrationCapabilities(registry);
}

const self = fileURLToPath(import.meta.url);
if (process.argv[1] && path.resolve(process.argv[1]) === self) {
  let result;
  try {
    result = validateIntegrationRegistryFile();
  } catch (error) {
    console.error(`Integration product truth failed: ${error.message}`);
    process.exit(1);
  }
  if (!result.ok) {
    result.errors.forEach((error) => console.error(`Integration product truth: ${error}`));
    process.exit(1);
  }
  console.log(JSON.stringify(result.summary));
}
