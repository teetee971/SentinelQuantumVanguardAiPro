import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { buildRtrDirectory } from './update-rtr-numbering.js';
import { createRtrLookup } from '../public/phone-rtr.js';

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
