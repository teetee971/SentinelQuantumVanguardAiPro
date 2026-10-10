import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const activation = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/PhoneCoreActivationActivity.kt',
  'utf8'
);

test('Phone Core settings actions fail visibly when an OEM omits the target surface', () => {
  assert.match(
    activation,
    /fun launchSettingsOrReport\(request: Intent, failureMessage: String\) \{[\s\S]*?settingsLauncher\.launch\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'settings launches must share a guarded OEM/framework boundary'
  );
  assert.match(
    activation,
    /reportActivationActionError\(failureMessage\)/,
    'a missing settings activity must be converted into visible recovery state'
  );
  assert.equal(
    (activation.match(/settingsLauncher\.launch\(/g) ?? []).length,
    1,
    'all settings actions must route through the guarded launcher'
  );
});

test('settings cards retain explicit recovery text after a launch failure', () => {
  assert.match(
    activation,
    /launchSettingsOrReport\([\s\S]*Android n’a pas pu ouvrir les réglages/s,
    'settings actions must provide a French failure message'
  );
  assert.match(
    activation,
    /Action non effectuée[\s\S]*Actualiser l’état/s,
    'the shared action error card must remain the recovery affordance'
  );
});
