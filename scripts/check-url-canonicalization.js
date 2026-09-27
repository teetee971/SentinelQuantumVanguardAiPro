import fs from 'node:fs';
import path from 'node:path';

const roots = ['public', 'security', 'decision-plane', 'network'];
const sourceExtensions = new Set(['.js', '.mjs', '.cjs', '.ts', '.tsx']);
const errors = [];

function walk(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const p = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(p) : [p];
  });
}

for (const root of roots) {
  for (const file of walk(root)) {
    if (!sourceExtensions.has(path.extname(file))) continue;
    const src = fs.readFileSync(file, 'utf8');
    // Security decisions must not be made against a raw URL before canonicalization.
    // Flag code that inspects percent-encoded/raw URL material next to authorization
    // or routing decisions without using URL/URLSearchParams/decodeURIComponent.
    const securityDecision = /(?:authorize|allowlist|denylist|route|pathname|path|permission|access|auth)/i.test(src);
    const rawUrlUse = /(?:req\.url|request\.url|originalUrl|rawUrl|%[0-9a-f]{2})/i.test(src);
    const canonicalizes = /(?:new URL\s*\(|URLSearchParams\s*\(|decodeURIComponent\s*\()/i.test(src);
    if (securityDecision && rawUrlUse && !canonicalizes) {
      errors.push(`${file}: security-sensitive URL handling has no explicit canonicalization primitive`);
    }
  }
}

if (errors.length) {
  for (const error of errors) console.error(`::error::${error}`);
  process.exit(1);
}
console.log('URL canonicalization gate passed.');
