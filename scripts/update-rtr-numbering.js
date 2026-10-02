import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseSemicolonCsv } from './update-arcep-numbering.js';
import { createRtrLookup, RTR_SOURCE_URL, RTR_TERMS_URL } from '../public/phone-rtr.js';
import { fetchOfficialBytes } from './official-numbering-download.js';

const HEADERS = {
  geo: ['ortsnetzkennzahl', 'ortsnetzname', 'rufnummernbeginn', 'rufnummernende', 'betreiber', 'betreiberid'],
  services: ['rufnummernbereich', 'bereichskennzahl', 'rufnummernbeginn', 'rufnummernende', 'betreiber', 'betreiberid'],
  areas: ['ortsnetzkennzahl', 'ortsnetzname']
};
const DATASETS = { geo: 'tn-geo', services: 'tn-dienste', areas: 'tn-ortsnetze' };
const RTR_ORIGINS = ['https://data.rtr.at', 'https://www.rtr.at'];
const EXCLUDED_CATEGORIES = new Set(['Betreiberauswahl-Präfix', 'Routingnummern']);
const SPECIAL = new Map([
  ['------ nicht zugeteilt ------', -1],
  ['--- NICHT im Zuteilungsbereich ---', -2],
  ['wird derzeit nicht vergeben', -3],
  ['Teilweise zugeteilt; Zuteilungsstatus siehe www.rtr.at/num/gz', -4],
  ['Keine Zuteilung gem. § 10 Abs 11 KEM-V 2009', -5]
]);
const fail = (message) => { throw new Error(`RTR_${message}`); };
function table(bytes, kind) {
  if (!Buffer.isBuffer(bytes) || bytes.length > 8 * 1024 * 1024) fail('INPUT_TOO_LARGE');
  const rows = parseSemicolonCsv(new TextDecoder('utf-8', { fatal: true }).decode(bytes).replace(/^\uFEFF/, ''));
  if (JSON.stringify(rows.shift()) !== JSON.stringify(HEADERS[kind])) fail(`SCHEMA_${kind}`);
  if (!rows.length || rows.length > 100_000 || rows.some((row) => row.length !== HEADERS[kind].length)) fail(`ROWS_${kind}`);
  return rows.map((row) => Object.fromEntries(HEADERS[kind].map((key, index) => [key, row[index]])));
}

