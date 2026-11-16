import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRtrLoader, createRtrLookup, RTR_DIRECTORY_URL, RTR_STATUSES } from './phone-rtr.js';
import { createTranslator } from './phone-intelligence-i18n.js';

function fixture() {
  return {
    schemaVersion: 1, country: 'AT', generatedAt: '2026-09-15T00:00:00Z', recordCount: 2,
    holders: [['Example allocation holder', '1234']],
    groups: { '1/7': { kind: 'geographic', category: 'Geografische Rufnummern', area: 'Wien',
      ranges: [['2000000', '2000099', 0], ['2000100', '2000199', -1]] } }
  };
}

test('RTR boundaries, leading zeros and number length are preserved', () => {
  const lookup = createRtrLookup(fixture());
  assert.equal(lookup('+4312000000').status, 'allocated');
  assert.equal(lookup('+4312000099').status, 'allocated');
  assert.equal(lookup('+4312000100').status, 'unallocated');
  assert.equal(lookup('+4312000199').status, 'unallocated');
  assert.equal(lookup('+4312000200').status, 'no-match');
  assert.equal(lookup('+431200000').status, 'no-match');
  assert.equal(lookup('+43120000000').status, 'no-match');
  for (const number of ['+33612345678', '+43012000000', '*200', '+43<script>', null, 123]) assert.equal(lookup(number), null);
  assert.equal(lookup('+4312000100').matches[0].allocationHolder, null);
});

test('nested overlapping ranges are ambiguous even when an earlier range contains the later ones', () => {
  const data = fixture();
  data.groups['1/7'].ranges = [['2000000', '2999999', 0], ['2000100', '2000199', -4], ['2000500', '2000599', -4]];
  data.recordCount = 3;
  const lookup = createRtrLookup(data);
  assert.equal(lookup('+4312000500').status, 'ambiguous');
  assert.equal(lookup('+4312000500').matches.length, 2);
  assert.equal(lookup('+4312999999').status, 'allocated');
});

test('corrupt directory fails closed without a plausible attribution', () => {
  const changes = [
    (d) => { d.schemaVersion = 2; }, (d) => { d.country = 'FR'; },
    (d) => { d.recordCount = 1; }, (d) => { d.generatedAt = 'bad'; },
    (d) => { d.holders[0][1] = 'invalid'; }, (d) => { d.holders[0][0] = '\u0000'; },
    (d) => { d.groups['1/7'].ranges[0][2] = 99; },
    (d) => { d.groups['1/7'].ranges[0][2] = -99; },
    (d) => { d.groups['1/7'].ranges.reverse(); },
    (d) => { d.groups['1/7'].ranges[0][0] = '3000000'; },
    (d) => { d.groups['1/7'].ranges[0][1] = '2000'; }
  ];
  for (const change of changes) { const d = fixture(); change(d); assert.throws(() => createRtrLookup(d), /INVALID_RTR_DIRECTORY/); }
});

test('loader is lazy, same-origin, bounded, shared across concurrent searches and retryable', async () => {
  const calls = [];
  const load = createRtrLoader(async (...args) => {
    calls.push(args);
    if (calls.length === 1) return new Response('unavailable', { status: 503 });
    return new Response(JSON.stringify(fixture()));
  });
  assert.equal(calls.length, 0);
  assert.equal(await load(), null);
  const a = load(); const b = load();
  assert.equal(a, b);
  assert.equal((await a).lookup('+4312000000').status, 'allocated');
  await load();
  assert.equal(calls.length, 2);
  assert.equal(calls[1][0], RTR_DIRECTORY_URL);
  assert.equal(calls[1][1].credentials, 'omit');
  assert.equal(calls[1][1].referrerPolicy, 'no-referrer');
  assert.ok(calls[1][1].signal instanceof AbortSignal);
  for (const response of [new Response('{'), new Response('{}'), new Response('x'.repeat(4 * 1024 * 1024 + 1)),
    new Response('{}', { headers: { 'content-length': String(5 * 1024 * 1024) } })]) {
    assert.equal(await createRtrLoader(async () => response)(), null);
  }
});

test('committed snapshot validates, keeps administrative exceptions and does not invent ownership for 673', () => {
  const bytes = readFileSync(new URL('./data/rtr-numbering.json', import.meta.url));
  assert.ok(bytes.length < 4 * 1024 * 1024);
  const data = JSON.parse(bytes);
  const lookup = createRtrLookup(data);
  assert.equal(data.recordCount + Object.values(data.excludedCategories).reduce((a, b) => a + b, 0), data.sources.geo.rows + data.sources.services.rows);
  assert.equal(data.sourcePublishedAt, null); // Uploaded CSVs contain no source publication timestamp.
  assert.equal(lookup('+436734518629').status, 'unallocated');
  assert.equal(lookup('+436734518629').matches[0].allocationHolder, null);
  assert.equal(lookup('+4313650250').status, 'ambiguous');
  assert.equal(lookup('+4312000000').matches[0].area, 'Wien');
  for (const number of ['+4312000000', '+436734518629']) {
    assert.equal(Object.hasOwn(lookup(number), 'riskScore'), false);
    assert.equal(Object.hasOwn(lookup(number), 'callerName'), false);
  }
});

test('every published boundary in the snapshot is returned, without losing containing ranges', () => {
  const data = JSON.parse(readFileSync(new URL('./data/rtr-numbering.json', import.meta.url)));
  const lookup = createRtrLookup(data);
  for (const [key, group] of Object.entries(data.groups)) {
    const prefix = key.split('/')[0];
    for (const [start, end] of group.ranges) {
      for (const edge of [start, end]) {
        assert.ok(lookup(`+43${prefix}${edge}`)?.matches.some((r) => r.start === `+43${prefix}${start}` && r.end === `+43${prefix}${end}`), `Missing boundary ${key}/${edge}`);
      }
    }
  }
});

test('all RTR statuses and caveats have French and English labels', () => {
  for (const locale of ['fr', 'en']) {
    const t = createTranslator(locale);
    for (const status of ['allocated', 'ambiguous', 'no-match', ...RTR_STATUSES]) {
      const key = `runtime.rtrStatus.${status}`;
      assert.notEqual(t(key), key);
    }
    assert.doesNotMatch(t('runtime.rtrFreshness', { imported: 'DATE', published: 'UNKNOWN' }), /\{\w+\}/);
  }
});
