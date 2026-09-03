#!/usr/bin/env node
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, extname } from 'node:path';

const ROOT = process.cwd();
const SKIP = new Set(['.git', 'node_modules', 'frontend/dist', 'dist', 'build']);
const CANDIDATE_EXTENSIONS = new Set(['.html', '.js', '.mjs', '.css', '.json', '.svg']);
const files = [];

function walk(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    const rel = relative(ROOT, full).replaceAll('\\', '/');
    if (SKIP.has(rel) || [...SKIP].some((p) => rel.startsWith(`${p}/`))) continue;
    if (entry.isDirectory()) walk(full);
    else if (CANDIDATE_EXTENSIONS.has(extname(entry.name).toLowerCase())) files.push(rel);
  }
}

walk(ROOT);
const corpus = files.map((file) => {
  try { return [file, readFileSync(join(ROOT, file), 'utf8')]; }
  catch { return [file, '']; }
});

const referenced = new Set();
for (const [file, text] of corpus) {
  for (const target of files) {
    if (target === file) continue;
    const base = target.split('/').pop();
    if (text.includes(target) || text.includes(`/${target}`) || text.includes(`./${base}`) || text.includes(`'${base}'`) || text.includes(`"${base}"`)) {
      referenced.add(target);
    }
  }
}

const entryPoints = new Set(['index.html', 'package.json', 'vite.config.js']);
const candidates = files.filter((file) => !referenced.has(file) && !entryPoints.has(file));

console.log('ORPHAN CANDIDATE REPORT (non-destructive)');
if (!candidates.length) {
  console.log('No candidates found.');
  process.exit(0);
}
for (const file of candidates) console.log(`CANDIDATE ${file}`);
console.log(`Candidates: ${candidates.length}`);
console.log('Review each candidate before deletion; negative isolation/security fixtures may intentionally have no production references.');
