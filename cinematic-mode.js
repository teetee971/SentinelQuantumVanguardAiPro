/**
 * SENTINEL CINEMATIC MODE CONTROLLER
 * Handles visual mode toggling between Institutional and Cinematic
 * Lazy loads assets only when cinematic mode is enabled
 */

(function() {
    'use strict';
    
    const STORAGE_KEY = 'sentinel-visual-mode';
    const DEFAULT_MODE = 'institutional';
    
    // Visual mode state
    let currentMode = DEFAULT_MODE;
    let cinematicAssetsLoaded = false;
    
    /**
     * Initialize visual mode system
     */
    function initVisualMode() {
        // Load saved preference
        const savedMode = localStorage.getItem(STORAGE_KEY) || DEFAULT_MODE;
        
        // Create mode toggle UI
        createModeToggle();
        
        // Apply saved mode
        setVisualMode(savedMode, false);
        
        console.log('[SENTINEL] Visual mode initialized:', savedMode);
    }
    
    /**
     * Create mode toggle UI in header
     */
    function createModeToggle() {
        const toggleHeader = document.createElement('div');
        toggleHeader.className = 'mode-toggle-header';
        toggleHeader.innerHTML = `
            <div class="mode-toggle-container">
                <span class="mode-toggle-label">Visual Mode</span>
                <div class="mode-toggle-switch">
                    <button class="mode-toggle-option active" data-mode="institutional">
                        Institutional
                    </button>
                    <button class="mode-toggle-option" data-mode="cinematic">
                        Cinematic
                    </button>
                </div>
            </div>
        `;
        
        // Add to body
        document.body.appendChild(toggleHeader);
        
        // Add event listeners
        const buttons = toggleHeader.querySelectorAll('.mode-toggle-option');
        buttons.forEach(button => {
            button.addEventListener('click', () => {
                const mode = button.dataset.mode;
                setVisualMode(mode, true);
            });
        });
    }
    
    /**
     * Set visual mode
     * @param {string} mode - 'institutional' or 'cinematic'
     * @param {boolean} savePreference - Whether to save to localStorage
     */
    function setVisualMode(mode, savePreference = true) {
        if (mode !== 'institutional' && mode !== 'cinematic') {
            console.error('[SENTINEL] Invalid visual mode:', mode);
            return;
        }
        
        currentMode = mode;
        
        // Update body attribute
        document.body.setAttribute('data-visual-mode', mode);
        
        // Update toggle buttons
        const buttons = document.querySelectorAll('.mode-toggle-option');
        buttons.forEach(button => {
            if (button.dataset.mode === mode) {
                button.classList.add('active');
            } else {
                button.classList.remove('active');
            }
        });
        
        // Load cinematic assets if needed
        if (mode === 'cinematic' && !cinematicAssetsLoaded) {
            loadCinematicAssets();
        }
        
        // Save preference
        if (savePreference) {
            localStorage.setItem(STORAGE_KEY, mode);
        }
        
        console.log('[SENTINEL] Visual mode changed to:', mode);
    }
    
    /**
     * Lazy load cinematic assets
     */
    function loadCinematicAssets() {
        console.log('[SENTINEL] Loading cinematic assets...');
        
        // Create cinematic hero section
        createCinematicHero();
        
        // Load background video (if motion is not reduced)
        if (!window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            loadBackgroundVideo();
        }
        
        cinematicAssetsLoaded = true;
        console.log('[SENTINEL] Cinematic assets loaded');
    }
    
    /**
     * Create cinematic hero section
     */
    function createCinematicHero() {
        // Check if hero already exists
        if (document.querySelector('.cinematic-hero')) {
            return;
        }
        
        const hero = document.createElement('div');
        hero.className = 'cinematic-hero';
        hero.innerHTML = `
            <div class="cinematic-video-bg" id="cinematic-video-container">
                <!-- Video will be injected here if motion is allowed -->
            </div>
            <div class="cinematic-hero-overlay"></div>
            <div class="cinematic-hero-content">
                <h1 class="cinematic-hero-title">SENTINEL QUANTUM VANGUARD</h1>
                <p class="cinematic-hero-subtitle">Advanced Defense & Security Operations Platform</p>
                <p class="cinematic-hero-description">
                    Next-generation security infrastructure combining AI-powered threat detection,
                    quantum defense systems, and real-time operational intelligence.
                    Designed for institutional deployment and government-grade security requirements.
                </p>
                <div class="cinematic-soldier-image" id="cinematic-soldier-container">
                    <!-- Image will be loaded here -->
                </div>
            </div>
        `;
        
        // Insert after container opening
        const container = document.querySelector('.container');
        if (container) {
            container.insertBefore(hero, container.firstChild);
        }
        
        // Load soldier image
        loadSoldierImage();
    }
    
    /**
     * Load soldier image with fallback
     */
    function loadSoldierImage() {
        const imageContainer = document.getElementById('cinematic-soldier-container');
        if (!imageContainer) {
            return;
        }
        
        // Try to load soldier image
        const imagePath = '/assets/cinematic/hero-soldier.webp';
        const img = new Image();
        
        img.onload = function() {
            imageContainer.innerHTML = '';
            img.alt = 'Sentinel Operator';
            img.style.width = '100%';
            img.style.height = '100%';
            img.style.objectFit = 'cover';
            img.loading = 'lazy';
            imageContainer.appendChild(img);
            console.log('[SENTINEL] Soldier image loaded successfully');
        };
        
        img.onerror = function() {
            // Fallback to placeholder
            imageContainer.innerHTML = `
                <div class="cinematic-soldier-placeholder">
                    Sentinel Operator Visual Asset
                    <br>
                    <small>Place image at: /assets/cinematic/hero-soldier.webp</small>
                </div>
            `;
            console.log('[SENTINEL] Soldier image not found, using placeholder');
        };
        
        img.src = imagePath;
    }
    
    /**
     * Load background video (lazy loaded)
     */
    function loadBackgroundVideo() {
        const videoContainer = document.getElementById('cinematic-video-container');
        if (!videoContainer) {
            return;
        }
        
        // Check if video already loaded
        if (videoContainer.querySelector('video')) {
            return;
        }
        
        // Video source URL
        const videoSource = '/assets/cinematic/hero-background.mp4';
        
        // Try to load video
        const video = document.createElement('video');
        video.setAttribute('autoplay', '');
        video.setAttribute('muted', '');
        video.setAttribute('loop', '');
        video.setAttribute('playsinline', '');
        video.setAttribute('preload', 'none'); // Lazy load
        
        // Add error handler for graceful degradation
        video.onerror = function() {
            console.log('[SENTINEL] Background video not available or failed to load');
            video.remove();
        };
        
        video.onloadeddata = function() {
            console.log('[SENTINEL] Background video loaded successfully');
        };
        
        video.src = videoSource;
        videoContainer.appendChild(video);
    }
    
    /**
     * Check if user prefers reduced motion
     */
    function setupMotionPreference() {
        const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
        
        mediaQuery.addEventListener('change', () => {
            if (mediaQuery.matches) {
                // Remove video if it exists
                const video = document.querySelector('.cinematic-video-bg video');
                if (video) {
                    video.remove();
                    console.log('[SENTINEL] Video removed due to reduced motion preference');
                }
            }
        });
    }
    
    // Initialize on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initVisualMode();
            setupMotionPreference();
        });
    } else {
        initVisualMode();
        setupMotionPreference();
    }
    
    // Expose API for debugging
    window.SentinelVisualMode = {
        getMode: () => currentMode,
        setMode: (mode) => setVisualMode(mode, true),
        isAssetsLoaded: () => cinematicAssetsLoaded
    };
    
})();
