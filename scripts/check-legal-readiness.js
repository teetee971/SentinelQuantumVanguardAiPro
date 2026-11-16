#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PLACEHOLDER = /(?:\btodo\b|\btbd\b|\bunknown\b|\bplaceholder\b|example\.(?:com|org|net)|your[- _]|à compléter|a completer|inconnu)/i;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function requiredString(value, field, errors, { max = 500, allowUrl = false } = {}) {
  if (typeof value !== 'string' || !value.trim()) {
    errors.push(`${field}: missing`);
    return null;
  }
  const clean = value.trim();
  if (clean.length > max) errors.push(`${field}: too long`);
  if (PLACEHOLDER.test(clean)) errors.push(`${field}: placeholder value rejected`);
  if (!allowUrl && /^https?:\/\//i.test(clean)) errors.push(`${field}: expected text, not URL`);
  return clean;
}

export function validateLegalManifest(input) {
  const errors = [];
  if (!input || typeof input !== 'object' || Array.isArray(input)) {
    return { ok: false, errors: ['manifest: expected object'] };
  }
  if (input.schema_version !== 1) errors.push('schema_version: expected 1');

  const publisher = input.publisher;
  if (!publisher || typeof publisher !== 'object' || Array.isArray(publisher)) {
    errors.push('publisher: missing object');
  } else {
    requiredString(publisher.legal_name, 'publisher.legal_name', errors, { max: 200 });
    requiredString(publisher.publication_director, 'publisher.publication_director', errors, { max: 200 });
    const email = requiredString(publisher.contact_email, 'publisher.contact_email', errors, { max: 254 });
    if (email && !EMAIL.test(email)) errors.push('publisher.contact_email: invalid email');
    requiredString(publisher.contact_address, 'publisher.contact_address', errors, { max: 500 });
    requiredString(publisher.country, 'publisher.country', errors, { max: 100 });
  }

  const hosting = input.hosting;
  if (!hosting || typeof hosting !== 'object' || Array.isArray(hosting)) {
    errors.push('hosting: missing object');
  } else {
    requiredString(hosting.provider, 'hosting.provider', errors, { max: 200 });
    const url = requiredString(hosting.public_information_url, 'hosting.public_information_url', errors, { max: 500, allowUrl: true });
    if (url) {
      try {
        const parsed = new URL(url);
        if (parsed.protocol !== 'https:') errors.push('hosting.public_information_url: HTTPS required');
      } catch {
        errors.push('hosting.public_information_url: invalid URL');
      }
    }
  }

  const reviewedAt = requiredString(input.reviewed_at, 'reviewed_at', errors, { max: 64 });
  if (reviewedAt && Number.isNaN(Date.parse(reviewedAt))) errors.push('reviewed_at: invalid timestamp');
  requiredString(input.reviewed_by, 'reviewed_by', errors, { max: 200 });

  return { ok: errors.length === 0, errors };
}

export function readAndValidateLegalManifest(manifestPath) {
  const absolute = path.resolve(manifestPath);
  let parsed;
  try {
    parsed = JSON.parse(fs.readFileSync(absolute, 'utf8'));
  } catch (error) {
    return { ok: false, errors: [`manifest: unreadable or invalid JSON (${error.code || error.name})`] };
  }
  return validateLegalManifest(parsed);
}

function main(argv) {
  const manifestPath = argv[2];
  if (!manifestPath) {
    console.error('Usage: node scripts/check-legal-readiness.js <verified-legal-manifest.json>');
    process.exitCode = 2;
    return;
  }
  const result = readAndValidateLegalManifest(manifestPath);
  if (!result.ok) {
    console.error('LEGAL_RELEASE_READINESS=FAILED');
    for (const error of result.errors) console.error(`- ${error}`);
    process.exitCode = 1;
    return;
  }
  console.log('LEGAL_RELEASE_READINESS=PASSED');
}

const isCli = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1]);
if (isCli) main(process.argv);
