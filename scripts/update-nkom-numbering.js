import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseCommaCsv } from './update-ofcom-numbering.js';

export const NKOM_DATASET_PAGE = 'https://data.norge.no/nb/datasets/c1617f91-fb9c-4546-8f06-dcd53f82a76f/samla-norsk-nummerplan-for-telefoni-mm-e164';
export const NKOM_SOURCE_PAGE = 'https://nkom.no/telefoni-og-telefonnummer/telefonnummer-og-den-norske-nummerplan/alle-nummerserier-for-norske-telefonnumre/den-norske-nummerplanen-for-telefoni-med-mer-e.164';
export const NKOM_CSV_URL = 'https://stenonicprdnoea01.blob.core.windows.net/enonicpubliccontainer/numsys/nkom.no/E164.csv';
export const NKOM_LICENSE_URL = 'https://data.norge.no/nlod/en/2.0';

const HEADER = Object.freeze(['Fra','Til','Tilbyder','Status','Kommentar','Antall','Kategori','Punktkode']);
const STATUS = Object.freeze({
  Tildelt: 'allocated',
  Ledig: 'available',
  Blokkert: 'blocked'
});
const MAX_INPUT_BYTES = 8 * 1024 * 1024;
const MAX_ROWS = 25_000;
const MAX_OUTPUT_BYTES = 16 * 1024 * 1024;
const SUSPICIOUS_DROP_RATIO = 0.20;

