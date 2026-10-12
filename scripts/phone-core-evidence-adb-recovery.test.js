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

test('Phone Core evidence collection gives a transient offline emulator a bounded recovery window', () => {
  const evidence = workflowStep(
    'Collect qualification evidence even after failure',
    'Upload emulator qualification evidence'
  );

  assert.match(evidence, /ADB_RECOVERY_READY=0/);
  assert.match(evidence, /for _ in \$\(seq 1 10\); do/);
  assert.match(evidence, /timeout 3s adb get-state[^\n]*grep -Fxq ['"]device['"]/);
  assert.match(evidence, /adb reconnect/);
  assert.match(evidence, /adb-recovery-status\.txt/);

  const recovery = evidence.indexOf('ADB_RECOVERY_READY=0');
  const collection = evidence.indexOf('adb logcat -d -v time');
  assert.ok(recovery >= 0 && collection > recovery, 'ADB recovery must run before evidence collection');
});

test('Phone Core evidence collection hard-bounds every ADB evidence command', () => {
  const evidence = workflowStep(
    'Collect qualification evidence even after failure',
    'Upload emulator qualification evidence'
  );

  assert.match(
    evidence,
    /timeout --signal=TERM --kill-after=5s 20s adb logcat -d -v time[^\n]*LOGCAT_STATUS=\$\?/
  );
  assert.match(
    evidence,
    /timeout --signal=TERM --kill-after=5s 20s adb shell dumpsys telecom/
  );
  assert.match(
    evidence,
    /timeout --signal=TERM --kill-after=5s 20s adb shell dumpsys role/
  );
  assert.match(
    evidence,
    /timeout --signal=TERM --kill-after=5s 20s adb shell dumpsys package com\.sentinel\.quantum/
  );
});
