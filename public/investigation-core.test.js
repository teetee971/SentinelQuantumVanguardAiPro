import test from 'node:test';
import assert from 'node:assert/strict';
import { emptyCase, validateCase, importCase, exportCase, timeline, removeEntity, exampleCase, LIMITS } from './investigation-core.js';

test('empty and demonstration cases round-trip without network or verification claims', () => {
  for (const input of [emptyCase(), exampleCase()]) assert.deepEqual(importCase(exportCase(input)), input);
  assert.ok(exampleCase().entities.every(item => item.verification === 'non_verifiee'));
});
test('unknown fields and imported verification claims cannot promote evidence', () => {
  const input = exampleCase(); input.secret = 'do not export'; input.entities[0].verification = 'verified'; input.entities[0].confidence = 100;
  const result = validateCase(input);
  assert.equal(result.entities[0].verification, 'non_verifiee');
  assert.equal(result.secret, undefined); assert.equal(result.entities[0].confidence, undefined);
});
test('malformed payloads and oversize unicode are rejected', () => {
  for (const raw of ['null', '{}', '[]', '{', JSON.stringify({ ...emptyCase(), version: 2 }), 'é'.repeat(LIMITS.bytes)]) assert.throws(() => importCase(raw));
});
test('bounds, identifiers and graph endpoints fail closed', () => {
  const variations = [
    value => { value.entities.push(value.entities[0]); },
    value => { value.links.push(value.links[0]); },
    value => { value.links[0].from = 'missing'; },
    value => { value.links[0].to = value.links[0].from; },
    value => { value.entities[0].id = '<script>'; },
    value => { value.entities[0].type = 'personne_confirmee'; },
    value => { value.entities = Array(61).fill(value.entities[0]); },
    value => { value.links = Array(121).fill(value.links[0]); },
    value => { value.entities[0].label = 'a'.repeat(161); },
    value => { value.entities[0].note = ''; }
  ];
  for (const mutate of variations) { const input = exampleCase(); mutate(input); assert.throws(() => validateCase(input)); }
});
test('source protocol, credentials, controls and canonical timestamps are validated', () => {
  for (const source of ['javascript:alert(1)', 'http://example.com', 'file:///tmp/a', 'https://name:pass@example.com', 'https://example.com/\n']) {
    const input = exampleCase(); input.entities[0].source = source; assert.throws(() => validateCase(input));
  }
  for (const observedAt of ['2026-02-30T00:00:00.000Z', '2026-09-16', 'invalid', '2026-09-16T00:00:00+02:00']) {
    const input = exampleCase(); input.links[0].observedAt = observedAt; assert.throws(() => validateCase(input));
  }
});
test('timeline includes relations, deterministic ties, and does not mutate input', () => {
  const input = exampleCase(); input.entities[0].observedAt = '2026-09-17T00:00:00.000Z';
  const original = structuredClone(input); const result = timeline(input);
  assert.equal(result.length, 5); assert.equal(result.at(-1).id, 'demo_org'); assert.deepEqual(input, original);
});
test('entity removal cascades only its own links', () => {
  const result = removeEntity(exampleCase(), 'demo_org');
  assert.equal(result.entities.length, 2); assert.equal(result.links.length, 1); assert.equal(result.links[0].id, 'demo_link_2');
});
test('identical labels remain distinct: no automatic identity merge', () => {
  const input = exampleCase(); input.entities[1].label = input.entities[0].label;
  assert.equal(validateCase(input).entities.length, 3);
});
