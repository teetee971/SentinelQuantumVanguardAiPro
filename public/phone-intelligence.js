import { createTranslator, LOCALE_STORAGE_KEY, normalizeLocale, resolveLocale, translateDocument } from './phone-intelligence-i18n.js';

export const STORAGE_KEY = 'sentinel.phone-intelligence.v1';
export const COUNTRY_RULES = Object.freeze({
  FR: { code: '33', min: 9, max: 9 },
  BE: { code: '32', min: 8, max: 9 },
  CH: { code: '41', min: 9, max: 9 },
  CA: { code: '1', min: 10, max: 10 },
});
const MAX_ENTRIES = 500;
const ARCEP_DIRECTORY_URL = '/public/data/arcep-numbering.json';
let arcepDirectoryPromise;

export function toArcepNationalNumber(normalizedNumber) {
  if (typeof normalizedNumber !== 'string' || !/^\+33\d{9}$/.test(normalizedNumber)) return null;
  return `0${normalizedNumber.slice(3)}`;
}

export function findArcepAllocation(directory, normalizedNumber) {
  const national = toArcepNationalNumber(normalizedNumber);
  const entries = directory?.schemaVersion === 1 && Array.isArray(directory.entries)
    ? directory.entries
    : [];
  if (!national || entries.length > 150_000) return null;
  let low = 0;
  let high = entries.length - 1;
  let candidate = null;
  while (low <= high) {
    const middle = Math.floor((low + high) / 2);
    const entry = entries[middle];
    if (!Array.isArray(entry) || entry.length !== 6 || typeof entry[0] !== 'string') return null;
    if (entry[0] <= national) {
      candidate = entry;
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  if (!candidate || candidate[0].length !== national.length || national > candidate[1]) return null;
  const [start, end, operatorCode, attributedOperator, territory, allocationDate] = candidate;
  return { start, end, operatorCode, attributedOperator, territory, allocationDate };
}

async function loadArcepDirectory() {
  if (!arcepDirectoryPromise) {
    arcepDirectoryPromise = fetch(ARCEP_DIRECTORY_URL, { headers: { Accept: 'application/json' } })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      })
      .then((directory) => {
        if (directory?.schemaVersion !== 1 || !Array.isArray(directory.entries)) {
          throw new Error('INVALID_ARCEP_DIRECTORY');
        }
        return directory;
      })
      .catch(() => null);
  }
  return arcepDirectoryPromise;
}

export function normalizePhone(raw, country = 'FR') {
  const rule = COUNTRY_RULES[country];
  if (!rule || typeof raw !== 'string') return null;
  let value = raw.trim().replace(/[^\d+]/g, '');
  if (value.startsWith('00')) value = `+${value.slice(2)}`;
  if (value.startsWith('+')) {
    if (!value.startsWith(`+${rule.code}`)) return null;
    value = value.slice(rule.code.length + 1);
  } else {
    value = value.replace(/^0/, '');
  }
  if (!/^\d+$/.test(value) || value.length < rule.min || value.length > rule.max) return null;
  return `+${rule.code}${value}`;
}

export function analyzeSms(raw = '') {
  const original = typeof raw === 'string' ? raw.trim() : '';
  const text = original.slice(0, 5000);
  const lower = text.toLocaleLowerCase('fr-FR');
  const signals = [];
  const add = (id, weight) => { if (!signals.some((item) => item.id === id)) signals.push({ id, weight }); };
  if (/https?:\/\/|\bwww\.|\b[a-z0-9-]+\.(?:com|net|org|info|xyz|top|click|site|fr)\b/i.test(text)) add('link', 2);
  if (/\b(?:bit\.ly|tinyurl\.com|t\.co|cutt\.ly|tiny\.cc)\b/i.test(text)) add('short-link', 2);
  if (/\b(?:urgent|immédiatement|dernier rappel|suspendu|bloqué|expire|amende)\b/i.test(lower)) add('urgency', 1);
  if (/\b(?:mot de passe|code secret|identifiant|carte bancaire|cvv|iban|connexion)\b/i.test(lower)) add('credentials', 3);
  if (/\b(?:payer|paiement|remboursement|colis|livraison|péage|crit.?air|contravention)\b/i.test(lower)) add('payment-or-delivery', 2);
  if (/\b(?:banque|assurance maladie|ameli|impôts|police|gendarmerie|opérateur)\b/i.test(lower)) add('claimed-authority', 1);
  const score = signals.reduce((sum, item) => sum + item.weight, 0);
  return { score, level: score >= 5 ? 'élevé' : score >= 2 ? 'attention' : 'faible', signals, truncated: original.length > text.length };
}

