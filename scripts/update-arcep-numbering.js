import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const ARCEP_NUMBERING_URL = 'https://extranet.arcep.fr/uploads/MAJNUM.csv';
export const ARCEP_OPERATORS_URL = 'https://extranet.arcep.fr/uploads/identifiants_CE.csv';
const MAX_INPUT_BYTES = 8 * 1024 * 1024;
const MAX_ROWS = 150_000;

function fail(code, detail = '') {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

export function parseSemicolonCsv(text) {
  if (typeof text !== 'string') fail('ARCEP_TEXT_REQUIRED');
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
    } else if (char === ';') {
      row.push(field);
      field = '';
    } else if (char === '\n') {
      row.push(field.replace(/\r$/, ''));
      rows.push(row);
      if (rows.length > MAX_ROWS + 1) fail('ARCEP_TOO_MANY_ROWS');
      row = [];
      field = '';
    } else {
      field += char;
    }
  }
  if (quoted) fail('ARCEP_UNTERMINATED_QUOTE');
  if (field || row.length) {
    row.push(field.replace(/\r$/, ''));
    rows.push(row);
  }
  return rows.filter((value) => value.some((cell) => cell !== ''));
}

function table(records, expectedHeaders) {
  if (!records.length) fail('ARCEP_EMPTY_FILE');
  const headers = records[0].map((value) => value.replace(/^\uFEFF/, '').trim());
  for (const required of expectedHeaders) {
    if (!headers.includes(required)) fail('ARCEP_MISSING_HEADER', required);
  }
  return {
    headers,
    rows: records.slice(1).map((cells) =>
      Object.fromEntries(headers.map((header, index) => [header, String(cells[index] ?? '').trim()]))
    )
  };
}

function requireBounded(value, code, max) {
  if (!value || value.length > max) fail(code);
  return value;
}

function optionalBounded(value, code, max) {
  if (typeof value !== 'string' || value.length > max) fail(code);
  return value || null;
}

export function buildArcepDirectory(numberingText, operatorsText, { generatedAt = new Date().toISOString() } = {}) {
  const numbering = table(
    parseSemicolonCsv(numberingText),
    ['Tranche_Debut', 'Tranche_Fin', 'Mnémo', 'Territoire', 'Date_Attribution']
  );
  const operators = table(
    parseSemicolonCsv(operatorsText),
    ['IDENTITE_OPERATEUR', 'CODE_OPERATEUR', 'SIRET_ACTEUR', 'RCS_ACTEUR',
      'ADRESSE_COMPLETE_ACTEUR', 'ATTRIB_RESS_NUM', 'DATE_DECLARATION_OPERATEUR']
  );

  const operatorDirectory = new Map();
  for (const row of operators.rows) {
    const code = requireBounded(row.CODE_OPERATEUR, 'ARCEP_INVALID_OPERATOR_CODE', 25);
    const name = requireBounded(row.IDENTITE_OPERATEUR, 'ARCEP_INVALID_OPERATOR_NAME', 255);
    const businessIdentifier = optionalBounded(row.SIRET_ACTEUR.replace(/\s/g, ''), 'ARCEP_INVALID_BUSINESS_ID', 14);
    if (businessIdentifier && !/^\d{9}(?:\d{5})?$/.test(businessIdentifier)) fail('ARCEP_INVALID_BUSINESS_ID', code);
    const canReceiveNumbering = row.ATTRIB_RESS_NUM === '1';
    if (!['0', '1'].includes(row.ATTRIB_RESS_NUM)) fail('ARCEP_INVALID_NUMBERING_STATUS', code);
    const declarationDate = requireBounded(row.DATE_DECLARATION_OPERATEUR, 'ARCEP_INVALID_DECLARATION_DATE', 10);
    if (!/^\d{2}\/\d{2}\/\d{4}$/.test(declarationDate)) fail('ARCEP_INVALID_DECLARATION_DATE', code);
    if (!operatorDirectory.has(code)) {
      operatorDirectory.set(code, [
        name,
        businessIdentifier,
        optionalBounded(row.RCS_ACTEUR, 'ARCEP_INVALID_RCS', 120),
        optionalBounded(row.ADRESSE_COMPLETE_ACTEUR, 'ARCEP_INVALID_OPERATOR_ADDRESS', 350),
        canReceiveNumbering,
        declarationDate
      ]);
    }
  }

  const entries = numbering.rows.map((row, index) => {
    const start = requireBounded(row.Tranche_Debut, 'ARCEP_INVALID_RANGE_START', 20);
    const end = requireBounded(row.Tranche_Fin, 'ARCEP_INVALID_RANGE_END', 20);
    if (!/^\d+$/.test(start) || !/^\d+$/.test(end) || start.length !== end.length || start > end) {
      fail('ARCEP_INVALID_RANGE', String(index + 2));
    }
    const code = requireBounded(row['Mnémo'], 'ARCEP_INVALID_OPERATOR_CODE', 25);
    const territory = requireBounded(row.Territoire, 'ARCEP_INVALID_TERRITORY', 50);
    const allocationDate = requireBounded(row.Date_Attribution, 'ARCEP_INVALID_DATE', 10);
    if (!/^\d{2}\/\d{2}\/\d{4}$/.test(allocationDate)) fail('ARCEP_INVALID_DATE', allocationDate);
    return [start, end, code, territory, allocationDate];
  });
  entries.sort((left, right) => left[0].localeCompare(right[0]) || left[1].localeCompare(right[1]));

  return {
    schemaVersion: 2,
    generatedAt,
    license: 'Licence Ouverte / Open Licence 2.0',
    sources: {
      numbering: ARCEP_NUMBERING_URL,
      operators: ARCEP_OPERATORS_URL,
      documentation: 'https://extranet.arcep.fr/uploads/spec_export_num_arcep.pdf',
      enterprises: 'https://annuaire-entreprises.data.gouv.fr/donnees/api-entreprises'
    },
    semantics: 'Attribution ARCEP de tranche; ne tient pas compte de la portabilité et ne constitue pas une réputation antifraude.',
    entryFields: ['start', 'end', 'operatorCode', 'territory', 'allocationDate'],
    operatorFields: ['name', 'businessIdentifier', 'rcs', 'address', 'canReceiveNumbering', 'declarationDate'],
    operators: Object.fromEntries([...operatorDirectory.entries()].sort(([left], [right]) => left.localeCompare(right))),
    entries
  };
}

