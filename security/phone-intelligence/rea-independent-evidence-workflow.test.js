import fs from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';

const WORKFLOW_PATH = '.github/workflows/rea-independent-evidence.yml';

function workflowSource() {
  assert.ok(fs.existsSync(WORKFLOW_PATH), `${WORKFLOW_PATH} must exist`);
  return fs.readFileSync(WORKFLOW_PATH, 'utf8');
}

test('REA evidence workflow stays manual, pinned, read-only and non-blocking', () => {
  const source = workflowSource();

  assert.match(source, /workflow_dispatch:/);
  assert.doesNotMatch(source, /\bpush:\s*$/m);
  assert.doesNotMatch(source, /\bpull_request:\s*$/m);
  assert.match(source, /rea-agents@6\.4\.0/);
  assert.match(source, /continue-on-error:\s*true/);
  assert.match(source, /inspect-adb-client/);
  assert.match(source, /list-adb-devices/);
  assert.match(source, /inspect-adb-device/);
  assert.match(source, /inspect-adb-package/);
  assert.match(source, /read-adb-logcat/);
  assert.match(source, /capture-adb-screen/);

  for (const mutatingCommand of [
    'install-adb-package',
    'uninstall-adb-package',
    'push-adb-file',
    'start-adb-app',
    'start-adb-activity',
    'stop-adb-app'
  ]) {
    assert.ok(!source.includes(mutatingCommand), `${mutatingCommand} must not be used in the read-only evidence lane`);
  }
});
