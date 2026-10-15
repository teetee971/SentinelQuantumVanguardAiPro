import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

const workflowPath = path.join(
  process.cwd(),
  '.github',
  'workflows',
  'rea-independent-evidence.yml'
);
const workflow = fs.readFileSync(workflowPath, 'utf8');

test('REA evidence job fails when a required command fails', () => {
  assert.doesNotMatch(workflow, /^\s+continue-on-error:\s*true\s*$/m);
  assert.doesNotMatch(workflow, /\|\|\s*true/);
  assert.match(workflow, /set -euo pipefail/);
  assert.equal((workflow.match(/local exit_code=\$\?/g) || []).length, 2);
  assert.equal((workflow.match(/else\n\s+local exit_code=\$\?/g) || []).length, 2);
});

test('REA evidence proves the executed version and validates JSON outputs', () => {
  assert.match(workflow, /REA_VERSION_ACTUAL=/);
  assert.match(workflow, /!= "6\.4\.0"/);
  assert.match(workflow, /validate_json adb-client\.json/);
  assert.match(workflow, /validate_json adb-devices\.json/);
  assert.match(workflow, /if-no-files-found: error/);
  assert.match(workflow, /Report REA evidence status/);
});

test('REA device evidence keeps the no-device state explicit', () => {
  assert.match(workflow, /device_validation=pending/);
  assert.match(workflow, /inspect-adb-package/);
  assert.match(workflow, /read-adb-logcat.*--count 500/);
  assert.match(workflow, /capture-adb-screen/);
});

test('REA device evidence cannot bypass the single-device guard with a requested serial', () => {
  const deviceStep = workflow.slice(workflow.indexOf('Collect REA device evidence when one device is available'));
  const enumeration = deviceStep.indexOf('adb devices -l > "$OUT/adb-devices-host.txt"');
  const singleDeviceGuard = deviceStep.indexOf('if [[ ${#DEVICES[@]} -ne 1 ]]; then');
  const requestedSerialBranch = deviceStep.indexOf('if [[ -z "$SERIAL" ]]; then');

  assert.ok(enumeration >= 0, 'device enumeration must always be present');
  assert.ok(singleDeviceGuard > enumeration, 'the single-device guard must follow enumeration');
  assert.ok(requestedSerialBranch === -1 || requestedSerialBranch > singleDeviceGuard,
    'a requested serial must not bypass the single-device guard');
});

test('REA workflow commands are observation-only', () => {
  for (const forbidden of [
    'install-adb-package',
    'uninstall-adb-package',
    'push-adb-file',
    'start-adb-activity',
    'start-adb-app',
    'stop-adb-app'
  ]) {
    assert.doesNotMatch(workflow, new RegExp(`\\b${forbidden}\\b`));
  }
});
