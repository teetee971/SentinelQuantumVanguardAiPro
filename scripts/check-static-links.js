#!/usr/bin/env node

/**
 * Validate local href/src targets and obvious local file references on the
 * static web surface. External URLs, data/blob URLs and fragments are ignored.
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const errors = [];

function collectFiles(directory, extensions) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue;
    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...collectFiles(fullPath, extensions));
    else if (extensions.has(extname(entry.name).toLowerCase())) files.push(fullPath);
  }
  return files;
}

export function cleanTarget(rawTarget) {
  return rawTarget.trim().split('#', 1)[0].split('?', 1)[0];
}

export function targetToSourcePath(sourceFile, target) {
  const cleaned = cleanTarget(target);
  if (!cleaned) return null;
  if (cleaned.startsWith('/')) return resolve(rootDir, `.${cleaned}`);
  return resolve(dirname(sourceFile), normalize(cleaned));
}

export function existsAsWebTarget(sourcePath) {
  if (existsSync(sourcePath) && statSync(sourcePath).isFile()) return true;
  if (existsSync(sourcePath) && statSync(sourcePath).isDirectory()) {
    return existsSync(join(sourcePath, 'index.html'));
  }
  if (!extname(sourcePath)) return existsSync(join(sourcePath, 'index.html'));
  return false;
}

function isExternalTarget(target) {
  return target === '#'
    || target.startsWith('#')
    || target.startsWith('//')
    || /^(?:https?:|mailto:|tel:|javascript:|data:|blob:)/i.test(target);
}

function validateTarget(sourceFile, target) {
  if (isExternalTarget(target)) return;
  const sourcePath = targetToSourcePath(sourceFile, target);
  if (sourcePath && !existsAsWebTarget(sourcePath)) {
    errors.push(`${sourceFile.replace(rootDir + '/', '')} -> ${target}`);
  }
}

function validateHtmlFile(file) {
  const content = readFileSync(file, 'utf8');
  const attributePattern = /\b(?:href|src)\s*=\s*["']([^"']+)["']/gi;
  let match;
  while ((match = attributePattern.exec(content)) !== null) validateTarget(file, match[1].trim());
}

function validateJavaScriptFile(file) {
  const content = readFileSync(file, 'utf8');
  // Only inspect obvious local file-like strings. This intentionally avoids
  // treating arbitrary application data as a path.
  const stringPattern = /["'`]((?:\/{1,2}(?:public|assets|config|scripts|frontend)\/|\.\.?\/)[^"'`\s]+\.(?:html|css|js|json|svg|png|webp|jpg|jpeg|ico))(?:["'`])/gi;
  let match;
  while ((match = stringPattern.exec(content)) !== null) validateTarget(file, match[1]);
}

const htmlFiles = collectFiles(rootDir, new Set(['.html']));
const javascriptFiles = collectFiles(rootDir, new Set(['.js']));

for (const file of htmlFiles) validateHtmlFile(file);
for (const file of javascriptFiles) validateJavaScriptFile(file);

if (errors.length > 0) {
  console.error('Broken local web targets detected:');
  for (const error of errors) console.error(`- ${error}`);
  process.exit(1);
}

console.log(`Static local links and obvious JavaScript asset references are valid (${htmlFiles.length} HTML, ${javascriptFiles.length} JS scanned).`);
