import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflow = readFileSync('.github/workflows/build-native-android.yml', 'utf8');

test('Build Native Android 10 APK installation has a hard foreground timeout', () => {
  const start = workflow.indexOf('      - name: Install and launch APK on Android 10 emulator');
  const end = workflow.indexOf('      - name: Get version and prepare APK', start);
  assert.ok(start >= 0 && end > start, 'Android 10 install/launch step must exist');
  const installStep = workflow.slice(start, end);

  assert.match(
    installStep,
    /timeout --signal=TERM --kill-after=30s 120s adb install -r "\$APK_PATH"/,
    'Build Native APK installation must be bounded by a hard foreground timeout'
  );
  assert.doesNotMatch(
    installStep,
    /\n\s*adb install -r "\$APK_PATH"/,
    'Build Native APK installation must never run unbounded'
  );
});
