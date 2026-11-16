import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { validateSharedComponents } from './check-android-ui-consistency.js';

test('UI consistency guard checks the canonical shared glass component source', () => {
  const guard = fs.readFileSync(new URL('./check-android-ui-consistency.js', import.meta.url), 'utf8');
  const chrome = fs.readFileSync(
    new URL('../native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelChrome.kt', import.meta.url),
    'utf8'
  );
  const glass = fs.readFileSync(
    new URL('../native-android-app/app/src/main/java/com/sentinel/quantum/ui/design/SentinelGlassComponents.kt', import.meta.url),
    'utf8'
  );

  assert.doesNotMatch(chrome, /fun SentinelSectionHeader\(/);
  assert.match(glass, /fun SentinelSectionHeader\(/);
  assert.match(guard, /errors\.push\(\.\.\.validateSharedComponents\(chrome, glassComponents\)\)/);

  assert.deepEqual(
    validateSharedComponents(
      'fun SentinelTopBar() {} fun SentinelHero() {} fun SentinelPanel() {}',
      'fun SentinelSectionHeader() {}',
      'SentinelChrome.kt',
      'SentinelGlassComponents.kt'
    ),
    []
  );
  assert.deepEqual(
    validateSharedComponents(
      'fun SentinelTopBar() {} fun SentinelHero() {}',
      'fun SentinelSectionHeader() {}',
      'SentinelChrome.kt',
      'SentinelGlassComponents.kt'
    ),
    ['shared UI component missing: SentinelPanel (SentinelChrome.kt)']
  );
  assert.deepEqual(
    validateSharedComponents(
      'fun SentinelTopBar() {} fun SentinelHero() {} fun SentinelPanel() {}',
      '',
      'SentinelChrome.kt',
      'SentinelGlassComponents.kt'
    ),
    ['shared UI component missing: SentinelSectionHeader (SentinelGlassComponents.kt)']
  );
});
