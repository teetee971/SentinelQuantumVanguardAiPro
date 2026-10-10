import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const settings = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt',
  'utf8'
);

test('settings permission and document actions contain framework launch failures', () => {
  assert.match(
    settings,
    /fun requestOsintNotificationPermission\(\)[\s\S]*?notificationPermissionLauncher\.launch\(Manifest\.permission\.POST_NOTIFICATIONS\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'notification permission must use a guarded launcher'
  );
  assert.match(
    settings,
    /fun launchBackupExport\(\)[\s\S]*?createBackupLauncher\.launch\("Sentinel-backup\.json"\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'backup export must use a guarded launcher'
  );
  assert.match(
    settings,
    /fun launchBackupRestore\(\)[\s\S]*?restoreBackupLauncher\.launch\(arrayOf\("application\/json", "text\/plain"\)\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'backup restore must use a guarded launcher'
  );
  assert.match(
    settings,
    /notificationStatus\?\.let \{[\s\S]*?MaterialTheme\.colorScheme\.error/,
    'notification prompt failures must remain visible'
  );
  assert.equal(
    (settings.match(/(?:notificationPermissionLauncher|createBackupLauncher|restoreBackupLauncher)\.launch\(/g) ?? []).length,
    3,
    'settings launchers must only be called from guarded helpers'
  );
});
