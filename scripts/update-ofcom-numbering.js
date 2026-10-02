import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const OFCOM_PAGE_URL = 'https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbering-data';
const OFCOM_ORIGIN = 'https://www.ofcom.org.uk';
const OFCOM_CSV_PREFIX = '/siteassets/resources/documents/phones-telecoms-and-internet/information-for-industry/numbering/regular-updates/telephone-numbers/';
const REQUIRED_FILES = Object.freeze(['s1.csv', 's3.csv', 's5.csv', 's7.csv', 's8.csv', 's9.csv']);
const MAX_PAGE_BYTES = 2 * 1024 * 1024;
const MAX_CSV_BYTES = 16 * 1024 * 1024;
const MAX_ROWS_PER_FILE = 450_000;
const MAX_TOTAL_ROWS = 500_000;
const MAX_OUTPUT_BYTES = 64 * 1024 * 1024;

function fail(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

function bounded(value, code, max, { required = false } = {}) {
  const text = String(value ?? '').trim();
  if ((required && !text) || text.length > max) fail(code, text.slice(0, 80));
  return text;
}

export function parseCommaCsv(text) {
  if (typeof text !== 'string') fail('OFCOM_TEXT_REQUIRED');
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    if (quoted) {
      if (char === '"' && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else if (char === '"') {
        quoted = false;
      } else {
        field += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === ',') {
      row.push(field);
      field = '';
    } else if (char === '\n') {
      row.push(field.replace(/\r$/, ''));
      rows.push(row);
      if (rows.length > MAX_ROWS_PER_FILE + 1) fail('OFCOM_TOO_MANY_ROWS');
      row = [];
      field = '';
    } else {
      field += char;
    }
  }
  if (quoted) fail('OFCOM_UNTERMINATED_QUOTE');
  if (field || row.length) {
    row.push(field.replace(/\r$/, ''));
    rows.push(row);
  }
  return rows.filter((cells) => cells.some((cell) => String(cell).trim() !== ''));
}

function normalizedHeader(value) {
  return String(value ?? '').replace(/^\uFEFF/, '').trim().toLowerCase().replace(/[^a-z0-9]/g, '');
}

function findColumn(headers, aliases, required = false) {
  const normalized = headers.map(normalizedHeader);
  for (const alias of aliases) {
    const index = normalized.indexOf(alias);
    if (index >= 0) return index;
  }
  if (required) fail('OFCOM_SCHEMA_COLUMN_MISSING', aliases[0]);
  return -1;
}

function segmentBounds(raw, code) {
  const value = String(raw ?? '').replace(/\s+/g, '');
  if (!value) return { start: '', end: '' };
  if (/^\d+$/.test(value)) return { start: value, end: value };
  const match = /^(\d+)-(\d+)$/.exec(value);
  if (!match || match[1].length !== match[2].length || match[1] > match[2]) fail(code, value);
  return { start: match[1], end: match[2] };
}

function nationalPrefix(parts, code) {
  const digits = parts.join('');
  if (!/^\d+$/.test(digits)) fail(code, digits);
  const normalized = digits.startsWith('0') ? digits : `0${digits}`;
  if (normalized.length < 2 || normalized.length > 11) fail(code, normalized);
  return normalized;
}

function dictionaryIndex(map, values, value) {
  if (!value) return -1;
  if (!map.has(value)) {
    map.set(value, values.length);
    values.push(value);
  }
  return map.get(value);
}

export function parseOfcomNumberingCsv(text, sourceName = 'unknown.csv') {
  const rows = parseCommaCsv(text);
  if (rows.length < 2) fail('OFCOM_EMPTY_FILE', sourceName);
  const headers = rows[0];
  const baseIndex = findColumn(headers, ['sabc', 'sabccode'], true);
  const segmentIndex = findColumn(headers, ['dde', 'range', 'd'], true);
  const subRangeIndex = findColumn(headers, ['subrange', 'e']);
  const statusIndex = findColumn(headers, ['status'], true);
  const providerIndex = findColumn(headers, ['communicationsprovider', 'provider', 'rangeholder'], true);
  const useIndex = findColumn(headers, ['use', 'servicetype']);
  const notesIndex = findColumn(headers, ['notes', 'note']);
  const dateIndex = findColumn(headers, ['date']);
  const changeIndex = findColumn(headers, ['change', 'changedate', 'lastchange']);

  const parsed = [];
  for (let rowIndex = 1; rowIndex < rows.length; rowIndex += 1) {
    const cells = rows[rowIndex];
    if (cells.length > headers.length + 2) fail('OFCOM_ROW_WIDTH', `${sourceName}:${rowIndex + 1}`);
    const base = segmentBounds(cells[baseIndex], 'OFCOM_INVALID_SABC');
    const segment = segmentBounds(cells[segmentIndex], 'OFCOM_INVALID_SEGMENT');
    const sub = subRangeIndex >= 0 ? segmentBounds(cells[subRangeIndex], 'OFCOM_INVALID_SUBRANGE') : { start: '', end: '' };
    const start = nationalPrefix([base.start, segment.start, sub.start], 'OFCOM_INVALID_PREFIX');
    const end = nationalPrefix([base.end, segment.end, sub.end], 'OFCOM_INVALID_PREFIX');
    if (start.length !== end.length || start > end) fail('OFCOM_INVALID_PREFIX_RANGE', `${start}-${end}`);

    parsed.push({
      start,
      end,
      status: bounded(cells[statusIndex], 'OFCOM_INVALID_STATUS', 100, { required: true }),
      provider: bounded(cells[providerIndex], 'OFCOM_INVALID_PROVIDER', 200),
      use: useIndex >= 0 ? bounded(cells[useIndex], 'OFCOM_INVALID_USE', 160) : '',
      notes: notesIndex >= 0 ? bounded(cells[notesIndex], 'OFCOM_INVALID_NOTES', 600) : '',
      date: dateIndex >= 0 ? bounded(cells[dateIndex], 'OFCOM_INVALID_DATE_TEXT', 64) : '',
      change: changeIndex >= 0 ? bounded(cells[changeIndex], 'OFCOM_INVALID_CHANGE_TEXT', 64) : '',
      source: sourceName
    });
  }
  return parsed;
}

const MONTHS = Object.freeze({
  january: '01', february: '02', march: '03', april: '04', may: '05', june: '06',
  july: '07', august: '08', september: '09', october: '10', november: '11', december: '12'
});

function validIsoDay(value) {
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

function parseEnglishDate(value) {
  const match = /^(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})$/.exec(String(value).trim());
  if (!match) fail('OFCOM_PUBLICATION_DATE_INVALID', value);
  const month = MONTHS[match[2].toLowerCase()];
  if (!month) fail('OFCOM_PUBLICATION_DATE_INVALID', value);
  const day = match[1].padStart(2, '0');
  const iso = `${match[3]}-${month}-${day}`;
  if (!validIsoDay(iso)) fail('OFCOM_PUBLICATION_DATE_INVALID', value);
  return iso;
}

function assertCsvUrl(rawUrl) {
  const url = new URL(rawUrl, OFCOM_PAGE_URL);
  if (url.origin !== OFCOM_ORIGIN || !url.pathname.startsWith(OFCOM_CSV_PREFIX) || !url.pathname.toLowerCase().endsWith('.csv')) {
    fail('OFCOM_SOURCE_NOT_ALLOWED', url.href);
  }
  return url;
}

export function discoverOfcomCsvUrls(html) {
  if (typeof html !== 'string' || Buffer.byteLength(html) > MAX_PAGE_BYTES) fail('OFCOM_PAGE_INVALID');
  const dateMatch = /Current files publish date:\s*([0-9]{1,2}\s+[A-Za-z]+\s+[0-9]{4})/i.exec(html);
  if (!dateMatch) fail('OFCOM_PUBLICATION_DATE_MISSING');
  const sourcePublishedAt = parseEnglishDate(dateMatch[1]);
  const links = new Map();
  const hrefPattern = /href\s*=\s*["']([^"']+\.csv(?:\?[^"']*)?)["']/gi;
  for (const match of html.matchAll(hrefPattern)) {
    const raw = match[1].replaceAll('&amp;', '&');
    let url;
    try {
      url = assertCsvUrl(raw);
    } catch {
      continue;
    }
    const name = decodeURIComponent(url.pathname.split('/').at(-1)).toLowerCase();
    if (REQUIRED_FILES.includes(name) && !links.has(name)) links.set(name, url.href);
  }
  for (const name of REQUIRED_FILES) {
    if (!links.has(name)) fail('OFCOM_REQUIRED_FILE_MISSING', name);
  }
  return { sourcePublishedAt, links };
}

export function assertNotOlder(existing, incomingPublishedAt) {
  if (!existing || typeof existing !== 'object') return;
  const previous = existing.sourcePublishedAt;
  if (typeof previous === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(previous) && incomingPublishedAt < previous) {
    fail('OFCOM_ROLLBACK_REJECTED', `${incomingPublishedAt}<${previous}`);
  }
}

function stableDirectory(directory) {
  if (!directory || typeof directory !== 'object') return null;
  const { generatedAt, ...stable } = directory;
  return JSON.stringify(stable);
}

export function buildOfcomDirectory(files, { sourcePublishedAt, generatedAt = new Date().toISOString() } = {}) {
  if (!files || typeof files !== 'object') fail('OFCOM_FILES_REQUIRED');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(sourcePublishedAt ?? ''))) fail('OFCOM_PUBLICATION_DATE_REQUIRED');
  if (!validIsoDay(sourcePublishedAt)) fail('OFCOM_PUBLICATION_DATE_INVALID');
  if (!Number.isFinite(Date.parse(generatedAt))) fail('OFCOM_GENERATED_AT_INVALID');

  const holders = [];
  const statuses = [];
  const uses = [];
  const holderMap = new Map();
  const statusMap = new Map();
  const useMap = new Map();
  const entries = [];
  const sources = {};

  for (const name of REQUIRED_FILES) {
    const input = files[name];
    if (input === undefined) fail('OFCOM_REQUIRED_FILE_MISSING', name);
    const bytes = Buffer.isBuffer(input) ? input : Buffer.from(String(input), 'utf8');
    if (!bytes.length || bytes.length > MAX_CSV_BYTES) fail('OFCOM_INPUT_SIZE', name);
    const text = new TextDecoder('windows-1252', { fatal: false }).decode(bytes);
    const parsed = parseOfcomNumberingCsv(text, name);
    sources[name] = {
      sha256: createHash('sha256').update(bytes).digest('hex'),
      rows: parsed.length
    };
    for (const row of parsed) {
      entries.push([
        row.start,
        row.end,
        dictionaryIndex(holderMap, holders, row.provider),
        dictionaryIndex(statusMap, statuses, row.status),
        dictionaryIndex(useMap, uses, row.use),
        row.date || null,
        row.change || null,
        row.notes || null,
        row.source
      ]);
      if (entries.length > MAX_TOTAL_ROWS) fail('OFCOM_TOO_MANY_ROWS');
    }
  }

  entries.sort((left, right) => left[0].localeCompare(right[0]) || left[1].localeCompare(right[1]));
  return {
    schemaVersion: 1,
    country: 'GB',
    countryCallingCode: '+44',
    generatedAt,
    sourcePublishedAt,
    sourcePage: OFCOM_PAGE_URL,
    attribution: 'Ofcom numbering data',
    semantics: 'Regulatory numbering allocation/status. Range holder is not necessarily the current provider after number portability and is not proof of caller identity, legitimacy, fraud, or spoofing.',
    entryFields: ['nationalPrefixStart', 'nationalPrefixEnd', 'holderIndex', 'statusIndex', 'useIndex', 'date', 'change', 'notes', 'sourceFile'],
    holders,
    statuses,
    uses,
    sources,
    recordCount: entries.length,
    entries
  };
}

