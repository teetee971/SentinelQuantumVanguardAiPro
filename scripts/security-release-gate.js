#!/usr/bin/env node
import { spawnSync } from 'node:child_process';

const checks = [
  ['isolation', ['run', 'test:isolation']],
  ['static-links', ['run', 'test:static-links']],
  ['public-claims', ['run', 'test:public-claims']],
  ['android-manifest', ['run', 'test:android-manifest']],
  ['action-pinning', ['run', 'test:ci-supply-chain']],
  ['security-governance', ['run', 'test:security-governance']],
];

let failed = false;
for (const [name, args] of checks) {
  console.log(`\n=== ${name} ===`);
  const result = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', args, {
    stdio: 'inherit',
    shell: false,
    env: { ...process.env, CI: '1' },
  });
  if (result.status !== 0) {
    failed = true;
    console.error(`FAILED: ${name}`);
    break;
  }
}

if (failed) {
  console.error('\nSECURITY RELEASE GATE: BLOCKED');
  process.exit(1);
}

console.log('\nSECURITY RELEASE GATE: PASS');
console.log('This gate proves only the checks executed locally/CI; it does not prove deployment safety or absence of unknown vulnerabilities.');
