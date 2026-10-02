import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { fetchOfficialBytes } from './common/download-official.js';
import { CTU_DOWNLOAD_URL, CTU_SCHEMA_URL, CTU_RESOURCE_PAGE, buildCtuDirectory, assertSafeCtuRefresh } from '../update-ctu-numbering.js';

const INPUTS = Object.freeze({
  csv: { url: CTU_DOWNLOAD_URL, maxBytes: 8 * 1024 * 1024 },
  schema: { url: CTU_SCHEMA_URL, maxBytes: 128 * 1024 },
  metadata: { url: CTU_RESOURCE_PAGE, maxBytes: 2 * 1024 * 1024 }
});
const ORIGINS = ['https://data.ctu.gov.cz','https://ctu.gov.cz'];
export async function collectCtu({ fetchImpl = fetch, fetchedAt = new Date().toISOString() } = {}) {
  const inputs = {};
  const provenance = {};
  await Promise.all(Object.entries(INPUTS).map(async ([kind, config]) => {
    const result = await fetchOfficialBytes(config.url, { allowedOrigins: ORIGINS, maxBytes: config.maxBytes,
      maxRedirects: 3, fetchImpl });
    inputs[kind] = result.bytes;
    provenance[kind] = { sourceUrl: config.url, finalUrl: result.url, bytes: result.bytes.length,
      sha256: createHash('sha256').update(result.bytes).digest('hex') };
  }));
  const data = buildCtuDirectory(inputs.csv, inputs.schema, inputs.metadata, { fetchedAt });
  data.downloadProvenance = Object.fromEntries(Object.keys(INPUTS).map(kind => [kind, provenance[kind]]));
  return data;
}

export function assertCtuRefresh(before, after) {
  if (after.country !== 'CZ' || after.countryCallingCode !== '+420' || after.schemaVersion !== 1) throw new Error('CTU_COUNTRY_OR_SCHEMA_INVALID');
  if (before) {
    for (const field of ['country','countryCallingCode','schemaVersion','entryFields','holderFields']) {
      if (JSON.stringify(before[field]) !== JSON.stringify(after[field])) throw new Error('CTU_SCHEMA_CHANGED');
    }
    if (before.downloadProvenance) {
      for (const kind of Object.keys(INPUTS)) {
        if (before.downloadProvenance[kind]?.finalUrl !== after.downloadProvenance[kind]?.finalUrl) throw new Error('CTU_SOURCE_URL_CHANGED_REQUIRES_REVIEW');
      }
    }
    if (after.recordCount < before.recordCount * 0.9) throw new Error('CTU_ROW_DROP_REQUIRES_REVIEW');
    if (after.holders.length < before.holders.length * 0.9) throw new Error('CTU_HOLDER_DROP_REQUIRES_REVIEW');
  }
  assertSafeCtuRefresh(before, after);
}
function stable(data) {
  if (!data) return null;
  const { fetchedAt, ...content } = data;
  return JSON.stringify(content);
}
export async function refreshCtu(output, options = {}) {
  const target = resolve(output);
  let before = null;
  try { before = JSON.parse(await readFile(target, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  const after = await collectCtu(options);
  assertCtuRefresh(before, after);
  if (stable(before) === stable(after)) return { changed: false, data: before };
  const payload = JSON.stringify(after) + '\n';
  if (Buffer.byteLength(payload) > 32 * 1024 * 1024) throw new Error('CTU_OUTPUT_TOO_LARGE');
  await mkdir(dirname(target), { recursive: true });
  const temporary = target + `.tmp-${process.pid}`;
  try { await writeFile(temporary, payload, { encoding: 'utf8', flag: 'wx' }); await rename(temporary, target); }
  finally { await rm(temporary, { force: true }); }
  return { changed: true, data: after };
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const args = process.argv.slice(2);
  if (args.length && (args.length !== 2 || args[0] !== '--output')) throw new Error('CTU_ARGUMENTS_INVALID');
  try {
    const result = await refreshCtu(args[1] ?? 'public/data/ctu-numbering.json');
    console.log(`CTU verified: ${result.data.recordCount} records; changed=${result.changed}; sourceUpdatedLocal=${result.data.sourceUpdatedLocal}`);
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
