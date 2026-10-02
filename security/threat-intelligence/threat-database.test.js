import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildAndroidSha256Reputation,
  buildThreatDatabase
} from './threat-database.js';

function obs(overrides = {}) {
  return {
    observation_id: 'obs-a',
    indicator_type: 'sha256',
    indicator_value: 'a'.repeat(64),
    source_id: 'source-a',
    source_kind: 'malware_repository',
    source_uri: 'https://example.test/a',
    source_verdict: 'malicious',
    confidence: 90,
    observed_at: '2026-10-01T00:00:00Z',
    retrieved_at: '2026-10-01T01:00:00Z',
    legally_accessible: true,
    tags: ['trojan'],
    ...overrides
  };
}

test('deduplicates observation ids and keeps the newest retrieval', () => {
  const db = buildThreatDatabase([
    obs({ retrieved_at: '2026-10-01T01:00:00Z', confidence: 70 }),
    obs({ retrieved_at: '2026-10-01T02:00:00Z', confidence: 95 })
  ], { generatedAt: '2026-10-02T00:00:00Z' });

  assert.equal(db.observation_count, 1);
  assert.equal(db.record_count, 1);
  assert.equal(db.records[0].confidence, 95);
});

test('requires independent malicious sources before Android malicious export', () => {
  const db = buildThreatDatabase([
    obs({ source_id: 'source-a' }),
    obs({ observation_id: 'obs-b', source_id: 'source-b', confidence: 85 })
  ], { generatedAt: '2026-10-02T00:00:00Z' });

  const mobile = buildAndroidSha256Reputation(db);
  assert.deepEqual(mobile.malicious_sha256, ['a'.repeat(64)]);
  assert.deepEqual(mobile.suspicious_sha256, []);
});

test('single malicious source is exported only as suspicious', () => {
  const db = buildThreatDatabase([
    obs()
  ], { generatedAt: '2026-10-02T00:00:00Z' });

  const mobile = buildAndroidSha256Reputation(db);
  assert.deepEqual(mobile.malicious_sha256, []);
  assert.deepEqual(mobile.suspicious_sha256, ['a'.repeat(64)]);
});

test('expired evidence cannot remain active or reach Android export', () => {
  const db = buildThreatDatabase([
    obs({ expires_at: '2026-10-01T12:00:00Z' })
  ], { generatedAt: '2026-10-02T00:00:00Z' });

  assert.equal(db.records[0].active, false);
  assert.deepEqual(buildAndroidSha256Reputation(db).malicious_sha256, []);
  assert.deepEqual(buildAndroidSha256Reputation(db).suspicious_sha256, []);
});

test('preserves KEV exploitation and ransomware facts separately from malware consensus', () => {
  const db = buildThreatDatabase([
    obs({
      observation_id: 'cisa:CVE-2026-12345',
      indicator_type: 'cve',
      indicator_value: 'CVE-2026-12345',
      source_id: 'cisa_kev',
      source_kind: 'vulnerability_catalog',
      source_verdict: 'unknown',
      exploit_status: 'known_exploited',
      ransomware_use: 'Known',
      vendor: 'Vendor',
      product: 'Product',
      cwes: ['CWE-287']
    })
  ], { generatedAt: '2026-10-02T00:00:00Z' });

  assert.equal(db.records[0].known_exploited, true);
  assert.equal(db.records[0].known_ransomware_use, true);
  assert.deepEqual(db.records[0].vendors, ['Vendor']);
  assert.deepEqual(db.records[0].cwes, ['CWE-287']);
});

test('rejects observation-id collisions across different indicators', () => {
  assert.throws(
    () => buildThreatDatabase([
      obs(),
      obs({ indicator_value: 'b'.repeat(64) })
    ], { generatedAt: '2026-10-02T00:00:00Z' }),
    /THREAT_DATABASE_OBSERVATION_ID_COLLISION/
  );
});
