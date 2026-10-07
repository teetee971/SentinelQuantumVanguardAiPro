import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/build-native-android.yml'), 'utf8');

function workflowStep(name) {
  const start = workflow.indexOf(`- name: ${name}`);
  const end = workflow.indexOf('\n      - name:', start + 1);
  assert.ok(start >= 0, `${name} step must exist`);
  return workflow.slice(start, end > start ? end : workflow.length);
}

function installSmokeStep() {
  return workflowStep('Install and launch APK on Android 10 emulator');
}

function android16Step() {
  return workflowStep('Verify Telecom and inline reply on Android 16');
}

function assertHardenedInstall(step, label) {
  const bootComplete = step.indexOf('sys.boot_completed');
  const packageReady = step.indexOf('PACKAGE_MANAGER_READY=0');
  const packageProbe = step.indexOf('adb shell cmd package list packages');
  const installRetry = step.indexOf('for INSTALL_ATTEMPT in $(seq 1');
  const installCommand = step.indexOf('adb install -r "$APK_PATH"', installRetry);

  assert.ok(bootComplete >= 0, `${label}: boot-completed evidence must remain required`);
  assert.ok(packageReady > bootComplete, `${label}: PackageManager readiness must be proven after boot completion`);
  assert.ok(packageProbe > packageReady, `${label}: PackageManager readiness must use a real package-service command`);
  assert.ok(installRetry > packageProbe, `${label}: install retries must begin only after PackageManager is responsive`);
  assert.ok(installCommand > installRetry, `${label}: adb install must execute inside the bounded retry loop`);

  assert.match(step, /INSTALL_STATUS=0/,
    `${label}: install must keep an explicit success state instead of swallowing failures`);
  assert.match(step, /Broken pipe|device offline|device still authorizing|closed|transport/i,
    `${label}: retry policy must be limited to recognized transient adb/package-service failures`);
  assert.match(step, /adb wait-for-device/,
    `${label}: transient retries must re-establish adb transport before the next attempt`);
  assert.match(step, /if \[\[ "\$INSTALL_STATUS" != "0" \]\]; then[\s\S]*exit 1/,
    `${label}: permanent or exhausted install failures must remain fail-closed`);
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
  assertHardenedInstall(installSmokeStep(), 'Android 10 smoke');
});

test('Android 16 Phone Core gate uses the same fail-closed package-service install readiness', () => {
  const step = android16Step();
  assert.match(step, /system-images;android-36;google_apis;x86_64/);
  assertHardenedInstall(step, 'Android 16 Phone Core');
  const flowIndex = step.indexOf('bash ../scripts/phone-core-emulator-flow.sh');
  const installIndex = step.lastIndexOf('adb install -r "$APK_PATH"');
  assert.ok(flowIndex > installIndex,
    'Android 16 functional qualification must start only after hardened APK installation succeeds');
});
