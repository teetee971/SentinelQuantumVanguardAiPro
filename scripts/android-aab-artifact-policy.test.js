import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/build-aab-playconsole.yml'), 'utf8');
const verifyIndex = workflow.indexOf('- name: Locate, rename and verify AAB');
const uploadIndex = workflow.indexOf('- name: Upload AAB artifact');
const validation = workflow.slice(verifyIndex, uploadIndex);

test('unsigned AAB archive integrity is checked before artifact upload', () => {
  assert.ok(verifyIndex >= 0);
  assert.ok(uploadIndex > verifyIndex);
  assert.match(validation, /unzip -tq "\$NEW_NAME"/);
  assert.match(validation, /unzip -Z1 "\$NEW_NAME" \| grep -Fx 'BundleConfig\\.pb'/);
  assert.match(validation, /exit 1/);
});

test('optional bundletool does not silently claim semantic validation or mask failure', () => {
  assert.match(validation, /if command -v bundletool/);
  assert.match(validation, /bundletool validate --bundle="\$NEW_NAME"/);
  assert.match(validation, /semantic bundle validation not claimed/);
  assert.doesNotMatch(validation, /jarsigner|\|\| true/);
});
