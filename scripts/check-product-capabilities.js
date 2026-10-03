#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = path.join(rootDir, 'config', 'product-capabilities.json');

function fail(message, errors) {
  errors.push(message);
}

export function validateProductCapabilities(registry, baseDir = rootDir) {
  const errors = [];
  if (!registry || typeof registry !== 'object') return ['registry must be an object'];
  if (registry.schema_version !== 1) fail('schema_version must be 1', errors);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(registry.updated_at ?? '')) {
    fail('updated_at must be YYYY-MM-DD', errors);
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
        if (!fs.existsSync(path.join(baseDir, evidencePath))) {
          fail(`${id}: evidence path does not exist: ${evidencePath}`, errors);
        }
      }
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
