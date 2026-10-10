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

test('APK smoke proves the foreground setup surface before simulating app interruption', () => {
  const firstRun = workflow.indexOf('FIRST_RUN_READY=0');
  const surfaceProbe = workflow.indexOf('INTERRUPTION_SURFACE=""', firstRun);
  const interruption = workflow.indexOf('adb shell input keyevent KEYCODE_BACK', firstRun);
  const forceStop = workflow.indexOf('adb shell am force-stop com.sentinel.quantum', interruption);
  assert.ok(firstRun >= 0);
  assert.ok(surfaceProbe > firstRun);
  assert.ok(interruption > firstRun);
  assert.ok(forceStop > interruption);
  assert.ok(surfaceProbe < interruption);
  assert.match(workflow, /sentinel-interruption-dismiss\.txt/);
  assert.match(workflow, /resultTo=.*com\\\.sentinel\\\.quantum\/\\\.PhoneCoreActivationActivity/);
  assert.match(workflow, /PhoneCoreActivationActivity already foreground; simulating process death without BACK/);
  assert.match(workflow, /POST_BACK_READY=0/);
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
});
