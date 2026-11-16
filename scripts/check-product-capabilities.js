#!/usr/bin/env node
import fs from 'node:fs';
import { createHash, createPublicKey, verify } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = path.join(rootDir, 'config', 'product-capabilities.json');

function fail(message, errors) {
  errors.push(message);
}

const phoneCoreCriteria = ['incoming_call_connected', 'outgoing_call_connected', 'call_screening_observed',
  'contacts_provider_ready', 'call_history_provider_ready', 'incoming_sms_received', 'outgoing_sms_submitted',
  'outgoing_sms_delivered', 'incoming_mms_safe_preview', 'outgoing_mms_sent', 'incoming_call_notification',
  'incoming_sms_notification', 'caller_id_ui_shown', 'in_call_ui_shown'];

const operationalStages = ['deployed', 'runtime_verified', 'physically_validated', 'release_signed', 'customer_available'];

export function stableCapabilityProof(value) {
  if (Array.isArray(value)) return `[${value.map(stableCapabilityProof).join(',')}]`;
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableCapabilityProof(value[key])}`).join(',')}}`;
  return JSON.stringify(value);
}

function validDate(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
}

function safePath(baseDir, value) {
  if (typeof value !== 'string' || !value || value.includes('\\') || path.isAbsolute(value)) return null;
  const resolved = path.resolve(baseDir, value);
  const root = fs.realpathSync(baseDir);
  if (!resolved.startsWith(`${path.resolve(baseDir)}${path.sep}`)) return null;
  try {
    const real = fs.realpathSync(resolved);
    return real.startsWith(`${root}${path.sep}`) && fs.statSync(real).isFile() ? real : null;
  } catch { return null; }
}

function validateProof(capability, stage, baseDir, now, trust, errors) {
  const location = safePath(baseDir, capability.verification_evidence?.[stage]);
  if (!location) { errors.push(`${capability.id}: ${stage} requires operational evidence`); return; }
  try {
    if (fs.statSync(location).size > 256 * 1024) throw Error('proof too large');
    const record = JSON.parse(fs.readFileSync(location, 'utf8'));
    const payload = record.payload;
    const issuer = trust.issuers?.find(item => item.id === record.issuer && item.revoked !== true);
    if (!issuer || !issuer.stages?.includes(stage) || !issuer.capabilities?.includes(capability.id) ||
        !issuer.environments?.includes(payload?.environment)) throw Error('issuer not authorized for this scope');
    const key = createPublicKey(issuer.public_key_pem);
    if (key.asymmetricKeyType !== 'ed25519' || typeof record.signature !== 'string' ||
        !verify(null, Buffer.from(stableCapabilityProof(payload)), key, Buffer.from(record.signature, 'base64'))) throw Error('signature invalid');
    if (payload.proof_type !== 'SENTINEL_PRODUCT_CAPABILITY_OBSERVATION' || payload.schema_version !== 1 ||
        payload.issuer_id !== record.issuer || payload.capability_id !== capability.id || payload.stage !== stage || payload.result !== 'PASS' ||
        payload.evidence_sha !== capability.evidence_sha || !/^[a-f0-9]{40}$/.test(payload.evidence_sha ?? '') ||
        !/^[a-f0-9]{64}$/.test(payload.artifact_sha256 ?? '')) throw Error('revision/artifact binding invalid');
    const at = Date.parse(payload.evidence_at);
    const expires = Date.parse(payload.expires_at);
    if (new Date(at).toISOString() !== payload.evidence_at || new Date(expires).toISOString() !== payload.expires_at ||
        !Number.isFinite(at) || !Number.isFinite(expires) || at > now || expires <= now || expires <= at ||
        expires - at > 90 * 86400000) throw Error('evidence freshness invalid');
    if (typeof payload.environment !== 'string' || !payload.environment.trim() ||
        typeof payload.scope_id !== 'string' || !payload.scope_id.trim()) throw Error('observation scope missing');
    const sources = payload.source_files;
    if (!sources || Array.isArray(sources) || Object.keys(sources).length !== capability.source_scope.length) throw Error('source scope invalid');
    for (const source of capability.source_scope) {
      const file = safePath(baseDir, source);
      if (!file || createHash('sha256').update(fs.readFileSync(file)).digest('hex') !== sources[source]) throw Error('source scope changed');
    }
    if (stage === 'physically_validated') {
      if (payload.validation_kind !== 'PHYSICAL_DEVICE' || !Array.isArray(payload.criteria) || !payload.criteria.length ||
          payload.criteria.some(item => !item || item.passed !== true || typeof item.id !== 'string') ||
          new Set(payload.criteria.map(item => item.id)).size !== payload.criteria.length) throw Error('physical criteria invalid');
      const required = capability.required_physical_criteria;
      if (required.length && (required.length !== payload.criteria.length || required.some(id => !payload.criteria.some(item => item.id === id)))) throw Error('physical criteria incomplete');
    }
    return payload;
  } catch (error) { errors.push(`${capability.id}: ${stage} evidence rejected: ${error.message}`); }
}

