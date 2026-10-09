import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const main = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt',
  'utf8'
);

test('first-run setup launch failures stay visible and retryable', () => {
  assert.match(
    main,
    /private fun launchPhoneCoreSetup\([\s\S]*?try \{[\s\S]*?phoneCoreSetupLauncher\.launch\([\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'the first-run ActivityResult launch must contain framework/OEM failures'
  );
  assert.match(
    main,
    /private var phoneCoreLaunchError by mutableStateOf<String\?>\(null\)/,
    'MainActivity must retain a visible setup launch failure'
  );
  assert.match(
    main,
    /phoneCoreLaunchError\?\.let \{ message ->[\s\S]*?Réessayer la configuration Phone Core/s,
    'the main surface must expose a retry action after setup launch failure'
  );
});

test('persistence recovery reopens the setup surface through a guarded helper', () => {
  assert.match(
    main,
    /private fun openPhoneCoreSetupRecoverySurface\(\)[\s\S]*?startActivity\([\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'the persistence-error recovery surface must not bypass the launch boundary'
  );
  assert.equal(
    (main.match(/phoneCoreSetupLauncher\.launch\(/g) ?? []).length,
    1,
    'the registered first-run launcher must only be called from its guarded helper'
  );
});
