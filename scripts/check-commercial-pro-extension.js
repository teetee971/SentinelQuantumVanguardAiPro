import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const readJson = (path) => JSON.parse(readFileSync(resolve(path), 'utf8'));

export function validateProExtension(baseCatalog, extension, capabilityRegistry) {
  const errors = [];
  const baseIds = new Set((baseCatalog.modules ?? []).map((module) => module.id));
  const capabilityMap = new Map((capabilityRegistry.capabilities ?? []).map((capability) => [capability.id, capability]));
  const extensionIds = new Set();

  if (extension.schema_version !== 1) errors.push('Pro extension schema_version must be 1');
  if (!Array.isArray(extension.modules) || extension.modules.length === 0) errors.push('Pro extension modules must be non-empty');

  for (const module of extension.modules ?? []) {
    const prefix = `module ${module.id ?? '<missing-id>'}`;
    if (typeof module.id !== 'string' || !module.id) errors.push(`${prefix}: id is required`);
    if (baseIds.has(module.id)) errors.push(`${prefix}: duplicate id already exists in base commercial catalog`);
    if (extensionIds.has(module.id)) errors.push(`${prefix}: duplicate id in Pro extension`);
    extensionIds.add(module.id);

    if (!['PRO', 'ENTERPRISE'].includes(module.tier)) errors.push(`${prefix}: Pro extension tier must be PRO or ENTERPRISE`);
    if (!['NOT_FOR_SALE', 'CONTRACT_ONLY'].includes(module.offer)) errors.push(`${prefix}: Pro extension offer must remain fail-closed`);
    if (module.permanent_free !== false) errors.push(`${prefix}: Pro extension module cannot be permanent_free`);
    if (module.trial_candidate || module.trial_eligible) errors.push(`${prefix}: Pro/Enterprise module cannot use consumer trial state`);
    if (!Array.isArray(module.required_capabilities) || module.required_capabilities.length === 0) {
      errors.push(`${prefix}: canonical capability gates are required`);
      continue;
    }

    for (const capabilityId of module.required_capabilities) {
      if (!capabilityMap.has(capabilityId)) errors.push(`${prefix}: unknown required capability ${capabilityId}`);
    }

    if (module.offer === 'CONTRACT_ONLY') {
      const unavailable = module.required_capabilities.filter((id) => capabilityMap.get(id)?.customer_available !== true);
      if (unavailable.length > 0) errors.push(`${prefix}: cannot become CONTRACT_ONLY while capabilities are unavailable: ${unavailable.join(', ')}`);
    }
  }

  const api = (extension.modules ?? []).find((module) => module.id === 'sentinel_api_pro');
  if (!api) errors.push('required Pro product missing: sentinel_api_pro');
  if (api) {
    if (api.tier !== 'PRO') errors.push('sentinel_api_pro must start in PRO tier');
    if (api.offer !== 'NOT_FOR_SALE') errors.push('sentinel_api_pro must remain NOT_FOR_SALE until dedicated readiness gates exist');
    if (!api.required_capabilities?.includes('saas_identity')) errors.push('sentinel_api_pro must require saas_identity');
    if (api.billable_remote_workload !== true) errors.push('sentinel_api_pro must be classified as a billable remote workload');
  }

  return errors;
}

export function runProExtensionCheck({
  baseCatalogPath = 'config/commercial-catalog.json',
  extensionPath = 'config/commercial-catalog-pro-extension.json',
  capabilityPath = 'config/product-capabilities.json'
} = {}) {
  const errors = validateProExtension(
    readJson(baseCatalogPath),
    readJson(extensionPath),
    readJson(capabilityPath)
  );
  if (errors.length > 0) throw new Error(`Commercial Pro extension validation failed:\n- ${errors.join('\n- ')}`);
  return { ok: true };
}

const invokedAsScript = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;
if (invokedAsScript) {
  try {
    runProExtensionCheck();
    console.log('Commercial Pro extension OK.');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
