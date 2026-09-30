#!/usr/bin/env node
'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const REPOSITORY_ROOT = path.resolve(__dirname, '..');
const FORBIDDEN_TOKEN_SHA256 =
  'c92f0a55ddf68fe82027e7555eabb323a3872e7998851f2b3282fd8daeed70d3';

function hashToken(token) {
  return crypto.createHash('sha256').update(token.toLowerCase(), 'utf8').digest('hex');
}

function containsForbiddenToken(text) {
  const tokens = String(text).match(/[A-Za-z0-9]+/g) || [];
  return tokens.some((token) => hashToken(token) === FORBIDDEN_TOKEN_SHA256);
}

function isProbablyBinary(buffer) {
  const sampleLength = Math.min(buffer.length, 8192);
  for (let index = 0; index < sampleLength; index += 1) {
    if (buffer[index] === 0) return true;
  }
  return false;
}

function trackedFiles() {
  const output = execFileSync('git', ['ls-files', '-z'], {
    cwd: REPOSITORY_ROOT,
    encoding: 'utf8',
    maxBuffer: 32 * 1024 * 1024
  });
  return output.split('\0').filter(Boolean);
}

function main() {
  const violations = [];
  for (const relativePath of trackedFiles()) {
    if (containsForbiddenToken(relativePath)) {
      violations.push(relativePath + ': filename');
      continue;
    }

    const absolutePath = path.join(REPOSITORY_ROOT, relativePath);
    let buffer;
    try {
      buffer = fs.readFileSync(absolutePath);
    } catch (error) {
      violations.push(relativePath + ': unreadable tracked file');
      continue;
    }
    if (isProbablyBinary(buffer)) continue;

    const text = buffer.toString('utf8');
    if (containsForbiddenToken(text)) {
      violations.push(relativePath + ': content');
    }
  }

  if (violations.length > 0) {
    process.stderr.write(
      'External reference-brand isolation check failed:\n' +
      violations.map((item) => ' - ' + item).join('\n') +
      '\n'
    );
    process.exit(1);
  }

  process.stdout.write('External reference-brand isolation check passed.\n');
}

if (require.main === module) main();

module.exports = { containsForbiddenToken };
