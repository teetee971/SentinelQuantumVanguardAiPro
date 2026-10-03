// Availability display only; runtime authorization remains a separate control.
function refreshCapabilityAvailability() {
  for (const element of document.querySelectorAll('[data-product-available]')) {
    const deadline = Number(element.dataset.productExpires);
    const available = element.dataset.productAvailable === 'true' &&
      Number.isSafeInteger(deadline) && deadline > Date.now();
    element.textContent = available ? 'Disponible' : 'Non disponible';
  }
}
refreshCapabilityAvailability();
setInterval(refreshCapabilityAvailability, 1000);
