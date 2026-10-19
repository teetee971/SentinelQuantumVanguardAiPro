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
for (const [legacy, reason] of [
  ['ElevatedCard(', 'old Material elevated cards'],
  ['Card(Modifier.fillMaxWidth())', 'unstyled full-width cards'],
  ['MaterialTheme.colorScheme.errorContainer', 'legacy error container'],
]) {
  assert.ok(!source.includes(legacy), 'D1 activation regressed: ' + reason);
}
for (const [marker, reason] of [
  ['SentinelD1.Danger.copy(alpha = 0.65f)', 'permission-denial alert border'],
  ['SmsActivationUiModel.Action.REQUEST_SMS_ROLE', 'SMS role action'],
  ['CallScreeningActivationPolicy.State.AVAILABLE_NOT_HELD', 'call screening state gate'],
]) {
  assert.ok(source.includes(marker), 'D1 activation missing: ' + reason);
}
for (const [marker, reason] of [
  ['LinearProgressIndicator(', 'visible setup progress'],
  ['setupChecks.count { it.second }.toFloat() / setupChecks.size', 'progress linked to Android-confirmed prerequisites'],
  ['if (setupChecks.isEmpty()) 0f', 'zero-denominator protection'],
  ['trackColor = SentinelD1.Card', 'D1 progress track'],
]) {
  assert.ok(source.includes(marker), 'D1 activation missing: ' + reason);
}
// Isolated APK must exit to a read-only diagnostic before any role/permission UI.
const isolatedGuard = source.indexOf('if (packageName == "com.sentinel.quantum.d1test") {');
const normalPhoneSetup = source.indexOf('SentinelCallNotificationHelper.ensureChannel(applicationContext)');
assert.ok(isolatedGuard >= 0 && normalPhoneSetup > isolatedGuard,
  'Isolated D1 guard must precede normal Phone Core initialization');
const isolatedBranch = source.slice(isolatedGuard, normalPhoneSetup);
for (const expected of ['setContent {', 'return', 'Phone Core non activable dans cette version de test']) {
  assert.ok(isolatedBranch.includes(expected), 'D1 isolated read-only contract missing: ' + expected);
}
for (const forbidden of ['roleLauncher.launch', 'permissionsLauncher.launch', 'setupPermissionLauncher.launch',
  'createRequestRoleIntent(', 'requestPermissions(']) {
  assert.ok(!isolatedBranch.includes(forbidden),
    'Isolated D1 path must not request protected Android roles/permissions: ' + forbidden);
}
console.log('D1 isolated Phone Core guard PASS (static only; runtime not verified)');
console.log('D1 activation source contract PASS (static only; APK and visual tests pending)');
