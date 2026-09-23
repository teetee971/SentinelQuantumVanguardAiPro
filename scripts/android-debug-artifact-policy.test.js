import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/build-native-android.yml'), 'utf8');

test('debug APK is verified before artifact upload', () => {
  const buildIndex = workflow.indexOf('- name: Build debug APK');
  const verifyIndex = workflow.indexOf('- name: Verify debug APK package, alignment and signature');
  const uploadIndex = workflow.indexOf('- name: Upload APK artifact');
  assert.ok(buildIndex >= 0);
  assert.ok(verifyIndex > buildIndex);
  assert.ok(uploadIndex > verifyIndex);
});

test('debug APK verification checks package, alignment and signature', () => {
  assert.match(workflow, /aapt" dump badging/);
  assert.match(workflow, /com\.sentinel\.quantum/);
  assert.match(workflow, /zipalign" -c -P 16 -v 4/);
  assert.match(workflow, /apksigner" verify --verbose --print-certs/);
  assert.match(workflow, /test -s "\$APK_PATH"/);
});
