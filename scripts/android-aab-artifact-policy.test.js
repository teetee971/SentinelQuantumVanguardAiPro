import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/build-aab-playconsole.yml'), 'utf8');
const verifyIndex = workflow.indexOf('- name: Locate, rename and verify AAB');
const uploadIndex = workflow.indexOf('- name: Upload AAB artifact');
const validation = workflow.slice(verifyIndex, uploadIndex);

test('pull request AAB builds use and attest the exact source head', () => {
  assert.match(
    workflow,
    /uses:\s*actions\/checkout@[^\n]+\n\s+with:\n\s+ref:\s*\$\{\{\s*github\.event\.pull_request\.head\.sha\s*\|\|\s*github\.sha\s*\}\}/
  );
  assert.match(workflow, /name: Assert exact source head[\s\S]*test "\$\(git rev-parse HEAD\)" = "\$TARGET_SHA"/);
  assert.match(validation, /sha256sum "\$NEW_NAME" > "\$\{NEW_NAME\}\.sha256"/);
  assert.match(workflow, /SentinelQuantumVanguard-AAB-UNSIGNED-VALIDATION-\$\{\{ github\.event\.pull_request\.head\.sha \|\| github\.sha \}\}/);
  assert.match(workflow, /native-android-app\/\$\{\{ steps\.aab\.outputs\.aab_name \}\}\.sha256/);
});

test('unsigned AAB archive integrity is checked before artifact upload', () => {
  assert.ok(verifyIndex >= 0);
  assert.ok(uploadIndex > verifyIndex);
  assert.match(validation, /unzip -tq "\$NEW_NAME"/);
  assert.match(validation, /unzip -Z1 "\$NEW_NAME"/);
  assert.match(validation, /grep -Fx 'BundleConfig\.pb'/);
  assert.match(validation, /exit 1/);
});

test('optional bundletool does not silently claim semantic validation or mask failure', () => {
  assert.match(validation, /if command -v bundletool/);
  assert.match(validation, /bundletool validate --bundle="\$NEW_NAME"/);
  assert.match(validation, /semantic bundle validation not claimed/);
  assert.doesNotMatch(validation, /jarsigner|\|\| true/);
});


test('unsigned validation AAB cannot be mistaken for a publishable release artifact', () => {
  assert.match(workflow, /^name: Build Android App Bundle \(Unsigned Validation\)$/m);
  assert.match(workflow, /SentinelQuantumVanguard-v\$\{VERSION_NAME\}-release-unsigned-validation\.aab/);
  assert.match(workflow, /SentinelQuantumVanguard-AAB-UNSIGNED-VALIDATION-\$\{\{ github\.event\.pull_request\.head\.sha \|\| github\.sha \}\}/);
  assert.doesNotMatch(workflow, /SentinelQuantumVanguard-v\$\{VERSION_NAME\}-release\.aab/);
});
