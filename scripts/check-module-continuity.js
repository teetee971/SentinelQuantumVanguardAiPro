#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SKIP_DIRS = new Set(['.git', 'node_modules', 'dist', 'build', '.gradle']);
const JS_EXTENSIONS = new Set(['.js', '.mjs', '.cjs']);

function collectJsFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && SKIP_DIRS.has(entry.name)) continue;
    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...collectJsFiles(fullPath));
    else if (JS_EXTENSIONS.has(extname(entry.name).toLowerCase())) files.push(fullPath);
  }
  return files;
}

export function resolveLocalModule(sourceFile, specifier) {
  if (typeof specifier !== 'string' || (!specifier.startsWith('./') && !specifier.startsWith('../'))) {
    return null;
  }
  const base = resolve(dirname(sourceFile), specifier);
  const candidates = [
    base,
    `${base}.js`,
    `${base}.mjs`,
    `${base}.cjs`,
    `${base}.json`,
    join(base, 'index.js'),
    join(base, 'index.mjs'),
  ];
  for (const candidate of candidates) {
    if (existsSync(candidate) && statSync(candidate).isFile()) return candidate;
  }
  return null;
}

export function extractLocalSpecifiers(source) {
  const found = new Set();
  const patterns = [
    /\b(?:import|export)\s+(?:[^'";]*?\s+from\s+)?['"]([^'"]+)['"]/g,
    /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
    /\brequire\s*\(\s*['"]([^'"]+)['"]\s*\)/g,
  ];
  for (const pattern of patterns) {
    let match;
    while ((match = pattern.exec(source)) !== null) {
      if (match[1].startsWith('./') || match[1].startsWith('../')) found.add(match[1]);
    }
  }
  return [...found];
}

export function validateModuleContinuity(baseDir = rootDir) {
  const errors = [];
  const files = collectJsFiles(baseDir);
  let edges = 0;
  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const specifier of extractLocalSpecifiers(source)) {
      edges += 1;
      if (!resolveLocalModule(file, specifier)) {
        errors.push(`${file.replace(`${baseDir}/`, '')} -> ${specifier}`);
      }
    }
  }
  return { files: files.length, edges, errors };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = validateModuleContinuity();
  if (result.errors.length) {
    console.error('Broken local module references detected:');
    for (const error of result.errors) console.error(`- ${error}`);
    process.exit(1);
  }
  console.log(`Module continuity valid (${result.files} JS modules, ${result.edges} local dependency edges checked).`);
}
