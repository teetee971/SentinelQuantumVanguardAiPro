#!/usr/bin/env node
// Source-only regression guard. Passing does not establish APK or screenshot conformity.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const activityPath = path.join(repoRoot,
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt');
const source = fs.readFileSync(activityPath, 'utf8');

for (const marker of [
  'SentinelD1.Background',
  'SentinelD1.Panel',
  'SentinelD1.Cyan',
  'SentinelVisualTokens.TextPrimary',
  'SentinelTopBar(',
  'PhoneCoreBrand(',
  'PhoneCoreSetupWizardStore',
  'RoleManager.ROLE_SMS',
  'RoleManager.ROLE_DIALER',
  'prérequis',
]) {
  assert.ok(source.includes(marker), 'D1 activation contract missing: ' + marker);
}
assert.doesNotMatch(source, /Color\(0x[0-9a-fA-F]{8}\)/,
  'Use shared D1 tokens instead of hardcoded ARGB colors');
assert.match(source, /stateDescription\s*=\s*"\$\{setupChecks\.count/,
  'Activation prerequisites must retain Android-derived accessibility status');
for (const [marker, purpose] of [
  ['private fun CapabilityCard(', 'capability card component'],
  ['CardDefaults.cardColors(containerColor = SentinelD1.Panel)', 'D1 capability panel'],
  ['val statusColor = if (ready) SentinelD1.Success else SentinelD1.Unknown', 'runtime status mapping'],
  ['stateDescription = if (ready)', 'status accessibility'],
]) {
  assert.ok(source.includes(marker), 'D1 activation missing ' + purpose);
}
console.log('D1 activation source contract PASS (static only; APK and visual tests pending)');
