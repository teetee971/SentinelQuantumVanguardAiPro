const statusNode = document.querySelector('[data-feature-flag-status]');

function showStatus(message, state = 'UNAVAILABLE') {
  if (!statusNode) return;
  statusNode.textContent = message;
  statusNode.dataset.state = state;
}

try {
  const response = await fetch('./feature-flags-status.json', { cache: 'no-store' });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const snapshot = await response.json();
  if (snapshot.informational_only !== true || snapshot.schema_version !== 1) {
    throw new Error('invalid feature flag snapshot');
  }
  const compliant = snapshot.governance?.compliant === true;
  showStatus(
    compliant
      ? 'Snapshot de gouvernance chargé · contrôles restrictifs'
      : 'Snapshot chargé · revue de gouvernance requise',
    compliant ? 'CONTROLLED' : 'REVIEW_REQUIRED'
  );
} catch {
  showStatus('Snapshot de gouvernance indisponible · état non affirmé');
}
