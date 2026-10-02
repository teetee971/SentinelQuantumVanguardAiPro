import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { assertRtrRefresh } from './update-rtr-numbering.js';

export const TARGETS = Object.freeze({
  arcep: 'public/data/arcep-numbering.json',
  ofcom: 'public/data/ofcom-numbering.json',
  acm: 'public/data/acm-numbering.json',
  ctu: 'public/data/ctu-numbering.json',
  rtr: 'public/data/rtr-numbering.json',
  sources: 'docs/data/international-numbering-watch.json'
});

function count(data) { return data.recordCount ?? data.entries?.length; }
function allocationMaximum(data) {
  const index = (data.entryFields ?? []).findIndex(field => ['allocationDate', 'decisionDate', 'allocatedFrom', 'date'].includes(field));
  if (index < 0) return null;
  const dates = (data.entries ?? []).map(row => row[index]).filter(Boolean).map(date => {
    const french = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(date);
    return french ? `${french[3]}-${french[2]}-${french[1]}` : date;
  }).filter(date => /^\d{4}-\d{2}-\d{2}$/.test(date)).sort();
  return dates.at(-1) ?? null;
}

export function validateRefresh(before, after, target) {
  if (!Object.hasOwn(TARGETS, target)) throw new Error('UNKNOWN_TARGET');
  if (target === 'sources') {
    if (after.schemaVersion !== 1 || !Array.isArray(after.sources) || !after.sources.length ||
        !Number.isFinite(Date.parse(after.checkedAt))) throw new Error('INVALID_WATCH_REPORT');
    return;
  }
  if (target === 'rtr') { assertRtrRefresh(before, after); return; }
  if (!before || !after || after.schemaVersion !== before.schemaVersion || after.country !== before.country) {
    throw new Error('SCHEMA_OR_COUNTRY_CHANGED');
  }
  if (JSON.stringify(after.entryFields) !== JSON.stringify(before.entryFields)) throw new Error('ENTRY_SCHEMA_CHANGED');
  const previousCount = count(before);
  const nextCount = count(after);
  if (!Number.isSafeInteger(previousCount) || previousCount < 1 || !Number.isSafeInteger(nextCount) || nextCount < 1) {
    throw new Error('INVALID_RECORD_COUNT');
  }
  if (!Array.isArray(after.entries) || after.entries.length !== nextCount) throw new Error('RECORD_COUNT_MISMATCH');
  // Revocations may be legitimate, but a loss above 10% requires investigation.
  if (nextCount < previousCount * 0.9) throw new Error('RECORD_COUNT_DROP');
  if (before.sourcePublishedAt && (!after.sourcePublishedAt || after.sourcePublishedAt < before.sourcePublishedAt)) {
    throw new Error('PUBLICATION_ROLLBACK');
  }
  const previousMaximum = allocationMaximum(before);
  const nextMaximum = allocationMaximum(after);
  if (previousMaximum && (!nextMaximum || nextMaximum < previousMaximum)) throw new Error('ALLOCATION_ROLLBACK');
}

export function publish(target, { git = (...args) => execFileSync('git', args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).trim(),
  read = path => readFileSync(path, 'utf8'), env = process.env } = {}) {
  if (!Object.hasOwn(TARGETS, target)) throw new Error('UNKNOWN_TARGET');
  if (env.GITHUB_ACTIONS !== 'true' || env.GITHUB_REF !== 'refs/heads/main' ||
      !['schedule', 'workflow_dispatch', 'push', 'workflow_call'].includes(env.GITHUB_EVENT_NAME)) throw new Error('MAIN_WORKFLOW_REQUIRED');
  const path = TARGETS[target];
  if (!git('status', '--porcelain', '--', path)) return false;
  const staged = git('diff', '--cached', '--name-only');
  if (staged) throw new Error('UNEXPECTED_STAGED_FILES');
  const after = JSON.parse(read(path));
  let before;
  if (target !== 'sources') before = JSON.parse(git('show', `HEAD:${path}`));
  validateRefresh(before, after, target);
  const base = git('rev-parse', 'HEAD');
  git('fetch', 'origin', 'main');
  if (git('rev-parse', 'origin/main') !== base) throw new Error('MAIN_MOVED_RETRY_NEXT_RUN');
  git('config', 'user.name', 'github-actions[bot]');
  git('config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com');
  git('add', '--', path);
  git('commit', '-m', `Phone intelligence: autonomous ${target} refresh`);
  // Normal fast-forward push: branch protections remain enforced by GitHub.
  git('push', 'origin', 'HEAD:refs/heads/main');
  return true;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try { console.log(publish(process.argv[2]) ? 'Validated refresh published.' : 'No data changes.'); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
