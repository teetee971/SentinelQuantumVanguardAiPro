#!/usr/bin/env node
import fs from 'node:fs';
import { validateProductCapabilities } from './check-product-capabilities.js';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const registryPath = path.join(rootDir, 'config', 'product-capabilities.json');
const outputPath = path.join(rootDir, 'docs', 'PRODUCT_CAPABILITY_STATUS.md');

export function statusFor(capability) {
  if (capability.customer_available) return 'AVAILABLE';
  if (!capability.implemented) return 'NOT_IMPLEMENTED';
  if (capability.runtime_verified) return 'VERIFIED_NOT_RELEASED';
  if (capability.deployed) return 'DEPLOYED_NOT_VERIFIED';
  if (capability.configured) return 'IMPLEMENTED_NOT_VERIFIED';
  return 'IMPLEMENTED_NOT_CONFIGURED';
}

export function renderProductCapabilities(registry) {
  const lines = [
    '# Product capability status — generated',
    '',
    `Source: \`config/product-capabilities.json\` · updated ${registry.updated_at}.`,
    '',
    'This file is generated. Do not promote a capability by editing this document; update the canonical registry with evidence and let CI validate the invariants.',
    '',
    '| Capability | Status | Customer available | Open blockers | Evidence revision | Observed at |',
    '|---|---|---:|---:|---|---|'
  ];

  for (const capability of registry.capabilities) {
    lines.push(
      `| ${capability.surface.replaceAll('|', '\\|')} | ${statusFor(capability)} | ${capability.customer_available ? 'YES' : 'NO'} | ${capability.blockers.length} | ${capability.evidence_sha ?? 'Not attested'} | ${capability.evidence_at ?? 'Not observed'} |`
    );
  }

  lines.push('', '## Blocking details', '');
  for (const capability of registry.capabilities) {
    lines.push(`### ${capability.surface}`, '');
    if (capability.blockers.length === 0) {
      lines.push('- None.', '');
    } else {
      for (const blocker of capability.blockers) lines.push(`- ${blocker}`);
      lines.push('');
    }
    lines.push('Evidence:');
    for (const evidence of capability.evidence) lines.push(`- \`${evidence}\``);
    lines.push('');
  }

  return `${lines.join('\n').trim()}\n`;
}

export function renderCapabilityConsumers(registry, baseDir = rootDir) {
  const byId = new Map(registry.capabilities.map(capability => [capability.id, capability]));
  function expiryFor(capability, visiting = new Set()) {
    if (!capability || visiting.has(capability.id)) return null;
    const next = new Set(visiting).add(capability.id);
    const expiries = Object.values(capability.verification_evidence).map(location => {
      try { return Date.parse(JSON.parse(fs.readFileSync(path.join(baseDir, location), 'utf8')).payload.expires_at); }
      catch { return NaN; }
    });
    for (const dependency of capability.dependencies) {
      if (dependency.required_stages.some(stage => ['deployed', 'runtime_verified', 'physically_validated', 'release_signed', 'customer_available'].includes(stage))) {
        const expiry = expiryFor(byId.get(dependency.id), next);
        if (expiry === null) return null;
        expiries.push(expiry);
      }
    }
    return expiries.length && expiries.every(Number.isFinite) ? Math.min(...expiries) : null;
  }
  const states = registry.capabilities.map(capability => {
    return { id: capability.id, surface: capability.surface, status: statusFor(capability),
      implemented: capability.implemented, customer_available: capability.customer_available,
      evidence_sha: capability.evidence_sha, evidence_at: capability.evidence_at,
      expires_at_ms: expiryFor(capability) };
  });
  const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
  const html = `<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>État des services Sentinel</title></head><body>
<main><h1>État des services Sentinel</h1><p>Le code présent et les tests ne prouvent pas la disponibilité d’un service. Les validations physiques et la publication sont distinctes.</p><table><thead><tr><th>Service</th><th>État du code</th><th>Disponibilité client</th></tr></thead><tbody>
${states.map(state => `<tr><td>${escape(state.surface)}</td><td>${escape({ AVAILABLE: 'Implémentation vérifiée', NOT_IMPLEMENTED: 'Prévu', VERIFIED_NOT_RELEASED: 'Vérifié, publication manquante', DEPLOYED_NOT_VERIFIED: 'Déployé, validation manquante', IMPLEMENTED_NOT_VERIFIED: 'Implémenté, validation manquante', IMPLEMENTED_NOT_CONFIGURED: 'Implémenté, configuration manquante' }[state.status])}</td><td data-product-available="${state.customer_available}" data-product-expires="${state.expires_at_ms ?? ''}">${state.customer_available ? 'Vérification de validité nécessaire' : 'Non disponible'}</td></tr>`).join('\n')}
</tbody></table><p><a href="capabilities-roadmap.html">Périmètre des capacités</a></p></main><script src="product-capability-status.js" defer></script></body></html>
`;
  const quoted = value => JSON.stringify(value).replaceAll('$', '\\$');
  const kotlin = `// Generated from config/product-capabilities.json. Do not edit.
package com.sentinel.quantum.security

object GeneratedProductCapabilities {
    data class State(val implemented: Boolean, val customerAvailable: Boolean, val expiresAtMs: Long?) {
        fun availableAt(nowMs: Long = System.currentTimeMillis()): Boolean =
            customerAvailable && expiresAtMs != null && nowMs >= 0L && nowMs < expiresAtMs
    }
    val states: Map<String, State> = mapOf(
${states.map(state => `        ${quoted(state.id)} to State(${state.implemented}, ${state.customer_available}, ${state.expires_at_ms === null ? 'null' : `${state.expires_at_ms}L`})`).join(',\n')}
    )
}
`;
  return {
    'public/product-capabilities.json': `${JSON.stringify({ schema_version: 2, updated_at: registry.updated_at, capabilities: states }, null, 2)}\n`,
    'public/product-status.html': html,
    'native-android-app/app/src/main/java/com/sentinel/quantum/security/GeneratedProductCapabilities.kt': kotlin
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'));
  const errors = validateProductCapabilities(registry);
  if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
  const outputs = { 'docs/PRODUCT_CAPABILITY_STATUS.md': renderProductCapabilities(registry), ...renderCapabilityConsumers(registry) };
  let stale = false;
  for (const [relative, rendered] of Object.entries(outputs)) {
    const output = path.join(rootDir, relative);
    if (process.argv.includes('--check')) {
      if (!fs.existsSync(output) || fs.readFileSync(output, 'utf8') !== rendered) {
        console.error(`PRODUCT CAPABILITY STATUS: stale generated file ${relative}`); stale = true;
      }
    } else { fs.mkdirSync(path.dirname(output), { recursive: true }); fs.writeFileSync(output, rendered); }
  }
  if (stale) process.exit(1);
  console.log(`PRODUCT CAPABILITY STATUS: ${process.argv.includes('--check') ? 'current' : 'generated'} (${Object.keys(outputs).length} views)`);
}
