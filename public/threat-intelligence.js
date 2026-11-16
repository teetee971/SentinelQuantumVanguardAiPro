const SOURCES = Object.freeze({
  github: 'https://api.github.com/advisories?per_page=10',
  nvd: 'https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=10',
  kev: 'https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json',
  osv: 'https://api.osv.dev/v1/query',
  persistentWatch: 'data/vulnerability-watch.json'
});
const TIMEOUT_MS = 8000;
const WATCH_FRESH_MS = 36 * 60 * 60 * 1000;
const WATCH_STALE_MS = 72 * 60 * 60 * 1000;

async function fetchJson(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: { Accept: 'application/json', ...(options.headers || {}) }
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
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

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>\'\"]/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '\"': '&quot;'
  }[char]));
}

function render(items) {
  const root = document.getElementById('results');
  if (!items.length) {
    root.innerHTML = '<p class="muted">Aucun résultat retourné lors de cette actualisation.</p>';
    return;
  }
  root.innerHTML = items.map((item) => `<article class="item"><strong>${escapeHtml(item.title)}</strong><div class="muted">${escapeHtml(item.source)} · ${escapeHtml(item.id)}</div></article>`).join('');
}

function formatObserved(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' }).format(date);
}

function classifyWatchFreshness(observedAt, now = Date.now()) {
  if (!observedAt) return { state: 'UNINITIALIZED', ageMs: null, label: 'VEILLE NON INITIALISÉE — AUCUNE COLLECTE PERSISTÉE' };
  const observed = new Date(observedAt).getTime();
  if (!Number.isFinite(observed)) return { state: 'INVALID', ageMs: null, label: 'HORODATAGE PERSISTANT INVALIDE' };
  const ageMs = Math.max(0, now - observed);
  if (ageMs <= WATCH_FRESH_MS) return { state: 'FRESH', ageMs, label: 'LEDGER VALIDÉ — DONNÉES FRAÎCHES' };
  if (ageMs <= WATCH_STALE_MS) return { state: 'DELAYED', ageMs, label: 'LEDGER VALIDÉ — COLLECTE EN RETARD (>36 H)' };
  return { state: 'STALE', ageMs, label: 'LEDGER VALIDÉ — DONNÉES PÉRIMÉES (>72 H)' };
}

function renderWatchEvents(events = []) {
  const root = document.getElementById('watchEvents');
  if (!Array.isArray(events) || !events.length) {
    root.innerHTML = '<p class="muted">Aucun événement récent dans le ledger publié.</p>';
    return;
  }
  const labels = {
    NEW_CVE: 'Nouvelle CVE',
    BECAME_KEV: 'Passage en CISA KEV',
    INVENTORY_MATCH_NEW: 'Nouveau match inventaire',
    PRIORITY_ESCALATION: 'Priorité aggravée'
  };
  root.innerHTML = events.slice(0, 20).map((event) => {
    const detail = event.type === 'PRIORITY_ESCALATION' ? ` · ${escapeHtml(event.from)} → ${escapeHtml(event.to)}` : '';
    return `<article class="item"><strong>${escapeHtml(labels[event.type] || event.type)} · ${escapeHtml(event.cve)}</strong><div class="muted">séquence ${escapeHtml(event.sequence)} · ${escapeHtml(formatObserved(event.observed_at))}${detail}</div></article>`;
  }).join('');
}

