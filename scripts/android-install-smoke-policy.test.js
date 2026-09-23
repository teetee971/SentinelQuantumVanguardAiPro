import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/build-native-android.yml'), 'utf8');

test('APK install smoke runs after structural verification and before artifact upload', () => {
  const verify = workflow.indexOf('- name: Verify debug APK package, alignment and signature');
  const install = workflow.indexOf('- name: Install and launch APK on Android 10 emulator');
  const upload = workflow.indexOf('- name: Upload APK artifact');
  assert.ok(verify >= 0);
  assert.ok(install > verify);
  assert.ok(upload > install);
});

test('APK install smoke uses Android 10 and verifies install plus launch', () => {
  assert.match(workflow, /system-images;android-29;google_apis;x86_64/);
  assert.match(workflow, /adb install -r "\$APK_PATH"/);
  assert.match(workflow, /adb shell pm path com\.sentinel\.quantum/);
  assert.match(workflow, /adb shell am start -W -n com\.sentinel\.quantum\/\.MainActivity/);
  assert.match(workflow, /sys\.boot_completed/);
});
