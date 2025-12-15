/*
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Shared Navigation & Common UI Components
 */

// Initialize navigation on page load
document.addEventListener('DOMContentLoaded', function() {
    initializeNavigation();
    initializeBackToTop();
    initializeSmoothScroll();
    highlightCurrentPage();
});

/**
 * Initialize top navigation
 */
function initializeNavigation() {
    // Create navigation if it doesn't exist
    if (!document.querySelector('.top-nav')) {
        createNavigation();
    }
    
    // Mobile menu toggle
    const mobileToggle = document.querySelector('.nav-mobile-toggle');
    const navLinks = document.querySelector('.nav-links');
    
    if (mobileToggle && navLinks) {
        mobileToggle.addEventListener('click', function() {
            navLinks.classList.toggle('active');
            this.setAttribute('aria-expanded', 
                navLinks.classList.contains('active'));
        });
    }
}

/**
 * Create navigation HTML
 */
function createNavigation() {
    const nav = document.createElement('nav');
    nav.className = 'top-nav';
    nav.setAttribute('role', 'navigation');
    nav.setAttribute('aria-label', 'Navigation principale');
    
    nav.innerHTML = `
        <div class="top-nav-container">
            <a href="/index.html" class="nav-brand">
                SENTINEL QUANTUM
            </a>
            <button class="nav-mobile-toggle" aria-expanded="false" aria-label="Menu">
                ☰
            </button>
            <ul class="nav-links">
                <li><a href="/index.html" class="nav-link" data-page="index">Accueil</a></li>
                <li><a href="/public/about.html" class="nav-link" data-page="about">À propos</a></li>
                <li><a href="/public/glossary.html" class="nav-link" data-page="glossary">Glossaire</a></li>
                <li><a href="/public/comparatif.html" class="nav-link" data-page="comparatif">Comparatif</a></li>
                <li><a href="/public/souverainete-numerique.html" class="nav-link" data-page="souverainete">Souveraineté</a></li>
                <li><a href="/public/download.html" class="nav-link" data-page="download">Télécharger</a></li>
                <li><a href="/public/legal.html" class="nav-link" data-page="legal">Mentions légales</a></li>
            </ul>
        </div>
    `;
    
    document.body.insertBefore(nav, document.body.firstChild);
    
    // Add padding to body to account for fixed nav
    document.body.style.paddingTop = '70px';
}

/**
 * Initialize back to top button
 */
function initializeBackToTop() {
    // Create button if it doesn't exist
    if (!document.querySelector('.back-to-top')) {
        const button = document.createElement('button');
        button.className = 'back-to-top';
        button.setAttribute('aria-label', 'Retour en haut');
        button.innerHTML = '↑';
        document.body.appendChild(button);
        
        button.addEventListener('click', function() {
            window.scrollTo({
                top: 0,
                behavior: 'smooth'
            });
        });
    }
    
    // Show/hide button on scroll
    window.addEventListener('scroll', function() {
        const button = document.querySelector('.back-to-top');
        if (button) {
            if (window.scrollY > 300) {
                button.classList.add('visible');
            } else {
                button.classList.remove('visible');
            }
        }
    });
}

/**
 * Initialize smooth scrolling for anchor links
 */
function initializeSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            const targetId = this.getAttribute('href');
            if (targetId && targetId !== '#') {
                const targetElement = document.querySelector(targetId);
                if (targetElement) {
                    e.preventDefault();
                    targetElement.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });
                    
                    // Update URL without scrolling
                    history.pushState(null, null, targetId);
                }
            }
        });
    });
}

/**
 * Highlight current page in navigation
 */
function highlightCurrentPage() {
    const currentPath = window.location.pathname;
    const navLinks = document.querySelectorAll('.nav-link');
    
    navLinks.forEach(link => {
        const linkPath = new URL(link.href).pathname;
        if (currentPath === linkPath || 
            (currentPath === '/' && linkPath.includes('index.html'))) {
            link.classList.add('active');
        }
    });
}

/**
 * Create table of contents for long pages
 * @param {string} containerSelector - Selector for the container to insert TOC
 * @param {string} headingSelector - Selector for headings to include (default: h2, h3)
 */
function createTableOfContents(containerSelector, headingSelector = 'h2, h3') {
    const container = document.querySelector(containerSelector);
    if (!container) return;
    
    const headings = document.querySelectorAll(headingSelector);
    if (headings.length === 0) return;
    
    const toc = document.createElement('nav');
    toc.className = 'table-of-contents';
    toc.setAttribute('role', 'navigation');
    toc.setAttribute('aria-label', 'Table des matières');
    
    const tocTitle = document.createElement('h2');
    tocTitle.textContent = 'Sommaire';
    tocTitle.className = 'toc-title';
    toc.appendChild(tocTitle);
    
    const tocList = document.createElement('ul');
    tocList.className = 'toc-list';
    
    headings.forEach((heading, index) => {
        // Create ID if it doesn't exist
        if (!heading.id) {
            heading.id = `section-${index}`;
        }
        
        const li = document.createElement('li');
        li.className = `toc-item toc-${heading.tagName.toLowerCase()}`;
        
        const link = document.createElement('a');
        link.href = `#${heading.id}`;
        link.textContent = heading.textContent;
        link.className = 'toc-link';
        
        li.appendChild(link);
        tocList.appendChild(li);
    });
    
    toc.appendChild(tocList);
    container.appendChild(toc);
}

/**
 * Add loading animation for images
 */
function addImageLoadingEffects() {
    const images = document.querySelectorAll('img');
    
    images.forEach(img => {
        if (!img.complete) {
            img.style.opacity = '0';
            img.addEventListener('load', function() {
                this.style.transition = 'opacity 0.3s ease';
                this.style.opacity = '1';
            });
        }
    });
}

/**
 * Initialize accessible modals
 */
function initializeModals() {
    const modals = document.querySelectorAll('[data-modal]');
    
    modals.forEach(modal => {
        const modalId = modal.getAttribute('data-modal');
        const triggers = document.querySelectorAll(`[data-modal-trigger="${modalId}"]`);
        const closeButtons = modal.querySelectorAll('[data-modal-close]');
        
        triggers.forEach(trigger => {
            trigger.addEventListener('click', () => openModal(modal));
        });
        
        closeButtons.forEach(button => {
            button.addEventListener('click', () => closeModal(modal));
        });
        
        // Close on overlay click
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeModal(modal);
            }
        });
        
        // Close on Escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && modal.classList.contains('active')) {
                closeModal(modal);
            }
        });
    });
}

function openModal(modal) {
    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
    
    // Focus first focusable element
    const focusable = modal.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
    if (focusable) {
        focusable.focus();
    }
}

function closeModal(modal) {
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
}

// Export functions for use in other scripts
window.SentinelUI = {
    createTableOfContents,
    addImageLoadingEffects,
    initializeModals,
    openModal,
    closeModal
};

console.log('[SENTINEL] Navigation et UI communs initialisés');
