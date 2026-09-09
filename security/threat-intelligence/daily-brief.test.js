import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDailyThreatBrief, consolidateThreatTopics, extractThreatIndicators } from './daily-brief.js';

const records = [
  {
    sourceId: 'cert-fr',
    sourceUri: 'https://www.cert.ssi.gouv.fr/avis/example',
    publishedAt: '2026-09-09T08:00:00Z',
    title: 'Exploitation de CVE-2026-12345',
    summary: 'Observation de 203.0.113.20 et du domaine command.example.org.'
  },
  {
    sourceId: 'cisa',
    sourceUri: 'https://www.cisa.gov/known-exploited-vulnerabilities-catalog',
    publishedAt: '2026-09-09T07:00:00Z',
    title: 'CVE-2026-12345 added to catalog',
    summary: 'A second source tracks the same vulnerability.'
  }
];

test('extracts bounded public IoCs and rejects private addresses', () => {
  const indicators = extractThreatIndicators('CVE-2026-12345 8.8.8.8 192.168.1.2 evil.test');
  assert.ok(indicators.includes('cve:CVE-2026-12345'));
  assert.ok(indicators.includes('ip:8.8.8.8'));
  assert.ok(!indicators.includes('ip:192.168.1.2'));
});

test('consolidates duplicate topics across independent sources', () => {
  const topics = consolidateThreatTopics(records);
  assert.equal(topics.length, 1);
  assert.equal(topics[0].sourceCount, 2);
});

test('LLM assistance is optional, bounded and never authorizes action', async () => {
  const brief = await buildDailyThreatBrief(records, {
    generatedAt: '2026-09-09T09:00:00Z',
    generateSummary: async (context) => `Revue humaine requise pour ${context.topicCount} sujet.`
  });
  assert.equal(brief.summaryMode, 'llm-assisted');
  assert.equal(brief.autonomousAction, false);
});
