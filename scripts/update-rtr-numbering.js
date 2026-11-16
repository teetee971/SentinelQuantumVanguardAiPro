import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseSemicolonCsv } from './update-arcep-numbering.js';
import { createRtrLookup, RTR_SOURCE_URL, RTR_TERMS_URL } from '../public/phone-rtr.js';

const HEADERS = {
  geo: ['ortsnetzkennzahl', 'ortsnetzname', 'rufnummernbeginn', 'rufnummernende', 'betreiber', 'betreiberid'],
  services: ['rufnummernbereich', 'bereichskennzahl', 'rufnummernbeginn', 'rufnummernende', 'betreiber', 'betreiberid'],
  areas: ['ortsnetzkennzahl', 'ortsnetzname']
};
const DATASETS = { geo: 'tn-geo', services: 'tn-dienste', areas: 'tn-ortsnetze' };
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

export async function main(argv = process.argv.slice(2)) {
  const options = {};
  const allowed = new Set(['geo', 'services', 'areas', 'generated-at', 'source-published-at', 'output']);
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i].replace(/^--/, '');
    if (!argv[i].startsWith('--') || !allowed.has(key) || !argv[i + 1] || argv[i + 1].startsWith('--') || options[key]) fail('ARGUMENT');
    options[key] = argv[i + 1];
  }
  const inputs = {};
  for (const kind of Object.keys(HEADERS)) {
    if (!options[kind]) fail(`MISSING_${kind}`);
    const path = resolve(options[kind]);
    if ((await stat(path)).size > 8 * 1024 * 1024) fail('INPUT_TOO_LARGE');
    inputs[kind] = await readFile(path);
  }
  const directory = buildRtrDirectory(inputs, {
    generatedAt: options['generated-at'] ?? new Date().toISOString(),
    sourcePublishedAt: options['source-published-at'] ?? null
  });
  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > 4 * 1024 * 1024) fail('OUTPUT_TOO_LARGE');
  const target = resolve(options.output ?? 'public/data/rtr-numbering.json');
  if (Object.keys(HEADERS).some((kind) => resolve(options[kind]) === target)) fail('OUTPUT_IS_INPUT');
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  console.log(`RTR: ${directory.recordCount} ranges; ${directory.overlapCount} overlaps preserved; source publication date: ${directory.sourcePublishedAt ?? 'unknown'}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => { console.error(error.message); process.exitCode = 1; });
}
