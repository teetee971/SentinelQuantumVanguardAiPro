import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const DEFAULT_CISA_KEV_URL = 'https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json';
const MAX_CATALOG_BYTES = 20 * 1024 * 1024;
const MAX_VULNERABILITIES = 10000;
const CVE_RE = /^CVE-\d{4}-\d{4,}$/;
const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function fail(code, detail) {
  const error = new Error(detail ? `${code}: ${detail}` : code);
  error.code = code;
  throw error;
}

function requiredString(value, field, { max = 20000 } = {}) {
  if (typeof value !== 'string' || value.trim() === '') {
    fail('KEV_INVALID_STRING', field);
  }
  const normalized = value.trim();
  if (normalized.length > max) fail('KEV_STRING_TOO_LONG', field);
  return normalized;
}

function optionalString(value, field, { max = 20000 } = {}) {
  if (value === undefined || value === null || value === '') return '';
  return requiredString(value, field, { max });
}

function dateString(value, field) {
  const normalized = requiredString(value, field, { max: 10 });
  if (!ISO_DATE_RE.test(normalized)) fail('KEV_INVALID_DATE', field);

  const [year, month, day] = normalized.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    fail('KEV_INVALID_DATE', field);
  }
  return normalized;
}

export function assertOfficialCisaUrl(value) {
  if (typeof value !== 'string' || value !== DEFAULT_CISA_KEV_URL) {
    fail('KEV_SOURCE_NOT_ALLOWED', String(value));
  }
  try {
    return new URL(value).toString();
  } catch {
    fail('KEV_INVALID_URL', value);
  }
}

function normalizeVulnerability(entry, index) {
  if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
    fail('KEV_INVALID_ENTRY', String(index));
  }

  const cveID = requiredString(entry.cveID, `vulnerabilities[${index}].cveID`, { max: 32 }).toUpperCase();
  if (!CVE_RE.test(cveID)) fail('KEV_INVALID_CVE', cveID);

  const normalized = {
    cveID,
    vendorProject: requiredString(entry.vendorProject, `${cveID}.vendorProject`, { max: 500 }),
    product: requiredString(entry.product, `${cveID}.product`, { max: 500 }),
    vulnerabilityName: requiredString(entry.vulnerabilityName, `${cveID}.vulnerabilityName`, { max: 1000 }),
    dateAdded: dateString(entry.dateAdded, `${cveID}.dateAdded`),
    shortDescription: requiredString(entry.shortDescription, `${cveID}.shortDescription`, { max: 10000 }),
    requiredAction: requiredString(entry.requiredAction, `${cveID}.requiredAction`, { max: 10000 }),
    dueDate: dateString(entry.dueDate, `${cveID}.dueDate`),
    knownRansomwareCampaignUse: optionalString(entry.knownRansomwareCampaignUse, `${cveID}.knownRansomwareCampaignUse`, { max: 100 }),
    notes: optionalString(entry.notes, `${cveID}.notes`, { max: 10000 })
  };

  if (entry.cwes !== undefined) {
    if (!Array.isArray(entry.cwes) || entry.cwes.some((value) => typeof value !== 'string' || value.trim() === '')) {
      fail('KEV_INVALID_CWES', cveID);
    }
    normalized.cwes = [...new Set(entry.cwes.map((value) => value.trim()))].sort();
  }

  return normalized;
}