export function buildRtrDirectory(inputs, { generatedAt, sourcePublishedAt = null } = {}) {
  if (!Number.isFinite(Date.parse(generatedAt)) || (sourcePublishedAt !== null && !/^\d{4}-\d{2}-\d{2}$/.test(sourcePublishedAt))) fail('DATE_REQUIRED');
  const tables = Object.fromEntries(Object.keys(HEADERS).map((kind) => [kind, table(inputs[kind], kind)]));
  const areas = new Map();
  for (const row of tables.areas) {
    if (!/^[1-9]\d{0,3}$/.test(row.ortsnetzkennzahl) || !row.ortsnetzname || areas.has(row.ortsnetzkennzahl)) fail('AREA');
    areas.set(row.ortsnetzkennzahl, row.ortsnetzname);
  }
  const holders = [];
  const holderIds = new Map();
  const groups = Object.create(null);
  const excludedCategories = {};
  let recordCount = 0;
  for (const kind of ['geo', 'services']) {
    for (const row of tables[kind]) {
      if (EXCLUDED_CATEGORIES.has(row.rufnummernbereich)) {
        excludedCategories[row.rufnummernbereich] = (excludedCategories[row.rufnummernbereich] ?? 0) + 1;
        continue;
      }
      const prefix = row.ortsnetzkennzahl ?? row.bereichskennzahl;
      const start = row.rufnummernbeginn;
      const end = row.rufnummernende;
      if (!/^[1-9]\d{0,3}$/.test(prefix) || !/^\d{2,11}$/.test(start) || !/^\d+$/.test(end) ||
          start.length !== end.length || start > end || prefix.length + start.length > 13) fail('RANGE');
      if (kind === 'geo' && areas.get(prefix) !== row.ortsnetzname) fail('AREA_MISMATCH');
      let holder = SPECIAL.get(row.betreiber);
      if (holder === undefined) {
        // Unknown administrative status must not silently become a person's/company's name.
        if (/^-|zuteil|vergeb/i.test(row.betreiber)) fail('UNKNOWN_STATUS');
        const entry = [row.betreiber, row.betreiberid || null];
        const key = JSON.stringify(entry);
        if (!holderIds.has(key)) { holderIds.set(key, holders.length); holders.push(entry); }
        holder = holderIds.get(key);
      } else if (row.betreiberid) fail('STATUS_WITH_HOLDER');
      const key = `${prefix}/${start.length}`;
      const info = { kind: kind === 'geo' ? 'geographic' : 'service', category: row.rufnummernbereich ?? 'Geografische Rufnummern', area: row.ortsnetzname ?? '' };
      const group = groups[key] ??= { ...info, ranges: [] };
      if (group.kind !== info.kind || group.category !== info.category || group.area !== info.area) fail('CONFLICTING_GROUP');
      group.ranges.push([start, end, holder]);
      recordCount += 1;
    }
  }
  let overlapCount = 0;
  for (const group of Object.values(groups)) {
    group.ranges.sort((a, b) => a[0].localeCompare(b[0]) || a[1].localeCompare(b[1]) || a[2] - b[2]);
    let maximum = '';
    for (const [start, end] of group.ranges) {
      if (start <= maximum) overlapCount += 1;
      if (end > maximum) maximum = end;
    }
  }
  const directory = {
    schemaVersion: 1, country: 'AT', generatedAt, sourcePublishedAt,
    attribution: 'RTR-GmbH – data.rtr.at', sourceUrl: RTR_SOURCE_URL, termsUrl: RTR_TERMS_URL,
    sourceDelivery: 'user-provided-csv',
    sources: Object.fromEntries(Object.keys(HEADERS).map((kind) => [kind, {
      url: `https://data.rtr.at/pages/open-data/${DATASETS[kind]}`,
      sha256: createHash('sha256').update(inputs[kind]).digest('hex'), rows: tables[kind].length
    }])),
    sourceRecordCount: tables.geo.length + tables.services.length,
    recordCount, excludedCategories, overlapCount,
    rangeFields: ['subscriberStart', 'subscriberEnd', 'holderIndexOrNegativeStatus'],
    holderFields: ['publishedAllocationHolder', 'rtrOperatorId'],
    holders, groups
  };
  createRtrLookup(directory);
  return directory;
}

export function discoverRtrCsv(html, kind) {
  if (!Object.hasOwn(DATASETS, kind) || typeof html !== 'string' || Buffer.byteLength(html) > 2 * 1024 * 1024) fail('DISCOVERY_INPUT');
  const page = `https://data.rtr.at/pages/open-data/${DATASETS[kind]}`;
  const candidates = new Set();
  for (const match of html.matchAll(/href\s*=\s*["']([^"']+)["']/gi)) {
    let url;
    try { url = new URL(match[1].replaceAll('&amp;', '&'), page); } catch { continue; }
    if (!RTR_ORIGINS.includes(url.origin) || url.username || url.password || url.port) continue;
    let name;
    try { name = decodeURIComponent(url.pathname.split('/').at(-1)).toLowerCase(); } catch { continue; }
    if (name !== `${DATASETS[kind]}.csv` && !(name === DATASETS[kind] && url.searchParams.get('format') === 'csv')) continue;
    url.hash = '';
    candidates.add(url.href);
  }
  if (candidates.size !== 1) fail(candidates.size ? 'AMBIGUOUS_DOWNLOAD' : 'CSV_LINK_MISSING');
  return [...candidates][0];
}

export async function downloadRtrInputs({ fetchImpl = fetch } = {}) {
  const inputs = {};
  const downloads = {};
  await Promise.all(Object.keys(DATASETS).map(async kind => {
    const pageUrl = `https://data.rtr.at/pages/open-data/${DATASETS[kind]}`;
    const page = await fetchOfficialBytes(pageUrl, { allowedOrigins: RTR_ORIGINS, maxBytes: 2 * 1024 * 1024, fetchImpl });
    const url = discoverRtrCsv(new TextDecoder('utf-8', { fatal: true }).decode(page.bytes), kind);
    const csv = await fetchOfficialBytes(url, { allowedOrigins: RTR_ORIGINS, maxBytes: 8 * 1024 * 1024, fetchImpl });
    inputs[kind] = csv.bytes;
    downloads[kind] = csv.url;
  }));
  return { inputs, downloads };
}

