import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, mkdir, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {
  checkSentinelIsolation,
  scanContentForViolations,
  isForbiddenFilename,
} from './check-sentinel-isolation.js';

const firebase = ['fire', 'base'].join('');
const projectName = ['aki', 'pri', 'sa', 'ye'].join('');
const forbiddenJson = ['google-services', '.json'].join('');

const mustDetect = [
  ['static Firebase import', `import { initializeApp } from "${firebase}/app";`],
  ['dynamic Firebase import', `const app = await import('${firebase}/app');`],
  ['Firebase require', `const fb = require('${firebase}/app');`],
  ['Firebase require with space', `const fb = require ( '${firebase}/app' );`],
  ['firebase-admin', `import admin from '${firebase}-admin';`],
  ['firebase-messaging', `import messaging from '${firebase}-messaging';`],
  ['@react-native-firebase', `import messaging from '@react-native-${firebase}/messaging';`],
  ['firebaseConfig literal', `const firebaseConfig = { apiKey: "x" };`],
  ['FIREBASE env prefix', 'const key = process.env.FIREBASE_API_KEY;'],
  ['google-services.json string', `// ${forbiddenJson} must never be present`],
  ['A KI PRI SA YE spaced', `// ${projectName} is forbidden operationally`],
  ['A KI PRI SA YÉ hyphenated', 'const id = "a-ki-pri-sa-ye";'],
  ['A KI PRI SA YÉ concatenated', 'const pkg = "com.akiprisaye.app";'],
  ['com.google.firebase gradle', `implementation 'com.google.${firebase}:${firebase}-messaging'`],
];

for (const [label, content] of mustDetect) {
  test(`detects violation: ${label}`, () => {
    const violations = scanContentForViolations(content, 'synthetic.js');
    assert.ok(violations.length > 0, `expected a violation for: ${label}`);
  });
}

test('google-services.json is flagged by filename regardless of content', () => {
  assert.equal(isForbiddenFilename(forbiddenJson), true);
  assert.equal(isForbiddenFilename('GOOGLE-SERVICES.JSON'), true);
});

test('GoogleService-Info.plist is flagged by filename regardless of content', () => {
  assert.equal(isForbiddenFilename('googleservice-info.plist'), true);
  assert.equal(isForbiddenFilename('GOOGLESERVICE-INFO.PLIST'), true);
});

test('unrelated filenames are not flagged', () => {
  assert.equal(isForbiddenFilename('services.json'), false);
  assert.equal(isForbiddenFilename('config.json'), false);
});

const mustNotDetect = [
  ['plain JavaScript', 'const campfire = igniteFire();'],
  ['unrelated word containing base', 'class DatabaseConnector {}'],
  ['unrelated package name', "import { z } from 'zod';"],
  ['ordinary Android Kotlin', 'class SecurityAudit { fun check() = true }'],
];

for (const [label, content] of mustNotDetect) {
  test(`does not falsely flag: ${label}`, () => {
    const violations = scanContentForViolations(content, 'synthetic.js');
    assert.equal(violations.length, 0, `unexpected violation for: ${label}`);
  });
}

test('repository scanner detects nested Kotlin and forbidden filenames', async () => {
  const tempRoot = await mkdtemp(path.join(os.tmpdir(), 'sentinel-isolation-'));
  try {
    await mkdir(path.join(tempRoot, 'core', 'nested'), { recursive: true });
    await writeFile(path.join(tempRoot, 'core', 'nested', 'safe.kt'), 'class Safe');
    await writeFile(path.join(tempRoot, 'core', 'nested', 'bad.kt'), `import ${JSON.stringify(firebase)}`);

    const contentViolation = await checkSentinelIsolation(tempRoot);
    assert.equal(contentViolation.passed, false, JSON.stringify(contentViolation));
    assert.ok(contentViolation.violations.some((v) => v.pattern === 'firebase-static-import'));

    await writeFile(path.join(tempRoot, forbiddenJson), '{}');
    const filenameViolation = await checkSentinelIsolation(tempRoot);
    assert.equal(filenameViolation.passed, false, JSON.stringify(filenameViolation));
    assert.ok(filenameViolation.violations.some((v) => v.pattern === 'forbidden-filename-present'));
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
  }
});

test('repository scanner fails closed on symlinks', async () => {
  const tempRoot = await mkdtemp(path.join(os.tmpdir(), 'sentinel-isolation-'));
  const outsideRoot = await mkdtemp(path.join(os.tmpdir(), 'sentinel-outside-'));
  try {
    await writeFile(path.join(outsideRoot, 'secret.js'), 'export const hidden = true;');
    await symlink(path.join(outsideRoot, 'secret.js'), path.join(tempRoot, 'hidden.js'));

    const result = await checkSentinelIsolation(tempRoot);
    assert.equal(result.passed, false, JSON.stringify(result));
    assert.ok(result.violations.some((v) => v.pattern === 'scan-error:symlink_not_allowed'));
  } finally {
    await rm(tempRoot, { recursive: true, force: true });
    await rm(outsideRoot, { recursive: true, force: true });
  }
});

test('current repository tree passes the isolation gate', async () => {
  const result = await checkSentinelIsolation();
  assert.equal(result.passed, true, JSON.stringify(result.violations, null, 2));
  assert.equal(result.violations.length, 0);
  assert.equal(result.read_errors.length, 0);
});

test('critical operational extensions remain covered', async () => {
  const { ALLOWED_TEXT } = await import('./check-sentinel-isolation.js');
  for (const required of ['.kt', '.kts', '.toml', '.lock', '.json', '.yml']) {
    assert.ok(ALLOWED_TEXT.has(required), `${required} must remain in ALLOWED_TEXT`);
  }
});

console.log('sentinel isolation regression suite loaded');
