import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const contract = readFileSync('scripts/check-phone-core-emulation-gate.js', 'utf8');

function workflowMarkerBlock(source) {
  const start = source.indexOf('for (const marker of [');
  assert.notEqual(start, -1, 'workflow marker contract exists');
  const end = source.indexOf("requireText(workflow, marker, 'shadow emulation workflow');", start);
  assert.notEqual(end, -1, 'workflow marker contract terminator exists');
  return source.slice(start, end);
}

test('emulation contract validates the shared crash oracle, not comments in workflow YAML', () => {
  assert.match(contract, /phone-core-logcat-crash-oracle\.py/, 'contract must load the shared crash oracle');
  assert.match(contract, /shared logcat crash oracle/, 'contract must validate crash markers against the oracle source');

  const workflowMarkers = workflowMarkerBlock(contract);
  assert.doesNotMatch(
    workflowMarkers,
    /'FATAL EXCEPTION:'/,
    'literal fatal marker in workflow contract can be satisfied by a comment and is not executable evidence'
  );
  assert.doesNotMatch(
    workflowMarkers,
    /'ANR in com\\\\\.sentinel\\\\\.quantum'/,
    'literal ANR marker in workflow contract can be satisfied by a comment and is not executable evidence'
  );
});
