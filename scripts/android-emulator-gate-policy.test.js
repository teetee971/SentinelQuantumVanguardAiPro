import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const codeqlWorkflow = readFileSync('.github/workflows/codeql-analysis.yml', 'utf8');
const instrumentationWorkflow = readFileSync('.github/workflows/android-instrumentation.yml', 'utf8');
const emulationWorkflow = readFileSync('.github/workflows/android-emulation-qualification.yml', 'utf8');
const nativeBuildWorkflow = readFileSync('.github/workflows/build-native-android.yml', 'utf8');

function requiredWorkflowBlock(workflow) {
  const start = workflow.indexOf('REQUIRED_WORKFLOWS=(');
  assert.notEqual(start, -1, 'CodeQL workflow must declare REQUIRED_WORKFLOWS');
  const end = workflow.indexOf('\n          )', start);
  assert.notEqual(end, -1, 'CodeQL REQUIRED_WORKFLOWS block must be bounded');
  return workflow.slice(start, end);
}

function apkInstallLines(workflow) {
  return workflow
    .split('\n')
    .filter((line) => /\badb\s+install(?:\s|$)/.test(line));
}

function assertBoundedApkInstall(line, name) {
  assert.match(
    line,
    /\btimeout\s+--signal=INT\s+--kill-after=(?:\d+)s\s+(?:\d+)s\s+adb\s+install(?:\s|$)/,
    `${name} contains an unbounded APK install: ${line.trim()}`
  );
}

test('required CodeQL Android status waits for emulator and comprehensive merge gates', () => {
  const block = requiredWorkflowBlock(codeqlWorkflow);
  assert.match(block, /"android-instrumentation\.yml"/);
  assert.match(block, /"production-merge-gate\.yml"/);
});

test('every emulator APK install has an independent bounded ADB watchdog', () => {
  for (const [name, workflow] of [
    ['legacy instrumentation', instrumentationWorkflow],
    ['Phone Core emulation qualification', emulationWorkflow],
    ['native Android build smoke', nativeBuildWorkflow]
  ]) {
    const installLines = apkInstallLines(workflow);
    assert.ok(installLines.length > 0, `${name} must install an APK`);
    for (const line of installLines) {
      assertBoundedApkInstall(line, name);
    }
  }
});

test('legacy API 24 instrumentation uses bounded non-streaming installs for app and test APKs', () => {
  const legacyBlock = instrumentationWorkflow.split('if [[ "$API_LEVEL" == "24" ]]; then')[1]?.split('\n          else')[0] || '';
  const installLines = apkInstallLines(legacyBlock);

  assert.equal(installLines.length, 2, 'API 24 must install exactly the app APK and instrumentation APK');
  for (const line of installLines) {
    assertBoundedApkInstall(line, 'legacy instrumentation');
    assert.match(line, /adb\s+install\s+--no-streaming\b/, 'API 24 installs must disable ADB streaming');
    assert.match(line, /\s-r(?:\s|$)/, 'API 24 installs must replace an existing package deterministically');
  }
  assert.ok(
    installLines.some((line) => /\s-t(?:\s|$)/.test(line) && line.includes('$TEST_APK')),
    'API 24 instrumentation APK must be installed with -t'
  );
  assert.ok(
    installLines.some((line) => line.includes('$APP_APK') && !/\s-t(?:\s|$)/.test(line)),
    'API 24 application APK must be installed separately from the testOnly APK'
  );
});

test('Phone Core emulation API 24 installs both test-only APKs with the legacy-safe flags', () => {
  const testApkInstalls = emulationWorkflow
    .split('\n')
    .filter((line) => line.includes('adb install') && line.includes('$TEST_APK'));
  assert.equal(testApkInstalls.length, 2, 'setup and reboot qualification must each install the test APK');
  for (const line of testApkInstalls) {
    assert.match(line, /--no-streaming\s+-r\s+-t\s+"\$TEST_APK"/);
  }
});