function renderPersistentWatch(data) {
  const counts = data?.counts || {};
  const evidence = data?.collection_evidence || null;
  const sourceLabels = {
    queried: 'INTERROGÉE',
    not_required: 'NON REQUISE',
    validated: 'VALIDÉE'
  };
  document.getElementById('watchSequence').textContent = Number.isSafeInteger(data?.sequence) ? data.sequence : '—';
  document.getElementById('watchSnapshotCount').textContent = Number.isSafeInteger(data?.snapshot_count) ? data.snapshot_count : '—';
  document.getElementById('watchInventoryMatches').textContent = Number.isSafeInteger(data?.inventory_matches) ? data.inventory_matches : '—';
  document.getElementById('watchObserved').textContent = formatObserved(data?.observed_at);
  document.getElementById('watchP0').textContent = Number.isSafeInteger(counts.P0_KNOWN_EXPLOITED) ? counts.P0_KNOWN_EXPLOITED : '—';
  document.getElementById('watchP1').textContent = Number.isSafeInteger(counts.P1_CRITICAL_REVIEW) ? counts.P1_CRITICAL_REVIEW : '—';
  document.getElementById('watchP2').textContent = Number.isSafeInteger(counts.P2_HIGH_REVIEW) ? counts.P2_HIGH_REVIEW : '—';
  document.getElementById('watchP3').textContent = Number.isSafeInteger(counts.P3_MONITOR) ? counts.P3_MONITOR : '—';

  const trust = data?.trust || null;
  document.getElementById('watchTrustIntegrity').textContent = trust?.integrity === 'hash-chain-verified' ? 'CHAÎNE VÉRIFIÉE' : trust?.integrity === 'empty' ? 'VIDE' : '—';
  document.getElementById('watchTrustPolicy').textContent = trust?.signature_policy === 'ed25519-required' ? 'ED25519 REQUISE' : trust?.signature_policy === 'none' ? 'AUCUNE' : '—';
  document.getElementById('watchTrustSigned').textContent = trust?.ed25519_signed === true ? 'SIGNÉ' : trust?.ed25519_signed === false ? 'NON SIGNÉ' : '—';

  document.getElementById('watchInventoryCount').textContent = Number.isSafeInteger(evidence?.inventory_count) ? evidence.inventory_count : '—';
  document.getElementById('watchOsvQueryCount').textContent = Number.isSafeInteger(evidence?.osv_query_count) ? evidence.osv_query_count : '—';
  document.getElementById('watchOsvResultCount').textContent = Number.isSafeInteger(evidence?.osv_result_count) ? evidence.osv_result_count : '—';
  document.getElementById('watchCveCount').textContent = Number.isSafeInteger(evidence?.cve_count) ? evidence.cve_count : '—';
  document.getElementById('watchOsvSource').textContent = sourceLabels[evidence?.sources?.osv] || '—';
  document.getElementById('watchNvdSource').textContent = sourceLabels[evidence?.sources?.nvd] || '—';
  document.getElementById('watchKevSource').textContent = sourceLabels[evidence?.sources?.cisa_kev] || '—';

  if (data?.state === 'VERIFIED_HASH_CHAIN' && Number.isSafeInteger(data?.sequence) && data.sequence > 0) {
    const freshness = classifyWatchFreshness(data.observed_at);
    if (freshness.state === 'FRESH') setStatus('watchState', freshness.label, true);
    else if (freshness.state === 'DELAYED') setNeutralStatus('watchState', freshness.label);
    else setStatus('watchState', freshness.label, false);
  } else if (data?.state === 'EMPTY' && data?.sequence === 0) {
    setNeutralStatus('watchState', 'VEILLE NON INITIALISÉE — AUCUNE COLLECTE PERSISTÉE');
  } else {
    setStatus('watchState', 'ÉTAT PERSISTANT INVALIDE', false);
  }
  renderWatchEvents(data?.events || []);
}

async function loadPersistentWatch() {
  try {
    const data = await fetchJson(SOURCES.persistentWatch, { cache: 'no-store' });
    if (data?.schema_version !== 1 || !data?.counts || !Array.isArray(data?.events)) throw new Error('invalid public watch schema');
    if (!data?.trust || !['hash-chain-verified', 'empty'].includes(data.trust.integrity)) throw new Error('invalid trust metadata');
    if (!['none', 'ed25519-required'].includes(data.trust.signature_policy) || typeof data.trust.ed25519_signed !== 'boolean') throw new Error('invalid trust metadata');
    if ((data.trust.signature_policy === 'none' && data.trust.ed25519_signed !== false) || (data.trust.signature_policy === 'ed25519-required' && data?.sequence > 0 && data.trust.ed25519_signed !== true)) throw new Error('invalid trust metadata');
    if (data?.state === 'VERIFIED_HASH_CHAIN' && data.trust.integrity !== 'hash-chain-verified') throw new Error('invalid trust metadata');
    if (data?.state === 'EMPTY' && data.trust.integrity !== 'empty') throw new Error('invalid trust metadata');
    if (data?.collection_evidence !== null && data?.collection_evidence !== undefined) {
      const evidence = data.collection_evidence;
      const counts = [evidence.inventory_count, evidence.osv_query_count, evidence.osv_result_count, evidence.cve_count];
      if (!counts.every((value) => Number.isSafeInteger(value) && value >= 0 && value <= 200)) throw new Error('invalid collection evidence');
      if (evidence.osv_query_count !== evidence.inventory_count || !evidence.sources) throw new Error('invalid collection evidence');
    }
    renderPersistentWatch(data);
  } catch {
    setStatus('watchState', 'RÉSUMÉ PERSISTANT INDISPONIBLE', false);
    renderWatchEvents([]);
  }
}

function extractCvss(cve = {}) {
  const metrics = cve.metrics || {};
  const groups = ['cvssMetricV40', 'cvssMetricV31', 'cvssMetricV30', 'cvssMetricV2'];
  for (const group of groups) {
    const rows = Array.isArray(metrics[group]) ? metrics[group] : [];
    for (const row of rows) {
      const score = Number(row?.cvssData?.baseScore);
      if (Number.isFinite(score)) return score;
    }
  }
  return null;
}

function updatePriorityMetrics(nvdVulnerabilities = [], kevEntries = []) {
  const kevIds = new Set(kevEntries.map((entry) => String(entry.cveID || '').toUpperCase()).filter(Boolean));
  let p0 = 0;
  let p1 = 0;
  let p2 = 0;

  for (const vulnerability of nvdVulnerabilities) {
    const id = String(vulnerability?.cve?.id || '').toUpperCase();
    const cvss = extractCvss(vulnerability?.cve || {});
    if (id && kevIds.has(id)) {
      p0++;
      continue;
    }
    if (cvss !== null && cvss >= 9) p1++;
    else if (cvss !== null && cvss >= 7) p2++;
  }

  document.getElementById('p0Count').textContent = p0;
  document.getElementById('p1Count').textContent = p1;
  document.getElementById('p2Count').textContent = p2;
  document.getElementById('freshness').textContent = new Intl.DateTimeFormat('fr-FR', {
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  }).format(new Date());
}

