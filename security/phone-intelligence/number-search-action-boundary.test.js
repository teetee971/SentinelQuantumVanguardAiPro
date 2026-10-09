import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const search = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NumberSearchScreen.kt',
  'utf8'
);

test('number search composeur action contains launch failures', () => {
  assert.match(
    search,
    /fun openDialer\(\)[\s\S]*?context\.startActivity\(Intent\(context, SentinelDialerActivity::class\.java\)\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'number search must contain OEM/framework dialer launch failures'
  );
  assert.match(
    search,
    /var actionStatus by remember \{ mutableStateOf<String\?>\(null\) \}/,
    'number search must retain a visible action failure'
  );
  assert.match(
    search,
    /actionStatus\?\.let \{[\s\S]*?MaterialTheme\.colorScheme\.error/,
    'number search must render launch failures'
  );
  assert.equal(
    (search.match(/context\.startActivity\(/g) ?? []).length,
    1,
    'number search must not bypass the guarded dialer launch'
  );
});
