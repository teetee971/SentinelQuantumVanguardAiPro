import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {
  checkSentinelIsolation,
  isForbiddenFilename,
  scanContentForViolations,
} from './check-sentinel-isolation.js';

const fireBase = ['fire', 'base'].join('');
const projectName = ['aki', 'pri', 'sa', 'ye'].join('');
const forbiddenJson = ['google-services', '.json'].join('');

const dynamicImport = `import(${JSON.stringify(`${fireBase}/app`)})`;
const spacedRequire = `require ( ${JSON.stringify(fireBase)} )`;
const hyphenatedProject = 'a-ki-pri-sa-ye';

assert.ok(scanContentForViolations(dynamicImport, 'fixture.js').some((v) => v.pattern === 'firebase-dynamic-import'));
assert.ok(scanContentForViolations(spacedRequire, 'fixture.js').some((v) => v.pattern === 'firebase-require'));
assert.ok(scanContentForViolations(`const x = '${projectName}';`, 'fixture.js').some((v) => v.pattern === 'akiprisaye-reference'));
assert.ok(scanContentForViolations(hyphenatedProject, 'fixture.js').some((v) => v.pattern === 'akiprisaye-reference'));
assert.equal(isForbiddenFilename(forbiddenJson), true);
assert.equal(isForbiddenFilename('normal.json'), false);

const tempRoot = await mkdtemp(path.join(os.tmpdir(), 'sentinel-isolation-'));
try {
  await mkdir(path.join(tempRoot, 'core', 'nested'), { recursive: true });
  await writeFile(path.join(tempRoot, 'core', 'nested', 'safe.js'), 'export const ok = true;');
  await writeFile(path.join(tempRoot, 'core', 'nested', 'bad.kt'), `import ${JSON.stringify(fireBase)}`);

  const clean = await checkSentinelIsolation(tempRoot);
  assert.equal(clean.passed, false, JSON.stringify(clean));
  assert.ok(clean.violations.some((v) => v.pattern === 'firebase-static-import'));

  await writeFile(path.join(tempRoot, forbiddenJson), '{}');
  const filenameViolation = await checkSentinelIsolation(tempRoot);
  assert.equal(filenameViolation.passed, false, JSON.stringify(filenameViolation));
  assert.ok(filenameViolation.violations.some((v) => v.pattern === 'forbidden-filename-present'));

  const outsideRoot = await mkdtemp(path.join(os.tmpdir(), 'sentinel-outside-'));
  try {
    await writeFile(path.join(outsideRoot, 'secret.js'), 'export const hidden = true;');
    await symlink(path.join(outsideRoot, 'secret.js'), path.join(tempRoot, 'hidden.js'));
    const symlinkResult = await checkSentinelIsolation(tempRoot);
    assert.equal(symlinkResult.passed, false, JSON.stringify(symlinkResult));
    assert.ok(symlinkResult.violations.some((v) => v.pattern === 'scan-error:symlink_not_allowed'));
  } finally {
    await rm(outsideRoot, { recursive: true, force: true });
  }
} finally {
  await rm(tempRoot, { recursive: true, force: true });
}

const result = await checkSentinelIsolation();
assert.equal(result.passed, true, JSON.stringify(result.violations));
assert.equal(result.violations.length, 0);

console.log(`sentinel isolation test: PASS (${result.files_scanned} files scanned)`);
