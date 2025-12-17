/**
 * Phase 4 - Cyber Strategic Intelligence
 * Client-side logic for data loading and UI interactions
 * 
 * Legal Notice: Offensive Security Simulation – Aucun accès non autorisé – 
 * Usage audit, formation et évaluation uniquement.
 */

// Global state
let phase4Data = null;

/**
 * Load Phase 4 sample data
 */
async function loadPhase4Data() {
  try {
    const response = await fetch('/assets/phase4-sample-data.json');
    if (!response.ok) throw new Error('Failed to load data');
    phase4Data = await response.json();
    console.log('Phase 4 data loaded:', phase4Data.metadata);
    return phase4Data;
  } catch (error) {
    console.error('Error loading Phase 4 data:', error);
    return null;
  }
}

/**
 * Render risk snapshot table
 */
function renderRiskSnapshot(data) {
  const tbody = document.getElementById('risk-snapshot-tbody');
  if (!tbody || !data) return;
  
  tbody.innerHTML = '';
  
  data.riskSnapshot.forEach(item => {
    const row = document.createElement('tr');
    
    // Risk level class
    const riskClass = item.riskLevel.toLowerCase();
    
    // Trend icon
    const trendIcon = item.trend === 'up' ? '↑' : item.trend === 'down' ? '↓' : '→';
    const trendClass = item.trend === 'up' ? 'trend-up' : item.trend === 'down' ? 'trend-down' : 'trend-flat';
    
    row.innerHTML = `
      <td>${item.sector}</td>
      <td>${item.region}</td>
      <td><span class="risk-badge risk-${riskClass}">${item.riskLevel}</span></td>
      <td><span class="trend-indicator ${trendClass}">${trendIcon}</span></td>
      <td>${item.score}/100</td>
      <td>${item.incidentCount}</td>
    `;
    
    tbody.appendChild(row);
  });
}

/**
 * Render predictions
 */
function renderPredictions(data) {
  if (!data) return;
  
  const predictions = data.predictions;
  
  ['7d', '30d', '90d'].forEach(horizon => {
    const key = `horizon${horizon}`;
    const container = document.getElementById(`prediction-${horizon}`);
    if (!container || !predictions[key]) return;
    
    container.innerHTML = `
      <p class="prediction-summary">${predictions[key].summary}</p>
      <ul class="prediction-risks">
        ${predictions[key].topRisks.map(risk => `<li>${risk}</li>`).join('')}
      </ul>
    `;
  });
}

/**
 * Render geopolitical timeline
 */
function renderGeopolitics(data) {
  const container = document.getElementById('geopolitics-timeline');
  if (!container || !data) return;
  
  container.innerHTML = data.geopoliticalEvents.map(event => `
    <div class="timeline-event">
      <div class="timeline-date">${new Date(event.date).toLocaleDateString('fr-FR')}</div>
      <div class="timeline-content">
        <h4>${event.title}</h4>
        <p class="timeline-region">${event.region}</p>
        <p class="timeline-correlation">${event.correlation}</p>
      </div>
    </div>
  `).join('');
}

/**
 * Render decision support recommendations
 */
function renderDecisionSupport(data) {
  const container = document.getElementById('decision-support-list');
  if (!container || !data) return;
  
  container.innerHTML = data.decisionSupport.map(rec => `
    <div class="recommendation ${rec.priority.toLowerCase()}">
      <div class="rec-header">
        <span class="rec-priority">${rec.priority}</span>
        <span class="rec-category">${rec.category}</span>
      </div>
      <h4>${rec.title}</h4>
      <p class="rec-rationale">${rec.rationale}</p>
      <div class="rec-meta">
        <span>Effort: ${rec.effort}</span>
        <span>Impact: ${rec.impact}</span>
        <span>Timeline: ${rec.timeline}</span>
      </div>
    </div>
  `).join('');
}

/**
 * Modal management
 */
function openModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) {
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
  }
}

function closeModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) {
    modal.classList.remove('active');
    document.body.style.overflow = '';
  }
}

function closeAllModals() {
  document.querySelectorAll('.modal').forEach(modal => {
    modal.classList.remove('active');
  });
  document.body.style.overflow = '';
}

/**
 * Initialize Phase 4
 */
async function initPhase4() {
  console.log('Initializing Phase 4...');
  
  const data = await loadPhase4Data();
  if (!data) {
    console.error('Failed to initialize Phase 4 - no data');
    return;
  }
  
  renderRiskSnapshot(data);
  renderPredictions(data);
  renderGeopolitics(data);
  renderDecisionSupport(data);
  
  // Setup modal event listeners
  document.querySelectorAll('[data-modal-open]').forEach(btn => {
    btn.addEventListener('click', () => {
      const modalId = btn.getAttribute('data-modal-open');
      openModal(modalId);
    });
  });
  
  document.querySelectorAll('[data-modal-close]').forEach(btn => {
    btn.addEventListener('click', () => {
      closeAllModals();
    });
  });
  
  // Close modals on overlay click
  document.querySelectorAll('.modal-overlay').forEach(overlay => {
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        closeAllModals();
      }
    });
  });
  
  // Close modals on Escape key
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      closeAllModals();
    }
  });
  
  console.log('Phase 4 initialized successfully');
}

// Initialize on DOM ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initPhase4);
} else {
  initPhase4();
}
