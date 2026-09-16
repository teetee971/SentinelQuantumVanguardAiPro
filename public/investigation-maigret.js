// Original adapter for Maigret's simple JSON export. No upstream code or collection.
import { validateCase, exportCase, LIMITS } from './investigation-core.js';
export const MAIGRET_LIMITS = Object.freeze({ bytes: 2 * 1024 * 1024, records: 1000 });
const object = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const clean = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max && !/[\u0000-\u001f\u007f]/u.test(value);
const https = value => {
  if (!clean(value, 2048)) return null;
  try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password ? url.href : null; } catch { return null; }
};
export async function inspectMaigret(bytes, cryptoProvider = globalThis.crypto) {
  if (!(bytes instanceof ArrayBuffer) || bytes.byteLength > MAIGRET_LIMITS.bytes) throw new Error('Rapport limité à 2 Mio.');
  let report;
  try { report = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes)); } catch { throw new Error('JSON UTF-8 simple attendu ; NDJSON non pris en charge.'); }
  if (!object(report)) throw new Error('Objet JSON Maigret attendu.');
  const entries = Object.entries(report);
  if (entries.length > MAIGRET_LIMITS.records) throw new Error('Rapport limité à 1 000 entrées.');
  const counts = { claimed: 0, available: 0, unknown: 0, illegal: 0, rejected: 0 };
  const candidates = [];
  entries.forEach(([site, row], index) => {
    if (!object(row) || !object(row.status) || typeof row.status.status !== 'string') { counts.rejected++; return; }
    const status = row.status;
    // Non-positive statuses are explicitly counted, never turned into account evidence.
    const nonPositive = { Available: 'available', Unknown: 'unknown', Illegal: 'illegal' };
    if (Object.hasOwn(nonPositive, status.status)) { counts[nonPositive[status.status]]++; return; }
    const source = https(row.url_user);
    if (status.status !== 'Claimed' || !clean(site, 80) || !clean(status.username, 64) || status.site_name !== site || !source || source !== https(status.url) || (row.username !== undefined && row.username !== status.username) || `${status.username} @ ${site}`.length > 160) { counts.rejected++; return; }
    counts.claimed++;
    candidates.push({ index, site, username: status.username, source });
  });
  const hash = await cryptoProvider.subtle.digest('SHA-256', bytes);
  const sha256 = [...new Uint8Array(hash)].map(byte => byte.toString(16).padStart(2, '0')).join('');
  return { format: 'maigret-simple-json', sha256, candidates, counts, entries: entries.length };
}
export function appendMaigret(input, preview, selectedIndexes, importedAt) {
  const dossier = validateCase(input);
  if (!preview || preview.format !== 'maigret-simple-json' || !/^[a-f0-9]{64}$/.test(preview.sha256) || !Array.isArray(preview.candidates)) throw new Error('Prévisualisation invalide.');
  if (!Array.isArray(selectedIndexes) || !selectedIndexes.length || selectedIndexes.length > LIMITS.entities || new Set(selectedIndexes).size !== selectedIndexes.length) throw new Error('Sélectionnez entre 1 et 60 résultats distincts.');
  const entities = [...dossier.entities]; let added = 0; let duplicates = 0;
  for (const index of selectedIndexes) {
    const candidate = preview.candidates.find(item => item.index === index);
    if (!candidate || !Number.isInteger(index) || index < 0 || index >= MAIGRET_LIMITS.records) throw new Error('Sélection invalide.');
    const id = `mg-${preview.sha256.slice(0, 40)}-${index}`;
    if (entities.some(item => item.id === id)) { duplicates++; continue; }
    entities.push({ id, type: 'pseudonyme', label: `${candidate.username} @ ${candidate.site}`, source: candidate.source,
      observedAt: importedAt, kind: 'hypothese', verification: 'non_verifiee',
      note: 'Résultat Maigret « Claimed » déclaré dans un fichier local. Compte potentiel, identité non établie. Date de collecte inconnue ; la chronologie indique la date d’import. Aucune vérification réseau.',
      importProvenance: { format: 'maigret-simple-json', sha256: preview.sha256, reportedStatus: 'Claimed', collectedAt: null }
    }); added++;
  }
  // Validation and serialization bounds apply atomically, without partial insertion.
  const result = validateCase({ ...dossier, entities }); exportCase(result);
  return { dossier: result, added, duplicates };
}
