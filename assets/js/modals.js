// Modal functionality for Sentinel Quantum Vanguard AI Pro
document.addEventListener('DOMContentLoaded', function() {
  // Open modal
  document.querySelectorAll('[data-modal]').forEach(button => {
    button.onclick = function() {
      const modalId = button.dataset.modal;
      const modal = document.getElementById(modalId);
      if (modal) {
        modal.classList.add('open');
      }
    };
  });

  // Close modal
  document.querySelectorAll('.close').forEach(closeBtn => {
    closeBtn.onclick = function() {
      const modal = closeBtn.closest('.modal');
      if (modal) {
        modal.classList.remove('open');
      }
    };
  });

  // Close modal on background click
  document.querySelectorAll('.modal').forEach(modal => {
    modal.onclick = function(e) {
      if (e.target === modal) {
        modal.classList.remove('open');
      }
    };
  });

  // Close modal on Escape key
  document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('.modal.open').forEach(modal => {
        modal.classList.remove('open');
      });
    }
  });
});
