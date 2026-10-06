import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const ALLOWED_TIERS = new Set(['FREE', 'PREMIUM_INDIVIDUAL', 'PRO', 'ENTERPRISE']);
const ALLOWED_OFFERS = new Set([
  'INCLUDED',
  'INCLUDED_WHEN_READY',
  'NOT_FOR_SALE',
  'TRIAL_BUNDLE',
  'ADDON',
  'CONTRACT_ONLY'
]);
const ALLOWED_COST_CLASSES = new Set([
  'LOCAL_ONLY',
  'LOW_SERVER',
  'LOCAL_OR_LOW_SERVER',
  'BANDWIDTH_HEAVY',
  'LICENSED_DATA',
  'HUMAN_SERVICE'
]);

export function loadJson(path) {
  return JSON.parse(readFileSync(resolve(path), 'utf8'));
}

export function capabilityMap(registry) {
  return new Map((registry.capabilities ?? []).map((capability) => [capability.id, capability]));
}

export function moduleTechnicalState(module, capabilities) {
  const required = module.required_capabilities ?? [];
  if (required.length === 0) {
    // A permanent-free module is technically ready without a capability dependency only when it is
    // already declared INCLUDED. INCLUDED_WHEN_READY explicitly says some technical qualification
    // is still outstanding; treating an empty dependency list as ready would silently bypass that
    // future prerequisite (for example physical wearable validation).
    const dependencyFreeReady = module.permanent_free === true && module.offer === 'INCLUDED';
    return {
      ready: dependencyFreeReady,
      missing: [],
      unavailable: []
    };
  }

  const missing = required.filter((id) => !capabilities.has(id));
  const unavailable = required.filter((id) => {
    const capability = capabilities.get(id);
    return capability && capability.customer_available !== true;
  });

  return {
    ready: missing.length === 0 && unavailable.length === 0,
    missing,
    unavailable
  };
}

export function evaluateCommercialAccess(module, catalog, capabilities) {
  const technical = moduleTechnicalState(module, capabilities);

  if (module.permanent_free) {
    return {
      customerOfferable: technical.ready,
      reason: technical.ready ? 'FREE_READY' : 'TECHNICAL_GATE'
    };
  }

  if (module.offer === 'NOT_FOR_SALE') {
    return { customerOfferable: false, reason: 'NOT_FOR_SALE' };
  }

  if (!technical.ready) {
    return { customerOfferable: false, reason: 'TECHNICAL_GATE' };
  }

  if (module.offer === 'CONTRACT_ONLY') {
    return { customerOfferable: true, reason: 'CONTRACT_READY' };
  }

  if (!catalog.commerce?.checkout_enabled || catalog.commerce?.entitlement_provider === 'UNSELECTED') {
    return { customerOfferable: false, reason: 'COMMERCE_GATE' };
  }

  return { customerOfferable: true, reason: 'COMMERCE_READY' };
}

