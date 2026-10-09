import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const activation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'utf8'
);

test('role activation failures remain visible and retryable', () => {
  assert.match(
    activation,
    /var activationActionError by remember \{ mutableStateOf<String\?>\(null\) \}/,
    'the activation center must retain an explicit system-action failure state'
  );
  assert.match(
    activation,
    /fun launchActivationRole\(role: String, failureMessage: String\) \{[\s\S]*?roleIntent\(role\)[\s\S]*?reportActivationActionError\(failureMessage\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'role Intent absence and framework launch failures must be converted into visible UI state'
  );
  assert.match(
    activation,
    /fun launchSmsRoleActivation\(\)[\s\S]*?roleRequestIntent\(\)[\s\S]*?legacyDefaultAppsIntent\(\)[\s\S]*?activationActionError =/,
    'SMS role activation must not silently discard a missing chooser Intent'
  );
});

test('activation cards use the guarded role launch path', () => {
  assert.match(
    activation,
    /!state\.dialerRole -> launchActivationRole\(\s*RoleManager\.ROLE_DIALER,/,
    'the dialer card must report a role request failure'
  );
  assert.match(
    activation,
    /launchActivationRole\(\s*RoleManager\.ROLE_CALL_SCREENING,/,
    'the call-screening card must report a role request failure'
  );
  assert.match(
    activation,
    /onClick = \{ launchSmsRoleActivation\(\) \}/,
    'the SMS card must use the guarded role launch path'
  );
  assert.doesNotMatch(
    activation,
    /roleIntent\(RoleManager\.ROLE_DIALER\)\?\.let\(roleLauncher::launch\)/,
    'the dialer card must not silently ignore a null role Intent'
  );
  assert.doesNotMatch(
    activation,
    /roleIntent\(RoleManager\.ROLE_CALL_SCREENING\)\?\.let\(roleLauncher::launch\)/,
    'the call-screening card must not silently ignore a null role Intent'
  );
});

test('activation action failures render a recovery affordance', () => {
  assert.match(
    activation,
    /activationActionError\?\.let \{ message ->[\s\S]*?Action non effectuée[\s\S]*?Actualiser l’état/s,
    'a failed system action must explain the failure and provide an explicit refresh'
  );
});

test('Phone Core internal surfaces use the same guarded launch boundary', () => {
  assert.match(
    activation,
    /fun launchInternalActivityOrReport\(request: Intent, failureMessage: String\) \{[\s\S]*?startActivity\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'internal Phone Core activities must report missing or disabled components'
  );
  assert.equal(
    (activation.match(/startActivity\(/g) ?? []).length,
    1,
    'Phone Core activation must route every internal activity launch through the helper'
  );
});