export function normalizeKevCatalog(payload, { sourceUrl = DEFAULT_CISA_KEV_URL } = {}) {
  assertOfficialCisaUrl(sourceUrl);
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) fail('KEV_INVALID_CATALOG');
  if (!Array.isArray(payload.vulnerabilities)) fail('KEV_MISSING_VULNERABILITIES');
  if (payload.vulnerabilities.length === 0) fail('KEV_EMPTY_CATALOG');
  if (payload.vulnerabilities.length > MAX_VULNERABILITIES) fail('KEV_CATALOG_TOO_LARGE');

  if (payload.count !== undefined && (!Number.isInteger(payload.count) || payload.count !== payload.vulnerabilities.length)) {
    fail('KEV_COUNT_MISMATCH', `${payload.count} != ${payload.vulnerabilities.length}`);
  }

  const vulnerabilities = payload.vulnerabilities.map(normalizeVulnerability);
  const seen = new Set();
  for (const vulnerability of vulnerabilities) {
    if (seen.has(vulnerability.cveID)) fail('KEV_DUPLICATE_CVE', vulnerability.cveID);
    seen.add(vulnerability.cveID);
  }
  vulnerabilities.sort((a, b) => a.cveID.localeCompare(b.cveID));

  return {
    source: assertOfficialCisaUrl(sourceUrl),
    title: optionalString(payload.title, 'title', { max: 500 }),
    catalogVersion: optionalString(payload.catalogVersion, 'catalogVersion', { max: 100 }),
    dateReleased: optionalString(payload.dateReleased, 'dateReleased', { max: 100 }),
    count: vulnerabilities.length,
    vulnerabilities
  };
}

async function readResponseWithLimit(response) {
  const declaredLength = Number(response.headers.get('content-length'));
  if (Number.isFinite(declaredLength) && declaredLength > MAX_CATALOG_BYTES) fail('KEV_RESPONSE_TOO_LARGE');
  const buffer = Buffer.from(await response.arrayBuffer());
  if (buffer.length > MAX_CATALOG_BYTES) fail('KEV_RESPONSE_TOO_LARGE');
  return buffer.toString('utf8');
}

export async function fetchOfficialKevCatalog({ url = DEFAULT_CISA_KEV_URL, timeoutMs = 30000 } = {}) {
  const sourceUrl = assertOfficialCisaUrl(url);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(sourceUrl, {
      redirect: 'error',
      signal: controller.signal,
      headers: { accept: 'application/json' }
    });
    if (!response.ok) fail('KEV_FETCH_FAILED', `HTTP ${response.status}`);
    const raw = await readResponseWithLimit(response);
    let payload;
    try {
      payload = JSON.parse(raw);
    } catch {
      fail('KEV_INVALID_JSON');
    }
    return normalizeKevCatalog(payload, { sourceUrl });
  } finally {
    clearTimeout(timeout);
  }
}

export async function loadKevCatalogFromFile(inputPath, { sourceUrl = DEFAULT_CISA_KEV_URL } = {}) {
  const raw = await readFile(resolve(inputPath), 'utf8');
  if (Buffer.byteLength(raw) > MAX_CATALOG_BYTES) fail('KEV_RESPONSE_TOO_LARGE');
  let payload;
  try {
    payload = JSON.parse(raw);
  } catch {
    fail('KEV_INVALID_JSON');
  }
  return normalizeKevCatalog(payload, { sourceUrl });
}

export async function writeCatalogAtomically(outputPath, catalog) {
  const target = resolve(outputPath);
  await mkdir(dirname(target), { recursive: true });
  const temp = `${target}.tmp-${process.pid}`;
  await writeFile(temp, `${JSON.stringify(catalog, null, 2)}\n`, { encoding: 'utf8', mode: 0o644 });
  await rename(temp, target);
  return target;
}

function parseArgs(argv) {
  const options = { url: DEFAULT_CISA_KEV_URL, input: null, output: 'data/threat-intel/cisa-kev.json' };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--url') options.url = argv[++i];
    else if (arg === '--input') options.input = argv[++i];
    else if (arg === '--output') options.output = argv[++i];
    else fail('KEV_UNKNOWN_ARGUMENT', arg);
  }
  if (!options.output) fail('KEV_OUTPUT_REQUIRED');
  return options;
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  const catalog = options.input
    ? await loadKevCatalogFromFile(options.input, { sourceUrl: options.url })
    : await fetchOfficialKevCatalog({ url: options.url });
  const target = await writeCatalogAtomically(options.output, catalog);
  console.log(`CISA KEV catalog validated: ${catalog.count} entries -> ${target}`);
}

const invokedPath = process.argv[1] ? pathToFileURL(resolve(process.argv[1])).href : '';
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
