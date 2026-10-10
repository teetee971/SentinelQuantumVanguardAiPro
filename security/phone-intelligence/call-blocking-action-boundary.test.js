import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const blocking = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CallBlockingScreen.kt',
  'utf8'
);

test('call screening and contacts activation contain launch failures', () => {
  assert.match(
    blocking,
    /fun launchCallScreeningRole\(\)[\s\S]*?roleLauncher\.launch\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'screening role activation must contain OEM/framework launch failures'
  );
  assert.match(
    blocking,
    /fun requestContactsPermission\(\)[\s\S]*?contactsLauncher\.launch\(Manifest\.permission\.READ_CONTACTS\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'contacts permission activation must contain OEM/framework launch failures'
  );
  assert.match(
    blocking,
    /status\?\.let \{[\s\S]*?Text\(it, style = MaterialTheme\.typography\.bodySmall/,
    'call blocking must render action failures'
  );
  assert.match(
    blocking,
    /roleLauncher = rememberLauncherForActivityResult[\s\S]*?status = when \(screeningState\)/,
    'role refusal or activation must be reflected in visible status'
  );
  assert.match(
    blocking,
    /contactsLauncher = rememberLauncherForActivityResult[\s\S]*?status = if \(granted\)/,
    'contacts refusal must be reflected in visible status'
  );
  assert.equal(
    (blocking.match(/(?:roleLauncher|contactsLauncher)\.launch\(/g) ?? []).length,
    2,
    'call blocking launchers must only be called from guarded helpers'
  );
});
