import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const homeScreen = readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/HomeScreen.kt',
  'utf8'
);

test('number verification shortcut opens the real number search route', () => {
  const start = homeScreen.indexOf('"Vérifier un numéro"');
  const end = homeScreen.indexOf('        HomeTool(', start + 1);
  const shortcut = homeScreen.slice(start, end);
  assert.ok(start >= 0 && end > start, 'number verification shortcut must exist');
  assert.match(shortcut, /navController\.navigate\(Screen\.Search\.route\)/);
  assert.doesNotMatch(shortcut, /SentinelDialerActivity/);
});
