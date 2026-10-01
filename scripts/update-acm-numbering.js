import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { parseSemicolonCsv } from './update-arcep-numbering.js';

export const ACM_PAGE_URL = 'https://www.acm.nl/nl/telefoonnummers-zoeken';
export const ACM_ARCHIVE_URL = 'https://www.acm.nl/sites/default/files/registers/nummers_csv.zip';
export const ACM_DATASET_URL = 'https://data.overheid.nl/dataset/register-van-toegekende-telefoonnummers';

const MAX_CSV_BYTES = 16 * 1024 * 1024;
const MAX_ARCHIVE_BYTES = 8 * 1024 * 1024;
const MAX_OUTPUT_BYTES = 32 * 1024 * 1024;
const MAX_ROWS = 150_000;
const SUSPICIOUS_DROP_RATIO = 0.25;
const KNOWN_STATUSES = new Map([
  ['toegekend', 'allocated'],
  ['afkoelen', 'cooling_off'],
  ['geblokkeerd', 'blocked']
]);

function fail(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function normalizeHeader(value) {
  return String(value ?? '')
    .replace(/^\uFEFF/, '')
    .trim()
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]/g, '');
}

function findColumn(headers, aliases, required = false) {
  const normalized = headers.map(normalizeHeader);
  for (const alias of aliases) {
    const index = normalized.indexOf(alias);
    if (index >= 0) return index;
  }
  if (required) fail('ACM_SCHEMA_COLUMN_MISSING', aliases[0]);
  return -1;
}

function bounded(value, code, max, { required = false } = {}) {
  const text = String(value ?? '').trim();
  if ((required && !text) || text.length > max) fail(code, text.slice(0, 120));
  return text;
}

function normalizeNationalNumber(raw) {
  const original = String(raw ?? '').trim();
  if (!original) fail('ACM_NUMBER_EMPTY');
  const compact = original.replace(/\s/g, '');
  if (compact.startsWith('+') && !compact.startsWith('+31')) fail('ACM_FOREIGN_PREFIX', original);
  if (compact.startsWith('00') && !compact.startsWith('0031')) fail('ACM_FOREIGN_PREFIX', original);
  if (!/^(?:\+31|0031|0)?[0-9().-]+$/.test(compact)) fail('ACM_NUMBER_INVALID', original);

  let local = compact;
  if (local.startsWith('+31')) local = `0${local.slice(3)}`;
  else if (local.startsWith('0031')) local = `0${local.slice(4)}`;
  const digits = local.replace(/[().-]/g, '');
  if (!/^0\d{1,14}$/.test(digits)) fail('ACM_NUMBER_INVALID', original);
  return digits;
}

function parseRangeField(raw) {
  const value = String(raw ?? '').trim();
  if (!value) fail('ACM_RANGE_EMPTY');

  const textual = value.split(/\s+(?:t\/?m|tot(?:\s+en\s+met)?)\s+/i);
  if (textual.length === 2) {
    return [normalizeNationalNumber(textual[0]), normalizeNationalNumber(textual[1])];
  }

  const spacedDash = value.split(/\s+[–—-]\s+/);
  if (spacedDash.length === 2) {
    return [normalizeNationalNumber(spacedDash[0]), normalizeNationalNumber(spacedDash[1])];
  }

  const single = normalizeNationalNumber(value);
  return [single, single];
}

