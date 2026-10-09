import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const screen = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CollectiveDefenseScreen.kt',
  'utf8'
);

test('collective-defense status is presentation-only and never a dead action', () => {
  assert.doesNotMatch(
    screen,
    /status\?\.let \{[\s\S]*AssistChip\([\s\S]*onClick\s*=\s*\{\s*\}/,
    'a status message must not expose an empty interactive callback'
  );
  assert.match(
    screen,
    /status\?\.let \{[\s\S]*Surface\([\s\S]*Text\(it\)/,
    'the status must remain visible as a non-interactive surface'
  );
});

