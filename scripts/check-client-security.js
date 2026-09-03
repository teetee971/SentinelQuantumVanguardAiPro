#!/usr/bin/env node
/**
 * Conservative client-side security gate.
 * Fails only on high-signal patterns that should not exist in the public app.
 * This is a static check; it does not prove runtime safety.
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const TARGETS = [path.join(ROOT, 'public'), path.join(ROOT, 'index.html')];
const EXTENSIONS = new Set(['.html', '.js', '.mjs', '.css']);
const MAX_FILE_BYTES = 2 * 1024 * 1024;

const rules = [
  { id: 'dynamic-code', re: /\b(?:eval|Function)\s*\(/, message: 'dynamic code execution (eval/Function)' },
  { id: 'javascript-url', re: /\b(?:href|src)\s*=\s*["']\s*javascript:/i, message: 'javascript: URL' },
  { id: 'document-write', re: /\bdocument\.write(?:ln)?\s*\(/, message: 'document.write' },
  { id: 'credentials', re: /(?:-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,})/, message: 'credential-like material' }
];

function collectFiles(target) {
  if (!fs.existsSync(target)) return [];
  const stat = fs.statSync(target);
  if (stat.isFile()) return EXTENSIONS.has(path.extname(target).toLowerCase()) ? [target] : [];
  const result = [];
  for (const entry of fs.readdirSync(target, { withFileTypes: true })) {
    const full = path.join(target, entry.name);
    if (entry.isDirectory()) result.push(...collectFiles(full));
    else if (EXTENSIONS.has(path.extname(entry.name).toLowerCase())) result.push(full);
  }
  return result;
}

const files = [...new Set(TARGETS.flatMap(collectFiles))];
const findings = [];

for (const file of files) {
  const stat = fs.statSync(file);
  if (stat.size > MAX_FILE_BYTES) continue;
  const text = fs.readFileSync(file, 'utf8');
  const lines = text.split(/\r?\n/);
  lines.forEach((line, index) => {
    for (const rule of rules) {
      if (rule.re.test(line)) {
        findings.push(`${path.relative(ROOT, file)}:${index + 1} [${rule.id}] ${rule.message}`);
      }
    }
  });
}

if (findings.length) {
  console.error('Client security gate: FAILED');
  findings.forEach(f => console.error(`- ${f}`));
  process.exit(1);
}

console.log(`Client security gate: PASS (${files.length} files scanned)`);
