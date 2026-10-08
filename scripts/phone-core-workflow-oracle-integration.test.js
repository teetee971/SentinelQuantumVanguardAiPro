import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

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

test('flow logcat marker cannot be credited when adb returns partial output with failure', () => {
  const flow = readFileSync('scripts/phone-core-emulator-flow.sh', 'utf8');
  const start = flow.indexOf('wait_logcat_marker() {');
  const end = flow.indexOf('\nstart_api37_incoming_answer_transport_bridge() {', start);
  assert.ok(start >= 0 && end > start);
  const waitMarker = flow.slice(start, end);
  const directory = mkdtempSync(join(tmpdir(), 'sentinel-logcat-marker-failure-'));
  try {
    const script = `set -euo pipefail
FLOW_OUTPUT_DIR="$1"
adb() { printf '%s\\n' 'CallScreeningService:onScreenCall' > "$FLOW_OUTPUT_DIR/evidence.txt"; return 7; }
sleep() { :; }
${waitMarker}
if wait_logcat_marker 'CallScreeningService:onScreenCall' evidence.txt; then exit 0; else exit 23; fi
`;
    const result = spawnSync('bash', ['-c', script, 'fixture', directory], { encoding: 'utf8' });
    assert.equal(result.status, 23, `${result.stdout}\n${result.stderr}`);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
