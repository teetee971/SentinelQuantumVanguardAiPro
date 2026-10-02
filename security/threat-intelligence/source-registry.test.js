import assert from 'node:assert/strict';
import test from 'node:test';

import {
  THREAT_SOURCE_REGISTRY,
  canSourceAttributeActors,
  canSourceAutoDownloadSamples,
  enabledAutomatedThreatSources,
  getThreatSourceProfile,
  sourceRequiresAuth,
  sourceRequiresCommercialReview
} from './source-registry.js';

test('registers authoritative malware, IOC, vulnerability and TTP sources', () => {
  for (const sourceId of [
    'cisa_kev',
    'mitre_attack',
    'nvd',
    'malwarebazaar',
    'threatfox',
    'urlhaus',
    'feodo_tracker',
    'malpedia',
    'misp_warninglists',
    'virusshare'
  ]) {
    assert.ok(getThreatSourceProfile(sourceId), sourceId);
  }
});

test('enables only the bounded CC0 CISA KEV ingestion by default', () => {
  assert.deepEqual(enabledAutomatedThreatSources(), ['cisa_kev']);
});

test('never enables automatic malware sample download through the source registry', () => {
  for (const sourceId of Object.keys(THREAT_SOURCE_REGISTRY)) {
    assert.equal(canSourceAutoDownloadSamples(sourceId), false);
  }
});

test('never treats provider telemetry as actor attribution capability', () => {
  for (const sourceId of Object.keys(THREAT_SOURCE_REGISTRY)) {
    assert.equal(canSourceAttributeActors(sourceId), false);
  }
});

test('marks abuse.ch commercial community feeds for explicit provisioning review', () => {
  for (const sourceId of ['malwarebazaar', 'threatfox', 'urlhaus']) {
    assert.equal(sourceRequiresAuth(sourceId), true);
    assert.equal(sourceRequiresCommercialReview(sourceId), true);
    assert.equal(getThreatSourceProfile(sourceId).automated_ingestion_enabled, false);
  }
});

test('keeps CISA KEV auth-free and commercially unblocked', () => {
  assert.equal(sourceRequiresAuth('cisa_kev'), false);
  assert.equal(sourceRequiresCommercialReview('cisa_kev'), false);
  assert.equal(getThreatSourceProfile('cisa_kev').source_kind, 'vulnerability_catalog');
});

test('preserves short TTLs for volatile IOC infrastructure', () => {
  assert.ok(
    getThreatSourceProfile('feodo_tracker').freshness_ttl_ms <
      getThreatSourceProfile('cisa_kev').freshness_ttl_ms
  );
  assert.ok(
    getThreatSourceProfile('urlhaus').freshness_ttl_ms <
      getThreatSourceProfile('mitre_attack').freshness_ttl_ms
  );
});

test('returns null for unknown sources rather than inventing capabilities', () => {
  assert.equal(getThreatSourceProfile('unknown-provider'), null);
  assert.equal(canSourceAutoDownloadSamples('unknown-provider'), false);
  assert.equal(canSourceAttributeActors('unknown-provider'), false);
  assert.equal(sourceRequiresAuth('unknown-provider'), false);
  assert.equal(sourceRequiresCommercialReview('unknown-provider'), false);
});
