import test from 'node:test';
import assert from 'node:assert/strict';

import {
  EVIDENCE_LEVELS,
  buildInfluenceGraphEdges,
  normalizeForeignInfluenceRecord
} from './registry-intelligence.js';

function fixture(overrides = {}) {
  return {
    record_id: 'registry-001',
    source: {
      publisher: 'Public authority',
      url: 'https://example.test/registry/001',
      retrieved_at: '2026-09-06T12:00:00Z'
    },
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

test('normalizes a declared registry record without inferring interference', () => {
  const record = normalizeForeignInfluenceRecord(fixture());
  assert.equal(record.record_type, 'DECLARED_FOREIGN_INFLUENCE');
  assert.equal(record.evidence_level, EVIDENCE_LEVELS.DECLARED_FACT);
  assert.equal(record.attribution_status, 'NOT_ASSESSED');
  assert.equal(record.interference_status, 'NOT_ASSESSED');
});

test('requires provenance and a supported declared activity channel', () => {
  assert.throws(
    () => normalizeForeignInfluenceRecord(fixture({ source: { publisher: '', url: '', retrieved_at: '' } })),
    /INVALID_SOURCE_PUBLISHER/
  );
  assert.throws(
    () => normalizeForeignInfluenceRecord(fixture({ activity: { ...fixture().activity, channel: 'HOSTILE' } })),
    /INVALID_ACTIVITY_CHANNEL/
  );
});

test('rejects inverted activity periods', () => {
  assert.throws(
    () => normalizeForeignInfluenceRecord(fixture({
      activity: {
        ...fixture().activity,
        start_date: '2026-10-01T00:00:00Z',
        end_date: '2026-09-01T00:00:00Z'
      }
    })),
    /INVALID_ACTIVITY_PERIOD/
  );
});

test('builds source-bound graph edges that remain declared facts', () => {
  const edges = buildInfluenceGraphEdges(fixture());
  assert.ok(edges.length >= 4);
  for (const edge of edges) {
    assert.equal(edge.evidence_level, EVIDENCE_LEVELS.DECLARED_FACT);
    assert.equal(edge.source_ref, 'Public authority:registry-001');
  }
  assert.ok(edges.some((edge) => edge.relation === 'DECLARES_ACTIVITY_TOWARD'));
  assert.ok(edges.some((edge) => edge.relation === 'DECLARES_POLICY_TOPIC'));
});

test('deduplicates repeated targets and topics', () => {
  const record = normalizeForeignInfluenceRecord(fixture({
    activity: {
      ...fixture().activity,
      targets: ['Public audience', 'Public audience'],
      policy_topics: ['Energy policy', 'Energy policy']
    }
  }));
  assert.deepEqual(record.activity.targets, ['Public audience']);
  assert.deepEqual(record.activity.policy_topics, ['Energy policy']);
});
