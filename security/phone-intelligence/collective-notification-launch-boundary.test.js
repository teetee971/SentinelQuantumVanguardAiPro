import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const defense = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/CollectiveDefenseScreen.kt',
  'utf8'
);

test('collective defense notification prompt contains launch failures', () => {
  assert.match(
    defense,
    /fun requestNotificationPermission\(\)[\s\S]*?notificationPermissionLauncher\.launch\(Manifest\.permission\.POST_NOTIFICATIONS\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'notification permission prompts must contain OEM/framework failures'
  );
  assert.match(
    defense,
    /requestNotificationPermission\(\)/,
    'notification toggle must use the guarded prompt'
  );
  assert.match(
    defense,
    /status = "Android n’a pas pu ouvrir la demande de notifications/,
    'notification prompt failures must remain visible'
  );
});
