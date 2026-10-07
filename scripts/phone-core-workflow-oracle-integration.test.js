import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflows = [
  '.github/workflows/android-emulation-qualification.yml',
  '.github/workflows/build-native-android.yml'
];

for (const path of workflows) {
  test(`${path} delegates crash qualification to the shared process-attributed oracle`, () => {
    const source = readFileSync(path, 'utf8');
    assert.doesNotMatch(
      source,
      /adb\s+logcat\s+-d\s+-v\s+brief\s*\|\s*grep\s+-E[q]?/,
      `${path} must not turn an unreadable adb logcat pipe into crash-free evidence`
    );
    assert.match(
      source,
      /phone-core-logcat-crash-oracle\.py/,
      `${path} must use the shared process-attributed crash oracle`
    );
    assert.doesNotMatch(
      source,
      /!\/FATAL EXCEPTION:\|ANR in com\\\.sentinel\\\.quantum\//,
      `${path} must not use a second unscoped regex oracle in machine verdict generation`
    );
  });
}
