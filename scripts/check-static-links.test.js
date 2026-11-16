import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const scriptPath = join(rootDir, 'scripts', 'check-static-links.js');

function runScanner() {
  return spawnSync(process.execPath, [scriptPath], {
    cwd: rootDir,
    encoding: 'utf8',
  });
}

test('ignores known generated Android build output', async () => {
  const buildDir = join(rootDir, 'native-android-app', 'build');
  await mkdir(buildDir, { recursive: true });
  const fixtureDir = await mkdtemp(join(buildDir, 'static-link-generated-'));
  try {
    await writeFile(join(fixtureDir, 'report.html'), '<a href="./missing-generated.html">generated</a>\n');
    const result = runScanner();
    assert.equal(result.status, 0, result.stderr || result.stdout);
  } finally {
    await rm(fixtureDir, { recursive: true, force: true });
  }
});

test('still scans a real source directory named build', async () => {
  const fixtureRoot = await mkdtemp(join(rootDir, 'static-link-source-'));
  const sourceBuild = join(fixtureRoot, 'build');
  try {
    await mkdir(sourceBuild, { recursive: true });
    await writeFile(join(sourceBuild, 'broken.html'), '<a href="./missing-source.html">broken</a>\n');
    const result = runScanner();
    assert.equal(result.status, 1, result.stdout);
    assert.match(result.stderr, /missing-source\.html/);
  } finally {
    await rm(fixtureRoot, { recursive: true, force: true });
  }
});