export function validateProductCapabilities(registry, baseDir = rootDir, { now = Date.now(), trust = null } = {}) {
  const errors = [];
  if (!registry || typeof registry !== 'object') return ['registry must be an object'];
  if (registry.schema_version !== 2) fail('schema_version must be 2', errors);
  if (!validDate(registry.updated_at) || Date.parse(registry.updated_at) > now) fail('updated_at must be a valid non-future calendar date', errors);
  if (trust === null) {
    try { trust = JSON.parse(fs.readFileSync(path.join(baseDir, 'config/product-evidence-trust.json'), 'utf8')); }
    catch { trust = { schema_version: 1, issuers: [] }; fail('operational trust configuration missing or unreadable', errors); }
  }
  if (!Array.isArray(registry.capabilities) || registry.capabilities.length === 0) {
    fail('capabilities must be a non-empty array', errors);
    return errors;
  }

  const ids = new Set();
  const booleanFields = [
    'implemented',
    'configured',
    'requires_deployment',
    'deployed',
    'runtime_verified',
    'requires_physical_validation',
    'physically_validated',
    'requires_signed_release',
    'release_signed',
    'customer_available'
  ];

  for (const capability of registry.capabilities) {
    const id = capability?.id;
    if (typeof id !== 'string' || !/^[a-z0-9_]+$/.test(id)) {
      fail(`invalid capability id: ${String(id)}`, errors);
      continue;
    }
    if (ids.has(id)) fail(`duplicate capability id: ${id}`, errors);
    ids.add(id);

    if (typeof capability.surface !== 'string' || capability.surface.trim().length < 3) {
      fail(`${id}: surface is required`, errors);
    }
    for (const field of booleanFields) {
      if (typeof capability[field] !== 'boolean') fail(`${id}: ${field} must be boolean`, errors);
    }

    if (!capability.implemented) {
      for (const field of ['configured', 'deployed', 'runtime_verified', 'physically_validated', 'release_signed', 'customer_available']) {
        if (capability[field]) fail(`${id}: ${field} cannot be true when implemented=false`, errors);
      }
    }
    if (capability.configured && !capability.implemented) {
      fail(`${id}: configured requires implemented`, errors);
    }
    if (capability.deployed && (!capability.implemented || !capability.configured)) {
      fail(`${id}: deployed requires implemented and configured`, errors);
    }
    if (capability.runtime_verified && !capability.configured) fail(`${id}: runtime_verified requires configured`, errors);
    if (capability.runtime_verified && capability.requires_deployment && !capability.deployed) {
      fail(`${id}: runtime_verified requires deployed when requires_deployment=true`, errors);
    }
    if (capability.physically_validated && !capability.runtime_verified) {
      fail(`${id}: physically_validated requires runtime_verified`, errors);
    }
    if (capability.release_signed && !capability.implemented) {
      fail(`${id}: release_signed requires implemented`, errors);
    }

    if (!Array.isArray(capability.blockers)) {
      fail(`${id}: blockers must be an array`, errors);
    } else {
      for (const blocker of capability.blockers) {
        if (typeof blocker !== 'string' || blocker.trim().length < 8) {
          fail(`${id}: blocker entries must be substantive strings`, errors);
        }
      }
    }

    if (!Array.isArray(capability.evidence) || capability.evidence.length === 0) {
      fail(`${id}: evidence must contain at least one repository path`, errors);
    } else {
      for (const evidencePath of capability.evidence) {
        if (typeof evidencePath !== 'string' || evidencePath.startsWith('/') || evidencePath.includes('..')) {
          fail(`${id}: invalid evidence path ${String(evidencePath)}`, errors);
          continue;
        }
        if (!safePath(baseDir, evidencePath)) {
          fail(`${id}: evidence path does not exist: ${evidencePath}`, errors);
        }
      }
    }

    if (!Array.isArray(capability.source_scope) || !capability.source_scope.length ||
        capability.source_scope.some(source => !safePath(baseDir, source)) ||
        new Set(capability.source_scope).size !== capability.source_scope.length) fail(`${id}: source_scope invalid`, errors);
    if (!Array.isArray(capability.required_physical_criteria) ||
        capability.required_physical_criteria.some(item => typeof item !== 'string' || !item) ||
        new Set(capability.required_physical_criteria).size !== capability.required_physical_criteria.length) fail(`${id}: required physical criteria invalid`, errors);
    if (!capability.verification_evidence || typeof capability.verification_evidence !== 'object' || Array.isArray(capability.verification_evidence) ||
        Object.keys(capability.verification_evidence).some(stage => !operationalStages.includes(stage))) fail(`${id}: verification_evidence invalid`, errors);
    if (id === 'phone_core_android' && (!Array.isArray(capability.required_physical_criteria) ||
        capability.required_physical_criteria.length !== 14 || phoneCoreCriteria.some(criterion => !capability.required_physical_criteria.includes(criterion)))) {
      fail(`${id}: exactly the 14 canonical Phone Core criteria are required`, errors);
    }
    if (capability.requires_physical_validation && !capability.required_physical_criteria?.length) fail(`${id}: physical criteria must be declared`, errors);
    const claims = operationalStages.filter(stage => capability[stage] === true);
    if (!claims.length) {
      if (capability.evidence_sha !== null || capability.evidence_at !== null) fail(`${id}: unverified operational evidence metadata must be null`, errors);
    } else {
      const proofs = claims.map(stage => validateProof(capability, stage, baseDir, now, trust, errors)).filter(Boolean);
      if (proofs.length && new Set(proofs.map(proof => `${proof.artifact_sha256}|${proof.environment}|${proof.scope_id}`)).size !== 1) fail(`${id}: operational proof scope mismatch`, errors);
      const latest = proofs.map(proof => proof.evidence_at).sort().at(-1);
      if (!latest || capability.evidence_at !== latest) fail(`${id}: evidence_at must match the latest verified observation`, errors);
    }

    if (capability.customer_available) {
      if (!capability.implemented || !capability.configured || !capability.runtime_verified) {
        fail(`${id}: customer_available requires implemented, configured and runtime_verified`, errors);
      }
      if (capability.requires_deployment && !capability.deployed) {
        fail(`${id}: customer_available requires deployed`, errors);
      }
      if (capability.requires_physical_validation && !capability.physically_validated) {
        fail(`${id}: customer_available requires physical validation`, errors);
      }
      if (capability.requires_signed_release && !capability.release_signed) {
        fail(`${id}: customer_available requires signed release`, errors);
      }
      if (capability.blockers?.length) {
        fail(`${id}: customer_available cannot have open blockers`, errors);
      }
    }
  }

  const byId = new Map(registry.capabilities.filter(item => item?.id).map(item => [item.id, item]));
  if (trust?.schema_version !== 1 || !Array.isArray(trust.issuers)) fail('operational trust configuration invalid', errors);
  else {
    const issuerIds = new Set();
    const issuerKeys = new Set();
    for (const issuer of trust.issuers) {
      try {
        if (typeof issuer.id !== 'string' || !/^[a-zA-Z0-9_-]{2,128}$/.test(issuer.id) || issuerIds.has(issuer.id) ||
            typeof issuer.public_key_pem !== 'string' || !issuer.public_key_pem.startsWith('-----BEGIN PUBLIC KEY-----') ||
            issuer.public_key_pem.length > 8192 || typeof issuer.revoked !== 'boolean' ||
            !Array.isArray(issuer.stages) || !issuer.stages.length || issuer.stages.some(stage => !operationalStages.includes(stage)) ||
            !Array.isArray(issuer.capabilities) || !issuer.capabilities.length || issuer.capabilities.some(id => !byId.has(id)) ||
            !Array.isArray(issuer.environments) || !issuer.environments.length || issuer.environments.some(value => typeof value !== 'string' || !/^[a-zA-Z0-9:_-]{2,128}$/.test(value))) throw Error();
        const key = createPublicKey(issuer.public_key_pem);
        const fingerprint = createHash('sha256').update(key.export({ type: 'spki', format: 'der' })).digest('hex');
        if (key.asymmetricKeyType !== 'ed25519' || issuerKeys.has(fingerprint)) throw Error();
        issuerIds.add(issuer.id); issuerKeys.add(fingerprint);
      } catch { fail('operational trust issuer configuration invalid', errors); }
    }
  }
  const visiting = new Set();
  const visited = new Set();
  function visit(capability) {
    if (visiting.has(capability.id)) { fail(`${capability.id}: dependency cycle`, errors); return; }
    if (visited.has(capability.id)) return;
    visiting.add(capability.id);
    if (!Array.isArray(capability.dependencies)) fail(`${capability.id}: dependencies must be an array`, errors);
    else for (const dependency of capability.dependencies) {
      const target = byId.get(dependency?.id);
      if (!target || !Array.isArray(dependency.required_stages) || !dependency.required_stages.length ||
          dependency.required_stages.some(stage => !booleanFields.includes(stage) || stage.startsWith('requires_'))) {
        fail(`${capability.id}: invalid dependency`, errors); continue;
      }
      if (capability.runtime_verified || capability.customer_available) {
        for (const stage of dependency.required_stages) if (target[stage] !== true) fail(`${capability.id}: dependency ${target.id} requires ${stage}`, errors);
      }
      visit(target);
    }
    visiting.delete(capability.id); visited.add(capability.id);
  }
  for (const capability of byId.values()) visit(capability);

  return errors;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'));
  const errors = validateProductCapabilities(registry);
  if (errors.length) {
    console.error('PRODUCT CAPABILITY TRUTH: BLOCKED');
    for (const error of errors) console.error(`- ${error}`);
    process.exit(1);
  }
  console.log(`PRODUCT CAPABILITY TRUTH: PASS (${registry.capabilities.length} capabilities)`);
}