test('every emulator workflow step that talks to a device bounds ordinary ADB calls', () => {
  const stepNames = [
    'Boot Android emulator',
    'Run connected instrumentation suite',
    'Reboot emulator and verify interrupted setup resumes',
    'Run emulator application/runtime qualification',
    'Collect qualification evidence even after failure',
    'Stop emulator'
  ];
  for (const name of stepNames) {
    const block = emulationWorkflow.split(`- name: ${name}\n`)[1]?.split('\n      - name: ')[0] || '';
    assert.match(block, /ADB_COMMAND_TIMEOUT_SECONDS="\$\{ADB_COMMAND_TIMEOUT_SECONDS:-30\}"/,
      `${name} must configure an ADB timeout`);
    assert.match(block, /adb\(\)\s*\{[\s\S]*command timeout[\s\S]*ADB_BIN[\s\S]*"\$@"/,
      `${name} must route ordinary ADB calls through its watchdog`);
  }
});

test('legacy instrumentation diagnostics and qualification also bound ordinary ADB calls', () => {
  const block = instrumentationWorkflow.split('- name: Run connected Android tests\n')[1]?.split('\n      - name: ')[0] || '';
  assert.match(block, /ADB_COMMAND_TIMEOUT_SECONDS="\$\{ADB_COMMAND_TIMEOUT_SECONDS:-30\}"/);
  assert.match(block, /ADB_COMMAND_KILL_GRACE_SECONDS="\$\{ADB_COMMAND_KILL_GRACE_SECONDS:-5\}"/);
  assert.match(block, /adb\(\)\s*\{[\s\S]*command timeout[\s\S]*ADB_BIN[\s\S]*"\$@"/);
});

test('native Android build emulator lanes bound ordinary ADB calls', () => {
  for (const name of ['Install and launch APK on Android 10 emulator', 'Verify Telecom and inline reply on Android 16']) {
    const block = nativeBuildWorkflow.split(`- name: ${name}\n`)[1]?.split('\n      - name: ')[0] || '';
    assert.match(block, /ADB_COMMAND_TIMEOUT_SECONDS="\$\{ADB_COMMAND_TIMEOUT_SECONDS:-30\}"/,
      `${name} must configure an ADB timeout`);
    assert.match(block, /ADB_COMMAND_KILL_GRACE_SECONDS="\$\{ADB_COMMAND_KILL_GRACE_SECONDS:-5\}"/,
      `${name} must configure an ADB kill grace period`);
    assert.match(block, /adb\(\)\s*\{[\s\S]*command timeout[\s\S]*ADB_BIN[\s\S]*"\$@"/,
      `${name} must route ordinary ADB calls through its watchdog`);
  }
});

test('Android emulator qualification covers minimum, current and newest runtime lanes', () => {
  assert.match(instrumentationWorkflow, /api-level:\s*24\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*36\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*37\b/);
  assert.match(instrumentationWorkflow, /connectedDebugAndroidTest/);
  assert.match(instrumentationWorkflow, /ACTUAL_API=.*ro\.build\.version\.sdk/);
});

test('instrumentation evidence persists the exact runtime SDK API', () => {
  assert.match(
    instrumentationWorkflow,
    /INSTRUMENTATION_SDK_EVIDENCE=.*sentinel-instrumentation-api\$\{API_LEVEL\}-sdk\.txt/
  );
  assert.match(
    instrumentationWorkflow,
    /printf '%s\\n' "\$ACTUAL_API" > "\$INSTRUMENTATION_SDK_EVIDENCE"[\s\S]*test "\$\(cat "\$INSTRUMENTATION_SDK_EVIDENCE"\)" = "\$API_LEVEL"/
  );
  assert.match(
    instrumentationWorkflow,
    /sentinel-instrumentation-api\$\{\{ matrix\.api-level \}\}-sdk\.txt/
  );
});

test('instrumentation failures preserve runtime diagnostics before the emulator is stopped', () => {
  assert.match(instrumentationWorkflow, /collect_instrumentation_diagnostics\(\)/);
  assert.match(instrumentationWorkflow, /trap cleanup_instrumentation EXIT/);
  assert.match(instrumentationWorkflow, /if \[\[ "\$status" -ne 0 \]\]; then/);
  assert.match(instrumentationWorkflow, /adb logcat -d -v threadtime/);
  assert.match(instrumentationWorkflow, /adb shell dumpsys telecom/);
  assert.match(instrumentationWorkflow, /adb shell dumpsys role/);
  assert.match(instrumentationWorkflow, /adb shell dumpsys package com\.sentinel\.quantum/);
  assert.match(
    instrumentationWorkflow,
    /sentinel-instrumentation-api\$\{\{ matrix\.api-level \}\}-diagnostics/
  );
});

test('instrumentation failures expose failing XML cases as check annotations', () => {
  assert.match(instrumentationWorkflow, /summarize_instrumentation_reports\(\)/);
  assert.match(instrumentationWorkflow, /::error title=Android instrumentation failure::/);
  assert.match(instrumentationWorkflow, /Instrumentation reports contain zero testcases/);
  assert.match(instrumentationWorkflow, /failure body empty/);
  assert.match(instrumentationWorkflow, /compact\(failure\[3\]\)/);
  assert.match(instrumentationWorkflow, /AndroidJUnitRunner tail/);
  assert.match(instrumentationWorkflow, /INSTRUMENTATION_LOG=\"\$INSTRUMENTATION_LOG\"/);
});
