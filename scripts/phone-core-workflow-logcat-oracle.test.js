import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflows = [
  '.github/workflows/build-native-android.yml',
  '.github/workflows/android-emulation-qualification.yml'
];

const unsafeCrashOracle = /if\s+adb\s+logcat\s+-d\s+-v\s+brief\s*\|\s*grep\s+-E[q]?/m;

test('Android workflows never treat an unreadable logcat pipe as crash-free evidence', () => {
  for (const path of workflows) {
    const source = readFileSync(path, 'utf8');
    assert.doesNotMatch(
      source,
      unsafeCrashOracle,
      `${path} must capture and validate adb logcat exit status before interpreting crash absence`
    );
  }
});
