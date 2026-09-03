#!/usr/bin/env node
import { spawnSync } from 'node:child_process';

// Release gate: deterministic repository controls first, then the production web build.
// Android signing/artifact verification remains a separate gate because this script
// cannot prove possession of signing credentials or the provenance of an APK.
const checks = [
  ['isolation', ['run', 'test:isolation']],
  ['static-links', ['run', 'test:static-links']],
  ['client-security', ['run', 'test:client-security']],
  ['public-claims', ['run', 'test:public-claims']],
  ['android-manifest', ['run', 'test:android-manifest']],
  ['action-pinning', ['run', 'test:ci-supply-chain']],
  ['security-governance', ['run', 'test:security-governance']],
  ['production-build', ['run', 'build']],
];

let failed = false;
for (const [name, args] of checks) {
  console.log(`\n=== ${name} ===`);
  const result = spawnSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', args, {
    stdio: 'inherit',
    shell: false,
    env: { ...process.env, CI: '1' },
  });

  if (result.error) {
    failed = true;
    console.error(`FAILED: ${name}: ${result.error.message}`);
    break;
  }

  if (result.status !== 0) {
    failed = true;
    console.error(`FAILED: ${name} (exit ${result.status ?? 'unknown'})`);
    break;
  }
}

if (failed) {
  console.error('\nSECURITY RELEASE GATE: BLOCKED');
  process.exit(1);
}

console.log('\nSECURITY RELEASE GATE: PASS');
console.log('Validated controls were executed successfully. This does not prove absence of unknown vulnerabilities, deployment safety, Android signing, artifact provenance, or runtime security.');
