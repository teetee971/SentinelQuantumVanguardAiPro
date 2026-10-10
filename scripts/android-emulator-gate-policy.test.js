import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const codeqlWorkflow = readFileSync('.github/workflows/codeql-analysis.yml', 'utf8');
const instrumentationWorkflow = readFileSync('.github/workflows/android-instrumentation.yml', 'utf8');

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

test('Android emulator qualification covers minimum, current and newest runtime lanes', () => {
  assert.match(instrumentationWorkflow, /api-level:\s*24\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*36\b/);
  assert.match(instrumentationWorkflow, /api-level:\s*37\b/);
  assert.match(instrumentationWorkflow, /connectedDebugAndroidTest/);
  assert.match(instrumentationWorkflow, /ACTUAL_API=.*ro\.build\.version\.sdk/);
});

test('API 24 APK installs are bounded and reject unsuccessful PackageManager output', () => {
  assert.match(instrumentationWorkflow, /if \[\[ "\$API_LEVEL" == "24" \]\]; then/);
  assert.match(instrumentationWorkflow, /PACKAGE_MANAGER_READY=0/);
  assert.match(instrumentationWorkflow, /if \[\[ "\$PACKAGE_MANAGER_READY" != "1" \]\]; then[\s\S]*?exit 1/);

  const start = instrumentationWorkflow.indexOf('install_api24_apk() {');
  assert.notEqual(start, -1, 'API 24 must declare a dedicated APK installer');
  const end = instrumentationWorkflow.indexOf('\n            }', start);
  assert.notEqual(end, -1, 'API 24 installer must have a bounded function body');
  const installer = instrumentationWorkflow.slice(start, end);
  assert.ok(installer.includes('timeout --signal=INT --kill-after=15s 120s adb push "$apk_path" "$remote_path"'));
  assert.ok(installer.includes('timeout --signal=INT --kill-after=30s 300s adb shell pm install -r "$remote_path"'));
  assert.ok(installer.includes('INSTALL_STATUS=$?'));
  assert.ok(installer.includes('if [[ "$INSTALL_STATUS" -ne 0 ]] || ! grep -q'));
  assert.ok(installer.includes("'^Success$'"));
  assert.match(installer, /exit 1/, 'a missing Success response must fail even when pm install exits 0');

  assert.ok(instrumentationWorkflow.includes('install_api24_apk "$APP_APK" "/data/local/tmp/sentinel-app.apk" "app"'));
  assert.ok(instrumentationWorkflow.includes('install_api24_apk "$TEST_APK" "/data/local/tmp/sentinel-test.apk" "test"'));
});
