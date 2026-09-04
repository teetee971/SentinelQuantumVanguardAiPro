const SOURCES = Object.freeze({
  github: 'https://api.github.com/advisories?per_page=10',
  nvd: 'https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=10'
});
const TIMEOUT_MS = 8000;

async function fetchJson(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json' }
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

  try {
    const data = await fetchJson(SOURCES.github);
    const advisories = Array.isArray(data) ? data : [];
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
    const data = await fetchJson(SOURCES.nvd);
    const vulns = Array.isArray(data.vulnerabilities) ? data.vulnerabilities : [];
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

  render(items);
  const status = document.getElementById('status');
  status.textContent = ok === 2 ? 'SOURCES ACCESSIBLES' : ok === 1 ? 'SOURCE PARTIELLEMENT ACCESSIBLE' : 'SOURCES INDISPONIBLES';
  status.className = `status ${ok ? 'ok' : 'err'}`;
  button.disabled = false;
}

document.getElementById('refresh').addEventListener('click', refresh);
refresh();
