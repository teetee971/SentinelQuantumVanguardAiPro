import assert from 'node:assert/strict';
import test from 'node:test';

import {
  THREAT_SOURCE_REGISTRY,
  canSourceAttributeActors,
  canSourceAutoDownloadSamples,
  enabledAutomatedThreatSources,
  getThreatSourceProfile
} from './source-registry.js';

test('registers the requested malware and threat-map reference sources', () => {
  for (const sourceId of [
    'malwarebazaar',
    'virusshare',
    'av_atlas',
    'kaspersky_cybermap',
    'checkpoint_threatmap',
    'radware_live_threat_map',
    'netscout_threat_horizon'
  ]) {
    assert.ok(getThreatSourceProfile(sourceId), sourceId);
  }
});

test('keeps all external source integrations disabled until bounded adapters exist', () => {
  assert.deepEqual(enabledAutomatedThreatSources(), []);
  for (const source of Object.values(THREAT_SOURCE_REGISTRY)) {
    assert.equal(source.automated_ingestion_enabled, false);
  }
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

test('identifies metadata-query-capable malware repositories separately from visual/statistical sources', () => {
  assert.equal(getThreatSourceProfile('malwarebazaar').metadata_query_supported, true);
  assert.equal(getThreatSourceProfile('virusshare').metadata_query_supported, true);
  assert.equal(getThreatSourceProfile('av_atlas').metadata_query_supported, false);
  assert.equal(getThreatSourceProfile('netscout_threat_horizon').source_kind, 'threat_map');
});

test('returns null for unknown sources rather than inventing capabilities', () => {
  assert.equal(getThreatSourceProfile('unknown-provider'), null);
  assert.equal(canSourceAutoDownloadSamples('unknown-provider'), false);
  assert.equal(canSourceAttributeActors('unknown-provider'), false);
});
