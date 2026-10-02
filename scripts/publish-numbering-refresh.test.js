import assert from 'node:assert/strict';
import test from 'node:test';
import { publish, validateRefresh } from './publish-numbering-refresh.js';

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
  return { git, calls };
}
test('publishes exactly one allowlisted file with a normal push', () => {
  const fake = fakeGit();
  assert.equal(publish('arcep', { ...fake, env, read: () => JSON.stringify(before) }), true);
  assert.deepEqual(fake.calls.find(args => args[0] === 'add'), ['add', '--', 'public/data/arcep-numbering.json']);
  assert.deepEqual(fake.calls.at(-1), ['push', 'origin', 'HEAD:refs/heads/main']);
});
test('main moving, unexpected staged files, and non-main runs never push', () => {
  for (const scenario of [{ moved: true }, { staged: true }]) {
    const fake = fakeGit(scenario);
    assert.throws(() => publish('arcep', { ...fake, env, read: () => JSON.stringify(before) }));
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
