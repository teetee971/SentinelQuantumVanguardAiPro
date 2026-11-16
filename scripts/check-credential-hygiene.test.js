import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const script = path.resolve('scripts/check-credential-hygiene.js');

function runIn(root) {
  return spawnSync(process.execPath, [script], {
    encoding: 'utf8',
    env: { ...process.env, SENTINEL_CREDENTIAL_HYGIENE_ROOT: root }
  });
}

function createFixtureRepo(files) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'sentinel-credential-hygiene-'));
  spawnSync('git', ['-C', root, 'init', '-q'], { encoding: 'utf8' });
  for (const [relative, content] of Object.entries(files)) {
    const full = path.join(root, relative);
    fs.mkdirSync(path.dirname(full), { recursive: true });
    fs.writeFileSync(full, content);
  }
  spawnSync('git', ['-C', root, 'add', '-A', '-f'], { encoding: 'utf8' });
  return root;
}

const COMPLIANT_GITIGNORE = [
  '.git-credentials',
  '**/.git-credentials',
  '.ssh/',
  '**/.ssh/',
  '.bash_history',
  '**/.bash_history',
  '.zsh_history',
  '**/.zsh_history',
  '.sh_history',
  '**/.sh_history',
  '.config/configstore/firebase-tools.json',
  '**/.config/configstore/firebase-tools.json',
  '.config/firebase/',
  '**/.config/firebase/',
  '**/id_rsa',
  '**/id_dsa',
  '**/id_ecdsa',
  '**/id_ed25519',
  '*.pem',
  '*.key',
  '.env',
  '.env.local',
  '.env.*.local',
  '.npmrc',
  '**/.npmrc',
  '.netrc',
  '**/.netrc',
  'secrets/',
  '**/secrets/'
].join('\n');

test('credential hygiene gate passes on the current repository', () => {
  const result = spawnSync(process.execPath, [script], { encoding: 'utf8' });
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
});

test('credential hygiene gate fails when a credential artifact is tracked', () => {
  const root = createFixtureRepo({
    '.gitignore': COMPLIANT_GITIGNORE,
    '.git-credentials': 'placeholder\n'
  });
  const result = runIn(root);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /git-credentials/);
});

test('credential hygiene gate fails when a required ignore pattern is removed', () => {
  const root = createFixtureRepo({
    '.gitignore': COMPLIANT_GITIGNORE.replace('.git-credentials\n', '')
  });
  const result = runIn(root);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /missing-pattern/);
});

test('credential hygiene gate fails when shell-history ignore patterns are missing', () => {
  const root = createFixtureRepo({
    '.gitignore': COMPLIANT_GITIGNORE.replace(/\*\*\/\.bash_history\n/g, '')
  });
  const result = runIn(root);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /missing-pattern.*bash_history/);
});

test('credential hygiene gate fails on high-signal credential material', () => {
  const root = createFixtureRepo({
    '.gitignore': COMPLIANT_GITIGNORE,
    'config/sample.js': `const token = '${'gh' + 'p_'}${'a'.repeat(36)}';\n`
  });
  const result = runIn(root);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /github-pat/);
});

test('credential hygiene gate accepts a placeholder environment template', () => {
  const root = createFixtureRepo({
    '.gitignore': COMPLIANT_GITIGNORE,
    '.env.example': 'DATABASE_URL=replace_me\n'
  });
  const result = runIn(root);
  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
});
