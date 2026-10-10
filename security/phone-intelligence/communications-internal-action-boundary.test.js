import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const communications = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CommunicationsHubScreen.kt',
  'utf8'
);

test('communications actions contain OEM and framework launch failures', () => {
  assert.match(
    communications,
    /fun launchPhoneSurface\(request: Intent, surface: String\)[\s\S]*?context\.startActivity\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'communications actions must share a visible launch failure boundary'
  );
  assert.match(
    communications,
    /var actionStatus by rememberSaveable \{ mutableStateOf<String\?>\(null\) \}/,
    'communications must retain a visible action failure'
  );
  assert.match(
    communications,
    /actionStatus\?\.let \{ status ->[\s\S]*?MaterialTheme\.colorScheme\.error/,
    'communications must render launch failures'
  );
  assert.equal(
    (communications.match(/context\.startActivity\(/g) ?? []).length,
    1,
    'communications actions must not bypass the shared launch helper'
  );
});
