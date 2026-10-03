#!/usr/bin/env node
import fs from 'node:fs';
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
    '| Capability | Status | Customer available | Open blockers |',
    '|---|---|---:|---:|'
  ];

  for (const capability of registry.capabilities) {
    lines.push(
      `| ${capability.surface.replaceAll('|', '\\|')} | ${statusFor(capability)} | ${capability.customer_available ? 'YES' : 'NO'} | ${capability.blockers.length} |`
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

const registry = JSON.parse(fs.readFileSync(registryPath, 'utf8'));
const rendered = renderProductCapabilities(registry);

if (process.argv.includes('--check')) {
  const current = fs.existsSync(outputPath) ? fs.readFileSync(outputPath, 'utf8') : '';
  if (current !== rendered) {
    console.error('PRODUCT CAPABILITY STATUS: generated document is stale');
    process.exit(1);
  }
  console.log('PRODUCT CAPABILITY STATUS: generated document is current');
} else {
  fs.writeFileSync(outputPath, rendered);
  console.log(`Wrote ${path.relative(rootDir, outputPath)}`);
}