function assertIsoCalendarDate(iso, original) {
  const parsed = new Date(`${iso}T00:00:00Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== iso) {
    fail('ACM_DATE_INVALID', original);
  }
  return iso;
}

function parseDecisionDate(raw) {
  const value = String(raw ?? '').trim();
  if (!value) return null;

  let match = /^(\d{2})[-/](\d{2})[-/](\d{4})$/.exec(value);
  if (match) return assertIsoCalendarDate(`${match[3]}-${match[2]}-${match[1]}`, value);

  match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (match) return assertIsoCalendarDate(value, value);

  fail('ACM_DATE_INVALID', value);
}

function statusCode(raw) {
  const value = bounded(raw, 'ACM_STATUS_INVALID', 80, { required: true }).toLowerCase();
  const code = KNOWN_STATUSES.get(value);
  if (!code) fail('ACM_STATUS_UNKNOWN', value);
  return code;
}

function detectColumns(headers) {
  const start = findColumn(headers, ['beginnummer', 'startnummer', 'nummerbegin', 'reeksbegin', 'nummervan', 'van']);
  const end = findColumn(headers, ['eindnummer', 'eindenummer', 'nummereinde', 'reekseinde', 'nummertot', 'tot', 'einde']);
  const range = findColumn(headers, ['nummerreeks', 'reeks', 'nummer', 'nummers']);

  if ((start < 0 || end < 0) && range < 0) fail('ACM_SCHEMA_RANGE_COLUMNS_MISSING');

  return {
    start,
    end,
    range,
    status: findColumn(headers, ['status'], true),
    destination: findColumn(headers, ['bestemming', 'dienst', 'toepassing'], true),
    holder: findColumn(headers, ['nummerhouder', 'houder', 'naamnummerhouder'], true),
    decisionDate: findColumn(headers, ['datumbeschikking', 'beschikkingdatum', 'datumbesluit', 'datumtoekenning'], true)
  };
}

export function parseAcmCsv(text) {
  if (typeof text !== 'string' || Buffer.byteLength(text) > MAX_CSV_BYTES) fail('ACM_INPUT_SIZE');
  const rows = parseSemicolonCsv(text);
  if (rows.length < 2 || rows.length > MAX_ROWS + 1) fail('ACM_ROWS_INVALID');

  const headers = rows[0].map((value) => String(value).replace(/^\uFEFF/, '').trim());
  const columns = detectColumns(headers);
  const parsed = [];

  for (let rowIndex = 1; rowIndex < rows.length; rowIndex += 1) {
    const cells = rows[rowIndex];
    if (cells.length !== headers.length) fail('ACM_ROW_WIDTH', String(rowIndex + 1));

    let start;
    let end;
    if (columns.start >= 0 && columns.end >= 0) {
      start = normalizeNationalNumber(cells[columns.start]);
      end = normalizeNationalNumber(cells[columns.end]);
    } else {
      [start, end] = parseRangeField(cells[columns.range]);
    }

    if (start.length !== end.length || start > end) fail('ACM_RANGE_INVALID', `${start}-${end}`);

    parsed.push({
      start,
      end,
      status: statusCode(cells[columns.status]),
      destination: bounded(cells[columns.destination], 'ACM_DESTINATION_INVALID', 180, { required: true }),
      holder: bounded(cells[columns.holder], 'ACM_HOLDER_INVALID', 260),
      decisionDate: parseDecisionDate(cells[columns.decisionDate])
    });
  }

  return parsed;
}

function dictionaryIndex(map, values, value) {
  if (!value) return -1;
  if (!map.has(value)) {
    map.set(value, values.length);
    values.push(value);
  }
  return map.get(value);
}

export function buildAcmDirectory(csvBytes, archiveBytes, { fetchedAt = new Date().toISOString() } = {}) {
  if (!Buffer.isBuffer(csvBytes) || !csvBytes.length || csvBytes.length > MAX_CSV_BYTES) fail('ACM_INPUT_SIZE');
  if (!Buffer.isBuffer(archiveBytes) || archiveBytes.length < 4 || archiveBytes.length > MAX_ARCHIVE_BYTES) fail('ACM_ARCHIVE_SIZE');
  if (!Number.isFinite(Date.parse(fetchedAt))) fail('ACM_FETCHED_AT_INVALID');

  const text = new TextDecoder('utf-8', { fatal: true }).decode(csvBytes);
  const rows = parseAcmCsv(text);
  const holders = [];
  const destinations = [];
  const holderMap = new Map();
  const destinationMap = new Map();

  const entries = rows.map((row) => [
    row.start,
    row.end,
    row.status,
    dictionaryIndex(destinationMap, destinations, row.destination),
    dictionaryIndex(holderMap, holders, row.holder),
    row.decisionDate
  ]);
  entries.sort((left, right) => left[0].localeCompare(right[0]) || left[1].localeCompare(right[1]) || left[2].localeCompare(right[2]));

  return {
    schemaVersion: 1,
    country: 'NL',
    countryCallingCode: '+31',
    fetchedAt,
    sourcePublishedAt: null,
    license: 'CC0 1.0',
    sourcePage: ACM_PAGE_URL,
    datasetPage: ACM_DATASET_URL,
    sourceArchive: ACM_ARCHIVE_URL,
    sourceIntegrity: {
      algorithm: 'sha256',
      archive: sha256(archiveBytes),
      csv: sha256(csvBytes)
    },
    semantics: 'Public ACM numbering assignment register. Holder means regulatory number holder, not necessarily the current telecom provider after portability and not the end user or caller identity.',
    privacyNote: 'The official register can contain public holder names. Do not infer that a holder is the current end user, subscriber, caller, fraud actor, or serving carrier.',
    statusSemantics: {
      allocated: 'Toegekend',
      cooling_off: 'Afkoelen',
      blocked: 'Geblokkeerd'
    },
    entryFields: ['nationalStart', 'nationalEnd', 'status', 'destinationIndex', 'holderIndex', 'decisionDate'],
    destinations,
    holders,
    recordCount: entries.length,
    entries
  };
}

function stableDirectory(directory) {
  if (!directory || typeof directory !== 'object') return null;
  const { fetchedAt, ...stable } = directory;
  return JSON.stringify(stable);
}

export function assertSafeRefresh(existing, incoming) {
  if (!existing || typeof existing !== 'object') return;

  if (typeof existing.fetchedAt === 'string' && Date.parse(incoming.fetchedAt) < Date.parse(existing.fetchedAt)) {
    fail('ACM_FETCH_TIME_ROLLBACK');
  }

  if (existing.sourceIntegrity?.archive === incoming.sourceIntegrity?.archive) return;

  const previous = Number(existing.recordCount);
  const next = Number(incoming.recordCount);
  if (Number.isFinite(previous) && previous > 0 && Number.isFinite(next)) {
    const drop = (previous - next) / previous;
    if (drop > SUSPICIOUS_DROP_RATIO) fail('ACM_SUSPICIOUS_ROW_DROP', `${previous}->${next}`);
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

  assertSafeRefresh(existing, directory);
  if (stableDirectory(existing) === stableDirectory(directory)) return { target, changed: false };

  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > MAX_OUTPUT_BYTES) fail('ACM_OUTPUT_TOO_LARGE');
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  return { target, changed: true };
}

function parseArgs(argv) {
  const options = {
    input: null,
    archive: null,
    output: 'public/data/acm-numbering.json',
    fetchedAt: new Date().toISOString()
  };
  const allowed = new Set(['input', 'archive', 'output', 'fetched-at']);
  for (let index = 0; index < argv.length; index += 2) {
    const raw = argv[index];
    const value = argv[index + 1];
    if (!raw?.startsWith('--') || !value || value.startsWith('--')) fail('ACM_ARGUMENTS_INVALID');
    const key = raw.slice(2);
    if (!allowed.has(key)) fail('ACM_ARGUMENTS_INVALID', key);
    if (key === 'fetched-at') options.fetchedAt = value;
    else options[key] = value;
  }
  if (!options.input || !options.archive) fail('ACM_INPUT_FILES_REQUIRED');
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
  const [csvBytes, archiveBytes] = await Promise.all([
    readBounded(options.input, MAX_CSV_BYTES, 'ACM_INPUT_SIZE'),
    readBounded(options.archive, MAX_ARCHIVE_BYTES, 'ACM_ARCHIVE_SIZE')
  ]);
  const directory = buildAcmDirectory(csvBytes, archiveBytes, { fetchedAt: options.fetchedAt });
  const result = await writeAtomically(options.output, directory);
  console.log(`ACM directory validated: ${directory.recordCount} ranges; changed=${result.changed}; fetched=${directory.fetchedAt} -> ${result.target}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
