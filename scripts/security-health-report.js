#!/usr/bin/env node
/**
 * Evidence-oriented security health report.
 *
 * This report deliberately separates source presence from execution evidence.
 * It never turns the existence of a file into a claim that a control passed.
 */

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(new URL('..', import.meta.url).pathname);

const controls = [
  ['Sentinel / A KI PRI SA YÉ isolation', 'scripts/check-sentinel-isolation.js'],
  ['Static local target validation', 'scripts/check-static-links.js'],
  ['Public claim validation', 'scripts/check-public-claims.js'],
  ['Client-side security gate', 'scripts/check-client-security.js'],
  ['GitHub Actions pinning validation', 'scripts/check-github-actions-pinning.js'],
  ['Android manifest security gate', 'scripts/check-android-manifest.js'],
  ['Security release gate', 'scripts/security-release-gate.js'],
  ['Decision-plane action verification tests', 'decision-plane/action-verification/action-plan.test.js'],
  ['Security fuzzing suite', 'security/fuzz/governance-fuzz.js'],
  ['Android application manifest', 'native-android-app/app/src/main/AndroidManifest.xml'],
  ['Cloudflare Pages headers', '_headers'],
  ['Feature evidence inventory', 'docs/FEATURE_INVENTORY.md'],
];

const packageJson = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8'));
const results = controls.map(([name, file]) => ({
  control: name,
  source: file,
  source_present: existsSync(resolve(root, file)),
  execution_status: 'NOT_OBSERVED',
}));

const report = {
  generated_at: new Date().toISOString(),
  repository: 'teetee971/SentinelQuantumVanguardAiPro',
  version: packageJson.version,
  interpretation: {
    source_present: 'The referenced control exists in the repository.',
    NOT_OBSERVED: 'This report does not claim that the control executed or passed.',
    rule: 'Only observed test/build/deployment evidence may change execution_status.',
  },
  controls: results,
};

const missing = results.filter(item => !item.source_present);
console.log(JSON.stringify(report, null, 2));

if (missing.length) {
  console.error(`Security health report: ${missing.length} referenced control(s) missing.`);
  process.exit(1);
}

console.error(`Security health report: ${results.length} controls inventoried; execution evidence intentionally not inferred.`);
