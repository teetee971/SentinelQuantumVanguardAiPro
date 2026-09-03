#!/usr/bin/env node

/**
 * Validate local href/src targets on the static web surface.
 * External URLs, data/blob URLs and fragment-only links are ignored.
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webExtensions = new Set(['.html']);
const errors = [];

function collectHtmlFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue;
    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectHtmlFiles(fullPath));
    } else if (webExtensions.has(extname(entry.name).toLowerCase())) {
      files.push(fullPath);
    }
  }
  return files;
}

function cleanTarget(rawTarget) {
  return rawTarget.trim().split('#', 1)[0].split('?', 1)[0];
}

function targetToSourcePath(sourceFile, target) {
  const cleaned = cleanTarget(target);
  if (!cleaned) return null;

  if (cleaned.startsWith('/')) {
    return resolve(rootDir, `.${cleaned}`);
  }

  return resolve(dirname(sourceFile), normalize(cleaned));
}

function existsAsWebTarget(sourcePath) {
  if (existsSync(sourcePath) && statSync(sourcePath).isFile()) return true;
  if (existsSync(sourcePath) && statSync(sourcePath).isDirectory()) {
    return existsSync(join(sourcePath, 'index.html'));
  }
  if (!extname(sourcePath)) return existsSync(join(sourcePath, 'index.html'));
  return false;
}

for (const file of collectHtmlFiles(rootDir)) {
  const content = readFileSync(file, 'utf8');
  const attributePattern = /\b(?:href|src)\s*=\s*["']([^"']+)["']/gi;
  let match;

  while ((match = attributePattern.exec(content)) !== null) {
    const target = match[1].trim();
    if (
      target === '#' ||
      target.startsWith('#') ||
      target.startsWith('//') ||
      target.startsWith('/') && target.startsWith('//') ||
      /^(?:https?:|mailto:|tel:|javascript:|data:|blob:)/i.test(target)
    ) continue;

    const sourcePath = targetToSourcePath(file, target);
    if (!sourcePath) continue;

    if (!existsAsWebTarget(sourcePath)) {
      errors.push(`${file.replace(rootDir + '/', '')} -> ${target}`);
    }
  }
}

if (errors.length > 0) {
  console.error('Broken local web targets detected:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log('Static local links and asset targets are valid.');
