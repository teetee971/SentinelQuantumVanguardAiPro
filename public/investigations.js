import { emptyCase, validateCase, importCase, exportCase, timeline, removeEntity, exampleCase, LIMITS } from './investigation-core.js';
import { mountMaigretImporter } from './investigation-maigret-ui.js';

export function mountInvestigations(doc, host) {
  let dossier = emptyCase();
  let revision = 0;
  let resetMaigret = () => {};
  const byId = id => doc.getElementById(id);
  const value = id => byId(id).value;
  const message = text => { byId('status').textContent = text; };
  const element = (tag, text) => { const node = doc.createElement(tag); if (text !== undefined) node.textContent = text; return node; };
  const safeAction = fn => { try { fn(); } catch (error) { message(error.message); } };
  const commit = candidate => {
    // Enforce both semantic and export-size bounds before replacing the current dossier.
    const validated = importCase(exportCase(candidate));
    dossier = validated; revision++; resetMaigret(); render();
  };
  function readEvidence(prefix) {
    const raw = value(`${prefix}-time`);
    const date = new Date(`${raw}Z`);
    if (!raw || !Number.isFinite(date.getTime())) throw new Error('Date UTC invalide.');
    return { source: value(`${prefix}-source`), observedAt: date.toISOString(), kind: value(`${prefix}-kind`), note: value(`${prefix}-note`) };
  }
  function record(item, remove) {
    const li = element('li');
    li.append(element('strong', item.label), element('p', `${item.kind === 'hypothese' ? 'Hypothèse' : 'Observation déclarée'} · ${item.observedAt} · source non vérifiée`), element('p', item.note), element('p', item.source));
    if (item.importProvenance) li.append(element('p', `Import Maigret · collecte inconnue · SHA-256 déclaré : ${item.importProvenance.sha256}. Cette empreinte n’authentifie pas le rapport.`));
    const button = element('button', 'Supprimer'); button.type = 'button';
    button.setAttribute('aria-label', `Supprimer ${item.label}`);
    button.addEventListener('click', () => safeAction(remove)); li.append(button); return li;
  }
  function svgElement(tag, attributes, text) {
    const node = doc.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [key, val] of Object.entries(attributes)) node.setAttribute(key, String(val));
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function renderGraph() {
    const svg = byId('graph'); svg.replaceChildren();
    const defs = svgElement('defs', {});
    const marker = svgElement('marker', { id: 'investigation-arrow', viewBox: '0 0 10 10', refX: 9, refY: 5, markerWidth: 6, markerHeight: 6, orient: 'auto-start-reverse' });
    marker.append(svgElement('path', { d: 'M 0 0 L 10 5 L 0 10 z', fill: '#7db9ca' })); defs.append(marker); svg.append(defs);
    const points = new Map(dossier.entities.map((item, i) => {
      const angle = 2 * Math.PI * i / dossier.entities.length;
      return [item.id, { x: 450 + 330 * Math.cos(angle), y: 290 + 210 * Math.sin(angle) }];
    }));
    for (const link of dossier.links) {
      const start = points.get(link.from); const end = points.get(link.to);
      const length = Math.hypot(end.x - start.x, end.y - start.y);
      const dx = (end.x - start.x) / length; const dy = (end.y - start.y) / length;
      const line = svgElement('line', { x1: start.x + dx * 12, y1: start.y + dy * 12, x2: end.x - dx * 16, y2: end.y - dy * 16, 'marker-end': 'url(#investigation-arrow)', 'stroke-dasharray': link.kind === 'hypothese' ? '6 6' : 'none' });
      line.append(svgElement('title', {}, link.label)); svg.append(line);
    }
    dossier.entities.forEach((item, index) => {
      const point = points.get(item.id);
      const group = svgElement('g', {});
      group.append(svgElement('title', {}, `${item.type} : ${item.label}`), svgElement('circle', { cx: point.x, cy: point.y, r: 10 }), svgElement('text', { x: point.x, y: point.y + 28, 'text-anchor': 'middle' }, `${index + 1}. ${item.label.slice(0, 20)}`));
      svg.append(group);
    });
  }
  function render() {
    byId('case-title').value = dossier.title;
    byId('counts').textContent = `${dossier.entities.length}/${LIMITS.entities} entités · ${dossier.links.length}/${LIMITS.links} relations · sources non vérifiées`;
    for (const name of ['link-from', 'link-to']) {
      const selected = value(name); const select = byId(name); select.replaceChildren();
      dossier.entities.forEach(item => { const option = element('option', `${item.type} : ${item.label}`); option.value = item.id; select.append(option); });
      if (dossier.entities.some(item => item.id === selected)) select.value = selected;
    }
    byId('add-link').disabled = dossier.entities.length < 2;
    byId('entities').replaceChildren(...dossier.entities.map((item, index) => {
      const row = record(item, () => { commit(removeEntity(dossier, item.id)); message('Entité et relations associées supprimées.'); });
      row.prepend(element('p', `${index + 1}. ${item.type}`)); return row;
    }));
    byId('links').replaceChildren(...dossier.links.map(item => {
      const row = record(item, () => { commit({ ...dossier, links: dossier.links.filter(link => link.id !== item.id) }); message('Relation supprimée.'); });
      row.prepend(element('p', `${dossier.entities.find(entity => entity.id === item.from).label} → ${dossier.entities.find(entity => entity.id === item.to).label}`)); return row;
    }));
    byId('timeline').replaceChildren(...timeline(dossier).map(item => element('li', `${item.observedAt} · ${item.importProvenance ? 'import (collecte inconnue)' : item.category} · ${item.label} · ${item.kind} · non vérifiée`)));
    renderGraph();
  }
  byId('entity-form').addEventListener('submit', event => { event.preventDefault(); safeAction(() => {
    commit({ ...dossier, entities: [...dossier.entities, { id: host.crypto.randomUUID(), type: value('entity-type'), label: value('entity-label'), ...readEvidence('entity') }] });
    message('Entité ajoutée, source non vérifiée.');
  }); });
  byId('link-form').addEventListener('submit', event => { event.preventDefault(); safeAction(() => {
    commit({ ...dossier, links: [...dossier.links, { id: host.crypto.randomUUID(), from: value('link-from'), to: value('link-to'), label: value('link-label'), ...readEvidence('link') }] });
    message('Relation déclarée ajoutée, sans inférence automatique.');
  }); });
  byId('case-title').addEventListener('change', () => safeAction(() => { commit({ ...dossier, title: value('case-title') }); message('Titre mis à jour.'); }));
  const mayReplace = () => (!dossier.entities.length && !dossier.links.length) || host.confirm('Remplacer le dossier ? Les données non exportées seront perdues.');
  byId('demo').addEventListener('click', () => safeAction(() => { if (mayReplace()) { commit(exampleCase()); message('Exemple fictif chargé. Aucune collecte effectuée.'); } }));
  byId('clear').addEventListener('click', () => safeAction(() => { if (mayReplace()) { commit(emptyCase()); message('Dossier vidé de la mémoire de cet onglet. Vos exports ne sont pas supprimés.'); } }));
  byId('import').addEventListener('change', async event => {
    const file = event.target.files?.[0]; const startRevision = revision;
    if (!file) return;
    try {
      if (file.size > LIMITS.bytes) throw new Error('Import limité à 256 Kio.');
      const candidate = importCase(await file.text());
      if (revision !== startRevision) { message('Import annulé : dossier modifié pendant la lecture.'); return; }
      if (mayReplace()) { commit(candidate); message('Import local terminé. Les sources restent non vérifiées.'); }
    } catch (error) { message(`Import refusé, dossier conservé : ${error.message}`); }
    finally { event.target.value = ''; }
  });
  byId('export').addEventListener('click', () => safeAction(() => {
    if (!host.confirm('Exporter en clair ? Le fichier peut contenir des données personnelles. Conservez-le dans un emplacement protégé.')) return;
    const raw = exportCase({ ...dossier, title: value('case-title') });
    const url = host.URL.createObjectURL(new Blob([raw], { type: 'application/json' }));
    const anchor = element('a'); anchor.href = url; anchor.download = 'sentinel-investigation.json';
    doc.body.append(anchor); anchor.click(); anchor.remove();
    host.setTimeout(() => host.URL.revokeObjectURL(url), 1000);
    message('Export en clair préparé. Aucun envoi vers un serveur.');
  }));
  host.addEventListener('beforeunload', event => { if (dossier.entities.length || dossier.links.length) { event.preventDefault(); event.returnValue = ''; } });
  for (const prefix of ['entity', 'link']) byId(`${prefix}-time`).value = new Date().toISOString().slice(0, 19);
  resetMaigret = mountMaigretImporter(doc, host, () => dossier, commit);
  render();
  return { snapshot: () => validateCase(dossier) };
}
if (typeof document !== 'undefined' && document.getElementById('investigations')) mountInvestigations(document, window);