export function assertRtrRefresh(before, after) {
  createRtrLookup(after);
  if (!before) return;
  createRtrLookup(before);
  if (before.schemaVersion !== after.schemaVersion ||
      JSON.stringify(before.rangeFields) !== JSON.stringify(after.rangeFields) ||
      JSON.stringify(before.holderFields) !== JSON.stringify(after.holderFields)) fail('SCHEMA_CHANGED');
  if (before.sourcePublishedAt && (!after.sourcePublishedAt || after.sourcePublishedAt < before.sourcePublishedAt)) fail('PUBLICATION_ROLLBACK');
  if (after.recordCount < before.recordCount * 0.9) fail('RECORD_COUNT_DROP');
  for (const kind of Object.keys(DATASETS)) {
    const previous = before.sources?.[kind]?.rows;
    const incoming = after.sources?.[kind]?.rows;
    if (!Number.isSafeInteger(previous) || !Number.isSafeInteger(incoming) || incoming < 1) fail('PROVENANCE_ROWS');
    if (incoming < previous * 0.9) fail(`SOURCE_ROWS_DROP_${kind}`);
  }
}

export function sameRtrContent(before, after) {
  if (!before) return false;
  // The unchanged source batch must not become a new dataset just because it was fetched again.
  const stable = directory => {
    const { generatedAt, sourceDelivery, sources, ...data } = directory;
    return JSON.stringify({ ...data, sources: Object.fromEntries(Object.keys(DATASETS).map(kind => [kind, {
      sha256: sources[kind].sha256, rows: sources[kind].rows
    }])) });
  };
  return stable(before) === stable(after);
}

export async function main(argv = process.argv.slice(2), { fetchImpl = fetch } = {}) {
  const options = {};
  const allowed = new Set(['geo', 'services', 'areas', 'generated-at', 'source-published-at', 'output']);
  const automatic = argv[0] === '--automatic';
  if (automatic) argv = argv.slice(1);
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i].replace(/^--/, '');
    if (!argv[i].startsWith('--') || !allowed.has(key) || !argv[i + 1] || argv[i + 1].startsWith('--') || options[key]) fail('ARGUMENT');
    options[key] = argv[i + 1];
  }
  if (automatic && ['geo', 'services', 'areas', 'source-published-at'].some(key => options[key])) fail('AUTOMATIC_ARGUMENT_CONFLICT');
  const downloaded = automatic ? await downloadRtrInputs({ fetchImpl }) : null;
  const inputs = downloaded?.inputs ?? {};
  for (const kind of automatic ? [] : Object.keys(HEADERS)) {
    if (!options[kind]) fail(`MISSING_${kind}`);
    const path = resolve(options[kind]);
    if ((await stat(path)).size > 8 * 1024 * 1024) fail('INPUT_TOO_LARGE');
    inputs[kind] = await readFile(path);
  }
  const directory = buildRtrDirectory(inputs, {
    generatedAt: options['generated-at'] ?? new Date().toISOString(),
    sourcePublishedAt: options['source-published-at'] ?? null
  });
  if (automatic) {
    directory.sourceDelivery = 'official-https-csv';
    for (const kind of Object.keys(DATASETS)) directory.sources[kind].downloadUrl = downloaded.downloads[kind];
  }
  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > 4 * 1024 * 1024) fail('OUTPUT_TOO_LARGE');
  const target = resolve(options.output ?? 'public/data/rtr-numbering.json');
  if (!automatic && Object.keys(HEADERS).some((kind) => resolve(options[kind]) === target)) fail('OUTPUT_IS_INPUT');
  let previous = null;
  try { previous = JSON.parse(await readFile(target, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  assertRtrRefresh(previous, directory);
  if (sameRtrContent(previous, directory)) {
    console.log('RTR: source batch unchanged; previous index preserved.');
    return;
  }
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  console.log(`RTR: ${directory.recordCount} ranges; ${directory.overlapCount} overlaps preserved; source publication date: ${directory.sourcePublishedAt ?? 'unknown'}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => { console.error(error.message); process.exitCode = 1; });
}
