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
    const installLines = workflow.split('\n').filter((line) => line.includes('adb install -r'));
    assert.ok(installLines.length > 0, `${name} must install an APK`);
    for (const line of installLines) {
      assert.match(
        line,
        /timeout --signal=INT --kill-after=30s 180s adb install -r/,
        `${name} contains an unbounded APK install: ${line.trim()}`
      );
    }
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

test('Android emulator qualification covers minimum, current and newest runtime lanes', () => {
  assert.match(instrumentationWorkflow, /api-level:\s*24\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*36\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*37\b/);
  assert.match(instrumentationWorkflow, /connectedDebugAndroidTest/);
  assert.match(instrumentationWorkflow, /ACTUAL_API=.*ro\.build\.version\.sdk/);
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
