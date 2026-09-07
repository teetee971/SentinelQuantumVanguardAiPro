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
});

function initializeSkipLink() {
    if (document.querySelector('.skip-link')) return;

    const main = document.querySelector('main');
    if (!main) return;

    if (!main.id) main.id = 'main-content';

    const skipLink = createElement('a', 'skip-link', 'Aller au contenu principal');
    skipLink.href = `#${main.id}`;
    skipLink.style.cssText = 'position:fixed;top:8px;left:8px;z-index:2000;padding:10px 14px;background:#ffffff;color:#0b0f14;border-radius:8px;text-decoration:none;font-weight:700;transform:translateY(-200%);transition:transform 0.15s ease;';
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
    const brand = createElement('a', 'nav-brand');
    brand.href = '/index.html';
    brand.setAttribute('aria-label', 'Sentinel Quantum Vanguard AI Pro — Accueil');

    const logo = document.createElement('img');
    logo.src = '/public/logo.svg';
    logo.alt = '';
    logo.width = 26;
    logo.height = 26;
    logo.style.display = 'block';
    brand.appendChild(logo);
    brand.appendChild(document.createTextNode('SENTINEL QUANTUM'));

    const toggle = createElement('button', 'nav-mobile-toggle', '☰');
    toggle.type = 'button';
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-controls', 'primary-navigation');
    toggle.setAttribute('aria-label', 'Menu');

    const links = [
        ['/index.html', 'index', 'Accueil'],
        ['/public/logiciel.html', 'logiciel', 'Logiciel'],
        ['/public/pricing.html', 'pricing', 'Offres'],
        ['/public/telechargement.html', 'telechargement', 'Téléchargement'],
        ['/public/roadmap.html', 'roadmap', 'Roadmap'],
        ['/public/faq.html', 'faq', 'FAQ'],
        ['/public/legal.html', 'legal', 'Mentions légales']
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

    const accountItem = createElement('li');
    const accountLink = createElement('a', 'nav-link nav-account', 'Espace Client');
    accountLink.href = '/public/espace-client/login.html';
    accountLink.dataset.page = 'espace-client';
    accountItem.appendChild(accountLink);
    list.appendChild(accountItem);

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
        const isCurrent = currentPath === linkPath || (currentPath === '/' && linkPath.includes('index.html'));
        link.classList.toggle('active', isCurrent);
        if (isCurrent) {
            link.setAttribute('aria-current', 'page');
        } else {
            link.removeAttribute('aria-current');
        }
    });
}
