const MAX_RECORDS = 500;
const MAX_TEXT = 8_000;
const MAX_INDICATORS = 200;
const TOKEN_STOPWORDS = new Set([
  'avec', 'dans', 'des', 'les', 'pour', 'sur', 'une', 'the', 'and', 'from', 'that', 'this', 'vulnerability'
]);

function boundedText(value, field, max = MAX_TEXT) {
  if (typeof value !== 'string' || !value.trim() || value.length > max) {
    throw new TypeError(`INVALID_${field.toUpperCase()}`);
  }
  return value.trim();
}

function normalizeRecord(record) {
  if (!record || typeof record !== 'object' || Array.isArray(record)) throw new TypeError('INVALID_RECORD');
  const publishedAt = new Date(boundedText(record.publishedAt, 'publishedAt', 64));
  if (!Number.isFinite(publishedAt.getTime())) throw new TypeError('INVALID_PUBLISHED_AT');
  const sourceUri = boundedText(record.sourceUri, 'sourceUri', 2048);
  const parsed = new URL(sourceUri);
  if (parsed.protocol !== 'https:') throw new TypeError('INVALID_SOURCE_URI');
  return Object.freeze({
    sourceId: boundedText(record.sourceId, 'sourceId', 128),
    sourceUri,
    publishedAt: publishedAt.toISOString(),
    title: boundedText(record.title, 'title', 1000),
    summary: typeof record.summary === 'string' ? record.summary.trim().slice(0, MAX_TEXT) : ''
  });
}

function publicIpv4(value) {
  const octets = value.split('.').map(Number);
  if (octets.length !== 4 || octets.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) return false;
  return !(octets[0] === 10 ||
    octets[0] === 127 ||
    (octets[0] === 169 && octets[1] === 254) ||
    (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31) ||
    (octets[0] === 192 && octets[1] === 168));
}

export function extractThreatIndicators(text) {
  const value = String(text ?? '').slice(0, MAX_TEXT * 2);
  const indicators = new Set();
  for (const match of value.matchAll(/\bCVE-\d{4}-\d{4,}\b/gi)) indicators.add(`cve:${match[0].toUpperCase()}`);
  for (const match of value.matchAll(/\b[a-f0-9]{64}\b/gi)) indicators.add(`sha256:${match[0].toLowerCase()}`);
  for (const match of value.matchAll(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g)) {
    if (publicIpv4(match[0])) indicators.add(`ip:${match[0]}`);
  }
  for (const match of value.matchAll(/\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}\b/gi)) {
    const domain = match[0].toLowerCase();
    if (!domain.endsWith('.local') && !domain.endsWith('.example')) indicators.add(`domain:${domain}`);
  }
  return [...indicators].slice(0, MAX_INDICATORS);
}

function topicTokens(record) {
  const cves = extractThreatIndicators(`${record.title} ${record.summary}`)
    .filter((value) => value.startsWith('cve:'));
  if (cves.length) return new Set(cves);
  return new Set(
    record.title.toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
      .split(/[^a-z0-9]+/)
      .filter((token) => token.length >= 4 && !TOKEN_STOPWORDS.has(token))
      .slice(0, 30)
  );
}

function similarity(left, right) {
  const intersection = [...left].filter((token) => right.has(token)).length;
  const union = new Set([...left, ...right]).size;
  return union ? intersection / union : 0;
}

export function consolidateThreatTopics(records) {
  if (!Array.isArray(records) || records.length > MAX_RECORDS) throw new TypeError('INVALID_RECORDS');
  const normalized = records.map(normalizeRecord).sort((a, b) => b.publishedAt.localeCompare(a.publishedAt));
  const topics = [];
  for (const record of normalized) {
    const tokens = topicTokens(record);
    const existing = topics.find((topic) => similarity(topic.tokens, tokens) >= 0.6);
    if (existing) {
      existing.records.push(record);
      for (const token of tokens) existing.tokens.add(token);
    } else {
      topics.push({ tokens, records: [record] });
    }
  }
  return topics.map((topic) => Object.freeze({
    title: topic.records[0].title,
    sourceCount: new Set(topic.records.map((record) => record.sourceId)).size,
    sources: [...new Set(topic.records.map((record) => record.sourceId))].sort(),
    publishedAt: topic.records[0].publishedAt,
    indicators: extractThreatIndicators(topic.records.map((record) => `${record.title} ${record.summary}`).join('\n'))
  }));
}

export async function buildDailyThreatBrief(records, { generatedAt = new Date().toISOString(), generateSummary } = {}) {
  const topics = consolidateThreatTopics(records);
  const context = Object.freeze({
    generatedAt,
    recordCount: records.length,
    topicCount: topics.length,
    topics: topics.slice(0, 20)
  });
  let strategicSummary = topics.length
    ? `${topics.length} sujet(s) consolidé(s) à examiner; priorité aux sujets confirmés par plusieurs sources et aux IoC explicitement observés.`
    : 'Aucun sujet exploitable dans les entrées fournies.';
  let summaryMode = 'deterministic';
  if (generateSummary) {
    const candidate = await generateSummary(context);
    if (typeof candidate !== 'string' || !candidate.trim() || candidate.length > 4_000) {
      throw new TypeError('INVALID_LLM_SUMMARY');
    }
    strategicSummary = candidate.trim();
    summaryMode = 'llm-assisted';
  }
  return Object.freeze({ schemaVersion: 1, ...context, strategicSummary, summaryMode, autonomousAction: false });
}
