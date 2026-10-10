import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const compose = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/SmsComposeActivity.kt',
  'utf8'
);

test('MMS attachment picker failures remain visible instead of crashing the composer', () => {
  assert.match(
    compose,
    /fun launchMmsAttachmentPicker\(\)[\s\S]*?mmsAttachmentLauncher\.launch\("image\/\*"\)[\s\S]*?catch \(_: ActivityNotFoundException\)[\s\S]*?catch \(_: RuntimeException\)/,
    'the Android image picker must have an explicit OEM/framework failure boundary'
  );
  assert.equal(
    (compose.match(/mmsAttachmentLauncher\.launch\(/g) ?? []).length,
    1,
    'the MMS picker must only be launched through the guarded helper'
  );
});

test('message export only reports success after the chooser actually opens', () => {
  assert.match(
    compose,
    /try \{\s*startActivity\(Intent\.createChooser\(share, "Exporter les messages"\)\)\s*status = "Export préparé/s,
    'export success status must follow a successful chooser launch'
  );
  assert.match(
    compose,
    /catch \(_: ActivityNotFoundException\)[\s\S]*?Impossible d’ouvrir le partage des messages/s,
    'missing share handlers must produce visible French recovery text'
  );
});
