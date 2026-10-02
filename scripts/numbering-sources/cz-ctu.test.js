import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { collectCtu, refreshCtu, assertCtuRefresh } from './cz-ctu.js';
import { CTU_DOWNLOAD_URL, CTU_SCHEMA_URL, CTU_RESOURCE_PAGE } from '../update-ctu-numbering.js';
const HEADER = 'Číslo/Číselný rozsah;Počet čísel;Typ čísla;Podnikatel;IČO;Přiděleno od;Přiděleno do;Číslo reference';
const CSV = [
  HEADER,
  '14 112;1;14 – Služby;O2 Czech Republic a.s.;60193336;2003-01-23;2027-12-31;ČTÚ-45 112/2022-610',
  '800 333 318 až 800 333 319;2;800 – Přístup ke službám;O2 Czech Republic a.s.;60193336;2006-08-15;2030-12-31;ČTÚ-26 387/2026-610'
].join('\n');

const SCHEMA = JSON.stringify({
  tableSchema: {
    columns: [
      { titles: 'Číslo/Číselný rozsah', datatype: 'string', required: true },
      { titles: 'Počet čísel', datatype: 'integer', required: true },
      { titles: 'Typ čísla', datatype: 'string', required: true },
      { titles: 'Podnikatel', datatype: 'string', required: false },
      { titles: 'IČO', datatype: 'string', required: false },
      { titles: 'Přiděleno od', datatype: 'date', required: true },
      { titles: 'Přiděleno do', datatype: 'date', required: true },
      { titles: 'Číslo reference', datatype: 'string', required: true }
    ]
  }
});

const META = '<html><body><div>Datum aktualizace:</div><div>01.10.2026 - 04:40</div></body></html>';


const source = { [CTU_DOWNLOAD_URL]: CSV, [CTU_SCHEMA_URL]: SCHEMA, [CTU_RESOURCE_PAGE]: META };
const options = { fetchedAt: '2026-10-02T00:00:00Z', fetchImpl: async url => new Response(source[url]) };
test('collects only exact official inputs and persists original-byte and final-URL provenance', async () => {
  const data = await collectCtu(options);
  assert.equal(data.recordCount, 2);
  assert.deepEqual(Object.keys(data.downloadProvenance), ['csv','schema','metadata']);
  assert.equal(data.downloadProvenance.csv.finalUrl, CTU_DOWNLOAD_URL);
  assert.equal(data.downloadProvenance.csv.sha256, data.sourceIntegrity.csv);
  assert.match(data.downloadProvenance.metadata.sha256, /^[a-f0-9]{64}$/);
});
test('HTML or invalid encoding in CSV/schema/metadata fail before publication', async () => {
  for (const [url, bytes] of [[CTU_DOWNLOAD_URL,'<html>Error</html>'],[CTU_SCHEMA_URL,'<html>Error</html>'],[CTU_RESOURCE_PAGE,Buffer.from([255])]]) {
    await assert.rejects(collectCtu({ ...options, fetchImpl: async input => new Response(input === url ? bytes : source[input]) }));
  }
});
test('foreign redirects and decompression-size growth are rejected by streamed download', async () => {
  await assert.rejects(collectCtu({ ...options, fetchImpl: async () => new Response(null,{status:302,headers:{location:'https://evil.test/a'}}) }), /OFFICIAL_SOURCE_NOT_ALLOWED/);
  await assert.rejects(collectCtu({ ...options, fetchImpl: async url => new Response(source[url],{headers:{'content-length':'999999999'}}) }), /OFFICIAL_INPUT_TOO_LARGE/);
});
test('known source URL, schema, holder count and row-count changes require review', async () => {
  const before=await collectCtu(options);
  for (const mutate of [d=>d.downloadProvenance.csv.finalUrl='https://ctu.gov.cz/new.csv',d=>d.entryFields[0]='different',d=>d.holders=[],d=>d.recordCount=0,d=>d.countryCallingCode='+421']) {
    const data=structuredClone(before);mutate(data);
    assert.throws(()=>assertCtuRefresh(before,data));
  }
});
test('source rollback and timestamp-stable content replacement fail closed', async () => {
  const before=await collectCtu(options);
  assert.throws(()=>assertCtuRefresh(before,{...before,sourceUpdatedLocal:'2026-09-01T00:00'}),/CTU_SOURCE_ROLLBACK/);
  assert.throws(()=>assertCtuRefresh(before,{...before,sourceIntegrity:{...before.sourceIntegrity,csv:'0'.repeat(64)}}),/CTU_SAME_TIMESTAMP_CONTENT_CHANGE/);
});
test('identical content or only fetchedAt changing does not rewrite the snapshot; failed collection preserves bytes', async () => {
  const folder=await mkdtemp(join(tmpdir(),'ctu-bounded-'));
  const path=join(folder,'ctu.json');
  try {
    assert.equal((await refreshCtu(path,options)).changed,true);
    const original=await readFile(path,'utf8');
    assert.equal((await refreshCtu(path,{...options,fetchedAt:'2026-10-03T00:00:00Z'})).changed,false);
    await assert.rejects(refreshCtu(path,{...options,fetchImpl:async()=>new Response('error',{status:503})}));
    assert.equal(await readFile(path,'utf8'),original);
  } finally {await rm(folder,{recursive:true,force:true});}
});
