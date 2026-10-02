import assert from 'node:assert/strict';
import test from 'node:test';
import { publish, validateRefresh } from './publish-numbering-refresh.js';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const before = { schemaVersion: 1, country: 'FR', entryFields: ['allocationDate'], entries: Array.from({ length: 100 }, () => ['01/09/2026']), recordCount: 100, sourcePublishedAt: '2026-09-01' };
test('valid refresh and sources without publication dates are allowed', () => {
  validateRefresh(before, { ...before, sourcePublishedAt: '2026-10-01' }, 'arcep');
  validateRefresh({ ...before, sourcePublishedAt: null }, { ...before, sourcePublishedAt: null }, 'arcep');
});
test('publication rollback and removal of a known date are rejected', () => {
  for (const sourcePublishedAt of ['2026-08-01', null]) {
    assert.throws(() => validateRefresh(before, { ...before, sourcePublishedAt }, 'arcep'), /PUBLICATION_ROLLBACK/);
  }
});
test('allocation maximum cannot regress silently', () => {
  assert.throws(() => validateRefresh(before, { ...before, entries: Array.from({ length: 100 }, () => ['31/08/2026']) }, 'arcep'), /ALLOCATION_ROLLBACK/);
});
test('large losses, empty records, country and schema changes are rejected', () => {
  for (const update of [{ recordCount: 89 }, { recordCount: 0 }, { country: 'GB' }, { schemaVersion: 2 }, { entryFields: ['different'] }]) {
    assert.throws(() => validateRefresh(before, { ...before, ...update }, 'arcep'));
  }
  validateRefresh(before, { ...before, recordCount: 90, entries: before.entries.slice(0, 90) }, 'arcep');
});
test('unknown targets and malformed reports fail closed', () => {
  assert.throws(() => validateRefresh(before, before, '../unknown'), /UNKNOWN_TARGET/);
  assert.throws(() => validateRefresh(null, {}, 'sources'), /INVALID_WATCH_REPORT/);
});
const env = { GITHUB_ACTIONS: 'true', GITHUB_REF: 'refs/heads/main', GITHUB_EVENT_NAME: 'schedule' };
function fakeGit({ moved = false, staged = false, unchanged = false } = {}) {
  const calls = [];
  const git = (...args) => {
    calls.push(args);
    if (args[0] === 'status') return unchanged ? '' : ' M public/data/arcep-numbering.json';
    if (args[0] === 'diff') return staged ? 'unrelated.js' : '';
    if (args[0] === 'show') return JSON.stringify(before);
    if (args[0] === 'rev-parse') return args[1] === 'origin/main' && moved ? 'new-base' : 'base';
    return '';
  };
  return { git, calls, gh: (...args) => { calls.push(['gh', ...args]); return ''; } };
}
test('proposes one allowlisted file on an automation branch and never pushes main', () => {
  const fake = fakeGit();
  assert.equal(publish('arcep', { ...fake, env, read: () => JSON.stringify({ ...before, sourcePublishedAt: '2026-10-01' }) }), true);
  assert.deepEqual(fake.calls.find(args => args[0] === 'add'), ['add', '--', 'public/data/arcep-numbering.json']);
  const push = fake.calls.find(args => args[0] === 'push');
  assert.match(push[2], /^HEAD:refs\/heads\/automation\/numbering-arcep-[a-f0-9]{16}$/);
  assert.equal(fake.calls.at(-1)[1], 'pr');
  assert.equal(fake.calls.at(-1)[2], 'create');
});
test('main moving, unexpected staged files, and non-main runs never push', () => {
  for (const scenario of [{ moved: true }, { staged: true }]) {
    const fake = fakeGit(scenario);
    assert.throws(() => publish('arcep', { ...fake, env, read: () => JSON.stringify({ ...before, sourcePublishedAt: '2026-10-01' }) }));
    assert.equal(fake.calls.some(args => args[0] === 'push'), false);
  }
  const fake = fakeGit();
  assert.throws(() => publish('arcep', { ...fake, env: { ...env, GITHUB_REF: 'refs/heads/feature' } }), /MAIN_WORKFLOW_REQUIRED/);
  assert.equal(fake.calls.length, 0);
});
test('unchanged data is a no-op', () => {
  const fake = fakeGit({ unchanged: true });
  assert.equal(publish('arcep', { ...fake, env }), false);
  assert.equal(fake.calls.length, 1);
});

test('RTR grouped snapshots use their own full structural validator before publication', () => {
  const data = JSON.parse(readFileSync('public/data/rtr-numbering.json', 'utf8'));
  validateRefresh(data, data, 'rtr');
  assert.throws(() => validateRefresh(data, { ...data, recordCount: data.recordCount - 1 }, 'rtr'), /INVALID_RTR_DIRECTORY/);
});

