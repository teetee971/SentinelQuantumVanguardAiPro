import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const ignoredDirectories = new Set(['.git', 'node_modules', 'frontend']);

function filesUnder(directory) {
  const result = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (ignoredDirectories.has(entry.name)) continue;
    const absolutePath = join(directory, entry.name);
    if (entry.isDirectory()) result.push(...filesUnder(absolutePath));
    else result.push(absolutePath);
  }
  return result;
}

const repositoryFiles = filesUnder(rootDir);

function read(relativePath) {
  return readFileSync(join(rootDir, relativePath), 'utf8');
}

function runtimeJavascriptFiles() {
  return repositoryFiles.filter((file) => {
    const relativePath = relative(rootDir, file).replaceAll('\\', '/');
    return /\.(?:js|mjs|cjs|ts|tsx)$/.test(relativePath)
      && relativePath !== 'config/feature-flags.js'
      && relativePath !== 'config/feature-flags.test.js'
      && relativePath !== 'scripts/product-integration-audit.test.js'
      && !relativePath.endsWith('.test.js');
  });
}

test('canonical capability evidence and source scope resolve to repository files', () => {
  const registry = JSON.parse(read('config/product-capabilities.json'));
  assert.ok(Array.isArray(registry.capabilities) && registry.capabilities.length > 0);

  for (const capability of registry.capabilities) {
    for (const field of ['evidence', 'source_scope']) {
      for (const path of capability[field] ?? []) {
        assert.equal(
          existsSync(join(rootDir, path)),
          true,
          `${capability.id}.${field} points to missing file: ${path}`
        );
      }
    }
  }
});

test('feature flags have a non-test runtime consumer', () => {
  const consumers = runtimeJavascriptFiles().filter((file) => {
    const source = readFileSync(file, 'utf8');
    return /(?:from\s+['"][^'"]*feature-flags\.js['"]|import\s*\(\s*['"][^'"]*feature-flags\.js['"]|require\(\s*['"][^'"]*feature-flags\.js['"])/.test(source);
  });

  assert.notEqual(
    consumers.length,
    0,
    'config/feature-flags.js is not connected to any non-test runtime module'
  );
});

test('generated Android capability state is read by application code', () => {
  const generatedPath = join(
    rootDir,
    'native-android-app/app/src/main/java/com/sentinel/quantum/security/GeneratedProductCapabilities.kt'
  );
  assert.equal(existsSync(generatedPath), true, 'generated Android capability file is missing');

  const consumers = repositoryFiles.filter((file) => {
    const relativePath = relative(rootDir, file).replaceAll('\\', '/');
    return relativePath.endsWith('.kt')
      && relativePath !== 'native-android-app/app/src/main/java/com/sentinel/quantum/security/GeneratedProductCapabilities.kt'
      && /\bGeneratedProductCapabilities\b/.test(readFileSync(file, 'utf8'));
  });

  assert.notEqual(
    consumers.length,
    0,
    'GeneratedProductCapabilities.kt is generated but never read by Android application code'
  );
});
