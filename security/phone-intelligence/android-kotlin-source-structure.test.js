import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';

const sourceRoot = 'native-android-app/app/src';

function kotlinFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return kotlinFiles(entryPath);
    return entry.isFile() && entry.name.endsWith('.kt') ? [entryPath] : [];
  });
}

function delimiterError(source) {
  let state = 'code';
  let blockCommentDepth = 0;
  let delimiterDepth = 0;
  let line = 1;
  let escaped = false;

  for (let index = 0; index < source.length; index += 1) {
    const character = source[index];
    const next = source[index + 1];

    if (character === '\n') line += 1;

    if (state === 'line-comment') {
      if (character === '\n') state = 'code';
      continue;
    }
    if (state === 'block-comment') {
      if (character === '/' && next === '*') {
        blockCommentDepth += 1;
        index += 1;
      } else if (character === '*' && next === '/') {
        blockCommentDepth -= 1;
        index += 1;
        if (blockCommentDepth === 0) state = 'code';
      }
      continue;
    }
    if (state === 'string') {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === '"') state = 'code';
      continue;
    }
    if (state === 'raw-string') {
      if (source.slice(index, index + 3) === '\"\"\"') {
        state = 'code';
        index += 2;
      }
      continue;
    }
    if (state === 'char') {
      if (escaped) escaped = false;
      else if (character === '\\') escaped = true;
      else if (character === "'") state = 'code';
      continue;
    }

    if (character === '/' && next === '/') {
      state = 'line-comment';
      index += 1;
    } else if (character === '/' && next === '*') {
      state = 'block-comment';
      blockCommentDepth = 1;
      index += 1;
    } else if (source.slice(index, index + 3) === '\"\"\"') {
      state = 'raw-string';
      index += 2;
    } else if (character === '"') {
      state = 'string';
    } else if (character === "'") {
      state = 'char';
    } else if (character === '{') {
      delimiterDepth += 1;
    } else if (character === '}') {
      delimiterDepth -= 1;
      if (delimiterDepth < 0) return `line ${line}: closing delimiter before opening`;
    }
  }

  if (state === 'block-comment') return `line ${line}: unterminated block comment`;
  if (state === 'string' || state === 'raw-string' || state === 'char') {
    return `line ${line}: unterminated literal`;
  }
  return delimiterDepth === 0 ? null : `line ${line}: delimiter depth ${delimiterDepth}`;
}

test('Android Kotlin sources have balanced structural delimiters', () => {
  const failures = kotlinFiles(sourceRoot)
    .map((filePath) => [filePath, delimiterError(fs.readFileSync(filePath, 'utf8'))])
    .filter(([, error]) => error)
    .map(([filePath, error]) => `${filePath}: ${error}`);

  assert.deepEqual(failures, [], failures.join('\n'));
});
