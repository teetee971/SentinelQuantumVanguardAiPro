import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const settings = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt',
  'utf8'
);

test('voice studio entry contains activity launch failures', () => {
  assert.match(
    settings,
    /fun openVoiceStudio\(\)[\s\S]*?context\.startActivity\(Intent\(context, VoiceStudioActivity::class\.java\)\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'voice studio must contain OEM/framework activity launch failures'
  );
  assert.match(
    settings,
    /voiceStudioStatus\?\.let \{[\s\S]*?MaterialTheme\.colorScheme\.error/,
    'voice studio must render launch failures'
  );
  assert.equal(
    (settings.match(/context\.startActivity\(Intent\(context, VoiceStudioActivity::class\.java\)\)/g) ?? []).length,
    1,
    'voice studio must only launch through its guarded helper'
  );
});
