import assert from 'node:assert/strict';
import test from 'node:test';
import { parseRecentAtom, selectRelevantRecentDatasets } from './recent-atom-watch.js';

const FEED = `<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom">
<entry><id>1</id><title>Jeu de données cybersécurité</title><summary>Signalements de fraude téléphonique</summary><updated>2026-09-17T10:00:00Z</updated><link href="https://www.data.gouv.fr/fr/datasets/1/"/></entry>
<entry><id>2</id><title>Inventaire des arbres</title><summary>Données botaniques</summary><updated>2026-09-17T11:00:00Z</updated><link href="https://www.data.gouv.fr/fr/datasets/2/"/></entry>
</feed>`;

test('parses Atom entries without executing embedded content', () => {
  const entries = parseRecentAtom(FEED);
  assert.equal(entries.length, 2);
  assert.equal(entries[0].title, 'Jeu de données cybersécurité');
});

test('selects only datasets relevant to Sentinel watch domains', () => {
  const selected = selectRelevantRecentDatasets(FEED);
  assert.equal(selected.length, 1);
  assert.equal(selected[0].id, '1');
});

test('fails closed on non-Atom input', () => {
  assert.deepEqual(parseRecentAtom('not xml'), []);
});
