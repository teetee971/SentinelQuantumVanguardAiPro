import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const CNMC_ARCHIVE_URL = 'https://numeracionyoperadores.cnmc.es/bd-num.zip';
export const CNMC_DATASET_PAGE = 'https://datos.gob.es/es/catalogo/ea0042931-registro-de-numeracion-de-telecomunicaciones-cnmc';
export const CNMC_TERMS_URL = 'https://data.cnmc.es/condiciones-de-uso';
export const CNMC_ATTRIBUTION = 'Origen de los datos: Comisión Nacional de los Mercados y la Competencia';

const MAX_SOURCE_BYTES = 16 * 1024 * 1024;
const MAX_ROWS_PER_FILE = 250_000;
const MAX_OUTPUT_BYTES = 32 * 1024 * 1024;
const SUSPICIOUS_DROP_RATIO = 0.20;
const REQUIRED_FILES = Object.freeze(['geograficos.txt', 'moviles.txt']);

function fail(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

function sha256(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function decodeWindows1252(bytes) {
  if (!Buffer.isBuffer(bytes) || !bytes.length || bytes.length > MAX_SOURCE_BYTES) fail('CNMC_INPUT_SIZE');
  return new TextDecoder('windows-1252', { fatal: true }).decode(bytes).replace(/^\uFEFF/, '');
}

function parseRows(text, sourceName) {
  const lines = text.split(/\r?\n/).filter((line) => line.trim() !== '');
  if (!lines.length || lines.length > MAX_ROWS_PER_FILE) fail('CNMC_ROWS_INVALID', sourceName);
  return lines.map((line, index) => {
    const cells = line.split('#');
    if (cells.length !== 6) fail('CNMC_ROW_WIDTH', `${sourceName}:${index + 1}`);
    return cells.map((value) => value.trim());
  });
}

function digits(value, code, { allowEmpty = false, max = 9 } = {}) {
  const text = String(value ?? '').trim();
  if (allowEmpty && !text) return '';
  if (!/^\d+$/.test(text) || text.length > max) fail(code, text);
  return text;
}

function bounded(value, code, max, { required = false } = {}) {
  const text = String(value ?? '').trim();
  if ((required && !text) || text.length > max) fail(code, text.slice(0, 120));
  return text;
}

function strictDate(raw) {
  const value = bounded(raw, 'CNMC_DATE_INVALID', 10, { required: true });
  const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value);
  if (!match) fail('CNMC_DATE_INVALID', value);
  const iso = `${match[3]}-${match[2]}-${match[1]}`;
  const parsed = new Date(`${iso}T00:00:00Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== iso) {
    fail('CNMC_DATE_INVALID', value);
  }
  return iso;
}

function normalizeStatus(raw) {
  const value = bounded(raw, 'CNMC_STATUS_INVALID', 160, { required: true });
  const lower = value.toLowerCase();
  if (lower === 'asignado') return { kind: 'assigned', suffixes: [''] };
  if (lower === 'compartido') return { kind: 'shared', suffixes: [''] };

  const sub = /^subasignado\s+([0-9]+(?:\s*,\s*[0-9]+)*)$/i.exec(value);
  if (sub) {
    const suffixes = sub[1].split(',').map((part) => part.trim());
    if (new Set(suffixes).size !== suffixes.length) fail('CNMC_SUBASSIGNMENT_DUPLICATE', value);
    if (suffixes.some((suffix) => !/^\d{1,6}$/.test(suffix))) fail('CNMC_SUBASSIGNMENT_INVALID', value);
    return { kind: 'subassigned', suffixes };
  }
  fail('CNMC_STATUS_UNKNOWN', value);
}

function normalizePrefix(indicator, block, suffix = '') {
  const prefix = `${indicator}${block}${suffix}`;
  if (!/^\d{2,9}$/.test(prefix)) fail('CNMC_PREFIX_INVALID', prefix);
  return prefix;
}

function parseGeographic(bytes) {
  const rows = parseRows(decodeWindows1252(bytes), 'geograficos.txt');
  const out = [];
  for (const [indicatorRaw, blockRaw, provinceRaw, statusRaw, operatorRaw, dateRaw] of rows) {
    const indicator = digits(indicatorRaw, 'CNMC_INDICATOR_INVALID', { max: 3 });
    const block = digits(blockRaw, 'CNMC_BLOCK_INVALID', { allowEmpty: true, max: 4 });
    const province = bounded(provinceRaw, 'CNMC_PROVINCE_INVALID', 120, { required: true });
    const operator = bounded(operatorRaw, 'CNMC_OPERATOR_INVALID', 260, { required: true });
    const decisionDate = strictDate(dateRaw);
    const { kind, suffixes } = normalizeStatus(statusRaw);
    for (const suffix of suffixes) {
      const prefix = normalizePrefix(indicator, block, suffix);
      if (prefix.length > 9) fail('CNMC_PREFIX_INVALID', prefix);
      out.push({
        resourceType: 'geographic',
        prefix,
        assignmentKind: kind,
        operator,
        province,
        networkType: null,
        decisionDate,
        sourceFile: 'geograficos.txt'
      });
    }
  }
  return out;
}

function parseMobile(bytes) {
  const rows = parseRows(decodeWindows1252(bytes), 'moviles.txt');
  const out = [];
  for (const [indicatorRaw, blockRaw, networkTypeRaw, statusRaw, operatorRaw, dateRaw] of rows) {
    const indicator = digits(indicatorRaw, 'CNMC_INDICATOR_INVALID', { max: 3 });
    const block = digits(blockRaw, 'CNMC_BLOCK_INVALID', { allowEmpty: true, max: 4 });
    const networkType = bounded(networkTypeRaw, 'CNMC_NETWORK_TYPE_INVALID', 120);
    const operator = bounded(operatorRaw, 'CNMC_OPERATOR_INVALID', 260, { required: true });
    const decisionDate = strictDate(dateRaw);
    const { kind, suffixes } = normalizeStatus(statusRaw);
    for (const suffix of suffixes) {
      const prefix = normalizePrefix(indicator, block, suffix);
      if (prefix.length > 9) fail('CNMC_PREFIX_INVALID', prefix);
      out.push({
        resourceType: 'mobile',
        prefix,
        assignmentKind: kind,
        operator,
        province: null,
        networkType: networkType || null,
        decisionDate,
        sourceFile: 'moviles.txt'
      });
    }
  }
  return out;
}

function dictionaryIndex(map, values, value) {
  const key = JSON.stringify(value);
  if (!map.has(key)) {
    map.set(key, values.length);
    values.push(value);
  }
  return map.get(key);
}

export function parseCnmcFiles(files) {
  if (!files || typeof files !== 'object') fail('CNMC_FILES_REQUIRED');
  for (const name of REQUIRED_FILES) {
    if (!Buffer.isBuffer(files[name])) fail('CNMC_REQUIRED_FILE_MISSING', name);
  }
  return [...parseGeographic(files['geograficos.txt']), ...parseMobile(files['moviles.txt'])];
}

export function buildCnmcDirectory(files, archiveBytes, { fetchedAt = new Date().toISOString() } = {}) {
  if (!Buffer.isBuffer(archiveBytes) || archiveBytes.length < 4 || archiveBytes.length > 32 * 1024 * 1024) {
    fail('CNMC_ARCHIVE_SIZE');
  }
  if (!Number.isFinite(Date.parse(fetchedAt))) fail('CNMC_FETCHED_AT_INVALID');

  const rows = parseCnmcFiles(files);
  const operators = [];
  const provinces = [];
  const networkTypes = [];
  const operatorMap = new Map();
  const provinceMap = new Map();
  const networkTypeMap = new Map();
  let maxDecisionDate = null;

  const entries = rows.map((row) => {
    if (!maxDecisionDate || row.decisionDate > maxDecisionDate) maxDecisionDate = row.decisionDate;
    return [
      row.prefix,
      row.resourceType,
      row.assignmentKind,
      dictionaryIndex(operatorMap, operators, row.operator),
      row.province ? dictionaryIndex(provinceMap, provinces, row.province) : -1,
      row.networkType ? dictionaryIndex(networkTypeMap, networkTypes, row.networkType) : -1,
      row.decisionDate,
      row.sourceFile
    ];
  });

  entries.sort((left, right) =>
    right[0].length - left[0].length ||
    left[0].localeCompare(right[0]) ||
    left[1].localeCompare(right[1]) ||
    left[2].localeCompare(right[2])
  );

  return {
    schemaVersion: 1,
    country: 'ES',
    countryCallingCode: '+34',
    fetchedAt,
    sourcePublishedAt: null,
    maxDecisionDate,
    sourceArchive: CNMC_ARCHIVE_URL,
    datasetPage: CNMC_DATASET_PAGE,
    termsUrl: CNMC_TERMS_URL,
    license: 'CC BY-SA 4.0',
    attribution: CNMC_ATTRIBUTION,
    coverage: ['geographic', 'mobile'],
    excludes: ['portability-current-carrier'],
    semantics: 'CNMC regulatory assignment and subassignment prefixes. The published operator is not necessarily the current serving operator after portability and is not proof of caller identity, legitimacy, fraud, or spoofing.',
    sourceIntegrity: {
      algorithm: 'sha256',
      archive: sha256(archiveBytes),
      files: Object.fromEntries(REQUIRED_FILES.map((name) => [name, {
        sha256: sha256(files[name]),
        bytes: files[name].length
      }]))
    },
    entryFields: ['prefix', 'resourceType', 'assignmentKind', 'operatorIndex', 'provinceIndex', 'networkTypeIndex', 'decisionDate', 'sourceFile'],
    operators,
    provinces,
    networkTypes,
    recordCount: entries.length,
    entries
  };
}

function stableDirectory(directory) {
  if (!directory || typeof directory !== 'object') return null;
  const { fetchedAt, sourceIntegrity, ...rest } = directory;
  const stableIntegrity = sourceIntegrity && typeof sourceIntegrity === 'object'
    ? { ...sourceIntegrity, archive: undefined }
    : sourceIntegrity;
  return JSON.stringify({ ...rest, sourceIntegrity: stableIntegrity });
}

export function assertSafeCnmcRefresh(existing, incoming) {
  if (!existing || typeof existing !== 'object') return;
  if (typeof existing.fetchedAt === 'string' && Date.parse(incoming.fetchedAt) < Date.parse(existing.fetchedAt)) {
    fail('CNMC_FETCH_TIME_ROLLBACK');
  }

  const previousFiles = existing.sourceIntegrity?.files;
  const incomingFiles = incoming.sourceIntegrity?.files;
  if (previousFiles && incomingFiles &&
      REQUIRED_FILES.every((name) => previousFiles[name]?.sha256 === incomingFiles[name]?.sha256)) {
    return;
  }

  if (typeof existing.maxDecisionDate === 'string' &&
      typeof incoming.maxDecisionDate === 'string' &&
      incoming.maxDecisionDate < existing.maxDecisionDate) {
    fail('CNMC_DECISION_DATE_ROLLBACK', `${incoming.maxDecisionDate}<${existing.maxDecisionDate}`);
  }

  const previous = Number(existing.recordCount);
  const next = Number(incoming.recordCount);
  if (Number.isFinite(previous) && previous > 0 && Number.isFinite(next)) {
    const drop = (previous - next) / previous;
    if (drop > SUSPICIOUS_DROP_RATIO) fail('CNMC_SUSPICIOUS_ROW_DROP', `${previous}->${next}`);
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
  assertSafeCnmcRefresh(existing, directory);
  if (stableDirectory(existing) === stableDirectory(directory)) return { target, changed: false };

  const payload = `${JSON.stringify(directory)}\n`;
  if (Buffer.byteLength(payload) > MAX_OUTPUT_BYTES) fail('CNMC_OUTPUT_TOO_LARGE');
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, payload, 'utf8');
  await rename(temporary, target);
  return { target, changed: true };
}

function parseArgs(argv) {
  const options = {
    geographic: null,
    mobile: null,
    archive: null,
    output: 'public/data/cnmc-numbering.json',
    fetchedAt: new Date().toISOString()
  };
  const allowed = new Set(['geographic', 'mobile', 'archive', 'output', 'fetched-at']);
  for (let index = 0; index < argv.length; index += 2) {
    const raw = argv[index];
    const value = argv[index + 1];
    if (!raw?.startsWith('--') || !value || value.startsWith('--')) fail('CNMC_ARGUMENTS_INVALID');
    const key = raw.slice(2);
    if (!allowed.has(key)) fail('CNMC_ARGUMENTS_INVALID', key);
    if (key === 'fetched-at') options.fetchedAt = value;
    else options[key] = value;
  }
  if (!options.geographic || !options.mobile || !options.archive) fail('CNMC_INPUT_FILES_REQUIRED');
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
  const [geographic, mobile, archive] = await Promise.all([
    readBounded(options.geographic, MAX_SOURCE_BYTES, 'CNMC_INPUT_SIZE'),
    readBounded(options.mobile, MAX_SOURCE_BYTES, 'CNMC_INPUT_SIZE'),
    readBounded(options.archive, 32 * 1024 * 1024, 'CNMC_ARCHIVE_SIZE')
  ]);
  const directory = buildCnmcDirectory({
    'geograficos.txt': geographic,
    'moviles.txt': mobile
  }, archive, { fetchedAt: options.fetchedAt });
  const result = await writeAtomically(options.output, directory);
  console.log(`CNMC directory validated: ${directory.recordCount} prefixes; maxDecisionDate=${directory.maxDecisionDate}; changed=${result.changed} -> ${result.target}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
