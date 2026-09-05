#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractLocalSpecifiers, resolveLocalModule } from './check-module-continuity.js';

export const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SKIP_DIRS = new Set(['.git', 'node_modules', 'dist', 'build', '.gradle']);
const JS_EXTENSIONS = new Set(['.js', '.mjs', '.cjs']);
const TEST_FILE = /(?:^|\/)[^/]+\.(?:test|spec)\.(?:js|mjs|cjs)$/;

function collectFiles(directory, predicate) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && SKIP_DIRS.has(entry.name)) continue;
    const full = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...collectFiles(full, predicate));
    else if (predicate(full)) files.push(full);
  }
  return files;
}

function normalizeRel(baseDir, file) {
  return relative(baseDir, file).replaceAll('\\', '/');
}

function loadEntrypointCorpus(baseDir) {
  const files = [];
  const packageJson = join(baseDir, 'package.json');
  if (existsSync(packageJson)) files.push(packageJson);

  const workflowDir = join(baseDir, '.github', 'workflows');
  if (existsSync(workflowDir) && statSync(workflowDir).isDirectory()) {
    files.push(...collectFiles(workflowDir, (file) => /\.ya?ml$/i.test(file)));
  }

  files.push(...collectFiles(baseDir, (file) => extname(file).toLowerCase() === '.html'));
  return files;
}

export function classifyModuleUsage(baseDir = rootDir) {
  const modules = collectFiles(baseDir, (file) => JS_EXTENSIONS.has(extname(file).toLowerCase()))
    .filter((file) => !TEST_FILE.test(normalizeRel(baseDir, file)));
  const moduleSet = new Set(modules.map((file) => resolve(file)));
  const inbound = new Map(modules.map((file) => [resolve(file), 0]));

  for (const sourceFile of modules) {
    const source = readFileSync(sourceFile, 'utf8');
    for (const specifier of extractLocalSpecifiers(source)) {
      const target = resolveLocalModule(sourceFile, specifier);
      if (target && moduleSet.has(resolve(target))) {
        inbound.set(resolve(target), (inbound.get(resolve(target)) ?? 0) + 1);
      }
    }
  }

  const corpus = loadEntrypointCorpus(baseDir)
    .map((file) => {
      try { return readFileSync(file, 'utf8'); }
      catch { return ''; }
    })
    .join('\n');

  const records = modules.map((file) => {
    const rel = normalizeRel(baseDir, file);
    const basename = rel.split('/').pop();
    const entrypoint = corpus.includes(rel)
      || corpus.includes(`./${rel}`)
      || corpus.includes(`node ${rel}`)
      || corpus.includes(`node ./${rel}`)
      || corpus.includes(`/${rel}`)
      || corpus.includes(basename);
    const inboundCount = inbound.get(resolve(file)) ?? 0;
    const classification = entrypoint ? 'ENTRYPOINT'
      : inboundCount > 0 ? 'IMPORTED'
        : 'ORPHAN_CANDIDATE';
    return { path: rel, inbound: inboundCount, entrypoint, classification };
  });

  return records.sort((a, b) => a.path.localeCompare(b.path));
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const records = classifyModuleUsage();
  const counts = records.reduce((acc, record) => {
    acc[record.classification] = (acc[record.classification] ?? 0) + 1;
    return acc;
  }, {});

  console.log('MODULE USAGE CLASSIFICATION (non-destructive)');
  for (const record of records.filter((record) => record.classification === 'ORPHAN_CANDIDATE')) {
    console.log(`ORPHAN_CANDIDATE ${record.path}`);
  }
  console.log(`Modules: ${records.length}`);
  console.log(`Entrypoints: ${counts.ENTRYPOINT ?? 0}`);
  console.log(`Imported: ${counts.IMPORTED ?? 0}`);
  console.log(`Orphan candidates: ${counts.ORPHAN_CANDIDATE ?? 0}`);
  console.log('Candidates require manual review before any deletion; generated tools, CLI entrypoints, test helpers, and security fixtures can be intentionally unreferenced.');
}
