import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const security = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/PhoneSecurityScreen.kt',
  'utf8'
);

test('phone security actions contain OEM and framework launch failures', () => {
  assert.match(
    security,
    /fun launchPhoneSurface\(request: Intent, surface: String\)[\s\S]*?context\.startActivity\(request\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'phone security actions must share a visible launch failure boundary'
  );
  assert.match(
    security,
    /var actionStatus by remember \{ mutableStateOf<String\?>\(null\) \}/,
    'phone security must retain a visible action failure'
  );
  assert.match(
    security,
    /actionStatus\?\.let \{[\s\S]*?Text\(it, style = MaterialTheme\.typography\.bodySmall/,
    'phone security must render launch failures'
  );
  assert.equal(
    (security.match(/context\.startActivity\(/g) ?? []).length,
    1,
    'phone security actions must not bypass the shared launch helper'
  );
});