test('real git publication handles a snapshot above the default 1 MiB process-output limit', async context => {
  const directory = await mkdtemp(join(tmpdir(), 'numbering-publish-git-'));
  const working = join(directory, 'working');
  const remote = join(directory, 'remote.git');
  const git = (...args) => execFileSync('git', args, { cwd: working, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    await mkdir(working);
    try { execFileSync('git', ['init', '--bare', '--initial-branch=main', remote], { stdio: 'pipe' }); }
    catch (error) {
      if (error.code === 'EPERM' && process.env.GITHUB_ACTIONS !== 'true') {
        context.skip('Local sandbox prevents child-process execution; this integration test is mandatory in GitHub Actions.');
        return;
      }
      throw error;
    }
    git('init', '--initial-branch=main');
    git('config', 'user.name', 'Numbering Test');
    git('config', 'user.email', 'numbering-test@example.invalid');
    const original = await readFile('public/data/rtr-numbering.json', 'utf8');
    assert.ok(Buffer.byteLength(original) > 1024 * 1024);
    await mkdir(join(working, 'public/data'), { recursive: true });
    await writeFile(join(working, 'public/data/rtr-numbering.json'), original);
    git('add', '.');
    git('commit', '-m', 'baseline');
    git('remote', 'add', 'origin', remote);
    git('push', '--set-upstream', 'origin', 'main');
    const next = JSON.parse(original);
    next.generatedAt = '2026-10-02T01:00:00Z';
    // A new official publication day is a version change; generation time alone is not.
    next.sourcePublishedAt = '2026-10-02';
    await writeFile(join(working, 'public/data/rtr-numbering.json'), JSON.stringify(next));
    const baseline = git('rev-parse', 'HEAD').trim();
    publish('rtr', { git: (...args) => git(...args).trim(), read: path => readFileSync(join(working, path), 'utf8'), env, gh: () => '' });
    const branch = git('branch', '--show-current').trim();
    const published = JSON.parse(execFileSync('git', ['--git-dir', remote, 'show', `${branch}:public/data/rtr-numbering.json`], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }));
    assert.equal(execFileSync('git', ['--git-dir', remote, 'rev-parse', 'main'], { encoding: 'utf8' }).trim(), baseline);
    assert.equal(published.generatedAt, next.generatedAt);
    assert.equal(published.sourcePublishedAt, next.sourcePublishedAt);
    assert.equal(published.recordCount, next.recordCount);
    assert.equal(git('diff', '--name-only', 'HEAD~1', 'HEAD').trim(), 'public/data/rtr-numbering.json');
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('generation time alone never creates a refresh branch or PR', () => {
  const fake = fakeGit();
  assert.equal(publish('arcep', { ...fake, env, read: () => JSON.stringify({ ...before, generatedAt: '2026-10-02T01:00:00Z' }) }), false);
  assert.equal(fake.calls.some(call => ['push','checkout','gh'].includes(call[0])), false);
});
test('the same source version reuses its proposed branch despite a different fetch time', () => {
  const branches = [];
  for (const fetchedAt of ['2026-10-02T01:00:00Z','2026-10-03T01:00:00Z']) {
    const fake = fakeGit();
    publish('arcep', { ...fake, env, read: () => JSON.stringify({ ...before, sourcePublishedAt: '2026-10-01', fetchedAt }) });
    branches.push(fake.calls.find(call => call[0] === 'push')[2]);
  }
  assert.equal(branches[0], branches[1]);
});
test('an already proposed source version is a no-op without touching Git history', () => {
  const fake = fakeGit();
  assert.equal(publish('arcep', { ...fake, env, read: () => JSON.stringify({ ...before, sourcePublishedAt: '2026-10-01' }), gh: () => '999' }), false);
  assert.equal(fake.calls.some(call => ['push','checkout','commit'].includes(call[0])), false);
});

test('watch timestamps alone are excluded from a source-version identity', async () => {
  const { semanticContent } = await import('./publish-numbering-refresh.js');
  const report={schemaVersion:1,checkedAt:'2026-10-02T00:00:00Z',sources:[{url:'https://official.example/',status:'UNCHANGED',sha256:'a'.repeat(64),checkedAt:'2026-10-02T00:00:00Z',lastSuccessfulAt:'2026-10-02T00:00:00Z',changedAt:'2026-10-01T00:00:00Z'}]};
  const newer=structuredClone(report);newer.checkedAt='2026-10-09T00:00:00Z';newer.sources[0].checkedAt=newer.checkedAt;newer.sources[0].lastSuccessfulAt=newer.checkedAt;
  assert.equal(semanticContent(report,'sources'),semanticContent(newer,'sources'));
  newer.sources[0].sha256='b'.repeat(64);
  assert.notEqual(semanticContent(report,'sources'),semanticContent(newer,'sources'));
});
