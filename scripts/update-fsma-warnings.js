import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import {
  FSMA_EXPORT_URL,
  FSMA_LICENSE_URL,
  FSMA_LIST_URL,
  FSMA_SOURCE_ID,
  normalizeFsmaCsv,
} from '../security/phone-intelligence/fsma-warning-adapter.js';

const MAX_INPUT_BYTES = 4 * 1024 * 1024;

function sha256(value) {
  return createHash('sha256').update(value, 'utf8').digest('hex');
}

export function buildFsmaSnapshot(csvText, { generatedAt = new Date().toISOString() } = {}) {
  if (typeof csvText !== 'string') throw new Error('FSMA_TEXT_REQUIRED');
  if (Buffer.byteLength(csvText, 'utf8') > MAX_INPUT_BYTES) throw new Error('FSMA_INPUT_TOO_LARGE');
  const warnings = normalizeFsmaCsv(csvText).map((warning) => ({
    title: warning.title,
    company: warning.company,
    fraudType: warning.fraudType,
    warningDate: warning.warningDate,
    domains: [...warning.domains],
    sourceId: warning.sourceId,
    sourceUrl: warning.sourceUrl,
    license: warning.license,
    autoBlock: false,
  }));
  return {
    schemaVersion: 1,
    generatedAt,
    advisoryOnly: true,
    source: {
      id: FSMA_SOURCE_ID,
      listUrl: FSMA_LIST_URL,
      exportUrl: FSMA_EXPORT_URL,
      license: 'CC BY 4.0',
      licenseUrl: FSMA_LICENSE_URL,
      sha256: sha256(csvText),
    },
    warnings,
  };
}

function comparable(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') return null;
  const { generatedAt, ...stable } = snapshot;
  return JSON.stringify(stable);
}

export function hasSameFsmaContent(left, right) {
  const a = comparable(left);
  const b = comparable(right);
  return a !== null && a === b;
}

async function fetchBounded() {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30_000);
  try {
    const response = await fetch(FSMA_EXPORT_URL, {
      redirect: 'error',
      signal: controller.signal,
      headers: { Accept: 'text/csv,text/plain;q=0.9,*/*;q=0.1' },
    });
    if (!response.ok) throw new Error(`FSMA_FETCH_FAILED: HTTP ${response.status}`);
    const bytes = Buffer.from(await response.arrayBuffer());
    if (bytes.length > MAX_INPUT_BYTES) throw new Error('FSMA_INPUT_TOO_LARGE');
    return bytes.toString('utf8');
  } finally {
    clearTimeout(timer);
  }
}

async function readBounded(path) {
  const bytes = await readFile(resolve(path));
  if (bytes.length > MAX_INPUT_BYTES) throw new Error('FSMA_INPUT_TOO_LARGE');
  return bytes.toString('utf8');
}

async function writeAtomically(output, snapshot) {
  const target = resolve(output);
  try {
    const previous = JSON.parse(await readFile(target, 'utf8'));
    if (hasSameFsmaContent(previous, snapshot)) return { target, changed: false };
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  await mkdir(dirname(target), { recursive: true });
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(snapshot)}\n`, 'utf8');
  await rename(temporary, target);
  return { target, changed: true };
}

function parseArgs(argv) {
  const options = { input: null, output: 'public/data/fsma-warnings.json' };
  for (let index = 0; index < argv.length; index += 1) {
    const value = argv[index];
    if (value === '--input') options.input = argv[++index];
    else if (value === '--output') options.output = argv[++index];
    else throw new Error(`FSMA_UNKNOWN_ARGUMENT: ${value}`);
  }
  return options;
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArgs(argv);
  const csv = options.input ? await readBounded(options.input) : await fetchBounded();
  const snapshot = buildFsmaSnapshot(csv);
  const result = await writeAtomically(options.output, snapshot);
  console.log(`FSMA warnings validated: ${snapshot.warnings.length}, changed=${result.changed} -> ${result.target}`);
}

if (import.meta.url === pathToFileURL(resolve(process.argv[1] ?? '')).href) {
  main().catch((error) => {
    console.error(error?.message || error);
    process.exitCode = 1;
  });
}
