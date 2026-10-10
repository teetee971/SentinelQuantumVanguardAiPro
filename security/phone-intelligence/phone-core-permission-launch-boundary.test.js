import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const activation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'utf8'
);

test('Phone Core permission requests fail visibly when Android cannot open the prompt', () => {
  assert.match(
    activation,
    /fun launchPermissionOrReport\(permission: String, failureMessage: String\) \{[\s\S]*?setupPermissionLauncher\.launch\(permission\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'single permission requests must share a guarded framework boundary'
  );
  assert.match(
    activation,
    /fun launchPermissionsOrReport\(permissions: Array<String>, failureMessage: String\) \{[\s\S]*?permissionsLauncher\.launch\(requested\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'multiple permission requests must share a guarded framework boundary'
  );
  assert.equal(
    (activation.match(/setupPermissionLauncher\.launch\(/g) ?? []).length,
    1,
    'wizard permission requests must route through the guarded helper'
  );
  assert.equal(
    (activation.match(/permissionsLauncher\.launch\(/g) ?? []).length,
    1,
    'activation-card permission requests must route through the guarded helper'
  );
});

test('permission launch failures clear in-flight state and reuse the visible recovery card', () => {
  assert.match(
    activation,
    /reportActivationActionError\(message: String\) \{[\s\S]*?setupPermissionInFlight = null[\s\S]*?activationActionError = message/s,
    'a failed permission request must not leave the wizard stuck as in-flight'
  );
  assert.match(
    activation,
    /launchPermissionsOrReport\([\s\S]*Android n’a pas pu ouvrir la demande d’autorisation/s,
    'permission failures must expose a French retry message'
  );
  assert.match(
    activation,
    /Action non effectuée[\s\S]*Actualiser l’état/s,
    'permission failures must remain recoverable from the shared action error card'
  );
});
