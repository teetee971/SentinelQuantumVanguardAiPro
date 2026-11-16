const engine = new OffensiveSimulationEngine();
let selectedScenarioId = null;
let currentSimulation = null;

const $ = (id) => document.getElementById(id);
const setVisible = (element, visible) => element.classList.toggle('results-hidden', !visible);
const clear = (element) => { while (element.firstChild) element.removeChild(element.firstChild); };
const text = (value) => document.createTextNode(String(value ?? ''));

function appendLine(parent, label, value) {
  const span = document.createElement('span');
  const strong = document.createElement('strong');
  strong.append(text(`${label}: `));
  span.append(strong, text(value));
  parent.append(span);
}

function makeBadge(value) {
  const badge = document.createElement('span');
  const normalized = String(value ?? 'medium').toLowerCase();
  badge.className = `badge badge-${normalized}`;
  badge.textContent = String(value ?? 'medium').toUpperCase();
  return badge;
}

function showStatus(type, icon, title, detail) {
  const container = $('simulationStatus');
  clear(container);
  const panel = document.createElement('div');
  panel.className = 'status-panel';
  const iconNode = document.createElement('div');
  iconNode.className = 'status-icon';
  iconNode.textContent = icon;
  const titleNode = document.createElement('div');
  titleNode.className = type === 'error' ? 'status-error' : `status-${type}`;
  titleNode.textContent = title;
  panel.append(iconNode, titleNode);
  if (detail) {
    const detailNode = document.createElement('p');
    detailNode.className = 'status-detail';
    detailNode.textContent = detail;
    panel.append(detailNode);
  }
  container.append(panel);
}

function loadScenarios() {
  const container = $('scenarioList');
  clear(container);
  for (const scenario of engine.scenarioEngine.getAllScenarios()) {
    const item = document.createElement('div');
    item.className = 'scenario-item';
    item.id = `scenario-${scenario.id}`;
    item.tabIndex = 0;
    item.setAttribute('role', 'button');
    item.setAttribute('aria-pressed', 'false');
    const name = document.createElement('div');
    name.className = 'scenario-name';
    name.textContent = scenario.name;
    const description = document.createElement('div');
    description.className = 'scenario-description';
    description.textContent = scenario.description;
    const meta = document.createElement('div');
    meta.className = 'scenario-meta';
    meta.append(makeBadge(scenario.complexity));
    for (const [label, value] of [['Durée', scenario.duration], ['Tactiques', scenario.tactics.length], ['Détectabilité', scenario.detectability]]) {
      const span = document.createElement('span');
      span.className = 'muted';
      span.textContent = `${label}: ${value}`;
      meta.append(span);
    }
    item.append(name, description, meta);
    item.addEventListener('click', () => selectScenario(scenario.id));
    item.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        selectScenario(scenario.id);
      }
    });
    container.append(item);
  }
}

function selectScenario(scenarioId) {
  const scenario = engine.scenarioEngine.getScenario(scenarioId);
  if (!scenario) return;
  document.querySelectorAll('.scenario-item').forEach((el) => {
    const selected = el.id === `scenario-${scenarioId}`;
    el.classList.toggle('selected', selected);
    el.setAttribute('aria-pressed', String(selected));
  });
  selectedScenarioId = scenarioId;
  $('startBtn').disabled = false;
  showStatus('selected', '—', `Scénario sélectionné : ${scenario.name}`, scenario.description);
}

async function startSimulation() {
  if (!selectedScenarioId) return;
  $('startBtn').disabled = true;
  $('startBtn').classList.add('results-hidden');
  $('stopBtn').classList.remove('results-hidden');
  $('stopBtn').disabled = false;
  showStatus('running', '⏳', 'SIMULATION EN COURS...', 'Génération des événements d’attaque simulés');
  try {
    currentSimulation = await engine.startScenario(selectedScenarioId);
    displayResults(currentSimulation);
    showStatus('completed', '✓', 'SIMULATION TERMINÉE', `Durée : ${currentSimulation.duration} ms`);
  } catch (error) {
    console.error('Simulation error:', error);
    showStatus('error', '×', 'ERREUR', error instanceof Error ? error.message : 'Erreur inconnue');
  } finally {
    $('stopBtn').classList.add('results-hidden');
    $('stopBtn').disabled = true;
    $('startBtn').classList.remove('results-hidden');
    $('startBtn').disabled = !selectedScenarioId;
  }
}

function stopSimulation() {
  if (currentSimulation?.id) {
    engine.stopSimulation(currentSimulation.id);
    $('stopBtn').disabled = true;
  }
}