export async function fetchBounded(url, maxBytes, fetchImpl = fetch) {
  const parsed = url === OFCOM_PAGE_URL ? new URL(url) : assertCsvUrl(url);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30_000);
  try {
    const response = await fetchImpl(parsed, {
      redirect: 'error',
      signal: controller.signal,
      headers: { 'User-Agent': 'SentinelQuantumVanguardAiPro/1.0 official-data-refresh' }
    });
    if (!response.ok) fail('OFCOM_FETCH_FAILED', `${response.status} ${parsed.pathname}`);
    const declared = Number(response.headers.get('content-length') || 0);
    if (declared > maxBytes) fail('OFCOM_INPUT_SIZE', parsed.pathname);
    if (!response.body) fail('OFCOM_INPUT_SIZE', parsed.pathname);
    const reader = response.body.getReader();
    const chunks = [];
    let size = 0;
    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > maxBytes) fail('OFCOM_INPUT_SIZE', parsed.pathname);
        chunks.push(Buffer.from(value));
      }
    } finally { await reader.cancel(); }
    if (!size) fail('OFCOM_INPUT_SIZE', parsed.pathname);
    return Buffer.concat(chunks);
  } finally {
    clearTimeout(timer);
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
  assertNotOlder(existing, directory.sourcePublishedAt);
  if (stableDirectory(existing) === stableDirectory(directory)) return { target, changed: false };
  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > MAX_OUTPUT_BYTES) fail('OFCOM_OUTPUT_TOO_LARGE');
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  return { target, changed: true };
}

export async function main(argv = process.argv.slice(2)) {
  let output = 'public/data/ofcom-numbering.json';
  if (argv.length) {
    if (argv.length !== 2 || argv[0] !== '--output' || !argv[1]) fail('OFCOM_ARGUMENTS_INVALID');
    output = argv[1];
  }
  const pageBytes = await fetchBounded(OFCOM_PAGE_URL, MAX_PAGE_BYTES);
  const page = new TextDecoder('utf-8', { fatal: false }).decode(pageBytes);
  const { sourcePublishedAt, links } = discoverOfcomCsvUrls(page);
  const files = {};
  await Promise.all(REQUIRED_FILES.map(async (name) => {
    files[name] = await fetchBounded(links.get(name), MAX_CSV_BYTES);
  }));
  const directory = buildOfcomDirectory(files, { sourcePublishedAt });
  const result = await writeAtomically(output, directory);
  console.log(`Ofcom directory validated: ${directory.recordCount} ranges; source=${sourcePublishedAt}; changed=${result.changed} -> ${result.target}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}

