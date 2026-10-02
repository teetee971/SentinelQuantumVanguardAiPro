import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { buildRtrDirectory, downloadRtrInputs, assertRtrRefresh, sameRtrContent, main } from './update-rtr-numbering.js';
import { createRtrLookup } from '../public/phone-rtr.js';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const utf8 = (text) => Buffer.from(`\uFEFF${text}`, 'utf8');
function inputs() {
  return {
    geo: utf8('ortsnetzkennzahl;ortsnetzname;rufnummernbeginn;rufnummernende;betreiber;betreiberid\r\n1;Wien;2000000;2000099;"Example; Test ""One""";1522\r\n'),
    services: utf8('rufnummernbereich;bereichskennzahl;rufnummernbeginn;rufnummernende;betreiber;betreiberid\nmobile Rufnummern;673;0000000;9999999;------ nicht zugeteilt ------;\n'),
    areas: utf8('ortsnetzkennzahl;ortsnetzname\n1;Wien\n')
  };
}
const options = { generatedAt: '2026-09-15T19:30:00Z' };

test('RTR import is reproducible, cross-validates areas and hashes original bytes', () => {
  const source = inputs();
  const data = buildRtrDirectory(source, options);
  assert.deepEqual(data, buildRtrDirectory(source, options));
  assert.equal(data.recordCount, 2);
  assert.equal(data.sourcePublishedAt, null);
  assert.equal(data.sources.geo.sha256, createHash('sha256').update(source.geo).digest('hex'));
  assert.equal(data.groups['673/7'].ranges[0][0], '0000000');
  assert.equal(createRtrLookup(data)('+4312000000').matches[0].allocationHolder, 'Example; Test "One"');
});

test('missing, duplicate, malformed headers/rows/bounds/statuses and area mismatches are rejected', () => {
  for (const [kind, before, after] of [
    ['geo', 'ortsnetzkennzahl;', 'wrong;'], ['geo', 'betreiberid', 'betreiber'],
    ['geo', ';1522', ';1522;extra'], ['geo', '2000099', '1999999'],
    ['geo', '2000099', '999'], ['geo', '2000099', '20x0099'],
    ['geo', 'Wien;', 'Other;'], ['areas', '1;Wien', '1;Wien\n1;Wien'],
    ['services', '------ nicht zugeteilt ------', '--- unknown future status ---']
  ]) {
    const source = inputs(); source[kind] = Buffer.from(source[kind].toString().replace(before, after));
    assert.throws(() => buildRtrDirectory(source, options));
  }
  const source = inputs(); source.geo = Buffer.alloc(8 * 1024 * 1024 + 1);
  assert.throws(() => buildRtrDirectory(source, options), /RTR_INPUT_TOO_LARGE/);
  assert.throws(() => buildRtrDirectory(inputs()), /DATE_REQUIRED/);
});

test('overlapping administrative records remain visible and cannot be turned into named holders', () => {
  const source = inputs();
  source.geo = Buffer.from(source.geo.toString() + '1;Wien;2000020;2000029;"Teilweise zugeteilt; Zuteilungsstatus siehe www.rtr.at/num/gz";\n');
  const data = buildRtrDirectory(source, options);
  assert.equal(data.overlapCount, 1);
  const result = createRtrLookup(data)('+4312000020');
  assert.equal(result.status, 'ambiguous');
  assert.equal(result.matches[1].allocationHolder, null);
});

test('carrier-selection and routing prefixes are not exposed as international caller numbers', () => {
  const source = inputs();
  source.services = Buffer.from(source.services.toString() + 'Betreiberauswahl-Präfix;10;01;01;Example;1234\nRoutingnummern;86;0000;9999;Example;1234\n');
  const data = buildRtrDirectory(source, options);
  assert.equal(data.recordCount, 2);
  assert.deepEqual(data.excludedCategories, { 'Betreiberauswahl-Präfix': 1, Routingnummern: 1 });
  assert.equal(data.groups['10/2'], undefined);
  assert.equal(data.groups['86/4'], undefined);
});