function displayResults(simulation) {
  const results = engine.getSimulationResults(simulation.id);
  if (!results) return;
  setVisible($('resultsSection'), true);
  $('metricEvents').textContent = results.metrics.totalEvents;
  $('metricIOCs').textContent = results.metrics.totalIOCs;
  $('metricTactics').textContent = results.metrics.tacticsUsed;
  $('metricTechniques').textContent = results.metrics.techniquesUsed;
  displayTimeline(results.events);
  displayIOCs(results.iocs);
  updateMITREMatrix(results.events);
}

function displayTimeline(events) {
  const container = $('timeline');
  clear(container);
  events.slice(0, 20).forEach((event) => {
    const item = document.createElement('div');
    item.className = 'timeline-item';
    const time = document.createElement('div');
    time.className = 'timeline-time';
    time.textContent = new Date(event.timestamp).toLocaleTimeString();
    const content = document.createElement('div');
    content.className = 'timeline-content';
    const technique = document.createElement('div');
    technique.className = 'timeline-technique';
    technique.append(makeBadge(event.severity), text(` ${event.technique}: ${event.techniqueName}`));
    const description = document.createElement('div');
    description.className = 'timeline-description';
    description.textContent = event.description;
    const detail = document.createElement('div');
    detail.className = 'detail-line';
    detail.textContent = `Tactic: ${event.tacticName} (${event.tactic})`;
    content.append(technique, description, detail);
    item.append(time, content);
    container.append(item);
  });
  if (events.length > 20) {
    const more = document.createElement('div');
    more.className = 'detail-line';
    more.textContent = `… et ${events.length - 20} événements supplémentaires`;
    container.append(more);
  }
}

function displayIOCs(iocs) {
  const container = $('iocList');
  clear(container);
  iocs.forEach((ioc) => {
    const item = document.createElement('div');
    item.className = 'ioc-item';
    const type = document.createElement('span');
    type.className = 'ioc-type';
    type.textContent = String(ioc.type ?? '').toUpperCase();
    const value = document.createElement('span');
    value.className = 'ioc-value';
    value.textContent = ioc.value;
    const detail = document.createElement('div');
    detail.className = 'detail-line';
    detail.textContent = `${ioc.category} • Technique: ${ioc.technique} • Confiance: ${ioc.confidence}`;
    item.append(type, value, detail);
    container.append(item);
  });
}

function updateMITREMatrix(events = []) {
  const container = $('mitreMatrix');
  clear(container);
  const usedTactics = new Set(events.map((event) => event.tactic));
  engine.mitreLibrary.tactics.forEach((tactic) => {
    const item = document.createElement('div');
    item.className = 'mitre-tactic';
    item.classList.toggle('used', usedTactics.has(tactic.id));
    item.textContent = tactic.name;
    container.append(item);
  });
}

function csvCell(value) {
  const cell = String(value ?? '');
  return /[",\n\r]/.test(cell) ? `"${cell.replaceAll('"', '""')}"` : cell;
}

function exportResults(format) {
  if (!currentSimulation) {
    showStatus('error', '×', 'EXPORT IMPOSSIBLE', 'Aucune simulation à exporter.');
    return;
  }
  const results = engine.getSimulationResults(currentSimulation.id);
  if (!results) return;
  let content;
  let filename;
  let mimeType;
  if (format === 'json') {
    content = JSON.stringify(results, null, 2);
    filename = `simulation-${currentSimulation.id}.json`;
    mimeType = 'application/json';
  } else if (format === 'csv') {
    const rows = [['Timestamp', 'Technique', 'Tactic', 'Severity', 'Description'], ...results.events.map((event) => [event.timestamp, event.technique, event.tactic, event.severity, event.description])];
    content = rows.map((row) => row.map(csvCell).join(',')).join('\n');
    filename = `simulation-${currentSimulation.id}.csv`;
    mimeType = 'text/csv;charset=utf-8';
  } else if (format === 'cef') {
    content = engine.loggingSystem.exportToCEF(results.logs);
    filename = `simulation-${currentSimulation.id}.cef`;
    mimeType = 'text/plain;charset=utf-8';
  } else {
    return;
  }
  const url = URL.createObjectURL(new Blob([content], { type: mimeType }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

window.addEventListener('DOMContentLoaded', () => {
  loadScenarios();
  updateMITREMatrix();
  $('startBtn').addEventListener('click', startSimulation);
  $('stopBtn').addEventListener('click', stopSimulation);
  $('exportJsonBtn').addEventListener('click', () => exportResults('json'));
  $('exportCsvBtn').addEventListener('click', () => exportResults('csv'));
  $('exportCefBtn').addEventListener('click', () => exportResults('cef'));
});