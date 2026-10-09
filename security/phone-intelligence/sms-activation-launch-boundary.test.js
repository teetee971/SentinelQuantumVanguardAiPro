import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const compose = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

test('SMS activation role launch failures remain visible and retryable', () => {
  assert.match(
    compose,
    /fun launchSmsRoleActivation\(\)[\s\S]*?roleRequestIntent\(\)[\s\S]*?legacyDefaultAppsIntent\(\)[\s\S]*?roleLauncher\.launch\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'the SMS role chooser must share a framework launch boundary'
  );
  assert.match(
    compose,
    /reportActivationFailure\([\s\S]*Android n’a pas pu ouvrir le sélecteur SMS/,
    'a failed SMS role launch must remain visible in the compose screen'
  );
  assert.equal(
    (compose.match(/roleLauncher\.launch\(/g) ?? []).length,
    1,
    'the role launcher must only be called by the guarded helper'
  );
});

test('SMS permission launch failures do not become silent activation refreshes', () => {
  assert.match(
    compose,
    /fun launchSmsPermissions\(permissions: Array<String>, failureMessage: String\)[\s\S]*?permissionLauncher\.launch\(permissions\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'permission requests must expose framework launch failures'
  );
  assert.equal(
    (compose.match(/permissionLauncher\.launch\(/g) ?? []).length,
    1,
    'the permission launcher must only be called by the guarded helper'
  );
  assert.match(
    compose,
    /onClick = \{ launchSmsRoleActivation\(\) \}/,
    'the role button must use the guarded helper'
  );
  assert.match(
    compose,
    /launchSmsPermissions\(\s*permissions\s*,/,
    'permission buttons must use the guarded helper'
  );
});