test('accepts the exact comma-delimited official schema with quoted operator punctuation', () => {
  const source = {
    geo: utf8('ortsnetzkennzahl,ortsnetzname,rufnummernbeginn,rufnummernende,betreiber,betreiberid\r\n1,Wien,2000000,2000099,"Example, Test ""One""",1522\r\n'),
    services: utf8('rufnummernbereich,bereichskennzahl,rufnummernbeginn,rufnummernende,betreiber,betreiberid\nmobile Rufnummern,673,0000000,9999999,------ nicht zugeteilt ------,\n'),
    areas: utf8('ortsnetzkennzahl,ortsnetzname\n1,Wien\n')
  };
  const data = buildRtrDirectory(source, options);
  assert.equal(data.holders[0][0], 'Example, Test "One"');
  assert.equal(data.groups['673/7'].ranges[0][0], '0000000');
  assert.equal(data.sources.geo.sha256, createHash('sha256').update(source.geo).digest('hex'));
  source.geo = Buffer.from(source.geo.toString().replace('betreiberid', 'unknown'));
  assert.throws(() => buildRtrDirectory(source, options), /RTR_SCHEMA_geo/);
});

function officialFetcher(source = inputs()) {
  const kinds = { 'tn-geo': 'geo', 'tn-dienste': 'services', 'tn-ortsnetze': 'areas' };
  return async url => {
    const name = new URL(url).pathname.split('/').at(-1);
    const dataset = name.replace(/\.csv$/, '');
    if (!kinds[dataset]) throw new Error('UNEXPECTED_FETCH');
    assert.equal(new URL(url).origin, 'https://data.rtr.at');
    assert.equal(new URL(url).pathname, `/api/v1/tables/${dataset}.csv`);
    return new Response(source[kinds[dataset]]);
  };
}

test('downloads all three inputs with exact source provenance and validates their schemas', async () => {
  const result = await downloadRtrInputs({ fetchImpl: officialFetcher() });
  assert.equal(buildRtrDirectory(result.inputs, options).recordCount, 2);
  assert.equal(Object.keys(result.downloads).length, 3);
  assert.equal(result.downloads.geo, 'https://data.rtr.at/api/v1/tables/tn-geo.csv');
});

test('rejects lost known publication dates, schema changes and source-row collapses', () => {
  const data = buildRtrDirectory(inputs(), options);
  assertRtrRefresh(data, structuredClone(data));
  assert.throws(() => assertRtrRefresh({ ...data, sourcePublishedAt: '2026-09-15' }, data), /PUBLICATION_ROLLBACK/);
  assert.throws(() => assertRtrRefresh(data, { ...data, rangeFields: ['different'] }), /SCHEMA_CHANGED/);
  const inflated = structuredClone(data);
  inflated.sources.geo.rows = 100;
  assert.throws(() => assertRtrRefresh(inflated, data), /SOURCE_ROWS_DROP_geo/);
  const many = structuredClone(data);
  many.groups['1/7'].ranges = Array.from({ length: 20 }, () => ['2000000', '2000099', 0]);
  many.recordCount = 21;
  assert.throws(() => assertRtrRefresh(many, data), /RECORD_COUNT_DROP/);
});

test('generation time and delivery method do not create changes to identical source batches', () => {
  const data = buildRtrDirectory(inputs(), options);
  const next = structuredClone(data);
  next.generatedAt = '2026-10-02T00:00:00Z';
  next.sourceDelivery = 'official-https-csv';
  next.sources.geo.downloadUrl = 'https://data.rtr.at/api/v1/tables/tn-geo.csv';
  assert.equal(sameRtrContent(data, next), true);
  next.sources.geo.sha256 = 'changed';
  assert.equal(sameRtrContent(data, next), false);
});

test('automatic CLI writes an index, stays idempotent and preserves it after a failed refresh', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'rtr-automatic-'));
  const output = join(dir, 'index.json');
  try {
    await main(['--automatic', '--output', output, '--generated-at', options.generatedAt], { fetchImpl: officialFetcher() });
    const original = await readFile(output, 'utf8');
    const parsed = JSON.parse(original);
    assert.equal(parsed.sourceDelivery, 'official-https-csv');
    assert.equal(parsed.sourcePublishedAt, null);
    await main(['--automatic', '--output', output, '--generated-at', '2026-10-02T00:00:00Z'], { fetchImpl: officialFetcher() });
    assert.equal(await readFile(output, 'utf8'), original);
    const broken = inputs(); broken.geo = Buffer.from('changed;schema\n');
    await assert.rejects(main(['--automatic', '--output', output], { fetchImpl: officialFetcher(broken) }), /RTR_SCHEMA_geo/);
    assert.equal(await readFile(output, 'utf8'), original);
    await assert.rejects(main(['--automatic', '--geo', 'file.csv'], { fetchImpl: officialFetcher() }), /AUTOMATIC_ARGUMENT_CONFLICT/);
  } finally { await rm(dir, { recursive: true, force: true }); }
});
