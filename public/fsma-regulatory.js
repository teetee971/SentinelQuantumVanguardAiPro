const FSMA_SNAPSHOT_URL = '/public/data/fsma-warnings.json';
const MAX_WARNINGS = 10_000;
const MAX_DOMAINS_PER_WARNING = 256;
let snapshotPromise;
let generation = 0;

function normalizeDomain(raw) {
  if (typeof raw !== 'string') return null;
  let value = raw.trim();
  if (!value) return null;
  if (!/^https?:\/\//i.test(value)) value = `https://${value}`;
  try {
    const url = new URL(value);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.port) return null;
    const host = url.hostname.toLowerCase().replace(/^www\./, '').replace(/\.$/, '');
    if (!host || host.length > 253 || !host.includes('.') || !/^[a-z0-9.-]+$/.test(host)) return null;
    if (host.split('.').some((label) => !label || label.length > 63 || label.startsWith('-') || label.endsWith('-'))) return null;
    return host;
  } catch {
    return null;
  }
}

export function extractMessageDomains(text) {
  if (typeof text !== 'string') return [];
  const matches = text.slice(0, 5000).match(/(?:https?:\/\/|www\.)[^\s<>()]+/gi) || [];
  return [...new Set(matches.map(normalizeDomain).filter(Boolean))].slice(0, 32);
}

export function validateFsmaSnapshot(snapshot) {
  if (!snapshot || snapshot.schemaVersion !== 1 || snapshot.advisoryOnly !== true) return null;
  if (!snapshot.source || snapshot.source.id !== 'fsma-belgium-warnings') return null;
  if (!Array.isArray(snapshot.warnings) || snapshot.warnings.length > MAX_WARNINGS) return null;
  for (const warning of snapshot.warnings) {
    if (!warning || warning.sourceId !== 'fsma-belgium-warnings' || warning.autoBlock !== false) return null;
    if (!Array.isArray(warning.domains) || warning.domains.length > MAX_DOMAINS_PER_WARNING) return null;
    if (!warning.domains.every((domain) => normalizeDomain(domain) === domain)) return null;
  }
  return snapshot;
}

export function buildFsmaIndex(snapshot) {
  const valid = validateFsmaSnapshot(snapshot);
  if (!valid) return null;
  const index = new Map();
  for (const warning of valid.warnings) {
    for (const domain of warning.domains) {
      const records = index.get(domain) || [];
      if (records.length < 32) records.push(warning);
      index.set(domain, records);
    }
  }
  return index;
}

export function matchFsmaDomains(index, domains) {
  if (!(index instanceof Map) || !Array.isArray(domains)) return [];
  const matches = [];
  for (const requested of domains) {
    const domain = normalizeDomain(requested);
    if (!domain) continue;
    let current = domain;
    while (current.includes('.')) {
      const records = index.get(current);
      if (records?.length) {
        matches.push({ requestedDomain: domain, matchedDomain: current, warnings: records.slice(0, 5) });
        break;
      }
      current = current.slice(current.indexOf('.') + 1);
    }
  }
  return matches;
}

async function loadSnapshot() {
  if (!snapshotPromise) {
    snapshotPromise = fetch(FSMA_SNAPSHOT_URL, {
      credentials: 'omit',
      headers: { Accept: 'application/json' },
      redirect: 'error',
      referrerPolicy: 'no-referrer',
    })
      .then(async (response) => {
        if (!response.ok) return null;
        const bytes = await response.arrayBuffer();
        if (bytes.byteLength > 4 * 1024 * 1024) return null;
        return validateFsmaSnapshot(JSON.parse(new TextDecoder().decode(bytes)));
      })
      .catch(() => null);
  }
  return snapshotPromise;
}

function appendText(parent, tag, text, className = '') {
  const node = document.createElement(tag);
  node.textContent = text;
  if (className) node.className = className;
  parent.append(node);
  return node;
}

function updateSourceTruth(snapshot) {
  const badge = document.getElementById('fsma-source-badge');
  const freshness = document.getElementById('fsma-source-freshness');
  if (badge) {
    badge.className = snapshot ? 'badge active' : 'badge pending';
    badge.textContent = snapshot ? 'Disponible' : 'Non chargé';
  }
  if (freshness) {
    freshness.textContent = snapshot
      ? `Snapshot reviewé chargé · ${snapshot.warnings.length} avertissement(s) · génération ${snapshot.generatedAt || 'inconnue'}`
      : 'Aucun snapshot FSMA validé n’est actuellement chargé dans cette version.';
  }
}

async function renderFsmaAdvisory(message, output, expectedGeneration) {
  const snapshot = await loadSnapshot();
  if (expectedGeneration !== generation || !output?.isConnected) return;
  const domains = extractMessageDomains(message);
  const index = buildFsmaIndex(snapshot);
  const matches = matchFsmaDomains(index, domains);
  const section = document.createElement('div');
  section.className = 'notice';

  if (!snapshot) {
    appendText(section, 'strong', 'FSMA Belgique : données réglementaires non chargées.');
    appendText(section, 'p', 'Aucune conclusion FSMA n’est produite tant que le snapshot officiel reviewable n’est pas disponible.');
  } else if (!domains.length) {
    appendText(section, 'strong', 'FSMA Belgique : aucun domaine à comparer dans ce SMS.');
  } else if (!matches.length) {
    appendText(section, 'strong', 'FSMA Belgique : aucune correspondance dans le snapshot chargé.');
    appendText(section, 'p', 'Absence de correspondance ≠ absence de fraude. Cette source est consultative uniquement.');
  } else {
    appendText(section, 'strong', 'Alerte réglementaire FSMA trouvée — information consultative.');
    for (const match of matches) {
      for (const warning of match.warnings) {
        appendText(
          section,
          'p',
          `${match.requestedDomain} correspond à ${match.matchedDomain} · ${warning.company || warning.title || 'Avertissement FSMA'} · ${warning.warningDate || 'date inconnue'}`
        );
      }
    }
    appendText(section, 'p', 'Aucun blocage automatique n’est déclenché par une correspondance FSMA.');
  }
  output.append(section);
}

function initialize() {
  const form = document.getElementById('sms-form');
  const input = document.getElementById('sms-text');
  const output = document.getElementById('sms-result');
  if (!form || !input || !output) return;

  loadSnapshot().then(updateSourceTruth);
  form.addEventListener('submit', () => {
    const message = input.value;
    const expectedGeneration = ++generation;
    queueMicrotask(() => {
      renderFsmaAdvisory(message, output, expectedGeneration);
    });
  });
}

if (typeof document !== 'undefined') initialize();
