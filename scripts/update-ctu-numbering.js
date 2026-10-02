import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const CTU_RESOURCE_PAGE = 'https://data.ctu.gov.cz/dataset/pridelena-cisla-kody/resource/d401d3ec-ae24-4f72-a8b7-a33d2d349012';
export const CTU_DOWNLOAD_URL = 'https://data.ctu.gov.cz/node/18/download';
export const CTU_SCHEMA_URL = 'https://ctu.gov.cz/schemas/pridelena_cisla_a_kody.json';

const EXPECTED_COLUMNS = Object.freeze([
  ['Číslo/Číselný rozsah', 'string', true],
  ['Počet čísel', 'integer', true],
  ['Typ čísla', 'string', true],
  ['Podnikatel', 'string', false],
  ['IČO', 'string', false],
  ['Přiděleno od', 'date', true],
  ['Přiděleno do', 'date', true],
  ['Číslo reference', 'string', true]
]);

const MAX_CSV_BYTES = 8 * 1024 * 1024;
const MAX_SCHEMA_BYTES = 128 * 1024;
const MAX_METADATA_BYTES = 2 * 1024 * 1024;
const MAX_ROWS = 120_000;
const MAX_OUTPUT_BYTES = 32 * 1024 * 1024;
const SUSPICIOUS_DROP_RATIO = 0.20;

function fail(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function bounded(value, code, max, { required = false } = {}) {
  const text = String(value ?? '').trim();
  if ((required && !text) || text.length > max) fail(code, text.slice(0, 120));
  return text;
}

function parseDelimited(text, delimiter) {
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
    } else if (char === delimiter) {
      row.push(field);
      field = '';
    } else if (char === '\n') {
      row.push(field.replace(/\r$/, ''));
      rows.push(row);
      if (rows.length > MAX_ROWS + 1) fail('CTU_TOO_MANY_ROWS');
      row = [];
      field = '';
    } else {
      field += char;
    }
  }
  if (quoted) fail('CTU_UNTERMINATED_QUOTE');
  if (field || row.length) {
    row.push(field.replace(/\r$/, ''));
    rows.push(row);
  }
  return rows.filter((cells) => cells.some((cell) => String(cell).trim() !== ''));
}

function expectedTitles() {
  return EXPECTED_COLUMNS.map(([title]) => title);
}

function exactHeaders(rows) {
  if (!rows.length) return false;
  const headers = rows[0].map((value) => String(value).replace(/^\uFEFF/, '').trim());
  return JSON.stringify(headers) === JSON.stringify(expectedTitles());
}

export function parseCtuCsv(text) {
  if (typeof text !== 'string' || Buffer.byteLength(text) > MAX_CSV_BYTES) fail('CTU_INPUT_SIZE');
  let rows = parseDelimited(text, ';');
  if (!exactHeaders(rows)) rows = parseDelimited(text, ',');
  if (!exactHeaders(rows)) fail('CTU_SCHEMA_HEADERS_MISMATCH');
  if (rows.length < 2) fail('CTU_EMPTY_FILE');

  const parsed = [];
  for (let index = 1; index < rows.length; index += 1) {
    const cells = rows[index];
    if (cells.length !== EXPECTED_COLUMNS.length) fail('CTU_ROW_WIDTH', String(index + 1));

    const [start, end] = parseResourceRange(cells[0]);
    const count = parseCount(cells[1]);
    verifyRangeCount(start, end, count);

    const company = bounded(cells[3], 'CTU_COMPANY_INVALID', 260);
    const ico = String(cells[4] ?? '').replace(/\s/g, '');
    if (ico && !/^\d{8}$/.test(ico)) fail('CTU_ICO_INVALID', ico);

    parsed.push({
      start,
      end,
      count,
      type: bounded(cells[2], 'CTU_TYPE_INVALID', 500, { required: true }),
      company,
      ico: ico || null,
      allocatedFrom: parseDate(cells[5], 'CTU_ALLOCATED_FROM_INVALID'),
      allocatedUntil: parseDate(cells[6], 'CTU_ALLOCATED_UNTIL_INVALID'),
      reference: bounded(cells[7], 'CTU_REFERENCE_INVALID', 240, { required: true })
    });
  }
  return parsed;
}

function normalizeResource(value) {
  let raw = String(value ?? '').trim().replace(/\u00A0/g, ' ');
  raw = raw.replace(/^\+420\s*/, '');
  const digits = raw.replace(/\s+/g, '');
  if (!/^\d{1,15}$/.test(digits)) fail('CTU_RESOURCE_INVALID', raw);
  return digits;
}

function parseResourceRange(raw) {
  const value = String(raw ?? '').trim().replace(/\u00A0/g, ' ');
  const parts = value.split(/\s+až\s+/i);
  if (parts.length === 1) {
    const single = normalizeResource(parts[0]);
    return [single, single];
  }
  if (parts.length !== 2) fail('CTU_RANGE_INVALID', value);
  const start = normalizeResource(parts[0]);
  const end = normalizeResource(parts[1]);
  if (start.length !== end.length || start > end) fail('CTU_RANGE_INVALID', value);
  return [start, end];
}

