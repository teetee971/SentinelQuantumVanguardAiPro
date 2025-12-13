/**
 * AUDIT FRONTAL LOCAL — LECTURE SEULE
 * 
 * Ce module collecte UNIQUEMENT des informations disponibles côté navigateur.
 * AUCUN scan de sécurité. AUCUNE action système. AUCUNE donnée sensible.
 * 
 * Informations collectées :
 * - User-Agent (navigateur)
 * - Plateforme (OS)
 * - Langue du navigateur
 * - Heure locale
 * - État réseau (en ligne/hors ligne)
 * 
 * Conformité :
 * - Aucune collecte de données personnelles
 * - Aucune transmission externe
 * - Aucune promesse de sécurité
 * - 100% transparent et pédagogique
 */

(function() {
    'use strict';

    // Module Audit Frontal Local
    const AuditFrontalLocal = {
        /**
         * Collecte les informations disponibles côté navigateur
         * @returns {Object} Informations système non sensibles
         */
        collectInfo: function() {
            return {
                userAgent: navigator.userAgent || 'Non disponible',
                platform: this.getPlatform(),
                language: navigator.language || 'Non disponible',
                languages: navigator.languages ? navigator.languages.join(', ') : 'Non disponible',
                onlineStatus: navigator.onLine ? 'En ligne' : 'Hors ligne',
                timestamp: new Date().toLocaleString('fr-FR', {
                    dateStyle: 'full',
                    timeStyle: 'long'
                }),
                timestampISO: new Date().toISOString(),
                screenResolution: `${window.screen.width} × ${window.screen.height}`,
                viewportSize: `${window.innerWidth} × ${window.innerHeight}`,
                colorDepth: `${window.screen.colorDepth} bits`,
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Non disponible',
                cookiesEnabled: navigator.cookieEnabled ? 'Oui' : 'Non',
                doNotTrack: navigator.doNotTrack || 'Non spécifié'
            };
        },

        /**
         * Détecte la plateforme (OS) à partir du User-Agent
         * @returns {string} Nom de la plateforme
         */
        getPlatform: function() {
            const ua = navigator.userAgent.toLowerCase();
            
            if (ua.indexOf('win') !== -1) return 'Windows';
            if (ua.indexOf('mac') !== -1) return 'macOS';
            if (ua.indexOf('linux') !== -1) return 'Linux';
            if (ua.indexOf('android') !== -1) return 'Android';
            if (ua.indexOf('iphone') !== -1 || ua.indexOf('ipad') !== -1) return 'iOS';
            
            return 'Inconnu';
        },

        /**
         * Génère un rapport HTML des informations collectées
         * @returns {string} HTML du rapport
         */
        generateReport: function() {
            const info = this.collectInfo();
            
            return `
                <div class="info-box" style="border-left-color: var(--green);">
                    <div class="info-box-title">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                        </svg>
                        Audit Frontal — Lecture Seule
                        <span class="badge green" style="margin-left: auto;">● LECTURE SEULE – INFORMATIONNEL</span>
                    </div>
                    <div class="info-box-content">
                        <p style="margin-bottom: 1rem; color: var(--yellow);">
                            ⚠️ <strong>Aucune analyse de sécurité réelle. Aucune action effectuée.</strong>
                            <br>Ce module affiche uniquement des informations publiques disponibles dans le navigateur.
                        </p>
                        
                        <div class="audit-grid">
                            <div class="audit-item">
                                <strong>Plateforme</strong>
                                <span>${this.escapeHtml(info.platform)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>État Réseau</strong>
                                <span>${info.onlineStatus}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Langue</strong>
                                <span>${this.escapeHtml(info.language)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Fuseau Horaire</strong>
                                <span>${this.escapeHtml(info.timezone)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Date et Heure</strong>
                                <span>${this.escapeHtml(info.timestamp)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Résolution Écran</strong>
                                <span>${this.escapeHtml(info.screenResolution)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Taille Viewport</strong>
                                <span>${this.escapeHtml(info.viewportSize)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Profondeur Couleur</strong>
                                <span>${this.escapeHtml(info.colorDepth)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Cookies Activés</strong>
                                <span>${this.escapeHtml(info.cookiesEnabled)}</span>
                            </div>
                            <div class="audit-item">
                                <strong>Do Not Track</strong>
                                <span>${this.escapeHtml(info.doNotTrack)}</span>
                            </div>
                            <div class="audit-item" style="grid-column: 1 / -1;">
                                <strong>User-Agent</strong>
                                <span style="word-break: break-all; font-size: 0.8rem; font-family: monospace;">
                                    ${this.escapeHtml(info.userAgent)}
                                </span>
                            </div>
                        </div>
                        
                        <p style="margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid var(--border); color: #888; font-size: 0.875rem;">
                            📊 <strong>Données collectées :</strong> ${Object.keys(info).length} informations non sensibles
                            <br>
                            🔒 <strong>Confidentialité :</strong> Aucune donnée transmise · Traitement local uniquement
                            <br>
                            ⚙️ <strong>Type :</strong> Informationnel · Aucune action système
                        </p>
                    </div>
                </div>
                
                <style>
                    .audit-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                        gap: 1rem;
                        margin-top: 1rem;
                    }
                    .audit-item {
                        background: rgba(255, 255, 255, 0.02);
                        padding: 0.75rem;
                        border-radius: 6px;
                        border: 1px solid var(--border);
                    }
                    .audit-item strong {
                        display: block;
                        color: var(--green);
                        font-size: 0.75rem;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                        margin-bottom: 0.25rem;
                    }
                    .audit-item span {
                        color: var(--text);
                        font-size: 0.9rem;
                    }
                </style>
            `;
        },

        /**
         * Échappe les caractères HTML pour éviter les injections
         * @param {string} text - Texte à échapper
         * @returns {string} Texte échappé
         */
        escapeHtml: function(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        },

        /**
         * Indicateur pour éviter les duplications d'event listeners
         */
        initialized: false,

        /**
         * Initialise le module et l'affiche dans le conteneur spécifié
         * @param {string} containerId - ID du conteneur HTML
         */
        init: function(containerId) {
            const container = document.getElementById(containerId);
            if (!container) {
                console.error(`Container with ID "${containerId}" not found`);
                return;
            }
            
            container.innerHTML = this.generateReport();
            
            // Ajoute les event listeners seulement une fois
            if (!this.initialized) {
                window.addEventListener('online', () => this.init(containerId));
                window.addEventListener('offline', () => this.init(containerId));
                this.initialized = true;
            }
        }
    };

    // Expose le module globalement
    window.AuditFrontalLocal = AuditFrontalLocal;
})();
