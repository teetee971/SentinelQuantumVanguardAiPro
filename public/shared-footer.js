/*
 * SENTINEL QUANTUM VANGUARD AI PRO
 * Shared footer for the public showcase site (vitrine).
 * Injects a consistent legal footer. Skipped when a page already
 * contains a <footer class="site-footer"> element.
 */

document.addEventListener('DOMContentLoaded', function() {
    if (document.querySelector('.site-footer')) return;

    const footer = document.createElement('footer');
    footer.className = 'site-footer';

    const shell = document.createElement('div');
    shell.className = 'brand-shell';

    const grid = document.createElement('div');
    grid.className = 'site-footer-grid';

    const columns = [
        {
            title: 'Produit',
            links: [
                ['/public/logiciel.html', 'Présentation du logiciel'],
                ['/public/pricing.html', 'Offres et abonnements'],
                ['/public/telechargement.html', 'Téléchargement'],
                ['/public/roadmap.html', 'Roadmap publique']
            ]
        },
        {
            title: 'Guides',
            links: [
                ['/public/guides/installation-pc.html', 'Installer sur PC'],
                ['/public/guides/utilisation-mobile.html', 'Utiliser sur téléphone'],
                ['/public/faq.html', 'FAQ']
            ]
        },
        {
            title: 'Compte',
            links: [
                ['/public/espace-client/login.html', 'Espace Client'],
                ['/public/telechargement.html', 'Accès aux téléchargements']
            ]
        },
        {
            title: 'Légal',
            links: [
                ['/public/legal.html', 'Mentions légales'],
                ['/public/privacy.html', 'Politique de confidentialité'],
                ['/public/terms.html', 'Modalités d’utilisation']
            ]
        }
    ];

    columns.forEach(function(column) {
        const section = document.createElement('div');
        const heading = document.createElement('h2');
        heading.textContent = column.title;
        const list = document.createElement('ul');
        column.links.forEach(function(entry) {
            const item = document.createElement('li');
            const link = document.createElement('a');
            link.href = entry[0];
            link.textContent = entry[1];
            item.appendChild(link);
            list.appendChild(item);
        });
        section.appendChild(heading);
        section.appendChild(list);
        grid.appendChild(section);
    });

    const bottom = document.createElement('div');
    bottom.className = 'site-footer-bottom';
    bottom.textContent = 'Sentinel Quantum Vanguard AI Pro · Logiciel natif local (PC et Android) distribué par abonnement · Le site public est une vitrine de présentation, sans accès gratuit aux applications.';

    shell.appendChild(grid);
    shell.appendChild(bottom);
    footer.appendChild(shell);
    document.body.appendChild(footer);
});
