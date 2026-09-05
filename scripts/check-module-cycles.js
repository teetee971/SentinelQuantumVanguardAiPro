#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractLocalSpecifiers, resolveLocalModule } from './check-module-continuity.js';

export const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SKIP_DIRS = new Set(['.git', 'node_modules', 'dist', 'build', '.gradle']);
const JS_EXTENSIONS = new Set(['.js', '.mjs', '.cjs']);
const TEST_FILE = /(?:^|\/)[^/]+\.(?:test|spec)\.(?:js|mjs|cjs)$/;

function normalizeRel(baseDir, file) {
  return relative(baseDir, file).replaceAll('\\', '/');
}

function collectModules(directory, baseDir) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory() && SKIP_DIRS.has(entry.name)) continue;
    const full = resolve(directory, entry.name);
    if (entry.isDirectory()) files.push(...collectModules(full, baseDir));
    else if (JS_EXTENSIONS.has(extname(entry.name).toLowerCase()) && !TEST_FILE.test(normalizeRel(baseDir, full))) {
      files.push(full);
    }
  }
  return files;
}

export function buildLocalModuleGraph(baseDir = rootDir) {
  if (!existsSync(baseDir)) return new Map();
  const modules = collectModules(baseDir, baseDir).map((file) => resolve(file));
  const moduleSet = new Set(modules);
  const graph = new Map(modules.map((file) => [file, []]));

  for (const sourceFile of modules) {
    const source = readFileSync(sourceFile, 'utf8');
    const targets = new Set();
    for (const specifier of extractLocalSpecifiers(source)) {
      const target = resolveLocalModule(sourceFile, specifier);
      if (target && moduleSet.has(resolve(target))) targets.add(resolve(target));
    }
    graph.set(sourceFile, [...targets].sort());
  }

  return graph;
}

export function detectModuleCycles(graph) {
  const state = new Map();
  const stack = [];
  const stackIndex = new Map();
  const cycles = new Set();

  function canonicalizeCycle(nodes) {
    const ring = nodes.slice(0, -1);
    const variants = [];
    for (let i = 0; i < ring.length; i += 1) {
      const rotated = [...ring.slice(i), ...ring.slice(0, i)];
      variants.push([...rotated, rotated[0]].join(' -> '));
    }
    return variants.sort()[0];
  }

  function visit(node) {
    state.set(node, 1);
    stackIndex.set(node, stack.length);
    stack.push(node);

    for (const target of graph.get(node) ?? []) {
      const targetState = state.get(target) ?? 0;
      if (targetState === 0) visit(target);
      else if (targetState === 1) {
        const start = stackIndex.get(target);
        if (start !== undefined) cycles.add(canonicalizeCycle([...stack.slice(start), target]));
      }
    }

    stack.pop();
    stackIndex.delete(node);
    state.set(node, 2);
  }

  for (const node of [...graph.keys()].sort()) {
    if ((state.get(node) ?? 0) === 0) visit(node);
  }

  return [...cycles].sort();
}

export function validateModuleCycles(baseDir = rootDir) {
  const graph = buildLocalModuleGraph(baseDir);
  const rawCycles = detectModuleCycles(graph);
  const cycles = rawCycles.map((cycle) => cycle
    .split(' -> ')
    .map((file) => normalizeRel(baseDir, file))
    .join(' -> '));
  return { modules: graph.size, cycles };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = validateModuleCycles();
  if (result.cycles.length) {
    console.error('Circular local module dependencies detected:');
    for (const cycle of result.cycles) console.error(`- ${cycle}`);
    process.exit(1);
  }
  console.log(`Module cycle validation passed (${result.modules} production JS modules, 0 cycles).`);
}
