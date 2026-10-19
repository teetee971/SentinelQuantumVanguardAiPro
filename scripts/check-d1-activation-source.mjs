#!/usr/bin/env node
// Static visual/behavioral regression guard; never claims screenshot or APK qualification.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const path = 'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt';
const source = fs.readFileSync(path, 'utf8');
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
  'pré requis'.replace(' ', ''),
]) {
  assert.ok(source.includes(marker), 'D1 activation contract missing: ' + marker);
}
assert.doesNotMatch(source, /Color\\(0x[0-9a-fA-F]{8}\\)/, 'Use shared D1 color tokens, not hardcoded ARGB colors');
console.log('D1 activation source contract PASS (static; not compiled or visually qualified)');
