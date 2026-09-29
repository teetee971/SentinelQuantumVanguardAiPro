/*
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Shared Navigation & Common UI Components
 */

document.addEventListener('DOMContentLoaded', function() {
    initializeSkipLink();
    initializeNavigation();
    initializeBackToTop();
    initializeSmoothScroll();
    highlightCurrentPage();
    initializeRoadmapFilters();
});

function initializeSkipLink() {
    if (document.querySelector('.skip-link')) return;

    const main = document.querySelector('main');
    if (!main) return;

    if (!main.id) main.id = 'main-content';

    const skipLink = createElement('a', 'skip-link', 'Aller au contenu principal');
    skipLink.href = `#${main.id}`;
    skipLink.style.cssText = 'position:fixed;top:8px;left:8px;z-index:2000;padding:10px 14px;background:#ffffff;color:#0b0f14;border-radius:8px;text-decoration:none;font-weight:800;transform:translateY(-200%);transition:transform 0.15s ease;';
    skipLink.addEventListener('focus', function() {
        this.style.transform = 'translateY(0)';
    });
    skipLink.addEventListener('blur', function() {
        this.style.transform = 'translateY(-200%)';
    });
    document.body.insertBefore(skipLink, document.body.firstChild);
}

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

        document.addEventListener('keydown', function(event) {
            if (event.key === 'Escape' && navLinks.classList.contains('active')) {
                navLinks.classList.remove('active');
                mobileToggle.setAttribute('aria-expanded', 'false');
                mobileToggle.focus();
            }
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
    const brand = createElement('a', 'nav-brand', 'Sentinel Quantum');
    brand.href = '/index.html';

    const toggle = createElement('button', 'nav-mobile-toggle', '☰');
    toggle.type = 'button';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-controls', 'primary-navigation');
    toggle.setAttribute('aria-label', 'Ouvrir le menu');

    const links = [
        ['/index.html', 'home', 'Accueil'],
        ['/public/produit.html', 'produit', 'Produit'],
        ['/public/phone-intelligence.html', 'phone-intelligence', 'Annuaire gratuit'],
        ['/index.html#modules', 'modules', 'Modules'],
        ['/public/pricing.html', 'pricing', 'Tarifs'],
        ['/public/download-guide.html', 'download-guide', 'Android'],
        ['/public/espace-client.html', 'espace-client', 'Espace client'],
        ['/public/about.html', 'about', 'À propos'],
        ['/public/faq.html', 'faq', 'FAQ'],
        ['/public/roadmap.html', 'roadmap', 'Avancement']
    ];

    const list = createElement('ul', 'nav-links');
    list.id = 'primary-navigation';
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
    const skipLink = document.querySelector('.skip-link');
    const insertionPoint = skipLink && skipLink.parentNode === document.body
        ? skipLink.nextSibling
        : document.body.firstChild;
    document.body.insertBefore(nav, insertionPoint);
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

function normalizePath(pathname) {
    if (pathname === '/') return '/index.html';
    if (pathname.endsWith('/')) return `${pathname}index.html`;
    return pathname;
}

function highlightCurrentPage() {
    const currentPath = normalizePath(window.location.pathname);
    const currentHash = window.location.hash;

    document.querySelectorAll('.nav-link').forEach(link => {
        const linkUrl = new URL(link.href);
        const linkPath = normalizePath(linkUrl.pathname);
        const isHashSection = linkUrl.hash && linkPath === '/index.html';
        const isCurrent = isHashSection
            ? currentPath === '/index.html' && currentHash === linkUrl.hash
            : currentPath === linkPath;

        link.classList.toggle('active', isCurrent);
        if (isCurrent) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
    });
}

function initializeRoadmapFilters() {
    const filters = Array.from(document.querySelectorAll('[data-roadmap-filter]'));
    const items = Array.from(document.querySelectorAll('[data-roadmap-status]'));
    const count = document.querySelector('[data-roadmap-count]');

    if (!filters.length || !items.length) return;

    const applyFilter = (value) => {
        let visible = 0;
        items.forEach((item) => {
            const matches = value === 'all' || item.dataset.roadmapStatus === value;
            item.hidden = !matches;
            if (matches) visible += 1;
        });

        filters.forEach((button) => {
            const selected = button.dataset.roadmapFilter === value;
            button.classList.toggle('is-active', selected);
            button.setAttribute('aria-pressed', selected ? 'true' : 'false');
        });

        if (count) {
            count.textContent = value === 'all'
                ? `Affichage de tous les chantiers (${visible}).`
                : `${visible} chantier${visible > 1 ? 's' : ''} dans cet état.`;
        }
    };

    filters.forEach((button) => {
        button.addEventListener('click', () => applyFilter(button.dataset.roadmapFilter || 'all'));
    });

    applyFilter('all');
}