function fail(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function cleanText(value, code, max, { required = false } = {}) {
  const text = String(value ?? '').replace(/\u00a0/g, ' ').trim();
  if ((required && !text) || text.length > max) fail(code, text.slice(0, 120));
  return text;
}

function normalizeNumber(value, code) {
  const text = cleanText(value, code, 40, { required: true });
  if (!/^[0-9 ]+$/.test(text)) fail(code, text);
  const digits = text.replace(/\s+/g, '');
  if (!/^\d{3,12}$/.test(digits)) fail(code, text);
  return digits;
}

function parseCount(value) {
  const text = cleanText(value, 'NKOM_COUNT_INVALID', 32, { required: true }).replace(/[ ,]/g, '');
  if (!/^\d+$/.test(text)) fail('NKOM_COUNT_INVALID', value);
  const count = BigInt(text);
  if (count < 1n || count > BigInt(Number.MAX_SAFE_INTEGER)) fail('NKOM_COUNT_INVALID', value);
  return Number(count);
}

function verifyCount(start, end, count) {
  if (start.length !== end.length || start > end) fail('NKOM_RANGE_INVALID', `${start}-${end}`);
  const expected = BigInt(end) - BigInt(start) + 1n;
  if (expected !== BigInt(count)) fail('NKOM_RANGE_COUNT_MISMATCH', `${start}-${end}:${count}`);
}

export function parseNkomCsv(text) {
  if (typeof text !== 'string' || Buffer.byteLength(text) > MAX_INPUT_BYTES) fail('NKOM_INPUT_SIZE');
  const rows = parseCommaCsv(text);
  if (rows.length < 2 || rows.length > MAX_ROWS + 1) fail('NKOM_ROWS_INVALID');

  const header = rows[0].map((value) => String(value).replace(/^\uFEFF/, '').trim());
  if (JSON.stringify(header) !== JSON.stringify(HEADER)) fail('NKOM_SCHEMA_HEADERS_MISMATCH');

  return rows.slice(1).map((cells, index) => {
    if (cells.length !== HEADER.length) fail('NKOM_ROW_WIDTH', String(index + 2));
    const start = normalizeNumber(cells[0], 'NKOM_START_INVALID');
    const end = normalizeNumber(cells[1], 'NKOM_END_INVALID');
    const count = parseCount(cells[5]);
    verifyCount(start, end, count);

    const sourceStatus = cleanText(cells[3], 'NKOM_STATUS_INVALID', 80, { required: true });
    const status = STATUS[sourceStatus];
    if (!status) fail('NKOM_STATUS_UNKNOWN', sourceStatus);

    const holder = cleanText(cells[2], 'NKOM_HOLDER_INVALID', 260);
    if (status === 'allocated' && !holder) fail('NKOM_ALLOCATED_WITHOUT_HOLDER');
    const category = cleanText(cells[6], 'NKOM_CATEGORY_INVALID', 160, { required: true });

    return {
      start,
      end,
      holder,
      status,
      sourceStatus,
      comment: cleanText(cells[4], 'NKOM_COMMENT_INVALID', 500),
      count,
      category,
      pointCode: cleanText(cells[7], 'NKOM_POINT_CODE_INVALID', 120)
    };
  });
}

function dictionaryIndex(map, values, value) {
  if (!value) return -1;
  if (!map.has(value)) {
    map.set(value, values.length);
    values.push(value);
  }
  return map.get(value);
}

export function buildNkomDirectory(csvBytes, { fetchedAt = new Date().toISOString() } = {}) {
  if (!Buffer.isBuffer(csvBytes) || !csvBytes.length || csvBytes.length > MAX_INPUT_BYTES) fail('NKOM_INPUT_SIZE');
  if (!Number.isFinite(Date.parse(fetchedAt))) fail('NKOM_FETCHED_AT_INVALID');

  const text = new TextDecoder('utf-8', { fatal: true }).decode(csvBytes);
  const rows = parseNkomCsv(text);
  const holders = [];
  const categories = [];
  const holderMap = new Map();
  const categoryMap = new Map();

  const entries = rows.map((row) => [
    row.start,
    row.end,
    row.count,
    row.status,
    dictionaryIndex(holderMap, holders, row.holder),
    dictionaryIndex(categoryMap, categories, row.category),
    row.comment || null,
    row.pointCode || null
  ]);
  entries.sort((a, b) => a[0].localeCompare(b[0]) || a[1].localeCompare(b[1]));

  return {
    schemaVersion: 1,
    country: 'NO',
    countryCallingCode: '+47',
    fetchedAt,
    sourcePublishedAt: null,
    datasetPage: NKOM_DATASET_PAGE,
    sourcePage: NKOM_SOURCE_PAGE,
    sourceCsv: NKOM_CSV_URL,
    license: 'NLOD 2.0',
    licenseUrl: NKOM_LICENSE_URL,
    attribution: 'Contains data under the Norwegian Licence for Open Government Data (NLOD) distributed by Nasjonal kommunikasjonsmyndighet (Nkom). Sentinel normalized the source representation.',
    sourceIntegrity: {
      algorithm: 'sha256',
      csv: sha256(csvBytes)
    },
    semantics: 'Official Nkom E.164 number-plan allocation/status data. Published provider is regulatory allocation metadata, not proof of the current serving provider after portability, caller identity, legitimacy, fraud, or spoofing.',
    entryFields: ['rangeStart', 'rangeEnd', 'count', 'status', 'holderIndex', 'categoryIndex', 'comment', 'pointCode'],
    holders,
    categories,
    recordCount: entries.length,
    entries
  };
}

function stableDirectory(directory) {
  if (!directory || typeof directory !== 'object') return null;
  const { fetchedAt, ...stable } = directory;
  return JSON.stringify(stable);
}

export function assertSafeNkomRefresh(existing, incoming) {
  if (!existing || typeof existing !== 'object') return;
  if (typeof existing.fetchedAt === 'string' && Date.parse(incoming.fetchedAt) < Date.parse(existing.fetchedAt)) {
    fail('NKOM_FETCH_TIME_ROLLBACK');
  }
  if (existing.sourceIntegrity?.csv === incoming.sourceIntegrity?.csv) return;

  const previous = Number(existing.recordCount);
  const next = Number(incoming.recordCount);
  if (Number.isFinite(previous) && previous > 0 && Number.isFinite(next)) {
    const drop = (previous - next) / previous;
    if (drop > SUSPICIOUS_DROP_RATIO) fail('NKOM_SUSPICIOUS_ROW_DROP', `${previous}->${next}`);
  }
}

async function writeAtomically(output, directory) {
  const target = resolve(output);
  let existing = null;
  try {
    existing = JSON.parse(await readFile(target, 'utf8'));
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }

  assertSafeNkomRefresh(existing, directory);
  if (stableDirectory(existing) === stableDirectory(directory)) return { target, changed: false };

  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > MAX_OUTPUT_BYTES) fail('NKOM_OUTPUT_TOO_LARGE');
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  return { target, changed: true };
}

function parseArgs(argv) {
  const options = { input: null, output: 'public/data/nkom-numbering.json', fetchedAt: new Date().toISOString() };
  const allowed = new Set(['input', 'output', 'fetched-at']);
  for (let index = 0; index < argv.length; index += 2) {
    const raw = argv[index];
    const value = argv[index + 1];
    if (!raw?.startsWith('--') || !value || value.startsWith('--')) fail('NKOM_ARGUMENTS_INVALID');
    const key = raw.slice(2);
    if (!allowed.has(key)) fail('NKOM_ARGUMENTS_INVALID', key);
    if (key === 'fetched-at') options.fetchedAt = value;
    else options[key] = value;
  }
  if (!options.input) fail('NKOM_INPUT_FILE_REQUIRED');
  return options;
}

async function readBounded(path) {
  const resolved = resolve(path);
  const info = await stat(resolved);
  if (!info.isFile() || info.size <= 0 || info.size > MAX_INPUT_BYTES) fail('NKOM_INPUT_SIZE');
  return readFile(resolved);
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  const csvBytes = await readBounded(options.input);
  const directory = buildNkomDirectory(csvBytes, { fetchedAt: options.fetchedAt });
  const result = await writeAtomically(options.output, directory);
  console.log(`Nkom directory validated: ${directory.recordCount} ranges; changed=${result.changed} -> ${result.target}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
