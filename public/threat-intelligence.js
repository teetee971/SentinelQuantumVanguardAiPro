const SOURCES = Object.freeze({
  github: 'https://api.github.com/advisories?per_page=10',
  nvd: 'https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=10',
  kev: 'https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json',
  osv: 'https://api.osv.dev/v1/query'
});
const TIMEOUT_MS = 8000;

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

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>\'"]/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    "'": '&#39;',
    '"': '&quot;'
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

async function refresh() {
  const button = document.getElementById('refresh');
  button.disabled = true;
  document.getElementById('status').textContent = 'CHARGEMENT';
  document.getElementById('status').className = 'status';
  const items = [];
  let ok = 0;

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
    const vulns = Array.isArray(nvdResult.value.vulnerabilities) ? nvdResult.value.vulnerabilities : [];
    document.getElementById('nvdCount').textContent = vulns.length;
    setStatus('nvdStatus', 'ACCESSIBLE', true);
    ok++;
    vulns.slice(0, 5).forEach((vulnerability) => items.push({
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
    const entries = Array.isArray(catalog.vulnerabilities) ? catalog.vulnerabilities : [];
    document.getElementById('kevCount').textContent = entries.length;
    setStatus('kevStatus', 'ACCESSIBLE', true);
    ok++;
    entries.slice(-5).reverse().forEach((vulnerability) => items.push({
      title: vulnerability.vulnerabilityName || vulnerability.cveID || 'KEV sans titre',
      source: 'CISA KEV',
      id: vulnerability.cveID || 'identifiant indisponible'
    }));
  } catch {
    document.getElementById('kevCount').textContent = '0';
    setStatus('kevStatus', 'INDISPONIBLE', false);
  }

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
