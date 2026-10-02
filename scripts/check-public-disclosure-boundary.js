#!/usr/bin/env node

import { readdir, readFile } from 'node:fs/promises';
import { extname, join, relative, resolve } from 'node:path';

const ROOT = resolve('public');
const LEGAL_EXCEPTIONS = new Set([
  'legal.html',
  'privacy.html',
  'terms.html',
  'cgv.html'
]);

const THREAT_SOURCE_PATTERNS = [
  /MalwareBazaar/iu,
  /ThreatFox/iu,
  /URLhaus/iu,
  /VirusShare/iu,
  /abuse\.ch/iu,
  /CISA\s+KEV/iu,
  /\bNVD\b/iu,
  /\bOSV(?:\.dev)?\b/iu,
  /MITRE\s+ATT&CK/iu,
  /Malpedia/iu,
  /Feodo/iu,
  /GitHub Security Advisories/iu
];

const INTERNAL_OPERATION_PATTERNS = [
  /sentinel-moteur-api\.onrender\.com/iu,
  /\bRedis\b/iu,
  /\bUpstash\b/iu,
  /\bEd25519\b/iu,
  /snapshot_hash/iu,
  /signature_policy/iu,
  /ed25519_signed/iu,
  /osv_query_count/iu,
  /osv_result_count/iu,
  /collection_evidence/iu,
  /github\.com\/teetee971\/SentinelQuantumVanguardAiPro\/pull\//iu,
  /github\.com\/teetee971\/SentinelQuantumVanguardAiPro\/commit\//iu
];

async function walk(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path));
    else if (['.html', '.js', '.json', '.md'].includes(extname(entry.name))) files.push(path);
  }
  return files;
}

export function auditPublicDisclosure(path, content) {
  const name = relative(ROOT, path).replaceAll('\\', '/');
  const errors = [];

  for (const pattern of THREAT_SOURCE_PATTERNS) {
    if (pattern.test(content)) {
      errors.push(`${name}: threat-intelligence source identity exposed (${pattern})`);
    }
  }

  if (!LEGAL_EXCEPTIONS.has(name)) {
    for (const pattern of INTERNAL_OPERATION_PATTERNS) {
      if (pattern.test(content)) {
        errors.push(`${name}: internal operational detail exposed (${pattern})`);
      }
    }
  }

  return errors;
}

export async function main() {
  const errors = [];
  for (const path of await walk(ROOT)) {
    const content = await readFile(path, 'utf8');
    errors.push(...auditPublicDisclosure(path, content));
  }

  if (errors.length) {
    console.error('PUBLIC_DISCLOSURE_BOUNDARY_FAILED');
    for (const error of errors) console.error('- ' + error);
    process.exitCode = 1;
    return;
  }

  console.log('Public disclosure boundary: OK');
}

if (process.argv[1] && import.meta.url === new URL('file://' + resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
