import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflow = readFileSync('.github/workflows/android-emulation-qualification.yml', 'utf8');

function workflowStep(name, nextName) {
  const start = workflow.indexOf(`      - name: ${name}`);
  const end = workflow.indexOf(`      - name: ${nextName}`, start);
  assert.ok(start >= 0 && end > start, `${name} step must exist`);
  return workflow.slice(start, end);
}

test('Phone Core evidence collection hard-bounds every ADB evidence command', () => {
  const evidence = workflowStep(
    'Collect qualification evidence even after failure',
    'Upload emulator qualification evidence'
  );

  assert.match(
    evidence,
    /timeout --signal=TERM --kill-after=5s 20s adb logcat -d -v time[^\n]*LOGCAT_STATUS=\$\?/
  );
  assert.match(evidence, /timeout --signal=TERM --kill-after=5s 20s adb shell dumpsys telecom/);
  assert.match(evidence, /timeout --signal=TERM --kill-after=5s 20s adb shell dumpsys role/);
  assert.match(
    evidence,
    /timeout --signal=TERM --kill-after=5s 20s adb shell dumpsys package com\.sentinel\.quantum/
  );

  assert.doesNotMatch(evidence, /\n\s*adb logcat -d -v time/);
  assert.doesNotMatch(evidence, /\n\s*adb shell dumpsys telecom/);
  assert.doesNotMatch(evidence, /\n\s*adb shell dumpsys role/);
  assert.doesNotMatch(evidence, /\n\s*adb shell dumpsys package com\.sentinel\.quantum/);
});
