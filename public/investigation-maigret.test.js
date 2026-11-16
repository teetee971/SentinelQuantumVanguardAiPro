import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto, createHash } from 'node:crypto';
import { inspectMaigret, appendMaigret, MAIGRET_LIMITS } from './investigation-maigret.js';
import { emptyCase, importCase, exportCase } from './investigation-core.js';

// Synthetic fixture matching report.py generate_json_report and result.py json().
const record = (site = 'Example', username = 'demo') => ({ username, url_user: `https://example.invalid/${username}`, status: { username, site_name: site, url: `https://example.invalid/${username}`, status: 'Claimed', ids: { sensitive: 'discard' }, tags: ['discard'] }, site: { arbitrary: 'discard' }, http_status: 200 });
const bytes = value => new TextEncoder().encode(JSON.stringify(value)).buffer;
const at = '2026-09-16T01:00:00.000Z';

test('actual simple JSON structure imports only selected potential accounts with exact-byte hash', async () => {
  const buffer = bytes({ Example: record(), Other: record('Other') });
  const preview = await inspectMaigret(buffer, webcrypto);
  assert.equal(preview.sha256, createHash('sha256').update(new Uint8Array(buffer)).digest('hex'));
  const { dossier } = appendMaigret(emptyCase(), preview, [1], at);
  assert.equal(dossier.entities.length, 1); assert.equal(dossier.links.length, 0);
  assert.equal(dossier.entities[0].label, 'demo @ Other'); assert.equal(dossier.entities[0].kind, 'hypothese');
  assert.equal(dossier.entities[0].importProvenance.collectedAt, null);
  assert.equal(dossier.entities[0].observedAt, at);
  assert.doesNotMatch(exportCase(dossier), /sensitive|arbitrary|discard/);
  assert.deepEqual(importCase(exportCase(dossier)), dossier);
});
test('Available, Unknown, Illegal and malformed rows stay separate and cannot become accounts', async () => {
  const input = Object.fromEntries(['Available', 'Unknown', 'Illegal', 'Claimed', 'Unexpected'].map(status => [status, { ...record(status), status: { ...record(status).status, status } }]));
  input.broken = null;
  const preview = await inspectMaigret(bytes(input), webcrypto);
  assert.deepEqual(preview.counts, { claimed: 1, available: 1, unknown: 1, illegal: 1, rejected: 2 });
  assert.equal(preview.candidates.length, 1);
});
test('untrusted URLs, conflicting fields and legacy formats are not silently accepted', async () => {
  for (const edit of [
    row => { row.url_user = 'javascript:alert(1)'; row.status.url = row.url_user; },
    row => { row.url_user = 'http://example.invalid/demo'; row.status.url = row.url_user; },
    row => { row.url_user = 'https://name:pass@example.invalid/demo'; row.status.url = row.url_user; },
    row => { row.status.url = 'https://other.invalid/demo'; },
    row => { row.username = 'another-user'; },
    row => { row.status.site_name = 'NotExample'; },
    row => { row.status = 'Claimed'; },
    row => { row.status.username = 'a'.repeat(65); }
  ]) { const row = record(); edit(row); const p = await inspectMaigret(bytes({ Example: row }), webcrypto); assert.equal(p.candidates.length, 0); assert.equal(p.counts.rejected, 1); }
});
test('NDJSON, non-UTF8, arrays and oversized files/record sets are rejected', async () => {
  for (const buffer of [new TextEncoder().encode('{}\n{}').buffer, new Uint8Array([255]).buffer, bytes([]), bytes(null), new ArrayBuffer(MAIGRET_LIMITS.bytes + 1), bytes(Object.fromEntries(Array.from({ length: 1001 }, (_, i) => [i, {}])))]) await assert.rejects(inspectMaigret(buffer, webcrypto));
});
test('empty export states no positive results without inventing total scanned coverage', async () => {
  const p = await inspectMaigret(bytes({}), webcrypto); assert.equal(p.entries, 0); assert.equal(p.candidates.length, 0); assert.equal(p.totalScanned, undefined);
});
test('repeat import is idempotent; same username across sites stays distinct', async () => {
  const p = await inspectMaigret(bytes({ Example: record(), Other: record('Other') }), webcrypto);
  const first = appendMaigret(emptyCase(), p, [0, 1], at);
  const again = appendMaigret(first.dossier, p, [0, 1], at);
  assert.equal(first.added, 2); assert.equal(again.added, 0); assert.equal(again.duplicates, 2); assert.deepEqual(again.dossier, first.dossier);
});
test('capacity and selection failures preserve the original dossier atomically', async () => {
  const p = await inspectMaigret(bytes(Object.fromEntries(Array.from({ length: 61 }, (_, i) => [`Site${i}`, record(`Site${i}`)]))), webcrypto);
  const full = appendMaigret(emptyCase(), p, Array.from({ length: 60 }, (_, i) => i), at).dossier;
  const saved = structuredClone(full);
  for (const selection of [[], [60], [500], [0, 0]]) assert.throws(() => appendMaigret(full, p, selection, at));
  assert.deepEqual(full, saved);
  assert.throws(() => appendMaigret(emptyCase(), p, [0], 'invalid-date'));
});
test('imported provenance cannot be upgraded to verified facts or a fabricated collection date', async () => {
  const p = await inspectMaigret(bytes({ Example: record() }), webcrypto);
  const d = appendMaigret(emptyCase(), p, [0], at).dossier;
  d.entities[0].verification = 'verified'; assert.equal(importCase(JSON.stringify(d)).entities[0].verification, 'non_verifiee');
  d.entities[0].importProvenance.collectedAt = at; assert.throws(() => importCase(JSON.stringify(d)));
  d.entities[0].importProvenance.collectedAt = null; d.entities[0].kind = 'observation'; assert.throws(() => importCase(JSON.stringify(d)));
});
