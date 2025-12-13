/**
 * Cybersecurity News Feed - Information Module
 * 
 * Module d'affichage de sources d'information publiques en cybersécurité
 * 
 * AVERTISSEMENT:
 * - Sources publiques uniquement
 * - Aucune analyse automatique
 * - Aucune corrélation avec données utilisateur
 * - Aucun traitement IA
 * - LECTURE SEULE stricte
 * 
 * @version 1.0.0
 * @date 2025-12-13
 */

(function () {
  'use strict';

  const container = document.getElementById('cyber-news');
  if (!container) {
    console.warn('Cyber news container not found');
    return;
  }

  // Sources d'information publiques et officielles
  // Ces informations sont à but pédagogique et informatif uniquement
  const news = [
    {
      source: 'ANSSI',
      title: 'Hausse des campagnes de phishing ciblé',
      date: 'Décembre 2025',
      link: 'https://www.ssi.gouv.fr',
      category: 'Alerte',
      color: '#FF5722'
    },
    {
      source: 'Europol',
      title: 'Rapport annuel sur la cybercriminalité en Europe',
      date: '2025',
      link: 'https://www.europol.europa.eu',
      category: 'Rapport',
      color: '#2196F3'
    },
    {
      source: 'CERT-FR',
      title: 'Vulnérabilités critiques signalées cette semaine',
      date: 'Actualisation continue',
      link: 'https://cert.ssi.gouv.fr',
      category: 'CVE',
      color: '#FF9800'
    },
    {
      source: 'CNIL',
      title: 'Nouvelles recommandations RGPD 2025',
      date: 'Janvier 2025',
      link: 'https://www.cnil.fr',
      category: 'Conformité',
      color: '#4CAF50'
    },
    {
      source: 'ENISA',
      title: 'Tendances des menaces cybernétiques',
      date: 'Décembre 2025',
      link: 'https://www.enisa.europa.eu',
      category: 'Analyse',
      color: '#9C27B0'
    },
    {
      source: 'US-CERT',
      title: 'Bulletin de sécurité hebdomadaire',
      date: 'Semaine en cours',
      link: 'https://www.cisa.gov',
      category: 'Bulletin',
      color: '#607D8B'
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
   * Rend le fil d'actualité
   */
  function render() {
    // En-tête de section
    const header = document.createElement('div');
    header.style.cssText = `
      background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
      color: white;
      padding: 16px;
      border-radius: 8px 8px 0 0;
      margin-bottom: 12px;
    `;
    header.innerHTML = `
      <h3 style="margin: 0; font-size: 1.2em;">
        📰 Fil d'actualité Cybersécurité
      </h3>
      <p style="margin: 8px 0 0 0; font-size: 0.85em; opacity: 0.9;">
        Sources publiques – Aucune analyse – Aucune corrélation automatique
      </p>
    `;
    container.appendChild(header);

    // Disclaimer
    const disclaimer = document.createElement('div');
    disclaimer.style.cssText = `
      background: #E3F2FD;
      border: 1px solid #2196F3;
      border-radius: 6px;
      padding: 12px;
      margin-bottom: 16px;
      font-size: 0.9em;
    `;
    disclaimer.innerHTML = `
      <strong>ℹ️ Information :</strong> Ces sources sont <u>publiques et officielles</u>.
      Aucune surveillance, aucun traitement automatisé, aucune collecte de données.
    `;
    container.appendChild(disclaimer);

    // Conteneur des news
    const newsContainer = document.createElement('div');
    newsContainer.style.cssText = `
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    `;

    // Rendu de chaque news
    news.forEach((item, index) => {
      const div = document.createElement('div');
      div.style.cssText = `
        background: white;
        border: 1px solid #e0e0e0;
        border-left: 4px solid ${item.color};
        padding: 14px;
        border-radius: 6px;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        transition: all 0.3s ease;
        animation: fadeIn 0.4s ease-out ${index * 0.1}s both;
      `;

      div.innerHTML = `
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <span style="
            background: ${item.color};
            color: white;
            padding: 3px 10px;
            border-radius: 12px;
            font-size: 0.75em;
            font-weight: bold;
          ">${escapeHtml(item.category)}</span>
          <span style="font-size: 0.75em; color: #888;">
            ${escapeHtml(item.source)}
          </span>
        </div>
        <h4 style="margin: 8px 0; color: #333; font-size: 0.95em; line-height: 1.4;">
          ${escapeHtml(item.title)}
        </h4>
        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px;">
          <small style="color: #666; font-size: 0.8em;">
            📅 ${escapeHtml(item.date)}
          </small>
          <a href="${escapeHtml(item.link)}" 
             target="_blank" 
             rel="noopener noreferrer"
             style="
               color: #2196F3;
               text-decoration: none;
               font-size: 0.85em;
               font-weight: 500;
               transition: color 0.2s;
             "
             onmouseover="this.style.color='#1976D2'; this.style.textDecoration='underline';"
             onmouseout="this.style.color='#2196F3'; this.style.textDecoration='none';">
            Source officielle →
          </a>
        </div>
      `;

      // Effet hover
      div.addEventListener('mouseenter', function() {
        this.style.transform = 'translateY(-4px)';
        this.style.boxShadow = '0 6px 12px rgba(0,0,0,0.15)';
      });

      div.addEventListener('mouseleave', function() {
        this.style.transform = 'translateY(0)';
        this.style.boxShadow = '0 2px 4px rgba(0,0,0,0.1)';
      });

      newsContainer.appendChild(div);
    });

    container.appendChild(newsContainer);

    // Footer avec badge
    const footer = document.createElement('div');
    footer.style.cssText = `
      background: #f5f5f5;
      border: 1px solid #ddd;
      border-radius: 6px;
      padding: 12px;
      text-align: center;
      font-size: 0.85em;
      color: #555;
    `;
    footer.innerHTML = `
      <strong>● LECTURE SEULE – INFORMATIONNEL</strong><br>
      <small style="margin-top: 4px; display: block; color: #777;">
        Sources vérifiées et accessibles publiquement
      </small>
    `;
    container.appendChild(footer);
  }

  // Animation CSS
  const style = document.createElement('style');
  style.textContent = `
    @keyframes fadeIn {
      from {
        opacity: 0;
        transform: scale(0.95);
      }
      to {
        opacity: 1;
        transform: scale(1);
      }
    }
  `;
  document.head.appendChild(style);

  // Initialisation
  render();

  console.log('Cyber News Feed initialized (Information mode)');
})();
