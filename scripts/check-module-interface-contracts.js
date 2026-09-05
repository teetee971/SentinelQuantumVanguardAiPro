#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { resolveLocalModule } from './check-module-continuity.js';

export const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SKIP_DIRS = new Set(['.git', 'node_modules', 'dist', 'build', '.gradle']);
const JS_EXTENSIONS = new Set(['.js', '.mjs', '.cjs']);

function isTestFile(name) {
  return /(?:\.test|\.spec)\.(?:js|mjs|cjs)$/i.test(name);
}

function collectJsFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && SKIP_DIRS.has(entry.name)) continue;
    const fullPath = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...collectJsFiles(fullPath));
    else if (JS_EXTENSIONS.has(extname(entry.name).toLowerCase()) && !isTestFile(entry.name)) files.push(fullPath);
  }
  return files;
}

function cleanSource(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

function parseNamedList(list) {
  return list
    .split(',')
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => part.split(/\s+as\s+/i)[0].trim())
    .filter(Boolean);
}

export function extractExportNames(source) {
  const cleaned = cleanSource(source);
  const names = new Set();
  let hasDefault = /\bexport\s+default\b/.test(cleaned);
  const declaration = /\bexport\s+(?:async\s+)?(?:function|class|const|let|var)\s+([A-Za-z_$][\w$]*)/g;
  let match;
  while ((match = declaration.exec(cleaned)) !== null) names.add(match[1]);

  const exportList = /\bexport\s*{([\s\S]*?)}(?:\s*from\s*['"][^'"]+['"])?\s*;?/g;
  while ((match = exportList.exec(cleaned)) !== null) {
    for (const item of match[1].split(',')) {
      const token = item.trim();
      if (!token) continue;
      const parts = token.split(/\s+as\s+/i).map((value) => value.trim());
      names.add(parts[1] || parts[0]);
    }
  }

  if (/\bmodule\.exports\s*=/.test(cleaned)) hasDefault = true;
  return { names, hasDefault };
}

export function extractLocalImportContracts(source) {
  const cleaned = cleanSource(source);
  const contracts = [];
  let match;

  const imports = /\bimport\s+([\s\S]*?)\s+from\s+['"]([^'"]+)['"]\s*;?/g;
  while ((match = imports.exec(cleaned)) !== null) {
    const clause = match[1].trim();
    const specifier = match[2];
    if (!specifier.startsWith('./') && !specifier.startsWith('../')) continue;
    if (clause.startsWith('*')) continue;

    const namedMatch = clause.match(/{([\s\S]*?)}/);
    const named = namedMatch ? parseNamedList(namedMatch[1]) : [];
    const beforeNamed = clause.split('{')[0].replace(/,$/, '').trim();
    const requiresDefault = beforeNamed.length > 0;
    contracts.push({ specifier, named, requiresDefault, kind: 'import' });
  }

  const reexports = /\bexport\s*{([\s\S]*?)}\s*from\s*['"]([^'"]+)['"]\s*;?/g;
  while ((match = reexports.exec(cleaned)) !== null) {
    const specifier = match[2];
    if (!specifier.startsWith('./') && !specifier.startsWith('../')) continue;
    contracts.push({ specifier, named: parseNamedList(match[1]), requiresDefault: false, kind: 're-export' });
  }

  return contracts;
}

export function validateModuleInterfaceContracts(baseDir = rootDir) {
  const files = collectJsFiles(baseDir);
  const errors = [];
  let contractsChecked = 0;

  for (const file of files) {
    const source = readFileSync(file, 'utf8');
    for (const contract of extractLocalImportContracts(source)) {
      const target = resolveLocalModule(file, contract.specifier);
      if (!target || !existsSync(target) || !JS_EXTENSIONS.has(extname(target).toLowerCase())) continue;
      contractsChecked += 1;
      const exported = extractExportNames(readFileSync(target, 'utf8'));
      const sourceLabel = relative(baseDir, file);
      const targetLabel = relative(baseDir, target);

      if (contract.requiresDefault && !exported.hasDefault) {
        errors.push(`${sourceLabel} imports default from ${targetLabel}, but no default export is declared`);
      }
      for (const name of contract.named) {
        if (!exported.names.has(name)) {
          errors.push(`${sourceLabel} requires named export ${name} from ${targetLabel}, but it is not declared`);
        }
      }
    }
  }

  return { files: files.length, contractsChecked, errors };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = validateModuleInterfaceContracts();
  if (result.errors.length) {
    console.error('Broken local module interface contracts detected:');
    for (const error of result.errors) console.error(`- ${error}`);
    process.exit(1);
  }
  console.log(`Module interface contracts valid (${result.files} JS modules, ${result.contractsChecked} local import/re-export contracts checked).`);
}