function cleanEntry(value) {
  const country = value && COUNTRY_RULES[value.country] ? value.country : null;
  const number = country ? normalizePhone(String(value.number || ''), country) : null;
  return number ? { number, country, createdAt: String(value.createdAt || '') } : null;
}

export function sanitizeState(value) {
  const source = value && typeof value === 'object' ? value : {};
  const cleanList = (list) => (Array.isArray(list) ? list : []).map(cleanEntry).filter(Boolean).slice(-MAX_ENTRIES);
  const reports = (Array.isArray(source.reports) ? source.reports : []).map((report) => {
    const base = cleanEntry(report);
    return base ? {
      ...base,
      reason: String(report.reason || 'other').slice(0, 40),
      operator: String(report.operator || 'unknown').slice(0, 80),
      preBlocked: Boolean(report.preBlocked),
      note: String(report.note || '').slice(0, 500),
    } : null;
  }).filter(Boolean).slice(-MAX_ENTRIES);
  return { allowlist: cleanList(source.allowlist), blocklist: cleanList(source.blocklist), reports };
}

export function numberSummary(state, number) {
  const safe = sanitizeState(state);
  const reports = safe.reports.filter((item) => item.number === number);
  return {
    allowed: safe.allowlist.some((item) => item.number === number),
    blocked: safe.blocklist.some((item) => item.number === number),
    reportCount: reports.length,
    operators: [...new Set(reports.map((item) => item.operator).filter((item) => item !== 'unknown'))],
  };
}

