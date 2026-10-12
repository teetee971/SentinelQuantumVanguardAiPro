import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const workflows = [
  readFileSync('.github/workflows/voice-api.yml', 'utf8'),
  readFileSync('.github/workflows/wangiri-api.yml', 'utf8')
];

test('Python API validation workflows run a pinned strict dependency audit', () => {
  for (const workflow of workflows) {
    assert.match(workflow, /pip-audit==2\.10\.1/);
    assert.match(workflow, /python -m pip_audit --requirement requirements\.txt --strict/);
  }
});
