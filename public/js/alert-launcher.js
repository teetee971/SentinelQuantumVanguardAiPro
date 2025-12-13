/**
 * Alert Launcher - Demo Mode
 * 
 * Module de démonstration d'alertes informationnelles
 * 
 * AVERTISSEMENT:
 * - Mode INFORMATION / DEMO uniquement
 * - Aucune détection de menace réelle
 * - Aucune analyse de sécurité
 * - 100% local, zéro collecte de données
 * 
 * @version 1.0.0
 * @date 2025-12-13
 */

(function () {
  'use strict';

  const container = document.getElementById('alert-section');
  if (!container) {
    console.warn('Alert section container not found');
    return;
  }

  // Alertes informationnelles de démonstration
  // Ces alertes sont FICTIVES et à but pédagogique uniquement
  const alerts = [
    {
      level: 'INFO',
      title: 'Activité réseau détectée',
      message: 'Connexion réseau active (information locale)',
      time: new Date().toLocaleTimeString('fr-FR'),
      color: '#2196F3'
    },
    {
      level: 'INFO',
      title: 'État du navigateur',
      message: 'Navigateur opérationnel – aucun diagnostic de sécurité',
      time: new Date().toLocaleTimeString('fr-FR'),
      color: '#4CAF50'
    },
    {
      level: 'INFO',
      title: 'Modules actifs',
      message: '4 modules en mode ACTIVE-DEMO disponibles pour consultation',
      time: new Date().toLocaleTimeString('fr-FR'),
      color: '#FF9800'
    }
  ];

  /**
   * Échappe les caractères HTML pour prévenir XSS
   * @param {string} str - Chaîne à échapper
   * @returns {string} Chaîne échappée
   */
  function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  /**
   * Rend les alertes dans le conteneur
   */
  function render() {
    // En-tête de section
    const header = document.createElement('div');
    header.style.cssText = `
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 16px;
      border-radius: 8px 8px 0 0;
      margin-bottom: 12px;
    `;
    header.innerHTML = `
      <h3 style="margin: 0; font-size: 1.2em;">
        🔔 Alertes Informationnelles
      </h3>
      <p style="margin: 8px 0 0 0; font-size: 0.85em; opacity: 0.9;">
        Mode démonstration – Aucune détection réelle
      </p>
    `;
    container.appendChild(header);

    // Badge de disclaimer
    const disclaimer = document.createElement('div');
    disclaimer.style.cssText = `
      background: #FFF3CD;
      border: 1px solid #FFC107;
      border-radius: 6px;
      padding: 12px;
      margin-bottom: 16px;
      font-size: 0.9em;
    `;
    disclaimer.innerHTML = `
      <strong>⚠️ Important :</strong> Ces alertes sont <u>informatives uniquement</u>.
      Aucune analyse de sécurité réelle, aucune détection de menace, aucune action effectuée.
    `;
    container.appendChild(disclaimer);

    // Conteneur des alertes
    const alertsContainer = document.createElement('div');
    alertsContainer.style.cssText = `
      display: flex;
      flex-direction: column;
      gap: 12px;
    `;

    // Rendu de chaque alerte
    alerts.forEach((alert, index) => {
      const div = document.createElement('div');
      div.style.cssText = `
        border-left: 4px solid ${alert.color};
        background: #f8f9fa;
        padding: 12px 16px;
        border-radius: 6px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        transition: transform 0.2s, box-shadow 0.2s;
        animation: slideIn 0.3s ease-out ${index * 0.1}s both;
      `;

      div.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 8px;">
          <strong style="color: #333; font-size: 1em;">
            [${escapeHtml(alert.level)}] ${escapeHtml(alert.title)}
          </strong>
          <span style="
            background: ${alert.color};
            color: white;
            padding: 2px 8px;
            border-radius: 12px;
            font-size: 0.75em;
            font-weight: bold;
          ">DÉMO</span>
        </div>
        <p style="margin: 0 0 8px 0; color: #555; font-size: 0.95em;">
          ${escapeHtml(alert.message)}
        </p>
        <small style="color: #888; font-size: 0.85em;">
          🕐 ${escapeHtml(alert.time)}
        </small>
      `;

      // Effet hover
      div.addEventListener('mouseenter', function() {
        this.style.transform = 'translateX(4px)';
        this.style.boxShadow = '0 4px 8px rgba(0,0,0,0.15)';
      });

      div.addEventListener('mouseleave', function() {
        this.style.transform = 'translateX(0)';
        this.style.boxShadow = '0 2px 4px rgba(0,0,0,0.1)';
      });

      alertsContainer.appendChild(div);
    });

    container.appendChild(alertsContainer);

    // Badge final
    const badge = document.createElement('div');
    badge.style.cssText = `
      margin-top: 16px;
      padding: 8px;
      background: #E3F2FD;
      border: 1px solid #2196F3;
      border-radius: 6px;
      text-align: center;
      font-size: 0.85em;
      color: #1976D2;
    `;
    badge.innerHTML = `
      <strong>● LECTURE SEULE – INFORMATIONNEL</strong>
    `;
    container.appendChild(badge);
  }

  // Animation CSS
  const style = document.createElement('style');
  style.textContent = `
    @keyframes slideIn {
      from {
        opacity: 0;
        transform: translateY(-10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
  `;
  document.head.appendChild(style);

  // Initialisation
  render();

  console.log('Alert Launcher initialized (DEMO mode)');
})();
