import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/build-native-android.yml'), 'utf8');

function installSmokeStep() {
  const start = workflow.indexOf('- name: Install and launch APK on Android 10 emulator');
  const end = workflow.indexOf('\n      - name:', start + 1);
  assert.ok(start >= 0, 'APK install smoke step must exist');
  return workflow.slice(start, end > start ? end : workflow.length);
}

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
});

test('APK install smoke waits for PackageManager and retries only transient install transport failures', () => {
  const step = installSmokeStep();
  const bootComplete = step.indexOf('sys.boot_completed');
  const packageReady = step.indexOf('PACKAGE_MANAGER_READY=0');
  const packageProbe = step.indexOf('adb shell cmd package list packages');
  const installRetry = step.indexOf('for INSTALL_ATTEMPT in $(seq 1');
  const installCommand = step.indexOf('adb install -r "$APK_PATH"', installRetry);

  assert.ok(bootComplete >= 0, 'boot-completed evidence must remain required');
  assert.ok(packageReady > bootComplete, 'PackageManager readiness must be proven after boot completion');
  assert.ok(packageProbe > packageReady, 'PackageManager readiness must use a real package-service command');
  assert.ok(installRetry > packageProbe, 'install retries must begin only after PackageManager is responsive');
  assert.ok(installCommand > installRetry, 'adb install must execute inside the bounded retry loop');

  assert.match(step, /INSTALL_STATUS=0/,
    'install must keep an explicit success state instead of swallowing failures');
  assert.match(step, /Broken pipe|device offline|device still authorizing|closed|transport/i,
    'retry policy must be limited to recognized transient adb/package-service failures');
  assert.match(step, /adb wait-for-device/,
    'transient retries must re-establish adb transport before the next attempt');
  assert.match(step, /if \[\[ "\$INSTALL_STATUS" != "0" \]\]; then[\s\S]*exit 1/,
    'permanent or exhausted install failures must remain fail-closed');
});