export function validateCommercialCatalog(catalog, capabilityRegistry) {
  const errors = [];
  const capabilities = capabilityMap(capabilityRegistry);

  if (catalog.schema_version !== 1) errors.push('commercial catalog schema_version must be 1');
  if (!Array.isArray(catalog.modules) || catalog.modules.length === 0) errors.push('commercial catalog modules must be non-empty');
  if (!catalog.commerce || typeof catalog.commerce !== 'object') errors.push('commercial catalog commerce object is required');
  if (catalog.commerce?.trial_duration_days !== 7) errors.push('initial Sentinel Premium trial must remain 7 days until deliberately versioned');

  const ids = new Set();
  for (const module of catalog.modules ?? []) {
    const prefix = module.id ? `module ${module.id}` : 'module <missing-id>';

    if (typeof module.id !== 'string' || !module.id) errors.push(`${prefix}: id is required`);
    if (ids.has(module.id)) errors.push(`${prefix}: duplicate id`);
    ids.add(module.id);

    if (!ALLOWED_TIERS.has(module.tier)) errors.push(`${prefix}: invalid tier ${module.tier}`);
    if (!ALLOWED_OFFERS.has(module.offer)) errors.push(`${prefix}: invalid offer ${module.offer}`);
    if (!ALLOWED_COST_CLASSES.has(module.cost_class)) errors.push(`${prefix}: invalid cost_class ${module.cost_class}`);
    if (!Array.isArray(module.required_capabilities)) errors.push(`${prefix}: required_capabilities must be an array`);
    if (typeof module.fallback !== 'string' || !module.fallback) errors.push(`${prefix}: fallback is required`);

    const required = module.required_capabilities ?? [];
    if (new Set(required).size !== required.length) errors.push(`${prefix}: duplicate required capability`);
    for (const capabilityId of required) {
      if (!capabilities.has(capabilityId)) errors.push(`${prefix}: unknown required capability ${capabilityId}`);
    }

    if (module.permanent_free === true) {
      if (module.tier !== 'FREE') errors.push(`${prefix}: permanent_free requires FREE tier`);
      if (!['INCLUDED', 'INCLUDED_WHEN_READY'].includes(module.offer)) errors.push(`${prefix}: permanent_free module cannot be sold as ${module.offer}`);
      if (module.trial_candidate || module.trial_eligible) errors.push(`${prefix}: permanent_free module cannot be trial-gated`);
      if (module.fallback !== 'KEEP_ACCESS' && !module.fallback.startsWith('KEEP_')) {
        errors.push(`${prefix}: permanent_free fallback must preserve access`);
      }
    } else if (module.tier === 'FREE') {
      errors.push(`${prefix}: FREE tier must be permanent_free`);
    }

    if (module.trial_candidate && module.tier !== 'PREMIUM_INDIVIDUAL') {
      errors.push(`${prefix}: trial_candidate is reserved for PREMIUM_INDIVIDUAL`);
    }

    if (module.trial_eligible) {
      if (!module.trial_candidate) errors.push(`${prefix}: trial_eligible requires trial_candidate`);
      if (!catalog.commerce?.trial_enrollment_enabled) errors.push(`${prefix}: trial cannot be eligible while enrollment is disabled`);
      const technical = moduleTechnicalState(module, capabilities);
      if (!technical.ready) errors.push(`${prefix}: trial cannot bypass technical capability readiness`);
      if (required.length === 0) errors.push(`${prefix}: paid trial requires canonical technical capability gates`);
    }

    if (module.offer === 'CONTRACT_ONLY' && module.tier !== 'ENTERPRISE') {
      errors.push(`${prefix}: CONTRACT_ONLY is reserved for ENTERPRISE`);
    }

    if (!module.permanent_free && module.offer !== 'NOT_FOR_SALE' && required.length === 0) {
      errors.push(`${prefix}: sellable paid module requires canonical technical capability gates`);
    }

    const access = evaluateCommercialAccess(module, catalog, capabilities);
    if (module.offer === 'NOT_FOR_SALE' && access.customerOfferable) {
      errors.push(`${prefix}: NOT_FOR_SALE module resolved as customer offerable`);
    }
  }

  const permanentFreeIds = new Set(
    (catalog.modules ?? []).filter((module) => module.permanent_free).map((module) => module.id)
  );
  for (const requiredFreeId of [
    'phone_core',
    'system_doctor_basic',
    'security_audit_basic',
    'app_permission_analyzer_basic',
    'sms_link_analysis_basic',
    'mail_shield_basic',
    'network_visibility_basic'
  ]) {
    if (!permanentFreeIds.has(requiredFreeId)) errors.push(`required permanent FREE baseline missing: ${requiredFreeId}`);
  }

  if (catalog.commerce?.checkout_enabled && catalog.commerce?.entitlement_provider === 'UNSELECTED') {
    errors.push('checkout cannot be enabled without an entitlement provider');
  }
  if (catalog.commerce?.trial_enrollment_enabled && !catalog.commerce?.checkout_enabled) {
    errors.push('trial enrollment cannot be enabled before commerce entitlement flow is enabled');
  }

  return errors;
}

export function runCommercialCatalogCheck({
  catalogPath = 'config/commercial-catalog.json',
  capabilityPath = 'config/product-capabilities.json'
} = {}) {
  const catalog = loadJson(catalogPath);
  const capabilities = loadJson(capabilityPath);
  const errors = validateCommercialCatalog(catalog, capabilities);

  if (errors.length > 0) {
    throw new Error(`Commercial catalog validation failed:\n- ${errors.join('\n- ')}`);
  }

  return {
    modules: catalog.modules.length,
    permanentFree: catalog.modules.filter((module) => module.permanent_free).length,
    trialCandidates: catalog.modules.filter((module) => module.trial_candidate).length,
    offerablePaid: catalog.modules.filter((module) =>
      !module.permanent_free && evaluateCommercialAccess(module, catalog, capabilityMap(capabilities)).customerOfferable
    ).length
  };
}

const invokedAsScript = process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href;
if (invokedAsScript) {
  try {
    const result = runCommercialCatalogCheck();
    console.log(`Commercial catalog OK: ${result.modules} modules, ${result.permanentFree} permanent-free, ${result.trialCandidates} trial candidates, ${result.offerablePaid} paid currently offerable.`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
