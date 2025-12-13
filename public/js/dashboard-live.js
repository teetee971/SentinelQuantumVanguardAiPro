/**
 * DASHBOARD VIVANT — DONNÉES RÉELLES NON SENSIBLES
 * 
 * Ce module ajoute des éléments dynamiques au dashboard pour donner
 * une sensation de vie réelle, sans aucune donnée externe ou sensible.
 * 
 * Fonctionnalités :
 * - Horodatage temps réel
 * - Statut réseau en direct (En ligne / Hors ligne)
 * - Bouton "Rafraîchir les informations"
 * - Badges dynamiques (LIVE / SIMULATION)
 * 
 * Règles strictes :
 * - Aucune donnée externe
 * - Aucune API tierce
 * - Aucune promesse de protection
 * - 100% local et transparent
 */

(function() {
    'use strict';

    const DashboardLive = {
        /**
         * État du dashboard
         */
        state: {
            lastUpdate: null,
            networkStatus: 'unknown',
            refreshCount: 0
        },

        /**
         * Initialise le dashboard live
         */
        init: function() {
            this.updateTimestamp();
            this.updateNetworkStatus();
            this.createLiveSection();
            
            // Met à jour l'horodatage toutes les secondes
            setInterval(() => this.updateTimestamp(), 1000);
            
            // Écoute les changements d'état réseau
            window.addEventListener('online', () => this.updateNetworkStatus());
            window.addEventListener('offline', () => this.updateNetworkStatus());
        },

        /**
         * Crée la section dashboard live
         */
        createLiveSection: function() {
            const container = document.getElementById('live-dashboard-section');
            if (!container) return;
            
            const isOnline = navigator.onLine;
            const networkClass = isOnline ? 'green' : 'red';
            const networkIcon = isOnline ? '✓' : '✗';
            const networkText = isOnline ? 'En ligne' : 'Hors ligne';
            
            const refreshBtnId = 'refresh-info-btn-' + Date.now();
            
            container.innerHTML = `
                <div class="section">
                    <h2 class="section-title">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="color:var(--blue)">
                            <path d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/>
                        </svg>
                        Informations en Temps Réel
                        <span class="badge green" style="margin-left: auto;">● LIVE – LECTURE SEULE</span>
                    </h2>
                    
                    <div class="grid">
                        <div class="card">
                            <div class="card-header">
                                <span class="card-title">Horodatage</span>
                                <span class="badge blue">● TEMPS RÉEL</span>
                            </div>
                            <div id="live-timestamp" class="card-value" style="font-size: 1.25rem; font-family: monospace;">--:--:--</div>
                            <p class="card-desc">Heure locale mise à jour en direct</p>
                        </div>
                        
                        <div class="card">
                            <div class="card-header">
                                <span class="card-title">Statut Réseau</span>
                                <span class="badge ${networkClass}">● ${networkText.toUpperCase()}</span>
                            </div>
                            <div class="card-value" style="font-size: 2rem;">${networkIcon}</div>
                            <p class="card-desc" id="network-status-desc">Connexion réseau : ${networkText}</p>
                        </div>
                        
                        <div class="card">
                            <div class="card-header">
                                <span class="card-title">Mises à Jour</span>
                                <span class="badge yellow">● SIMULATION</span>
                            </div>
                            <div id="refresh-count" class="card-value">0</div>
                            <p class="card-desc">Nombre de rafraîchissements manuels</p>
                        </div>
                    </div>
                    
                    <div style="margin-top: 1.5rem; display: flex; gap: 1rem; align-items: center;">
                        <button id="${refreshBtnId}" class="btn btn-primary">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
                            </svg>
                            Rafraîchir les informations
                        </button>
                        <span id="last-refresh" style="color: #666; font-size: 0.875rem;">Jamais rafraîchi</span>
                    </div>
                    
                    <div class="info-box" style="margin-top: 1.5rem; border-left-color: var(--yellow);">
                        <div class="info-box-title">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
                            </svg>
                            Mode de Fonctionnement
                        </div>
                        <div class="info-box-content">
                            <strong>LECTURE SEULE</strong> · Aucune donnée externe · Aucune API tierce · Traitement local uniquement
                            <br>
                            Les informations affichées proviennent uniquement du navigateur et ne sont jamais transmises.
                        </div>
                    </div>
                </div>
            `;
            
            // Initialise immédiatement le timestamp
            this.updateTimestamp();
            
            // Attache l'event listener au bouton de rafraîchissement
            const refreshBtn = document.getElementById(refreshBtnId);
            if (refreshBtn) {
                refreshBtn.addEventListener('click', () => this.refreshInfo());
            }
        },

        /**
         * Met à jour l'horodatage
         */
        updateTimestamp: function() {
            const timestampEl = document.getElementById('live-timestamp');
            if (!timestampEl) return;
            
            const now = new Date();
            const timeString = now.toLocaleTimeString('fr-FR', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                hour12: false
            });
            const dateString = now.toLocaleDateString('fr-FR', {
                weekday: 'long',
                year: 'numeric',
                month: 'long',
                day: 'numeric'
            });
            
            timestampEl.textContent = timeString;
            timestampEl.title = dateString;
        },

        /**
         * Met à jour le statut réseau
         */
        updateNetworkStatus: function() {
            const isOnline = navigator.onLine;
            const prevStatus = this.state.networkStatus;
            this.state.networkStatus = isOnline ? 'online' : 'offline';
            
            // Affiche une notification (non intrusive)
            console.log(`[Dashboard Live] Statut réseau: ${this.state.networkStatus}`);
            
            // Recrée la section seulement si le statut a changé
            if (prevStatus !== 'unknown' && prevStatus !== this.state.networkStatus) {
                this.createLiveSection();
            }
        },

        /**
         * Rafraîchit les informations
         */
        refreshInfo: function() {
            this.state.refreshCount++;
            this.state.lastUpdate = new Date();
            
            // Met à jour le compteur
            const countEl = document.getElementById('refresh-count');
            if (countEl) {
                countEl.textContent = this.state.refreshCount;
            }
            
            // Met à jour le texte de dernier rafraîchissement
            const lastRefreshEl = document.getElementById('last-refresh');
            if (lastRefreshEl) {
                const timeAgo = this.state.lastUpdate.toLocaleTimeString('fr-FR');
                lastRefreshEl.textContent = `Dernière mise à jour : ${timeAgo}`;
            }
            
            // Animation visuelle
            if (countEl) {
                countEl.style.transform = 'scale(1.2)';
                countEl.style.color = 'var(--green)';
                setTimeout(() => {
                    countEl.style.transform = 'scale(1)';
                    countEl.style.color = '';
                }, 300);
            }
            
            console.log('[Dashboard Live] Informations rafraîchies', {
                count: this.state.refreshCount,
                timestamp: this.state.lastUpdate.toISOString()
            });
        }
    };

    // Expose le module globalement
    window.DashboardLive = DashboardLive;

    // Auto-initialisation quand le DOM est prêt
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => DashboardLive.init());
    } else {
        DashboardLive.init();
    }
})();