function parseCount(raw) {
  const text = String(raw ?? '').replace(/\s/g, '');
  if (!/^\d+$/.test(text)) fail('CTU_COUNT_INVALID', text);
  const value = BigInt(text);
  if (value < 1n || value > BigInt(Number.MAX_SAFE_INTEGER)) fail('CTU_COUNT_INVALID', text);
  return Number(value);
}

function verifyRangeCount(start, end, count) {
  const expected = BigInt(end) - BigInt(start) + 1n;
  if (expected !== BigInt(count)) fail('CTU_RANGE_COUNT_MISMATCH', `${start}-${end}:${count}`);
}

function parseDate(raw, code) {
  const value = bounded(raw, code, 10, { required: true });
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) fail(code, value);
  const parsed = new Date(`${value}T00:00:00Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) {
    fail(code, value);
  }
  return value;
}

export function validateCtuSchema(schemaText) {
  if (typeof schemaText !== 'string' || Buffer.byteLength(schemaText) > MAX_SCHEMA_BYTES) fail('CTU_SCHEMA_SIZE');
  let parsed;
  try {
    parsed = JSON.parse(schemaText);
  } catch {
    fail('CTU_SCHEMA_JSON_INVALID');
  }
  const columns = parsed?.tableSchema?.columns;
  if (!Array.isArray(columns) || columns.length !== EXPECTED_COLUMNS.length) fail('CTU_SCHEMA_COLUMNS_MISMATCH');
  const normalized = columns.map((column) => [
    String(column?.titles ?? ''),
    String(column?.datatype ?? ''),
    Boolean(column?.required)
  ]);
  if (JSON.stringify(normalized) !== JSON.stringify(EXPECTED_COLUMNS)) fail('CTU_SCHEMA_COLUMNS_MISMATCH');
  return parsed;
}

function extractVisibleText(html) {
  const source = String(html);
  let output = '';
  let inTag = false;
  for (const char of source) {
    if (char === '<') {
      inTag = true;
      output += ' ';
    } else if (char === '>') {
      inTag = false;
      output += ' ';
    } else if (!inTag) {
      output += char;
    }
  }
  return output
    .replace(/&nbsp;|&#160;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/\s+/g, ' ')
    .trim();
}

export function parseCtuMetadata(html) {
  if (typeof html !== 'string' || Buffer.byteLength(html) > MAX_METADATA_BYTES) fail('CTU_METADATA_SIZE');
  const text = extractVisibleText(html);
  const match = /Datum aktualizace:\s*(\d{2})\.(\d{2})\.(\d{4})\s*-\s*(\d{2}):(\d{2})/i.exec(text);
  if (!match) fail('CTU_SOURCE_UPDATED_AT_MISSING');
  const [, day, month, year, hour, minute] = match;
  const local = `${year}-${month}-${day}T${hour}:${minute}:00`;
  const parsed = new Date(Date.UTC(
    Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute), 0
  ));
  if (!Number.isFinite(parsed.getTime()) ||
      parsed.getUTCFullYear() !== Number(year) ||
      parsed.getUTCMonth() !== Number(month) - 1 ||
      parsed.getUTCDate() !== Number(day) ||
      parsed.getUTCHours() !== Number(hour) ||
      parsed.getUTCMinutes() !== Number(minute)) {
    fail('CTU_SOURCE_UPDATED_AT_INVALID');
  }
  return { sourceUpdatedLocal: local, sourceTimeZone: 'Europe/Prague' };
}

function dictionaryIndex(map, values, value) {
  const key = JSON.stringify(value);
  if (!map.has(key)) {
    map.set(key, values.length);
    values.push(value);
  }
  return map.get(key);
}

export function buildCtuDirectory(csvBytes, schemaBytes, metadataBytes, { fetchedAt = new Date().toISOString() } = {}) {
  if (!Buffer.isBuffer(csvBytes) || !csvBytes.length || csvBytes.length > MAX_CSV_BYTES) fail('CTU_INPUT_SIZE');
  if (!Buffer.isBuffer(schemaBytes) || !schemaBytes.length || schemaBytes.length > MAX_SCHEMA_BYTES) fail('CTU_SCHEMA_SIZE');
  if (!Buffer.isBuffer(metadataBytes) || !metadataBytes.length || metadataBytes.length > MAX_METADATA_BYTES) fail('CTU_METADATA_SIZE');
  if (!Number.isFinite(Date.parse(fetchedAt))) fail('CTU_FETCHED_AT_INVALID');

  const schemaText = new TextDecoder('utf-8', { fatal: true }).decode(schemaBytes);
  validateCtuSchema(schemaText);
  const csvText = new TextDecoder('utf-8', { fatal: true }).decode(csvBytes);
  const rows = parseCtuCsv(csvText);
  const metadataText = new TextDecoder('utf-8', { fatal: true }).decode(metadataBytes);
  const metadata = parseCtuMetadata(metadataText);

  const holders = [];
  const types = [];
  const holderMap = new Map();
  const typeMap = new Map();
  const entries = rows.map((row) => [
    row.start,
    row.end,
    row.count,
    dictionaryIndex(typeMap, types, row.type),
    dictionaryIndex(holderMap, holders, [row.company || null, row.ico]),
    row.allocatedFrom,
    row.allocatedUntil,
    row.reference
  ]);
  entries.sort((left, right) => left[0].localeCompare(right[0]) || left[1].localeCompare(right[1]));

  return {
    schemaVersion: 1,
    country: 'CZ',
    countryCallingCode: '+420',
    fetchedAt,
    ...metadata,
    sourcePage: CTU_RESOURCE_PAGE,
    sourceDownload: CTU_DOWNLOAD_URL,
    sourceSchema: CTU_SCHEMA_URL,
    sourceRights: {
      copyrightWork: false,
      originalDatabaseCopyrightProtected: false,
      suiGenerisDatabaseRight: false,
      containsPersonalData: false
    },
    sourceIntegrity: {
      algorithm: 'sha256',
      csv: sha256(csvBytes),
      schema: sha256(schemaBytes)
    },
    semantics: 'Official ČTÚ assignments of numbers, number ranges and codes. The published assignee is regulatory allocation data, not proof of current carrier after portability, caller identity, legitimacy, fraud, or spoofing.',
    entryFields: ['resourceStart', 'resourceEnd', 'count', 'typeIndex', 'holderIndex', 'allocatedFrom', 'allocatedUntil', 'reference'],
    holderFields: ['publishedAssignee', 'ico'],
    holders,
    types,
    recordCount: entries.length,
    entries
  };
}

function stableDirectory(directory) {
  if (!directory || typeof directory !== 'object') return null;
  const { fetchedAt, ...stable } = directory;
  return JSON.stringify(stable);
}

export function assertSafeCtuRefresh(existing, incoming) {
  if (!existing || typeof existing !== 'object') return;
  if (typeof existing.sourceUpdatedLocal === 'string' && incoming.sourceUpdatedLocal < existing.sourceUpdatedLocal) {
    fail('CTU_SOURCE_ROLLBACK', `${incoming.sourceUpdatedLocal}<${existing.sourceUpdatedLocal}`);
  }
  if (existing.sourceUpdatedLocal === incoming.sourceUpdatedLocal &&
      existing.sourceIntegrity?.csv &&
      incoming.sourceIntegrity?.csv &&
      existing.sourceIntegrity.csv !== incoming.sourceIntegrity.csv) {
    fail('CTU_SAME_TIMESTAMP_CONTENT_CHANGE');
  }
  if (existing.sourceIntegrity?.csv === incoming.sourceIntegrity?.csv) return;
  const previous = Number(existing.recordCount);
  const next = Number(incoming.recordCount);
  if (Number.isFinite(previous) && previous > 0 && Number.isFinite(next)) {
    const drop = (previous - next) / previous;
    if (drop > SUSPICIOUS_DROP_RATIO) fail('CTU_SUSPICIOUS_ROW_DROP', `${previous}->${next}`);
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
  assertSafeCtuRefresh(existing, directory);
  if (stableDirectory(existing) === stableDirectory(directory)) return { target, changed: false };
  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > MAX_OUTPUT_BYTES) fail('CTU_OUTPUT_TOO_LARGE');
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  return { target, changed: true };
}

function parseArgs(argv) {
  const options = { input: null, schema: null, metadata: null, output: 'public/data/ctu-numbering.json', fetchedAt: new Date().toISOString() };
  const allowed = new Set(['input', 'schema', 'metadata', 'output', 'fetched-at']);
  for (let index = 0; index < argv.length; index += 2) {
    const raw = argv[index];
    const value = argv[index + 1];
    if (!raw?.startsWith('--') || !value || value.startsWith('--')) fail('CTU_ARGUMENTS_INVALID');
    const key = raw.slice(2);
    if (!allowed.has(key)) fail('CTU_ARGUMENTS_INVALID', key);
    if (key === 'fetched-at') options.fetchedAt = value;
    else options[key] = value;
  }
  if (!options.input || !options.schema || !options.metadata) fail('CTU_INPUT_FILES_REQUIRED');
  return options;
}

async function readBounded(path, maximum, code) {
  const resolved = resolve(path);
  const info = await stat(resolved);
  if (!info.isFile() || info.size <= 0 || info.size > maximum) fail(code);
  return readFile(resolved);
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  const [csvBytes, schemaBytes, metadataBytes] = await Promise.all([
    readBounded(options.input, MAX_CSV_BYTES, 'CTU_INPUT_SIZE'),
    readBounded(options.schema, MAX_SCHEMA_BYTES, 'CTU_SCHEMA_SIZE'),
    readBounded(options.metadata, MAX_METADATA_BYTES, 'CTU_METADATA_SIZE')
  ]);
  const directory = buildCtuDirectory(csvBytes, schemaBytes, metadataBytes, { fetchedAt: options.fetchedAt });
  const result = await writeAtomically(options.output, directory);
  console.log(`CTU directory validated: ${directory.recordCount} rows; source=${directory.sourceUpdatedLocal}; changed=${result.changed} -> ${result.target}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}

