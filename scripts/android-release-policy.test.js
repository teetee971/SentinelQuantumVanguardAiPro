import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/android-release.yml'), 'utf8');

test('production Android releases require the protected environment and current main head', () => {
  assert.match(workflow, /environment:\s*\n\s+name: android-production/);
  assert.match(workflow, /\[\[ "\$SHA" != "\$MAIN_SHA" \]\]/);
  assert.match(workflow, /"\$TAG" != "v\$VERSION_NAME"/);
});

test('release integrity records the signer certificate and verifies its checksum', () => {
  assert.match(workflow, /apksigner verify --verbose --print-certs/);
  assert.match(workflow, /\.apk\.certificates\.txt/);
  assert.match(workflow, /sha256sum -c/);
  assert.match(workflow, /expected exactly one release APK/);
  assert.match(workflow, /verify-android-release-evidence\.js --root \./);
});

test('the workflow creates a draft rather than a publicly downloadable release', () => {
  assert.match(workflow, /Publish GitHub Release[\s\S]+draft: true/);
  assert.doesNotMatch(workflow, /Publish GitHub Release[\s\S]+draft: false/);
});

test('production signing never falls back to a debug keystore', () => {
  assert.doesNotMatch(workflow, /debug\.keystore|assembleDebug|signingConfig\s+debug/i);
});
