/*
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Shared Navigation & Common UI Components
 */

document.addEventListener('DOMContentLoaded', function() {
    initializeNavigation();
    initializeBackToTop();
    initializeSmoothScroll();
    highlightCurrentPage();
});

function initializeNavigation() {
    if (!document.querySelector('.top-nav')) {
        createNavigation();
    }

    const mobileToggle = document.querySelector('.nav-mobile-toggle');
    const navLinks = document.querySelector('.nav-links');

    if (mobileToggle && navLinks) {
        mobileToggle.addEventListener('click', function() {
            navLinks.classList.toggle('active');
            this.setAttribute('aria-expanded', navLinks.classList.contains('active'));
        });
    }
}

function createNavigation() {
    const nav = document.createElement('nav');
    nav.className = 'top-nav';
    nav.setAttribute('role', 'navigation');
    nav.setAttribute('aria-label', 'Navigation principale');

    nav.innerHTML = `
        <div class="top-nav-container">
            <a href="/index.html" class="nav-brand">SENTINEL QUANTUM</a>
            <button class="nav-mobile-toggle" aria-expanded="false" aria-label="Menu">☰</button>
            <ul class="nav-links">
                <li><a href="/index.html" class="nav-link" data-page="index">Accueil</a></li>
                <li><a href="/public/about.html" class="nav-link" data-page="about">À propos</a></li>
                <li><a href="/public/glossary.html" class="nav-link" data-page="glossary">Glossaire</a></li>
                <li><a href="/public/comparatif.html" class="nav-link" data-page="comparatif">Comparatif</a></li>
                <li><a href="/public/souverainete-numerique.html" class="nav-link" data-page="souverainete">Souveraineté</a></li>
                <li><a href="/public/mobile-security.html" class="nav-link" data-page="mobile">Sécurité mobile</a></li>
                <li><a href="/public/legal.html" class="nav-link" data-page="legal">Mentions légales</a></li>
            </ul>
        </div>
    `;

    document.body.insertBefore(nav, document.body.firstChild);
    document.body.style.paddingTop = '70px';
}

function initializeBackToTop() {
    if (!document.querySelector('.back-to-top')) {
        const button = document.createElement('button');
        button.className = 'back-to-top';
        button.setAttribute('aria-label', 'Retour en haut');
        button.innerHTML = '↑';
        document.body.appendChild(button);

        button.addEventListener('click', function() {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }

    window.addEventListener('scroll', function() {
        const button = document.querySelector('.back-to-top');
        if (button) {
            button.classList.toggle('visible', window.scrollY > 300);
        }
    });
}

function initializeSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function(e) {
            const targetId = this.getAttribute('href');
            if (targetId && targetId !== '#') {
                const targetElement = document.querySelector(targetId);
                if (targetElement) {
                    e.preventDefault();
                    targetElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    history.pushState(null, null, targetId);
                }
            }
        });
    });
}

function highlightCurrentPage() {
    const currentPath = window.location.pathname;
    document.querySelectorAll('.nav-link').forEach(link => {
        const linkPath = new URL(link.href).pathname;
        if (currentPath === linkPath || (currentPath === '/' && linkPath.includes('index.html'))) {
            link.classList.add('active');
        }
    });
}

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
        if (!heading.id) heading.id = `section-${index}`;
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

function addImageLoadingEffects() {
    document.querySelectorAll('img').forEach(img => {
        if (!img.complete) {
            img.style.opacity = '0';
            img.addEventListener('load', function() {
                this.style.transition = 'opacity 0.3s ease';
                this.style.opacity = '1';
            });
        }
    });
}

function initializeModals() {
    document.querySelectorAll('[data-modal]').forEach(modal => {
        const modalId = modal.getAttribute('data-modal');
        const triggers = document.querySelectorAll(`[data-modal-trigger="${modalId}"]`);
        const closeButtons = modal.querySelectorAll('[data-modal-close]');

        triggers.forEach(trigger => trigger.addEventListener('click', () => openModal(modal)));
        closeButtons.forEach(button => button.addEventListener('click', () => closeModal(modal)));
        modal.addEventListener('click', e => {
            if (e.target === modal) closeModal(modal);
        });
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && modal.classList.contains('active')) closeModal(modal);
        });
    });
}

function openModal(modal) {
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeModal(modal) {
    modal.classList.remove('active');
    document.body.style.overflow = '';
}
