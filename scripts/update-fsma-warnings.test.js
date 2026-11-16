import test from 'node:test';
import assert from 'node:assert/strict';
import { buildFsmaSnapshot, hasSameFsmaContent } from './update-fsma-warnings.js';

const CSV = [
  'Title;Company name;Website;Type of fraud;Date',
  'Warning A;Example SA;https://fraud.example;Investment fraud;20/08/2026',
  'Warning B;Example Two;example-two.test;Clone;19/08/2026',
].join('\n');

test('buildFsmaSnapshot preserves advisory-only official-warning semantics', () => {
  const snapshot = buildFsmaSnapshot(CSV, { generatedAt: '2026-10-17T00:00:00.000Z' });
  assert.equal(snapshot.schemaVersion, 1);
  assert.equal(snapshot.advisoryOnly, true);
  assert.equal(snapshot.source.id, 'fsma-belgium-warnings');
  assert.match(snapshot.source.sha256, /^[a-f0-9]{64}$/);
  assert.equal(snapshot.warnings.length, 2);
  assert.deepEqual(snapshot.warnings[0].domains, ['fraud.example']);
  assert.equal(snapshot.warnings[0].autoBlock, false);
});

test('content comparison ignores generatedAt but not regulatory warning changes', () => {
  const first = buildFsmaSnapshot(CSV, { generatedAt: '2026-10-17T00:00:00.000Z' });
  const second = buildFsmaSnapshot(CSV, { generatedAt: '2026-10-18T00:00:00.000Z' });
  assert.equal(hasSameFsmaContent(first, second), true);

  const changed = buildFsmaSnapshot(CSV.replace('Warning A', 'Warning Z'));
  assert.equal(hasSameFsmaContent(first, changed), false);
});