async function readBoundedFile(path) {
  const bytes = await readFile(resolve(path));
  if (bytes.length > MAX_INPUT_BYTES) fail('ARCEP_INPUT_TOO_LARGE');
  return new TextDecoder('windows-1252', { fatal: false }).decode(bytes);
}

async function fetchBounded(url) {
  if (![ARCEP_NUMBERING_URL, ARCEP_OPERATORS_URL].includes(url)) fail('ARCEP_SOURCE_NOT_ALLOWED');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30_000);
  try {
    const response = await fetch(url, { redirect: 'error', signal: controller.signal });
    if (!response.ok) fail('ARCEP_FETCH_FAILED', `HTTP ${response.status}`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length > MAX_INPUT_BYTES) fail('ARCEP_INPUT_TOO_LARGE');
    return new TextDecoder('windows-1252', { fatal: false }).decode(bytes);
  } finally {
    clearTimeout(timer);
  }
}

async function writeAtomically(output, payload) {
  const target = resolve(output);
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(payload)}\n`, 'utf8');
  await rename(temporary, target);
}

function parseArgs(argv) {
  const options = { numberingInput: null, operatorsInput: null, output: 'public/data/arcep-numbering.json' };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === '--numbering-input') options.numberingInput = argv[++index];
    else if (value === '--operators-input') options.operatorsInput = argv[++index];
    else if (value === '--output') options.output = argv[++index];
    else fail('ARCEP_UNKNOWN_ARGUMENT', value);
  }
  if (Boolean(options.numberingInput) !== Boolean(options.operatorsInput)) fail('ARCEP_INPUT_PAIR_REQUIRED');
  return options;
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  const [numberingText, operatorsText] = options.numberingInput
    ? await Promise.all([readBoundedFile(options.numberingInput), readBoundedFile(options.operatorsInput)])
    : await Promise.all([fetchBounded(ARCEP_NUMBERING_URL), fetchBounded(ARCEP_OPERATORS_URL)]);
  const directory = buildArcepDirectory(numberingText, operatorsText);
  await writeAtomically(options.output, directory);
  console.log(`ARCEP directory validated: ${directory.entries.length} ranges -> ${resolve(options.output)}`);
}

if (import.meta.url === pathToFileURL(resolve(process.argv[1] ?? '')).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
