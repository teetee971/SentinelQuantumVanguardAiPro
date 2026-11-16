import { inspectMaigret, appendMaigret, MAIGRET_LIMITS } from './investigation-maigret.js';

export function mountMaigretImporter(doc, host, getCase, commitCase) {
  const byId = id => doc.getElementById(id);
  let generation = 0; let preview = null; const selected = new Set();
  const tell = text => { byId('maigret-status').textContent = text; };
  const node = (tag, text) => { const el = doc.createElement(tag); if (text !== undefined) el.textContent = text; return el; };
  function reset() {
    generation++; preview = null; selected.clear();
    byId('maigret-preview').replaceChildren(); byId('maigret-apply').disabled = true;
    byId('maigret-apply').textContent = 'Ajouter les résultats sélectionnés';
    byId('maigret-file').value = ''; tell('Aucun rapport chargé.');
  }
  byId('maigret-cancel').addEventListener('click', reset);
  byId('maigret-file').addEventListener('change', async event => {
    const file = event.target.files?.[0]; reset(); const request = generation;
    if (!file) return;
    tell('Lecture locale et calcul de l’empreinte…');
    try {
      if (file.size > MAIGRET_LIMITS.bytes) throw new Error('Rapport limité à 2 Mio.');
      const result = await inspectMaigret(await file.arrayBuffer(), host.crypto);
      if (request !== generation) return;
      preview = result;
      const { counts } = result;
      tell(`${result.candidates.length} compte(s) potentiel(s). Entrées ignorées : ${counts.available} Available, ${counts.unknown} Unknown, ${counts.illegal} Illegal, ${counts.rejected} invalides ou incompatibles. Ces compteurs portent uniquement sur le fichier. SHA-256 : ${result.sha256}`);
      result.candidates.forEach(candidate => {
        const li = node('li'); const checkbox = node('input'); checkbox.type = 'checkbox'; checkbox.checked = false;
        checkbox.id = `maigret-choice-${candidate.index}`;
        const label = node('label', `${candidate.username} @ ${candidate.site} — compte potentiel`); label.setAttribute('for', checkbox.id);
        checkbox.addEventListener('change', () => {
          if (checkbox.checked) selected.add(candidate.index); else selected.delete(candidate.index);
          byId('maigret-apply').disabled = selected.size === 0;
          byId('maigret-apply').textContent = `Ajouter ${selected.size} résultat(s) sélectionné(s)`;
        });
        li.append(checkbox, label, node('p', candidate.source)); byId('maigret-preview').append(li);
      });
    } catch (error) { if (request === generation) { reset(); tell(`Rapport refusé, dossier conservé : ${error.message}`); } }
  });
  byId('maigret-apply').addEventListener('click', () => {
    try {
      if (!preview) throw new Error('Chargez un rapport.');
      const result = appendMaigret(getCase(), preview, [...selected], new Date().toISOString());
      commitCase(result.dossier); reset();
      tell(`${result.added} compte(s) potentiel(s) ajouté(s), ${result.duplicates} entrée(s) déjà importée(s) ignorée(s). Aucune relation d’identité créée.`);
    } catch (error) { tell(`Import non appliqué : ${error.message}`); }
  });
  return reset;
}
