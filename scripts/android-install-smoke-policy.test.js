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
  assert.match(workflow, /SECOND_LAUNCH_STATUS=0/);
  assert.match(workflow, /GrantPermissionsActivity/);
  assert.match(workflow, /resultTo=.*PhoneCoreActivationActivity/);
  assert.match(
    workflow,
    /avdmanager create avd --force --name sentinel_phone_core_ci --package "system-images;android-29;google_apis;x86_64" --device "pixel"/
  );
});

test('Android 16 Phone Core qualification disables system animations before Telecom flow', () => {
  const android16 = workflow.split('name: Phone Core on Android 16')[1];
  assert.ok(android16);
  assert.match(android16, /adb shell settings put global window_animation_scale 0/);
  assert.match(android16, /adb shell settings put global transition_animation_scale 0/);
  assert.match(android16, /adb shell settings put global animator_duration_scale 0/);
});
