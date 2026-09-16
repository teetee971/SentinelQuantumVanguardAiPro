// Original Sentinel code. No external collection, inference or identity resolution.
export const LIMITS = Object.freeze({ bytes: 262144, entities: 60, links: 120 });
export const TYPES = Object.freeze(['organisation', 'domaine', 'ip', 'pseudonyme', 'telephone', 'evenement']);
export const KINDS = Object.freeze(['observation', 'hypothese']);
const fail = message => { throw new Error(message); };
const text = (value, max, label) => {
  if (typeof value !== 'string' || !value.trim() || value.length > max || /[\u0000-\u001f\u007f]/u.test(value)) fail(`${label} invalide.`);
  return value.trim();
};
const id = value => {
  if (typeof value !== 'string' || !/^[a-zA-Z0-9_-]{1,64}$/.test(value)) fail('Identifiant invalide.');
  return value;
};
function evidence(input) {
  if (!input || typeof input !== 'object') fail('Provenance obligatoire.');
  const source = text(input.source, 2048, 'Source');
  let url;
  try { url = new URL(source); } catch { fail('Source HTTPS obligatoire.'); }
  if (url.protocol !== 'https:' || url.username || url.password) fail('Source HTTPS sans identifiants obligatoire.');
  const observedAt = text(input.observedAt, 24, 'Date UTC');
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(observedAt) || !Number.isFinite(Date.parse(observedAt)) || new Date(observedAt).toISOString() !== observedAt) fail('Date UTC canonique obligatoire.');
  if (!KINDS.includes(input.kind)) fail('Nature inconnue.');
  let provenance = {};
  if (input.importProvenance !== undefined) {
    const item = input.importProvenance;
    if (!item || item.format !== 'maigret-simple-json' || typeof item.sha256 !== 'string' || !/^[a-f0-9]{64}$/.test(item.sha256) || item.reportedStatus !== 'Claimed' || item.collectedAt !== null || input.kind !== 'hypothese') fail('Provenance d’import invalide.');
    provenance = { importProvenance: { format: item.format, sha256: item.sha256, reportedStatus: 'Claimed', collectedAt: null } };
  }
  return { source: url.href, observedAt, kind: input.kind, note: text(input.note, 1000, 'Description'), verification: 'non_verifiee', ...provenance };
}
export function emptyCase() { return { version: 1, title: 'Nouveau dossier', entities: [], links: [] }; }
export function validateCase(input) {
  if (!input || input.version !== 1 || !Array.isArray(input.entities) || !Array.isArray(input.links)) fail('Format Sentinel v1 attendu.');
  if (input.entities.length > LIMITS.entities || input.links.length > LIMITS.links) fail('Dossier trop volumineux.');
  const entities = input.entities.map(item => {
    if (!item || !TYPES.includes(item.type)) fail('Type d’entité inconnu.');
    return { id: id(item.id), type: item.type, label: text(item.label, 160, 'Libellé'), ...evidence(item) };
  });
  const ids = new Set(entities.map(item => item.id));
  if (ids.size !== entities.length) fail('Identifiants d’entité dupliqués.');
  const links = input.links.map(item => {
    if (!item || !ids.has(item.from) || !ids.has(item.to) || item.from === item.to) fail('Extrémités de relation invalides.');
    return { id: id(item.id), from: item.from, to: item.to, label: text(item.label, 160, 'Relation'), ...evidence(item) };
  });
  if (new Set(links.map(item => item.id)).size !== links.length) fail('Identifiants de relation dupliqués.');
  return { version: 1, title: text(input.title, 160, 'Titre'), entities, links };
}
export function importCase(raw) {
  if (typeof raw !== 'string' || new TextEncoder().encode(raw).length > LIMITS.bytes) fail('Import limité à 256 Kio.');
  let parsed;
  try { parsed = JSON.parse(raw); } catch { fail('JSON invalide.'); }
  return validateCase(parsed);
}
export function exportCase(input) {
  const raw = JSON.stringify(validateCase(input), null, 2);
  if (new TextEncoder().encode(raw).length > LIMITS.bytes) fail('Export limité à 256 Kio : réduire le dossier.');
  return raw;
}
export function timeline(input) {
  const value = validateCase(input);
  return [...value.entities.map(item => ({ ...item, category: 'entite' })), ...value.links.map(item => ({ ...item, category: 'relation' }))]
    .sort((a, b) => a.observedAt.localeCompare(b.observedAt) || a.category.localeCompare(b.category) || a.id.localeCompare(b.id));
}
export function removeEntity(input, entityId) {
  const value = validateCase(input);
  return { ...value, entities: value.entities.filter(item => item.id !== entityId), links: value.links.filter(item => item.from !== entityId && item.to !== entityId) };
}
export function exampleCase() {
  const evidence = { source: 'https://example.invalid/demo', observedAt: '2026-09-16T00:00:00.000Z', kind: 'hypothese', note: 'Exemple fictif, aucune collecte effectuée.' };
  return validateCase({ version: 1, title: 'Démonstration fictive', entities: [
    { id: 'demo_org', type: 'organisation', label: 'Organisation fictive', ...evidence },
    { id: 'demo_domain', type: 'domaine', label: 'example.invalid', ...evidence },
    { id: 'demo_event', type: 'evenement', label: 'Signal à examiner', ...evidence }
  ], links: [
    { id: 'demo_link_1', from: 'demo_org', to: 'demo_domain', label: 'Association à vérifier', ...evidence },
    { id: 'demo_link_2', from: 'demo_event', to: 'demo_domain', label: 'Mention dans une source fictive', ...evidence }
  ] });
}
