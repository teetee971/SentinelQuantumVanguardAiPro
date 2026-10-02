import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const MAX_BYTES = 2 * 1024 * 1024;
export function sourceUrls(markdown) {
  const section = markdown.split(/^## Sources officielles[^\n]*$/m)[1]?.split('\n## ')[0];
  if (!section) throw new Error('SOURCE_SECTION_MISSING');
  const urls = [...new Set([...section.matchAll(/https:\/\/[^\s<>]+/g)].map(match => match[0]))];
  if (!urls.length || urls.length > 100) throw new Error('SOURCE_COUNT_INVALID');
  return urls;
}

export async function probe(url, { fetchImpl = fetch, maxBytes = MAX_BYTES } = {}) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.port) throw new Error('HTTPS_SOURCE_REQUIRED');
  const response = await fetchImpl(url, {
    redirect: 'error', signal: AbortSignal.timeout(20_000),
    headers: { 'User-Agent': 'SentinelQuantumVanguardAiPro/1.0 numbering-source-watch' }
  });
  if (!response.ok) throw new Error(`HTTP_${response.status}`);
  if (Number(response.headers.get('content-length')) > maxBytes) throw new Error('SOURCE_TOO_LARGE');
  if (!response.body) throw new Error('EMPTY_SOURCE');
  const reader = response.body.getReader();
  const hash = createHash('sha256');
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) throw new Error('SOURCE_TOO_LARGE');
      hash.update(value);
    }
  } finally { await reader.cancel(); }
  if (!size) throw new Error('EMPTY_SOURCE');
  return { sha256: hash.digest('hex'), bytes: size };
}

export async function watch(urls, previous = {}, { probeImpl = probe, checkedAt = new Date().toISOString() } = {}) {
  if (!Number.isFinite(Date.parse(checkedAt))) throw new Error('INVALID_CHECK_DATE');
  const old = new Map((previous.sources ?? []).map(source => [source.url, source]));
  const sources = [];
  // Four concurrent requests, two attempts per URL; one failed country cannot block the rest.
  let next = 0;
  await Promise.all(Array.from({ length: Math.min(4, urls.length) }, async () => {
    while (next < urls.length) {
      const index = next++;
      const url = urls[index];
      const prior = old.get(url);
      let result;
      let error;
      for (let attempt = 0; attempt < 2; attempt++) {
        try { result = await probeImpl(url); break; }
        catch (failure) { error = failure; }
      }
      const lastSuccessfulAt = result ? checkedAt : prior?.lastSuccessfulAt ?? null;
      sources[index] = {
        url, checkedAt, lastSuccessfulAt,
        status: result ? (prior?.sha256 ? (prior.sha256 === result.sha256 ? 'UNCHANGED' : 'CHANGED') : 'FIRST_SEEN') : 'UNAVAILABLE',
        sha256: result?.sha256 ?? prior?.sha256 ?? null,
        bytes: result?.bytes ?? prior?.bytes ?? null,
        changedAt: result && result.sha256 !== prior?.sha256 ? checkedAt : prior?.changedAt ?? null,
        // An HTTP check or content hash never establishes publication date, dataset format, or licence.
        error: result ? null : String(error?.message ?? 'FETCH_FAILED').slice(0, 200)
      };
    }
  }));
  return { schemaVersion: 1, checkedAt, purpose: 'Source availability/content monitoring only; not dataset qualification or runtime coverage.', sources };
}

export async function main() {
  const target = 'docs/data/international-numbering-watch.json';
  let previous = {};
  try { previous = JSON.parse(await readFile(target, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const markdown = await readFile('docs/INTERNATIONAL_NUMBERING_SOURCES.md', 'utf8');
  const report = await watch(sourceUrls(markdown), previous);
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(report, null, 2)}\n`);
  await rename(temporary, target);
  const unavailable = report.sources.filter(source => source.status === 'UNAVAILABLE').length;
  const summary = `Numbering source watch: ${report.sources.length} URLs; ${unavailable} unavailable. See ${target}.\n`;
  console.log(summary);
  if (process.env.GITHUB_STEP_SUMMARY) await writeFile(process.env.GITHUB_STEP_SUMMARY, summary, { flag: 'a' });
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
