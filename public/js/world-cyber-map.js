/**
 * World Cyber Map - Panorama Cyber Mondial
 * Module d'affichage informatif des événements cybersécurité mondiaux
 * 
 * CARACTÉRISTIQUES :
 * - Lecture seule stricte
 * - Aucune analyse de sécurité réelle
 * - Sources publiques officielles uniquement
 * - Aucune collecte de données
 * - Aucun traitement automatisé
 * - Aucune surveillance en temps réel
 */

(function() {
  'use strict';

  // Protection XSS - Échappement HTML
  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // Couleurs par type d'événement
  const EVENT_COLORS = {
    'INCIDENT': '#e74c3c',  // Rouge
    'ALERTE': '#f39c12',    // Orange
    'RAPPORT': '#3498db'    // Bleu
  };

  // Chargement et affichage des événements
  async function loadCyberEvents() {
    const container = document.getElementById('world-cyber-map-container');
    if (!container) {
      console.warn('World Cyber Map: Container not found');
      return;
    }

    try {
      // Chargement des données locales
      const response = await fetch('data/world-cyber-events.json');
      if (!response.ok) {
        throw new Error('Failed to load cyber events data');
      }
      
      const events = await response.json();
      
      // Création de la carte SVG simple (sans dépendance externe)
      renderSimpleMap(container, events);
      
    } catch (error) {
      console.error('World Cyber Map Error:', error);
      container.innerHTML = `
        <div style="padding: 20px; text-align: center; color: #e74c3c;">
          <p>⚠️ Impossible de charger les données de la carte.</p>
          <p style="font-size: 0.9em;">Mode fallback : données non disponibles.</p>
        </div>
      `;
    }
  }

  // Rendu de la carte simple avec SVG
  function renderSimpleMap(container, events) {
    // Création du conteneur de la carte
    const mapHtml = `
      <div style="position: relative; width: 100%; max-width: 1200px; margin: 0 auto;">
        <svg id="world-map-svg" viewBox="0 0 1000 500" style="width: 100%; height: auto; background: #ecf0f1; border: 2px solid #bdc3c7; border-radius: 8px;">
          <!-- Fond carte monde simplifié -->
          <rect width="1000" height="500" fill="#ecf0f1"/>
          
          <!-- Grille de référence -->
          <line x1="0" y1="250" x2="1000" y2="250" stroke="#bdc3c7" stroke-width="1" stroke-dasharray="5,5"/>
          <line x1="500" y1="0" x2="500" y2="500" stroke="#bdc3c7" stroke-width="1" stroke-dasharray="5,5"/>
          
          <!-- Marqueurs géographiques -->
          <g id="event-markers"></g>
        </svg>
        
        <!-- Légende -->
        <div style="margin-top: 15px; display: flex; justify-content: center; gap: 20px; flex-wrap: wrap;">
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 15px; height: 15px; background: ${EVENT_COLORS.INCIDENT}; border-radius: 50%;"></div>
            <span style="font-size: 0.9em;">Incident</span>
          </div>
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 15px; height: 15px; background: ${EVENT_COLORS.ALERTE}; border-radius: 50%;"></div>
            <span style="font-size: 0.9em;">Alerte</span>
          </div>
          <div style="display: flex; align-items: center; gap: 5px;">
            <div style="width: 15px; height: 15px; background: ${EVENT_COLORS.RAPPORT}; border-radius: 50%;"></div>
            <span style="font-size: 0.9em;">Rapport</span>
          </div>
        </div>
        
        <!-- Liste des événements -->
        <div id="events-list" style="margin-top: 20px;"></div>
      </div>
    `;
    
    container.innerHTML = mapHtml;
    
    // Ajout des marqueurs sur la carte
    const markersGroup = document.getElementById('event-markers');
    
    events.forEach(event => {
      // Conversion approximative lat/lon vers coordonnées SVG
      // Longitude: -180 à 180 -> 0 à 1000
      // Latitude: 90 à -90 -> 0 à 500
      const x = ((event.lon + 180) / 360) * 1000;
      const y = ((90 - event.lat) / 180) * 500;
      
      const color = EVENT_COLORS[event.type] || '#95a5a6';
      
      // Création du marqueur
      const marker = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      marker.setAttribute('cx', x);
      marker.setAttribute('cy', y);
      marker.setAttribute('r', '8');
      marker.setAttribute('fill', color);
      marker.setAttribute('stroke', '#fff');
      marker.setAttribute('stroke-width', '2');
      marker.setAttribute('style', 'cursor: pointer; transition: r 0.2s;');
      marker.setAttribute('data-event-id', event.id);
      
      // Animation au survol
      marker.addEventListener('mouseenter', function() {
        this.setAttribute('r', '12');
      });
      marker.addEventListener('mouseleave', function() {
        this.setAttribute('r', '8');
      });
      
      // Clic pour afficher les détails
      marker.addEventListener('click', function() {
        showEventDetails(event);
      });
      
      markersGroup.appendChild(marker);
    });
    
    // Affichage de la liste des événements
    renderEventsList(events);
  }

  // Affichage de la liste des événements
  function renderEventsList(events) {
    const listContainer = document.getElementById('events-list');
    if (!listContainer) return;
    
    let html = '<h3 style="margin-bottom: 15px;">📋 Liste des événements</h3>';
    html += '<div style="display: grid; gap: 15px;">';
    
    events.forEach(event => {
      const color = EVENT_COLORS[event.type] || '#95a5a6';
      const typeText = escapeHtml(event.type);
      const titleText = escapeHtml(event.title);
      const countryText = escapeHtml(event.country);
      const sourceText = escapeHtml(event.source);
      const dateText = escapeHtml(event.date);
      
      html += `
        <div style="border: 1px solid #ddd; border-radius: 8px; padding: 15px; background: #fff; box-shadow: 0 2px 4px rgba(0,0,0,0.1); transition: transform 0.2s, box-shadow 0.2s;" onmouseover="this.style.transform='translateY(-2px)'; this.style.boxShadow='0 4px 8px rgba(0,0,0,0.15)';" onmouseout="this.style.transform='translateY(0)'; this.style.boxShadow='0 2px 4px rgba(0,0,0,0.1)';">
          <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 10px;">
            <div style="display: flex; align-items: center; gap: 10px;">
              <span style="display: inline-block; padding: 4px 12px; background: ${color}; color: white; border-radius: 4px; font-size: 0.85em; font-weight: bold;">${typeText}</span>
              <span style="font-size: 0.9em; color: #7f8c8d;">📍 ${countryText}</span>
            </div>
            <span style="font-size: 0.85em; color: #95a5a6;">📅 ${dateText}</span>
          </div>
          <h4 style="margin: 0 0 10px 0; font-size: 1.1em;">${titleText}</h4>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span style="font-size: 0.9em; color: #7f8c8d;">Source: <strong>${sourceText}</strong></span>
            <a href="${escapeHtml(event.link)}" target="_blank" rel="noopener noreferrer" style="padding: 6px 12px; background: #3498db; color: white; text-decoration: none; border-radius: 4px; font-size: 0.9em; transition: background 0.2s;" onmouseover="this.style.background='#2980b9';" onmouseout="this.style.background='#3498db';">
              🔗 Source officielle
            </a>
          </div>
        </div>
      `;
    });
    
    html += '</div>';
    listContainer.innerHTML = html;
  }

  // Affichage des détails d'un événement (modal simple)
  function showEventDetails(event) {
    const typeText = escapeHtml(event.type);
    const titleText = escapeHtml(event.title);
    const countryText = escapeHtml(event.country);
    const sourceText = escapeHtml(event.source);
    const dateText = escapeHtml(event.date);
    const color = EVENT_COLORS[event.type] || '#95a5a6';
    
    // Création d'un modal simple
    const modal = document.createElement('div');
    modal.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.7);
      display: flex;
      justify-content: center;
      align-items: center;
      z-index: 10000;
      animation: fadeIn 0.2s;
    `;
    
    modal.innerHTML = `
      <div style="background: white; padding: 30px; border-radius: 12px; max-width: 500px; width: 90%; box-shadow: 0 10px 40px rgba(0,0,0,0.3); animation: slideIn 0.3s;">
        <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 20px;">
          <h3 style="margin: 0; font-size: 1.3em;">${titleText}</h3>
          <button onclick="this.closest('[style*=fixed]').remove()" style="background: none; border: none; font-size: 1.5em; cursor: pointer; color: #95a5a6; padding: 0; width: 30px; height: 30px; line-height: 1; transition: color 0.2s;" onmouseover="this.style.color='#e74c3c';" onmouseout="this.style.color='#95a5a6';">×</button>
        </div>
        <div style="margin-bottom: 15px;">
          <span style="display: inline-block; padding: 6px 14px; background: ${color}; color: white; border-radius: 4px; font-size: 0.9em; font-weight: bold; margin-right: 10px;">${typeText}</span>
          <span style="font-size: 0.95em; color: #7f8c8d;">📍 ${countryText}</span>
        </div>
        <div style="margin-bottom: 15px; padding: 15px; background: #ecf0f1; border-radius: 6px;">
          <p style="margin: 0 0 10px 0;"><strong>Source:</strong> ${sourceText}</p>
          <p style="margin: 0;"><strong>Date:</strong> ${dateText}</p>
        </div>
        <a href="${escapeHtml(event.link)}" target="_blank" rel="noopener noreferrer" style="display: inline-block; width: 100%; padding: 12px; background: #3498db; color: white; text-decoration: none; border-radius: 6px; text-align: center; font-weight: bold; transition: background 0.2s;" onmouseover="this.style.background='#2980b9';" onmouseout="this.style.background='#3498db';">
          🔗 Consulter la source officielle
        </a>
      </div>
    `;
    
    // Fermeture au clic sur le fond
    modal.addEventListener('click', function(e) {
      if (e.target === modal) {
        modal.remove();
      }
    });
    
    document.body.appendChild(modal);
    
    // Ajout des animations CSS si pas déjà présentes
    if (!document.getElementById('world-cyber-map-animations')) {
      const style = document.createElement('style');
      style.id = 'world-cyber-map-animations';
      style.textContent = `
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes slideIn {
          from { transform: translateY(-20px); opacity: 0; }
          to { transform: translateY(0); opacity: 1; }
        }
      `;
      document.head.appendChild(style);
    }
  }

  // Initialisation au chargement du DOM
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadCyberEvents);
  } else {
    loadCyberEvents();
  }

  // Export pour usage global si nécessaire
  window.WorldCyberMap = {
    reload: loadCyberEvents
  };

})();
