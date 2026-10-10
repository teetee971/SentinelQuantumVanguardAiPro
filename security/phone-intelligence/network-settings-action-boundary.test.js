import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const network = fs.readFileSync(
  'native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/NetworkSurveillanceScreen.kt',
  'utf8'
);

test('network permission settings recovery contains framework launch failures', () => {
  assert.match(
    network,
    /fun openPermissionSettings\(\)[\s\S]*?runCatching \{ context\.startActivity\(intent\) \}[\s\S]*?onFailure \{ statusMessage = /,
    'permission settings recovery must expose launch failure through the screen status'
  );
  assert.match(
    network,
    /onClick = \{ openPermissionSettings\(\) \}/,
    'permission settings button must use the guarded helper'
  );
});
