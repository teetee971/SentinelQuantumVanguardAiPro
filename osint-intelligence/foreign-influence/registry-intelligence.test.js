import test from 'node:test';
import assert from 'node:assert/strict';

import { EVIDENCE_LEVELS, buildInfluenceGraphEdges, normalizeForeignInfluenceRecord } from './registry-intelligence.js';

function fixture(overrides = {}) {
  return {
    record_id: 'registry-001',
    source: { publisher: 'Public authority', url: 'https://example.test/registry/001', retrieved_at: '2026-09-06T12:00:00Z' },
    declarant: { name: 'Declared actor', identifier: 'actor-001' },
    principal: { name: 'Foreign principal', jurisdiction: 'Example jurisdiction' },
    activity: {
      channel: 'PUBLIC_COMMUNICATION',
      objective: 'Public policy communication',
      start_date: '2026-07-01T00:00:00Z',
      end_date: '2026-09-30T23:59:59Z',
      targets: ['Public audience'],
      policy_topics: ['Energy policy'],
      intermediaries: ['Declared intermediary']
    },
    ...overrides
  };
}

test('keeps an unauthenticated registry-shaped record below declared-fact level', () => {
  const record = normalizeForeignInfluenceRecord(fixture());
  assert.equal(record.record_type, 'FOREIGN_INFLUENCE_REGISTRY_OBSERVATION');
  assert.equal(record.evidence_level, EVIDENCE_LEVELS.PUBLIC_SOURCE_OBSERVATION);
  assert.equal(record.source_verification, 'UNVERIFIED');
  assert.equal(record.attribution_status, 'NOT_ASSESSED');
  assert.equal(record.interference_status, 'NOT_ASSESSED');
  assert.equal(Object.isFrozen(record.activity), true);
});

test('requires HTTPS provenance and an explicit timezone', () => {
  assert.throws(() => normalizeForeignInfluenceRecord(fixture({ source: { publisher: 'Authority', url: 'http://example.test/1', retrieved_at: '2026-09-06T12:00:00Z' } })), /INVALID_SOURCE_URL/);
  assert.throws(() => normalizeForeignInfluenceRecord(fixture({ source: { publisher: 'Authority', url: 'https://example.test/1', retrieved_at: '2026-09-06 12:00:00' } })), /INVALID_SOURCE_RETRIEVED_AT/);
});

test('requires provenance and a supported declared activity channel', () => {
  assert.throws(() => normalizeForeignInfluenceRecord(fixture({ source: { publisher: '', url: '', retrieved_at: '' } })), /INVALID_SOURCE_PUBLISHER/);
  assert.throws(() => normalizeForeignInfluenceRecord(fixture({ activity: { ...fixture().activity, channel: 'HOSTILE' } })), /INVALID_ACTIVITY_CHANNEL/);
});

test('rejects inverted activity periods', () => {
  assert.throws(() => normalizeForeignInfluenceRecord(fixture({
    activity: { ...fixture().activity, start_date: '2026-10-01T00:00:00Z', end_date: '2026-09-01T00:00:00Z' }
  })), /INVALID_ACTIVITY_PERIOD/);
});

test('builds record-scoped, source-bound and strictly declarative graph edges', () => {
  const edges = buildInfluenceGraphEdges(fixture());
  assert.ok(edges.length >= 4);
  for (const edge of edges) {
    assert.equal(edge.evidence_level, EVIDENCE_LEVELS.PUBLIC_SOURCE_OBSERVATION);
    assert.equal(edge.source_verification, 'UNVERIFIED');
    assert.equal(edge.attribution_status, 'NOT_ASSESSED');
    assert.equal(edge.interference_status, 'NOT_ASSESSED');
    assert.doesNotMatch(edge.relation, /MANDATES|BENEFITS|USES/);
    assert.equal(Object.isFrozen(edge), true);
  }
  assert.ok(edges.some((edge) => edge.relation === 'DECLARES_FOREIGN_PRINCIPAL_RELATIONSHIP'));
});

test('source references are collision-safe and include the source URL', () => {
  const first = buildInfluenceGraphEdges(fixture({ record_id: 'b:c', source: { publisher: 'a', url: 'https://one.test/1', retrieved_at: '2026-09-06T12:00:00Z' } }))[0];
  const second = buildInfluenceGraphEdges(fixture({ record_id: 'c', source: { publisher: 'a:b', url: 'https://two.test/1', retrieved_at: '2026-09-06T12:00:00Z' } }))[0];
  assert.notEqual(first.source_ref, second.source_ref);
  assert.match(first.source_ref, /https:\\/\\/one\.test/);
});

test('same display name in different records does not merge identities automatically', () => {
  const first = buildInfluenceGraphEdges(fixture({ record_id: 'one' }))[0];
  const second = buildInfluenceGraphEdges(fixture({ record_id: 'two' }))[0];
  assert.equal(first.from_label, second.from_label);
  assert.notEqual(first.from, second.from);
});

test('deduplicates repeated targets and topics', () => {
  const record = normalizeForeignInfluenceRecord(fixture({
    activity: { ...fixture().activity, targets: ['Public audience', 'Public audience'], policy_topics: ['Energy policy', 'Energy policy'] }
  }));
  assert.deepEqual(record.activity.targets, ['Public audience']);
  assert.deepEqual(record.activity.policy_topics, ['Energy policy']);
});