async function refresh() {
  const button = document.getElementById('refresh');
  button.disabled = true;
  document.getElementById('status').textContent = 'CHARGEMENT';
  document.getElementById('status').className = 'status';
  const items = [];
  let ok = 0;
  let nvdVulnerabilities = [];
  let kevEntries = [];

  await loadPersistentWatch();

  const [githubResult, nvdResult, kevResult] = await Promise.allSettled([
    fetchJson(SOURCES.github),
    fetchJson(SOURCES.nvd),
    fetchJson(SOURCES.kev)
  ]);

  try {
    if (githubResult.status !== 'fulfilled') throw githubResult.reason;
    const advisories = Array.isArray(githubResult.value) ? githubResult.value : [];
    document.getElementById('githubCount').textContent = advisories.length;
    setStatus('githubStatus', 'ACCESSIBLE', true);
    ok++;
    advisories.slice(0, 5).forEach((advisory) => items.push({
      title: advisory.summary || advisory.ghsa_id || 'Advisory sans titre',
      source: 'GitHub Security Advisories',
      id: advisory.ghsa_id || advisory.cve_id || 'identifiant indisponible'
    }));
  } catch {
    document.getElementById('githubCount').textContent = '0';
    setStatus('githubStatus', 'INDISPONIBLE', false);
  }

  try {
    if (nvdResult.status !== 'fulfilled') throw nvdResult.reason;
    nvdVulnerabilities = Array.isArray(nvdResult.value.vulnerabilities) ? nvdResult.value.vulnerabilities : [];
    document.getElementById('nvdCount').textContent = nvdVulnerabilities.length;
    setStatus('nvdStatus', 'ACCESSIBLE', true);
    ok++;
    nvdVulnerabilities.slice(0, 5).forEach((vulnerability) => items.push({
      title: vulnerability.cve?.id || 'CVE sans identifiant',
      source: 'NVD',
      id: vulnerability.cve?.id || 'identifiant indisponible'
    }));
  } catch {
    document.getElementById('nvdCount').textContent = '0';
    setStatus('nvdStatus', 'INDISPONIBLE', false);
  }

  try {
    if (kevResult.status !== 'fulfilled') throw kevResult.reason;
    const catalog = kevResult.value;
    kevEntries = Array.isArray(catalog.vulnerabilities) ? catalog.vulnerabilities : [];
    document.getElementById('kevCount').textContent = kevEntries.length;
    setStatus('kevStatus', 'ACCESSIBLE', true);
    ok++;
    kevEntries.slice(-5).reverse().forEach((vulnerability) => items.push({
      title: vulnerability.vulnerabilityName || vulnerability.cveID || 'KEV sans titre',
      source: 'CISA KEV',
      id: vulnerability.cveID || 'identifiant indisponible'
    }));
  } catch {
    document.getElementById('kevCount').textContent = '0';
    setStatus('kevStatus', 'INDISPONIBLE', false);
  }

  updatePriorityMetrics(nvdVulnerabilities, kevEntries);
  render(items);
  const status = document.getElementById('status');
  status.textContent = ok === 3 ? '3 SOURCES ACCESSIBLES' : ok > 0 ? `${ok}/3 SOURCES ACCESSIBLES` : 'SOURCES INDISPONIBLES';
  status.className = `status ${ok ? 'ok' : 'err'}`;
  document.getElementById('corePulse').style.opacity = ok ? '1' : '.35';
  button.disabled = false;
}

async function queryOsv(event) {
  event.preventDefault();
  const ecosystem = document.getElementById('osvEcosystem').value.trim();
  const name = document.getElementById('osvPackage').value.trim();
  const version = document.getElementById('osvVersion').value.trim();
  if (!ecosystem || !name || !version) return;

  setStatus('osvStatus', 'REQUÊTE', true);
  try {
    const data = await fetchJson(SOURCES.osv, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ package: { ecosystem, name }, version })
    });
    const vulns = Array.isArray(data.vulns) ? data.vulns : [];
    setStatus('osvStatus', vulns.length ? `${vulns.length} TROUVÉE(S)` : 'AUCUNE CONNUE', true);
    render(vulns.slice(0, 20).map((vuln) => ({
      title: vuln.summary || vuln.id || 'Vulnérabilité OSV',
      source: `OSV · ${name}@${version}`,
      id: vuln.id || 'identifiant indisponible'
    })));
  } catch {
    setStatus('osvStatus', 'INDISPONIBLE', false);
  }
}

document.getElementById('refresh').addEventListener('click', refresh);
document.getElementById('osvForm').addEventListener('submit', queryOsv);
refresh();
