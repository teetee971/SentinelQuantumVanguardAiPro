import assert from 'node:assert/strict';
import test from 'node:test';

const SHA_REF = /^[0-9a-f]{40}$/;
const USES_PATTERN = /^\s*uses:\s*([^\s#]+)\s*(?:#.*)?$/;

function checkWorkflowText(text) {
  const violations = [];
  for (const [index, line] of text.split(/\r?\n/).entries()) {
    const match = line.match(USES_PATTERN);
    if (!match) continue;
    const reference = match[1];
    if (reference.startsWith('./') || reference.startsWith('../')) continue;
    const at = reference.lastIndexOf('@');
    const ref = at === -1 ? '' : reference.slice(at + 1);
    if (!at || !SHA_REF.test(ref)) violations.push({ line: index + 1, reference });
  }
  return violations;
}

test('accepts full commit SHA references', () => {
  assert.deepEqual(
    checkWorkflowText('uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1'),
    [],
  );
});

test('rejects floating tags and branches', () => {
  assert.equal(checkWorkflowText('uses: actions/checkout@v4').length, 1);
  assert.equal(checkWorkflowText('uses: owner/action@main').length, 1);
});

test('rejects missing refs but allows local actions', () => {
  assert.equal(checkWorkflowText('uses: owner/action').length, 1);
  assert.deepEqual(checkWorkflowText('uses: ./local-action'), []);
});
