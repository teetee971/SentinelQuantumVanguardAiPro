#!/usr/bin/env node
/**
 * Fail-closed credential hygiene gate (security incident #259).
 *
 * Checks, on the tracked content of the current working tree:
 *   1. no local credential artifact (shell/SSH/CLI credential files) is tracked;
 *   2. `.gitignore` still declares the required credential ignore patterns;
 *   3. no tracked text file contains high-signal credential material.
 *
 * This is a static repository check. It proves nothing about credential
 * revocation on the provider side: revocation and rotation are external
 * actions documented in `docs/SECURITY_CREDENTIALS.md`.
 */

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(process.env.SENTINEL_CREDENTIAL_HYGIENE_ROOT || path.join(__dirname, '..'));
const MAX_FILE_BYTES = 2 * 1024 * 1024;
const BINARY_EXTENSIONS = new Set([
  '.png', '.jpg', '.jpeg', '.gif', '.webp', '.ico', '.pdf', '.zip', '.gz', '.jar',
  '.apk', '.aab', '.woff', '.woff2', '.ttf', '.otf', '.mp4', '.webm', '.bin'
]);

// Tracked-path patterns for local credential artifacts that must never be committed.
const FORBIDDEN_PATHS = [
  { id: 'git-credentials', re: /(^|\/)\.git-credentials$/, message: 'Git credential store file' },
  { id: 'ssh-directory', re: /(^|\/)\.ssh\//, message: 'SSH directory' },
  { id: 'ssh-private-key', re: /(^|\/)id_(?:rsa|dsa|ecdsa|ed25519)(?!\.pub)/, message: 'SSH private key' },
  { id: 'shell-history', re: /(^|\/)\.(?:bash|zsh|sh)_history$/, message: 'shell history file' },
  { id: 'firebase-cli-credentials', re: /(^|\/)(?:\.config\/(?:configstore\/firebase-tools\.json|firebase\/)|firebase-tools\.json)/, message: 'Firebase CLI credential/config file' },
  { id: 'service-account', re: new RegExp('(^|/)(?:' + 'google' + '-services\\.json|service[-_]?account[^/]*\\.json)$', 'i'), message: 'service account / mobile service config' },
  { id: 'private-key-file', re: /\.(?:pem|key|p12|pfx|keystore|jks|asc)$/i, message: 'private key or signing material' },
  { id: 'dotenv', re: /(^|\/)\.env(?!\.example$)(\..*)?$/, message: 'environment file with runtime values' },
  { id: 'rc-credentials', re: /(^|\/)\.(?:npmrc|netrc|pypirc|dockercfg)$/, message: 'tool credential file' },
  { id: 'secrets-directory', re: /(^|\/)secrets\//, message: 'secrets directory' }
];

// Required `.gitignore` entries. Keeping them is part of the fail-closed guard.
// This list mirrors the credential ignore section of `.gitignore` so that a
// regression (removing a hardened pattern) fails the gate.
const REQUIRED_IGNORES = [
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
];

// Patterns are assembled at runtime so this file never contains a literal
// credential shape that would trip secret scanners or this check itself.
const CONTENT_RULES = [
  { id: 'github-pat', re: new RegExp('gh' + 'p_[A-Za-z0-9]{36}'), message: 'GitHub personal access token' },
  { id: 'github-fine-grained-pat', re: new RegExp('github' + '_pat_[A-Za-z0-9_]{60,}'), message: 'GitHub fine-grained token' },
  { id: 'github-app-token', re: new RegExp('gh[sruo]' + '_[A-Za-z0-9]{36,}'), message: 'GitHub app/refresh token' },
  { id: 'google-api-key', re: new RegExp('AI' + 'za[0-9A-Za-z_-]{35}'), message: 'Google API key' },
  { id: 'google-oauth-token', re: new RegExp('ya' + '29\\.[0-9A-Za-z_-]{20,}'), message: 'Google OAuth access token' },
  { id: 'telegram-bot-token', re: new RegExp('[0-9]{8,10}:' + 'AA[0-9A-Za-z_-]{33}'), message: 'Telegram bot token' },
  { id: 'private-key-block', re: new RegExp('-----BEGIN (?:RSA |EC |DSA |OPENSSH |PGP )?PRIVATE' + ' KEY-----'), message: 'private key block' },
  { id: 'aws-access-key', re: new RegExp('AK' + 'IA[0-9A-Z]{16}'), message: 'AWS access key id' },
  { id: 'slack-token', re: new RegExp('xox[abprs]-' + '[0-9A-Za-z-]{10,}'), message: 'Slack token' }
];

function listTrackedFiles() {
  const result = spawnSync('git', ['-C', ROOT, 'ls-files', '-z'], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  if (result.status !== 0) {
    throw new Error(`git ls-files failed: ${result.stderr || result.status}`);
  }
  return result.stdout.split('\0').filter(Boolean);
}

function checkTrackedPaths(files) {
  const findings = [];
  for (const file of files) {
    for (const rule of FORBIDDEN_PATHS) {
      if (rule.re.test(file)) {
        findings.push(`${file} [${rule.id}] tracked ${rule.message}`);
      }
    }
  }
  return findings;
}

function checkGitignore() {
  const gitignorePath = path.join(ROOT, '.gitignore');
  if (!fs.existsSync(gitignorePath)) {
    return ['.gitignore [missing] credential ignore rules are required'];
  }
  const entries = new Set(
    fs.readFileSync(gitignorePath, 'utf8')
      .split(/\r?\n/)
      .map(line => line.trim())
      .filter(line => line && !line.startsWith('#'))
  );
  return REQUIRED_IGNORES
    .filter(pattern => !entries.has(pattern))
    .map(pattern => `.gitignore [missing-pattern] ${pattern}`);
}

function checkTrackedContent(files) {
  const findings = [];
  for (const file of files) {
    if (BINARY_EXTENSIONS.has(path.extname(file).toLowerCase())) continue;
    const full = path.join(ROOT, file);
    let stat;
    try {
      stat = fs.statSync(full);
    } catch {
      continue; // path tracked but absent from the working tree
    }
    if (!stat.isFile() || stat.size > MAX_FILE_BYTES) continue;
    const lines = fs.readFileSync(full, 'utf8').split(/\r?\n/);
    lines.forEach((line, index) => {
      for (const rule of CONTENT_RULES) {
        if (rule.re.test(line)) {
          findings.push(`${file}:${index + 1} [${rule.id}] ${rule.message}`);
        }
      }
    });
  }
  return findings;
}

const files = listTrackedFiles();
const findings = [
  ...checkTrackedPaths(files),
  ...checkGitignore(),
  ...checkTrackedContent(files)
];

if (findings.length) {
  console.error('Credential hygiene gate: FAILED');
  findings.forEach(finding => console.error(`- ${finding}`));
  console.error('See docs/SECURITY_CREDENTIALS.md for revocation and rotation requirements.');
  process.exit(1);
}

console.log(`Credential hygiene gate: PASS (${files.length} tracked files checked)`);
