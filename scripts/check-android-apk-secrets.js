import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const apk = process.argv[2];
if (!apk || !fs.existsSync(apk)) {
  console.error('Usage: node scripts/check-android-apk-secrets.js <apk>');
  process.exit(2);
}

let strings;
try {
  strings = execFileSync('strings', ['-a', apk], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
} catch (error) {
  console.error('Unable to inspect APK strings:', error.message);
  process.exit(2);
}

const rules = [
  ['PEM private key', /-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----/],
  ['GitHub token', /gh[pousr]_[A-Za-z0-9_]{20,}/],
  ['Slack token', /xox[baprs]-[A-Za-z0-9-]{10,}/],
  ['AWS access key', /AKIA[0-9A-Z]{16}/],
  ['Google API key', /AIza[0-9A-Za-z_-]{35}/],
  ['Stripe live secret', /sk_live_[0-9A-Za-z]{16,}/],
  ['generic bearer JWT', /eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}/]
];

const hits = [];
for (const [name, pattern] of rules) {
  if (pattern.test(strings)) hits.push(name);
}

if (hits.length) {
  for (const hit of hits) console.error(`::error::Potential packaged secret detected: ${hit}`);
  process.exit(1);
}
console.log('APK secret gate passed: no recognized static credentials/private keys found.');
