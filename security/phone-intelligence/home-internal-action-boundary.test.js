import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const home = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt',
  'utf8'
);

test('home Phone Core actions contain OEM and framework launch failures', () => {
  assert.match(
    home,
    /fun launchPhoneSurface\(request: Intent, surface: String\)[\s\S]*?context\.startActivity\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'home actions must share a visible launch failure boundary'
  );
  assert.match(
    home,
    /var actionStatus by rememberSaveable \{ mutableStateOf<String\?>\(null\) \}/,
    'home must retain a visible action failure'
  );
  assert.match(
    home,
    /actionStatus\?\.let \{ status ->[\s\S]*?MaterialTheme\.colorScheme\.error/,
    'home must render launch failures'
  );
  assert.equal(
    (home.match(/context\.startActivity\(/g) ?? []).length,
    1,
    'Phone Core home actions must not bypass the shared launch helper'
  );
});
