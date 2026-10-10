import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const workflow = fs.readFileSync('.github/workflows/android-instrumentation.yml', 'utf8');

test('generic Android instrumentation unlocks the emulator before UI tests', () => {
  const bootIndex = workflow.indexOf('echo "Emulator ready: Android');
  const testCommandIndex = workflow.indexOf('./gradlew connectedDebugAndroidTest');
  assert.ok(
    bootIndex >= 0 && testCommandIndex > bootIndex,
    'workflow must verify boot before tests'
  );

  const preflight = workflow.slice(bootIndex, testCommandIndex);
  assert.match(
    preflight,
    /adb shell input keyevent KEYCODE_WAKEUP/,
    'instrumentation must wake the emulator display'
  );
  assert.match(
    preflight,
    /adb shell wm dismiss-keyguard/,
    'instrumentation must dismiss the emulator keyguard before Compose tests'
  );
});
