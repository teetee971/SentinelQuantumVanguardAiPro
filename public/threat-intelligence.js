const SUMMARY_URL = 'data/vulnerability-watch.json';
const TIMEOUT_MS = 8000;
const FRESH_MS = 36 * 60 * 60 * 1000;
const STALE_MS = 72 * 60 * 60 * 1000;

async function fetchJson(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      cache: 'no-store',
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) throw new Error('SUMMARY_UNAVAILABLE');
    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

function setStatus(id, text, ok) {
  const element = document.getElementById(id);
  element.textContent = text;
  element.className = `status ${ok ? 'ok' : 'err'}`;
}

function setNeutralStatus(id, text) {
  const element = document.getElementById(id);
  element.textContent = text;
  element.className = 'status';
}

function formatObserved(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('fr-FR', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(date);
}

function classifyFreshness(observedAt, now = Date.now()) {
  const observed = new Date(observedAt || '').getTime();
  if (!Number.isFinite(observed)) return { state: 'UNKNOWN', label: 'FRAÎCHEUR INCONNUE' };
  const ageMs = Math.max(0, now - observed);
  if (ageMs <= FRESH_MS) return { state: 'FRESH', label: 'VEILLE À JOUR' };
  if (ageMs <= STALE_MS) return { state: 'DELAYED', label: 'MISE À JOUR EN RETARD' };
  return { state: 'STALE', label: 'DONNÉES PÉRIMÉES' };
}

function validateSummary(data) {
  if (!data || data.schema_version !== 2) throw new Error('SUMMARY_SCHEMA_INVALID');
  if (!['ACTIVE', 'EMPTY'].includes(data.state)) throw new Error('SUMMARY_STATE_INVALID');
  if (!data.counts || typeof data.counts !== 'object') throw new Error('SUMMARY_COUNTS_INVALID');
  for (const key of ['critical', 'high', 'monitor']) {
    const value = data.counts[key];
    if (!Number.isSafeInteger(value) || value < 0 || value > 100000) {
      throw new Error('SUMMARY_COUNTS_INVALID');
    }
  }
  if (data.observed_at !== null && !Number.isFinite(Date.parse(data.observed_at))) {
    throw new Error('SUMMARY_TIME_INVALID');
  }
  return data;
}

function renderSummary(data) {
  const freshness = data.state === 'EMPTY'
    ? { state: 'UNKNOWN', label: 'VEILLE NON INITIALISÉE' }
    : classifyFreshness(data.observed_at);

  document.getElementById('watchStateMetric').textContent =
    data.state === 'ACTIVE' ? 'ACTIVE' : 'VIDE';
  document.getElementById('watchObserved').textContent = formatObserved(data.observed_at);
  document.getElementById('watchCritical').textContent = data.counts.critical;
  document.getElementById('watchHigh').textContent = data.counts.high;
  document.getElementById('watchMonitor').textContent = data.counts.monitor;

  if (freshness.state === 'FRESH') setStatus('watchState', freshness.label, true);
  else if (freshness.state === 'DELAYED') setNeutralStatus('watchState', freshness.label);
  else setStatus('watchState', freshness.label, false);

  const globalStatus = document.getElementById('status');
  globalStatus.textContent = data.state === 'ACTIVE' ? 'VEILLE ACTIVE' : 'VEILLE NON INITIALISÉE';
  globalStatus.className = `status ${data.state === 'ACTIVE' ? 'ok' : ''}`;
  document.getElementById('corePulse').style.opacity = data.state === 'ACTIVE' ? '1' : '.35';
}

async function refresh() {
  const button = document.getElementById('refresh');
  button.disabled = true;
  try {
    renderSummary(validateSummary(await fetchJson(SUMMARY_URL)));
  } catch {
    setStatus('watchState', 'RÉSUMÉ INDISPONIBLE', false);
    document.getElementById('status').textContent = 'INDISPONIBLE';
    document.getElementById('status').className = 'status err';
    document.getElementById('corePulse').style.opacity = '.35';
  } finally {
    button.disabled = false;
  }
}

document.getElementById('refresh').addEventListener('click', refresh);
refresh();