/**
 * SYSTEM TRANSPARENCY MODULE
 * 
 * Module JavaScript de détection des capacités du navigateur
 * MODE: LECTURE SEULE - INFORMATIONNEL UNIQUEMENT
 * 
 * ⚠️ AVERTISSEMENT:
 * - Aucun scan de sécurité
 * - Aucun test de vulnérabilité
 * - Aucun accès système
 * - Aucun fingerprint persistant
 * - Aucun stockage distant
 * - Toutes les données restent locales
 * 
 * Objectif: Afficher les capacités techniques du navigateur de manière transparente
 * Compatible: GitHub Pages, Cloudflare Pages (site statique uniquement)
 * 
 * @version 2.1.0-pro
 * @date 2025-01-13
 */

(function() {
    'use strict';

    // Prévention de l'initialisation multiple
    if (window.systemTransparencyInitialized) {
        return;
    }
    window.systemTransparencyInitialized = true;

    /**
     * Détecte les capacités et fonctionnalités du navigateur
     * Toutes les vérifications sont READ-ONLY et non-intrusives
     */
    function detectBrowserCapabilities() {
        const capabilities = {
            // APIs Web modernes
            webRTC: 'RTCPeerConnection' in window || 'webkitRTCPeerConnection' in window || 'mozRTCPeerConnection' in window,
            webGL: detectWebGL(),
            webGL2: detectWebGL2(),
            serviceWorker: 'serviceWorker' in navigator,
            
            // Stockage
            localStorage: detectStorage('localStorage'),
            sessionStorage: detectStorage('sessionStorage'),
            indexedDB: 'indexedDB' in window,
            
            // Notifications et permissions
            notifications: 'Notification' in window,
            geolocation: 'geolocation' in navigator,
            
            // APIs multimédia
            mediaDevices: 'mediaDevices' in navigator,
            getUserMedia: navigator.mediaDevices && 'getUserMedia' in navigator.mediaDevices,
            
            // Web Workers
            webWorkers: 'Worker' in window,
            sharedWorkers: 'SharedWorker' in window,
            
            // Connexion réseau
            onLine: navigator.onLine,
            connection: 'connection' in navigator || 'mozConnection' in navigator || 'webkitConnection' in navigator,
            
            // Autres APIs
            webSockets: 'WebSocket' in window,
            webAssembly: 'WebAssembly' in window,
            webBluetooth: 'bluetooth' in navigator,
            webUSB: 'usb' in navigator,
            
            // Performance et observateurs
            performanceObserver: 'PerformanceObserver' in window,
            intersectionObserver: 'IntersectionObserver' in window,
            mutationObserver: 'MutationObserver' in window,
            
            // Sécurité
            crypto: 'crypto' in window && 'subtle' in window.crypto,
            credentialsAPI: 'credentials' in navigator,
            
            // Paiements et authentification
            paymentRequest: 'PaymentRequest' in window,
            webAuthn: 'credentials' in navigator && 'create' in navigator.credentials
        };

        return capabilities;
    }

    /**
     * Détecte WebGL de manière sûre
     */
    function detectWebGL() {
        try {
            const canvas = document.createElement('canvas');
            return !!(canvas.getContext('webgl') || canvas.getContext('experimental-webgl'));
        } catch (e) {
            return false;
        }
    }

    /**
     * Détecte WebGL2 de manière sûre
     */
    function detectWebGL2() {
        try {
            const canvas = document.createElement('canvas');
            return !!canvas.getContext('webgl2');
        } catch (e) {
            return false;
        }
    }

    /**
     * Détecte la disponibilité du stockage (localStorage/sessionStorage)
     */
    function detectStorage(type) {
        try {
            const storage = window[type];
            const testKey = '__storage_test__';
            storage.setItem(testKey, 'test');
            storage.removeItem(testKey);
            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Récupère les informations sur l'environnement du navigateur
     */
    function getBrowserEnvironment() {
        const ua = navigator.userAgent;
        
        return {
            userAgent: ua,
            platform: navigator.platform,
            language: navigator.language,
            languages: navigator.languages || [navigator.language],
            cookiesEnabled: navigator.cookieEnabled,
            doNotTrack: navigator.doNotTrack || 'non spécifié',
            hardwareConcurrency: navigator.hardwareConcurrency || 'non disponible',
            maxTouchPoints: navigator.maxTouchPoints || 0,
            vendor: navigator.vendor || 'non spécifié',
            vendorSub: navigator.vendorSub || 'non spécifié',
            productSub: navigator.productSub || 'non spécifié'
        };
    }

    /**
     * Récupère le statut des permissions (lecture seule, sans demande)
     * Note: On ne DEMANDE PAS de permissions, on vérifie uniquement leur état
     */
    async function getPermissionsStatus() {
        const permissions = {
            notifications: 'non vérifié',
            geolocation: 'non vérifié',
            camera: 'non vérifié',
            microphone: 'non vérifié'
        };

        // Vérification des permissions SANS les demander
        if ('permissions' in navigator) {
            try {
                // Notifications
                if ('Notification' in window) {
                    permissions.notifications = Notification.permission;
                }

                // Note: On ne query PAS les autres permissions car cela pourrait déclencher
                // des demandes au navigateur. On affiche uniquement l'état des notifications
                // qui est accessible sans déclencher de prompt.
            } catch (e) {
                // Silencieux - permissions API peut ne pas être disponible
            }
        }

        return permissions;
    }

    /**
     * Détecte les informations réseau (si disponibles)
     */
    function getNetworkInfo() {
        const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
        
        if (!connection) {
            return {
                available: false,
                type: 'non disponible',
                effectiveType: 'non disponible',
                downlink: 'non disponible',
                rtt: 'non disponible',
                saveData: 'non disponible'
            };
        }

        return {
            available: true,
            type: connection.type || 'non spécifié',
            effectiveType: connection.effectiveType || 'non spécifié',
            downlink: connection.downlink ? connection.downlink + ' Mbps' : 'non disponible',
            rtt: connection.rtt ? connection.rtt + ' ms' : 'non disponible',
            saveData: connection.saveData !== undefined ? (connection.saveData ? 'Oui' : 'Non') : 'non disponible'
        };
    }

    /**
     * Construit l'interface utilisateur du panel System Transparency
     */
    function buildSystemTransparencyUI(capabilities, environment, permissions, networkInfo) {
        // Créer le conteneur principal
        const section = document.createElement('section');
        section.className = 'section';
        section.id = 'system-transparency-panel';

        // En-tête de section
        const header = document.createElement('h2');
        header.className = 'section-title';
        header.innerHTML = `
            <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" style="color:var(--blue)">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
            System Transparency Panel
        `;
        section.appendChild(header);

        // Disclaimer
        const disclaimer = document.createElement('div');
        disclaimer.className = 'info-box';
        disclaimer.style.borderLeftColor = 'var(--blue)';
        disclaimer.innerHTML = `
            <div class="info-box-title">
                <span class="badge blue">● LECTURE SEULE – INFORMATIONNEL</span>
            </div>
            <div class="info-box-content">
                ⚠️ <strong>Aucune analyse de sécurité réelle. Aucune action effectuée.</strong><br>
                Ce module détecte uniquement les capacités techniques du navigateur à des fins d'information.
                Aucun test de vulnérabilité, aucun scan, aucune collecte de données personnelles.
            </div>
        `;
        section.appendChild(disclaimer);

        // Grille de cartes
        const grid = document.createElement('div');
        grid.className = 'grid grid-3';

        // Carte 1: Capacités du navigateur
        grid.appendChild(createCapabilitiesCard(capabilities));

        // Carte 2: Environnement
        grid.appendChild(createEnvironmentCard(environment));

        // Carte 3: Statut réseau
        grid.appendChild(createNetworkCard(networkInfo));

        section.appendChild(grid);

        // Carte des permissions (pleine largeur)
        const permissionsCard = createPermissionsCard(permissions);
        section.appendChild(permissionsCard);

        return section;
    }

    /**
     * Crée la carte des capacités du navigateur
     */
    function createCapabilitiesCard(capabilities) {
        const card = document.createElement('div');
        card.className = 'card';
        
        const enabledCount = Object.values(capabilities).filter(v => v === true).length;
        const totalCount = Object.keys(capabilities).length;
        const percentage = Math.round((enabledCount / totalCount) * 100);

        card.innerHTML = `
            <h3 style="font-size:1.125rem;margin-bottom:1rem">Capacités du Navigateur</h3>
            <div style="margin-bottom:1rem">
                <div class="card-value" style="font-size:2.5rem">${enabledCount}/${totalCount}</div>
                <div class="card-desc">APIs détectées (${percentage}%)</div>
            </div>
            <ul class="status-list">
                ${createCapabilityItems(capabilities)}
            </ul>
        `;

        return card;
    }

    /**
     * Génère les items de capacités
     */
    function createCapabilityItems(capabilities) {
        const items = [];
        
        // Grouper les capacités par catégories
        const categories = {
            'APIs Modernes': ['webRTC', 'webGL', 'webGL2', 'serviceWorker'],
            'Stockage': ['localStorage', 'sessionStorage', 'indexedDB'],
            'Multimédia': ['mediaDevices', 'getUserMedia'],
            'Workers': ['webWorkers', 'sharedWorkers'],
            'Réseau': ['onLine', 'connection', 'webSockets'],
            'Avancé': ['webAssembly', 'crypto', 'webAuthn', 'paymentRequest']
        };

        // Sélectionner les 8 plus importantes
        const important = [
            'webRTC', 'webGL', 'serviceWorker', 'localStorage',
            'mediaDevices', 'webWorkers', 'onLine', 'webAssembly'
        ];

        important.forEach(key => {
            const status = capabilities[key];
            const label = formatCapabilityLabel(key);
            const badgeClass = status ? 'green' : 'red';
            const badgeText = status ? 'DISPONIBLE' : 'INDISPONIBLE';
            const dotClass = status ? 'green' : 'red';

            items.push(`
                <li>
                    <span class="module-status">
                        <span class="status-dot ${dotClass}"></span>
                        ${label}
                    </span>
                    <span class="badge ${badgeClass}">${badgeText}</span>
                </li>
            `);
        });

        return items.join('');
    }

    /**
     * Formate les labels de capacités
     */
    function formatCapabilityLabel(key) {
        const labels = {
            'webRTC': 'WebRTC',
            'webGL': 'WebGL',
            'webGL2': 'WebGL 2',
            'serviceWorker': 'Service Worker',
            'localStorage': 'Local Storage',
            'sessionStorage': 'Session Storage',
            'indexedDB': 'IndexedDB',
            'notifications': 'Notifications',
            'geolocation': 'Géolocalisation',
            'mediaDevices': 'Media Devices',
            'getUserMedia': 'GetUserMedia',
            'webWorkers': 'Web Workers',
            'sharedWorkers': 'Shared Workers',
            'onLine': 'État en ligne',
            'connection': 'Network Info',
            'webSockets': 'WebSockets',
            'webAssembly': 'WebAssembly',
            'webBluetooth': 'Web Bluetooth',
            'webUSB': 'Web USB',
            'crypto': 'Web Crypto',
            'credentialsAPI': 'Credentials API',
            'paymentRequest': 'Payment Request',
            'webAuthn': 'WebAuthn'
        };

        return labels[key] || key;
    }

    /**
     * Crée la carte de l'environnement
     */
    function createEnvironmentCard(environment) {
        const card = document.createElement('div');
        card.className = 'card';

        card.innerHTML = `
            <h3 style="font-size:1.125rem;margin-bottom:1rem">Environnement du Navigateur</h3>
            <ul class="status-list">
                <li>
                    <span style="color:#aaa">Plateforme</span>
                    <span style="font-weight:600">${escapeHtml(environment.platform)}</span>
                </li>
                <li>
                    <span style="color:#aaa">Langue</span>
                    <span style="font-weight:600">${escapeHtml(environment.language)}</span>
                </li>
                <li>
                    <span style="color:#aaa">Cookies</span>
                    <span class="badge ${environment.cookiesEnabled ? 'green' : 'red'}">
                        ${environment.cookiesEnabled ? 'ACTIVÉS' : 'DÉSACTIVÉS'}
                    </span>
                </li>
                <li>
                    <span style="color:#aaa">Do Not Track</span>
                    <span style="font-weight:600">${escapeHtml(String(environment.doNotTrack))}</span>
                </li>
                <li>
                    <span style="color:#aaa">Cœurs CPU</span>
                    <span style="font-weight:600">${escapeHtml(String(environment.hardwareConcurrency))}</span>
                </li>
                <li>
                    <span style="color:#aaa">Touch Points</span>
                    <span style="font-weight:600">${escapeHtml(String(environment.maxTouchPoints))}</span>
                </li>
                <li>
                    <span style="color:#aaa">Vendor</span>
                    <span style="font-weight:600;font-size:0.8rem">${escapeHtml(environment.vendor)}</span>
                </li>
            </ul>
        `;

        return card;
    }

    /**
     * Crée la carte réseau
     */
    function createNetworkCard(networkInfo) {
        const card = document.createElement('div');
        card.className = 'card';

        const statusBadge = navigator.onLine ? 
            '<span class="badge green">● EN LIGNE</span>' : 
            '<span class="badge red">● HORS LIGNE</span>';

        card.innerHTML = `
            <h3 style="font-size:1.125rem;margin-bottom:1rem">Informations Réseau</h3>
            <div style="margin-bottom:1rem">
                ${statusBadge}
            </div>
            <ul class="status-list">
                <li>
                    <span style="color:#aaa">Type de connexion</span>
                    <span style="font-weight:600">${escapeHtml(networkInfo.effectiveType)}</span>
                </li>
                <li>
                    <span style="color:#aaa">Débit descendant</span>
                    <span style="font-weight:600">${escapeHtml(networkInfo.downlink)}</span>
                </li>
                <li>
                    <span style="color:#aaa">RTT (latence)</span>
                    <span style="font-weight:600">${escapeHtml(networkInfo.rtt)}</span>
                </li>
                <li>
                    <span style="color:#aaa">Mode économie</span>
                    <span style="font-weight:600">${escapeHtml(networkInfo.saveData)}</span>
                </li>
            </ul>
        `;

        return card;
    }

    /**
     * Crée la carte des permissions
     */
    function createPermissionsCard(permissions) {
        const card = document.createElement('div');
        card.className = 'card';
        card.style.marginTop = '1.5rem';

        const getPermissionBadge = (status) => {
            if (status === 'granted') return '<span class="badge green">ACCORDÉE</span>';
            if (status === 'denied') return '<span class="badge red">REFUSÉE</span>';
            if (status === 'prompt') return '<span class="badge yellow">NON DEMANDÉE</span>';
            return '<span class="badge" style="background:rgba(128,128,128,0.1);color:#888">NON VÉRIFIÉ</span>';
        };

        card.innerHTML = `
            <h3 style="font-size:1.125rem;margin-bottom:1rem">Statut des Permissions (lecture seule)</h3>
            <div style="margin-bottom:1rem;padding:0.75rem;background:rgba(245,158,11,0.1);border-left:3px solid var(--yellow);border-radius:6px">
                <div style="font-size:0.875rem;color:#aaa">
                    ℹ️ Ces informations sont lues uniquement. Aucune permission n'est demandée par ce module.
                </div>
            </div>
            <ul class="status-list">
                <li>
                    <span class="module-status">
                        <span class="status-dot ${permissions.notifications === 'granted' ? 'green' : permissions.notifications === 'denied' ? 'red' : 'yellow'}"></span>
                        Notifications
                    </span>
                    ${getPermissionBadge(permissions.notifications)}
                </li>
                <li>
                    <span class="module-status">
                        <span class="status-dot yellow"></span>
                        Géolocalisation
                    </span>
                    ${getPermissionBadge(permissions.geolocation)}
                </li>
                <li>
                    <span class="module-status">
                        <span class="status-dot yellow"></span>
                        Caméra
                    </span>
                    ${getPermissionBadge(permissions.camera)}
                </li>
                <li>
                    <span class="module-status">
                        <span class="status-dot yellow"></span>
                        Microphone
                    </span>
                    ${getPermissionBadge(permissions.microphone)}
                </li>
            </ul>
        `;

        return card;
    }

    /**
     * Échappe le HTML pour éviter XSS
     */
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Initialise le module System Transparency
     */
    async function initSystemTransparency() {
        // Vérifier que nous sommes sur la page dashboard
        const targetContainer = document.getElementById('system-transparency-container');
        if (!targetContainer) {
            return; // Pas sur la bonne page
        }

        // Collecter toutes les informations
        const capabilities = detectBrowserCapabilities();
        const environment = getBrowserEnvironment();
        const permissions = await getPermissionsStatus();
        const networkInfo = getNetworkInfo();

        // Construire l'UI
        const panel = buildSystemTransparencyUI(capabilities, environment, permissions, networkInfo);

        // Injecter dans le DOM
        targetContainer.appendChild(panel);

        // Surveiller les changements de statut réseau
        window.addEventListener('online', updateNetworkStatus);
        window.addEventListener('offline', updateNetworkStatus);
    }

    /**
     * Met à jour le statut réseau dynamiquement
     */
    function updateNetworkStatus() {
        const networkCard = document.querySelector('#system-transparency-panel .grid .card:nth-child(3)');
        if (!networkCard) return;

        const statusBadge = navigator.onLine ? 
            '<span class="badge green">● EN LIGNE</span>' : 
            '<span class="badge red">● HORS LIGNE</span>';

        const badgeContainer = networkCard.querySelector('div[style*="margin-bottom"]');
        if (badgeContainer) {
            badgeContainer.innerHTML = statusBadge;
        }
    }

    // Auto-initialisation quand le DOM est prêt
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initSystemTransparency);
    } else {
        initSystemTransparency();
    }

})();
