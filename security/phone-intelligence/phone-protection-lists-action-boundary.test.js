import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const lists = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneProtectionListsScreen.kt',
  'utf8'
);

test('phone protection lists setup action contains launch failures', () => {
  assert.match(
    lists,
    /fun launchPhoneCoreSetup\(\)[\s\S]*?context\.startActivity\(Intent\(context, PhoneCoreActivationActivity::class\.java\)\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'protection lists must contain OEM/framework setup launch failures'
  );
  assert.match(
    lists,
    /actionStatus\?\.let \{[\s\S]*?Text\(it, style = MaterialTheme\.typography\.bodySmall/,
    'protection lists must render setup launch failures'
  );
  assert.equal(
    (lists.match(/context\.startActivity\(/g) ?? []).length,
    1,
    'protection lists must not bypass the setup launch boundary'
  );
});
