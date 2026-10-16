import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflow = readFileSync('.github/workflows/build-native-android.yml', 'utf8');

function workflowStep(name, nextName) {
  const start = workflow.indexOf(`      - name: ${name}`);
  const end = workflow.indexOf(`      - name: ${nextName}`, start);
  assert.ok(start >= 0 && end > start, `${name} step must exist`);
  return workflow.slice(start, end);
}

for (const [label, step] of [
  [
    'Android 10 install/launch',
    workflowStep('Install and launch APK on Android 10 emulator', 'Get version and prepare APK')
  ],
  [
    'Android 16 Phone Core',
    workflowStep('Verify Telecom and inline reply on Android 16', 'Upload Phone Core Android 16 screenshots')
  ]
]) {
  test(`Build Native ${label} APK installation has a hard foreground timeout`, () => {
    assert.match(
      step,
      /timeout --signal=TERM --kill-after=30s 120s adb install -r "\$APK_PATH"/,
      `${label} APK installation must be bounded by a hard foreground timeout`
    );
    assert.doesNotMatch(
      step,
      /\n\s*adb install -r "\$APK_PATH"/,
      `${label} APK installation must never run unbounded`
    );
  });
}