function initialize() {
  let state;
  try { state = sanitizeState(JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')); } catch { state = sanitizeState({}); }
  let locale = resolveLocale({ storedLocale: localStorage.getItem(LOCALE_STORAGE_KEY), browserLocale: navigator.language });
  let t = createTranslator(locale);
  const save = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(state = sanitizeState(state)));
  const byId = (id) => document.getElementById(id);
  const country = byId('country');
  const language = byId('language');
  const numberInput = byId('phone-number');
  const result = byId('number-result');
  const stats = byId('local-stats');
  const node = (tag, text, className) => { const element = document.createElement(tag); element.textContent = text; if (className) element.className = className; return element; };
  const current = () => normalizePhone(numberInput.value, country.value);
  let lookupGeneration = 0;

  function renderStats() {
    stats.replaceChildren();
    const rows = [[t('stats.reports'), state.reports.length], [t('stats.allow'), state.allowlist.length], [t('stats.block'), state.blocklist.length], [t('stats.countries'), new Set(state.reports.map((item) => item.country)).size]];
    rows.forEach(([label, value]) => { const card = node('div', '', 'stat'); card.append(node('strong', String(value)), node('span', label)); stats.append(card); });
  }

  function renderNumber() {
    result.replaceChildren();
    const number = current();
    if (!number) { result.append(node('p', t('runtime.invalidNumber'), 'status warning')); return null; }
    const summary = numberSummary(state, number);
    result.append(node('h3', number));
    result.append(node('p', summary.allowed ? t('runtime.allowed') : summary.blocked ? t('runtime.blocked') : t('runtime.noDecision'), `status ${summary.allowed ? 'safe' : summary.blocked ? 'danger' : 'neutral'}`));
    result.append(node('p', t('runtime.reportCount', { count: summary.reportCount })));
    result.append(node('p', summary.operators.length ? t('runtime.operators', { operators: summary.operators.join(', ') }) : t('runtime.operatorUnknown')));
    const allocation = node('div', '', 'allocation-card');
    allocation.setAttribute('role', 'status');
    allocation.append(node('strong', t('runtime.arcepLoading')));
    result.append(allocation);
    const generation = ++lookupGeneration;
    loadArcepDirectory().then((directory) => {
      if (generation !== lookupGeneration) return;
      allocation.replaceChildren();
      if (!directory) {
        allocation.append(node('strong', t('runtime.arcepUnavailable')));
        return;
      }
      const match = findArcepAllocation(directory, number);
      if (!match) {
        allocation.append(node('strong', country.value === 'FR' ? t('runtime.arcepNoMatch') : t('runtime.arcepFranceOnly')));
        return;
      }
      allocation.append(
        node('strong', t('runtime.arcepAttribution')),
        node('p', t('runtime.arcepOperator', { operator: match.attributedOperator || match.operatorCode, code: match.operatorCode })),
        node('p', t('runtime.arcepRange', { start: match.start, end: match.end })),
        node('p', t('runtime.arcepTerritory', { territory: match.territory, date: match.allocationDate })),
        node('p', t('runtime.arcepCaveat'), 'fine-print')
      );
    });
    return number;
  }

  function applyLocale(nextLocale, persist = true) {
    locale = normalizeLocale(nextLocale);
    t = createTranslator(locale);
    if (persist) localStorage.setItem(LOCALE_STORAGE_KEY, locale);
    language.value = locale;
    translateDocument(document, locale);
    renderStats();
    if (numberInput.value.trim()) renderNumber(); else result.replaceChildren();
    byId('sms-result').replaceChildren();
    byId('report-feedback').textContent = '';
  }

  language.addEventListener('change', () => applyLocale(language.value));
  byId('number-search').addEventListener('submit', (event) => { event.preventDefault(); renderNumber(); });
  byId('allow-number').addEventListener('click', () => {
    const number = renderNumber(); if (!number) return;
    state.blocklist = state.blocklist.filter((item) => item.number !== number);
    if (!state.allowlist.some((item) => item.number === number)) state.allowlist.push({ number, country: country.value, createdAt: new Date().toISOString() });
    save(); renderNumber(); renderStats();
  });
  byId('block-number').addEventListener('click', () => {
    const number = renderNumber(); if (!number) return;
    state.allowlist = state.allowlist.filter((item) => item.number !== number);
    if (!state.blocklist.some((item) => item.number === number)) state.blocklist.push({ number, country: country.value, createdAt: new Date().toISOString() });
    save(); renderNumber(); renderStats();
  });
  byId('sms-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const analysis = analyzeSms(byId('sms-text').value);
    const output = byId('sms-result');
    output.replaceChildren(node('h3', t('runtime.risk', { level: t(`risk.${analysis.level}`) })), node('p', t('runtime.score', { score: analysis.score })));
    const list = node('ul', '');
    (analysis.signals.length ? analysis.signals : [{ id: null }]).forEach((item) => list.append(node('li', item.id ? t(`signal.${item.id}`) : t('runtime.noSignal'))));
    output.append(list);
  });
  byId('report-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const number = current();
    if (!number) { byId('report-feedback').textContent = t('runtime.needNumber'); return; }
    const data = new FormData(event.currentTarget);
    state.reports.push({ number, country: country.value, createdAt: new Date().toISOString(), reason: data.get('reason'), operator: data.get('operator') || 'unknown', preBlocked: data.get('preBlocked') === 'on', note: data.get('note') || '' });
    save(); event.currentTarget.reset(); renderNumber(); renderStats();
    byId('report-feedback').textContent = t('runtime.savedLocal');
  });
  byId('clear-local-data').addEventListener('click', () => {
    if (!window.confirm(t('runtime.confirmClear'))) return;
    localStorage.removeItem(STORAGE_KEY); state = sanitizeState({}); result.replaceChildren(); renderStats();
  });

  applyLocale(locale, false);
  loadArcepDirectory().then((directory) => {
    const freshness = byId('arcep-freshness');
    if (freshness && directory?.generatedAt) {
      freshness.textContent = t('source.arcep.freshness', {
        date: new Date(directory.generatedAt).toLocaleDateString(locale)
      });
    }
  });
}

if (typeof document !== 'undefined') document.addEventListener('DOMContentLoaded', initialize);
