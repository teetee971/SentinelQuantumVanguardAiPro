import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { execFileSync, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { resolve, join } from 'node:path';

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

test('delivered APK checksum and identity describe actual build and PR source', () => {
  const prepare = workflow.split('- name: Get version and prepare APK')[1]
    .split('- name: Upload APK artifact')[0];
  const script = prepare.split('run: |\n')[1].split('\n')
    .map(line => line.startsWith('          ') ? line.slice(10) : line).join('\n');
  const root = mkdtempSync(join(tmpdir(), 'sentinel-apk-identity-'));
  try {
    mkdirSync(join(root, 'app/build/outputs/apk/debug'), { recursive: true });
    writeFileSync(join(root, 'app/build.gradle'), 'versionName "test-fixture"\n');
    const bytes = Buffer.from([0, 1, 2, 255, 10, 13, 0, 42]);
    writeFileSync(join(root, 'app/build/outputs/apk/debug/app-debug.apk'), bytes);
    execFileSync('bash', ['-euo', 'pipefail', '-c', script], {
      cwd: root,
      stdio: 'inherit',
      env: {
        PATH: process.env.PATH,
        GITHUB_OUTPUT: join(root, 'outputs'),
        // The delivered identity must bind to the PR source head, not the merge/check-out SHA.
        GITHUB_SHA: 'd'.repeat(40),
        SOURCE_HEAD_SHA: 'b'.repeat(40),
        SOURCE_BASE_SHA: 'c'.repeat(40),
        SOURCE_HEAD_REF: 'feature/provenance',
        GITHUB_REF: 'refs/pull/123/merge',
        GITHUB_REPOSITORY: 'fixture/repository',
        GITHUB_RUN_ID: '456',
        GITHUB_RUN_ATTEMPT: '2'
      }
    });
    const name = 'SentinelQuantumVanguard-vtest-fixture-debug.apk';
    assert.deepEqual(readFileSync(join(root, name)), bytes);
    const digest = createHash('sha256').update(bytes).digest('hex');
    assert.equal(readFileSync(join(root, name + '.sha256'), 'utf8'), digest + '  ' + name + '\n');
    const identity = JSON.parse(readFileSync(join(root, name + '.build.json'), 'utf8'));
    assert.deepEqual(identity, {
      schema_version: 2,
      channel: 'debug-physical-test-candidate',
      apk_name: name,
      apk_sha256: digest,
      apk_size_bytes: bytes.length,
      build_commit: 'b'.repeat(40),
      source_head_commit: 'b'.repeat(40),
      source_base_commit: 'c'.repeat(40),
      source_head_ref: 'feature/provenance',
      source_ref: 'refs/pull/123/merge',
      repository: 'fixture/repository',
      run_id: '456',
      run_attempt: '2',
      physical_validation: 'not-executed',
      functional_100_percent: false
    });
    // A modified APK must fail the same verification shipped to the tester.
    writeFileSync(join(root, name), Buffer.from('modified bytes'));
    const tampered = spawnSync('sha256sum', ['--check', name + '.sha256'], { cwd: root, stdio: 'inherit' });
    assert.notEqual(tampered.status, 0);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('APK artifacts are named with exact PR source head when available', () => {
  const exactHeadExpression = '${{ github.event.pull_request.head.sha || github.sha }}';
  assert.ok(workflow.includes('name: SentinelQuantumVanguard-APK-' + exactHeadExpression));
  assert.ok(workflow.includes('name: PhoneCore-Android16-UI-' + exactHeadExpression));
});

test('APK delivery uploads the APK, its checksum and its build identity', () => {
  const upload = workflow.split('- name: Upload APK artifact')[1];
  for (const suffix of ['', '.sha256', '.build.json']) {
    assert.ok(upload.includes('native-android-app/${{ steps.version.outputs.apk_name }}' + suffix + '\n'));
  }
  assert.ok(workflow.indexOf('sha256sum --check') < workflow.indexOf('- name: Upload APK artifact'));
});

