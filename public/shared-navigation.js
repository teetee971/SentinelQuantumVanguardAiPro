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

function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
}

function createNavigation() {
    const nav = createElement('nav', 'top-nav');
    nav.setAttribute('role', 'navigation');
    nav.setAttribute('aria-label', 'Navigation principale');

    const container = createElement('div', 'top-nav-container');
    const brand = createElement('a', 'nav-brand', 'SENTINEL QUANTUM');
    brand.href = '/index.html';

    const toggle = createElement('button', 'nav-mobile-toggle', '☰');
    toggle.type = 'button';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-label', 'Menu');

    const links = [
        ['/index.html', 'index', 'Accueil'],
        ['/public/about.html', 'about', 'À propos'],
        ['/public/glossary.html', 'glossary', 'Glossaire'],
        ['/public/comparatif.html', 'comparatif', 'Comparatif'],
        ['/public/souverainete-numerique.html', 'souverainete', 'Souveraineté'],
        ['/public/mobile-security.html', 'mobile', 'Sécurité mobile'],
        ['/public/legal.html', 'legal', 'Mentions légales']
    ];

    const list = createElement('ul', 'nav-links');
    links.forEach(([href, page, label]) => {
        const item = createElement('li');
        const link = createElement('a', 'nav-link', label);
        link.href = href;
        link.dataset.page = page;
        item.appendChild(link);
        list.appendChild(item);
    });

    container.appendChild(brand);
    container.appendChild(toggle);
    container.appendChild(list);
    nav.appendChild(container);
    document.body.insertBefore(nav, document.body.firstChild);
    document.body.style.paddingTop = '70px';
}

function initializeBackToTop() {
    if (!document.querySelector('.back-to-top')) {
        const button = createElement('button', 'back-to-top', '↑');
        button.type = 'button';
        button.setAttribute('aria-label', 'Retour en haut');
        document.body.appendChild(button);
        button.addEventListener('click', function() {
            window.scrollTo({ top: 0, behavior: 'smooth' });
        });
    }

    window.addEventListener('scroll', function() {
        const button = document.querySelector('.back-to-top');
        if (button) button.classList.toggle('visible', window.scrollY > 300);
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
