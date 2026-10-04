#!/usr/bin/env node
import { spawnSync } from 'node:child_process';

const npmCommand = process.platform === 'win32' ? 'npm.cmd' : 'npm';

// Release gate: product truth and deterministic repository controls first, then the
// production web build. Android signing/artifact verification remains a separate gate because
// this script cannot prove possession of signing credentials or the provenance of an APK.
const checks = [
  ['product-capability-truth', process.execPath, ['scripts/check-product-capabilities.js']],
  ['product-capability-tests', process.execPath, ['--test', 'scripts/check-product-capabilities.test.js']],
  ['product-capability-doc', process.execPath, ['scripts/render-product-capabilities.js', '--check']],
  ['proprietary-boundary', process.execPath, ['scripts/check-proprietary-boundary.js']],
  ['proprietary-boundary-tests', process.execPath, ['--test', 'scripts/check-proprietary-boundary.test.js']],
  ['sentinel-api-origin-contract', process.execPath, ['scripts/check-sentinel-api-origin-contract.js']],
  ['sentinel-api-origin-contract-tests', process.execPath, ['--test', 'scripts/check-sentinel-api-origin-contract.test.js']],
  ['isolation', npmCommand, ['run', 'test:isolation']],
  ['static-links', npmCommand, ['run', 'test:static-links']],
  ['client-security', npmCommand, ['run', 'test:client-security']],
  ['public-claims', npmCommand, ['run', 'test:public-claims']],
  ['android-manifest', npmCommand, ['run', 'test:android-manifest']],
  ['action-pinning', npmCommand, ['run', 'test:ci-supply-chain']],
  ['security-governance', npmCommand, ['run', 'test:security-governance']],
  ['production-build', npmCommand, ['run', 'build']],
];

let failed = false;
for (const [name, command, args] of checks) {
  console.log(`\n=== ${name} ===`);
  const result = spawnSync(command, args, {
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
