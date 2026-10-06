import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const workflow = readFileSync(resolve('.github/workflows/android-release.yml'), 'utf8');
const upgradeWorkflow = readFileSync(resolve('.github/workflows/android-upgrade-safe-apk.yml'), 'utf8');
const androidBuild = readFileSync(resolve('native-android-app/app/build.gradle'), 'utf8');

test('production Android releases require the protected environment and current main head', () => {
  assert.match(workflow, /environment:\s*\n\s+name: android-production/);
  assert.match(workflow, /\[\[ "\$SHA" != "\$MAIN_SHA" \]\]/);
  assert.match(workflow, /"\$TAG" != "v\$VERSION_NAME"/);
});

test('release integrity records APK and AAB signer evidence and verifies checksums', () => {
  assert.match(workflow, /apksigner verify --verbose --print-certs/);
  assert.match(workflow, /\.apk\.certificates\.txt/);
  assert.match(workflow, /sha256sum -c/);
  assert.match(workflow, /expected exactly one release APK/);
  assert.match(workflow, /bundleRelease/);
  assert.match(workflow, /expected exactly one signed release AAB/);
  assert.match(workflow, /jarsigner -verify -strict/);
  assert.match(workflow, /keytool -printcert -jarfile/);
  assert.match(workflow, /\.aab\.sha256/);
  assert.match(workflow, /\.aab\.certificates\.txt/);
  assert.match(workflow, /verify-android-release-evidence\.js --root \./);
});

test('the workflow creates a draft rather than a publicly downloadable release', () => {
  assert.match(workflow, /Publish GitHub Release[\s\S]+draft: true/);
  assert.doesNotMatch(workflow, /Publish GitHub Release[\s\S]+draft: false/);
});

test('production signing never falls back to a debug keystore', () => {
  assert.doesNotMatch(workflow, /debug\.keystore|assembleDebug|signingConfig\s+debug/i);
});

test('releaseUnsigned explicitly clears any signing config inherited from release', () => {
  const block = androidBuild.match(/releaseUnsigned\s*\{([\s\S]*?)\n\s*\}/)?.[1] ?? '';
  assert.match(block, /initWith\s+release/);
  assert.match(block, /signingConfig\s*=\s*null/);
});

test('Android versionCode accepts only a bounded explicit CI override', () => {
  assert.match(androidBuild, /SENTINEL_VERSION_CODE/);
  assert.match(androidBuild, /sentinelVersionCodeRaw ==~ \/\^\[0-9\]\+\$\//);
  assert.match(androidBuild, /sentinelVersionCode < 7 \|\| sentinelVersionCode > 2100000000/);
  assert.match(androidBuild, /versionCode sentinelVersionCode/);
});

test('upgrade-safe tester APK is manual, protected, and restricted to current main', () => {
  assert.match(upgradeWorkflow, /^on:\s*\n\s+workflow_dispatch:/m);
  assert.doesNotMatch(upgradeWorkflow, /^\s+push:/m);
  assert.match(upgradeWorkflow, /environment:\s*\n\s+name: android-production/);
  assert.match(upgradeWorkflow, /MAIN_SHA="\$\(git rev-parse origin\/main\)"/);
  assert.match(upgradeWorkflow, /\[\[ "\$GITHUB_SHA" != "\$MAIN_SHA" \]\]/);
  assert.match(upgradeWorkflow, /KEYSTORE_BASE64: \$\{\{ secrets\.KEYSTORE_BASE64 \}\}/);
  assert.doesNotMatch(upgradeWorkflow, /debug\.keystore|assembleDebug|signingConfig\s+debug/i);
});

test('upgrade-safe tester APK proves same signer and strictly increasing versionCode', () => {
  assert.match(upgradeWorkflow, /BASE_CODE=\$\(\(COMMIT_COUNT \* 10\)\)/);
  assert.match(upgradeWorkflow, /CANDIDATE_CODE=\$\(\(BASE_CODE \+ 1\)\)/);
  assert.match(upgradeWorkflow, /SENTINEL_VERSION_CODE: \$\{\{ steps\.versions\.outputs\.base_code \}\}/);
  assert.match(upgradeWorkflow, /SENTINEL_VERSION_CODE: \$\{\{ steps\.versions\.outputs\.candidate_code \}\}/);
  assert.match(upgradeWorkflow, /test "\$BASE_CERT" = "\$CANDIDATE_CERT"/);
  assert.match(upgradeWorkflow, /adb install -r "\$RUNNER_TEMP\/sentinel-candidate\.apk"/);
});

test('upgrade qualification cannot silently become uninstall/reinstall', () => {
  assert.doesNotMatch(upgradeWorkflow, /adb\s+uninstall|pm\s+clear/i);
  assert.match(upgradeWorkflow, /uninstall_used_for_upgrade_proof": false/);
  assert.match(upgradeWorkflow, /same_signer_proven": true/);
  assert.match(upgradeWorkflow, /physical_validation": "not-executed"/);
  assert.match(upgradeWorkflow, /functional_100_percent": false/);
});
