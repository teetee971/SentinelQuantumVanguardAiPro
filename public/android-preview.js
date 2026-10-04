(() => {
  const root = document.querySelector('[data-android-showcase]');
  if (!root) return;

  const image = root.querySelector('[data-preview-image]');
  const title = root.querySelector('[data-preview-title]');
  const description = root.querySelector('[data-preview-description]');
  const evidence = root.querySelector('[data-preview-evidence]');
  const status = root.querySelector('[data-preview-status]');
  const tabs = Array.from(root.querySelectorAll('[data-screen]'));

  const screens = [
    {
      title: 'Accueil protection',
      description: 'Vos protections principales et leurs raccourcis au même endroit.',
      evidence: 'Écran principal intégré à l’application Sentinel ; l’application reste en validation avant distribution.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '0% 0%'
    },
    {
      title: 'Activation des protections',
      description: 'Activez les fonctions téléphone, filtrage et messagerie pas à pas.',
      evidence: 'Les autorisations restent sous le contrôle d’Android et de l’utilisateur.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '25% 0%'
    },
    {
      title: 'Appel entrant',
      description: 'Décrocher, refuser, raccrocher ou mettre un appel en attente depuis Sentinel.',
      evidence: 'Les commandes d’appel sont encore vérifiées sur des appareils et réseaux réels.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '50% 0%'
    },
    {
      title: 'Identification de l’appelant',
      description: 'Retrouvez les informations disponibles sur un numéro et les signaux de vigilance associés.',
      evidence: 'La fonction est intégrée, mais l’application complète reste en validation avant distribution.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '75% 0%'
    },
    {
      title: 'VPN défensif',
      description: 'Une protection de connexion prévue pour sécuriser davantage les échanges réseau.',
      evidence: 'Le client Android existe, mais aucune passerelle Sentinel de sortie n’est encore provisionnée.',
      status: 'À venir',
      statusClass: 'android-status-planned',
      position: '100% 0%'
    },
    {
      title: 'Choix du pays VPN',
      description: 'Choisissez un pays lorsque plusieurs points de connexion seront disponibles.',
      evidence: 'Les pays ne seront proposés qu’après validation des connexions correspondantes.',
      status: 'À venir',
      statusClass: 'android-status-planned',
      position: '0% 100%'
    },
    {
      title: 'Anti-publicité et anti-traceurs',
      description: 'Une protection prévue pour réduire les domaines publicitaires, les traceurs et certains domaines malveillants.',
      evidence: 'Aucun bloqueur n’est livré aujourd’hui ; cette protection doit encore être intégrée au chemin VPN/DNS.',
      status: 'À venir',
      statusClass: 'android-status-planned',
      position: '25% 100%'
    },
    {
      title: 'SMS sécurisé',
      description: 'Envoyez et recevez vos messages depuis Sentinel avec des protections supplémentaires.',
      evidence: 'La messagerie est encore vérifiée sur des appareils réels, notamment avec plusieurs cartes SIM.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '50% 100%'
    },
    {
      title: 'Exposition numérique',
      description: 'Repérez certains signaux qui peuvent indiquer une exposition de vos informations en ligne.',
      evidence: 'Les fonctions les plus avancées seront ajoutées progressivement.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '75% 100%'
    },
    {
      title: 'Paramètres',
      description: 'Gérez vos préférences, votre confidentialité et les protections activées.',
      evidence: 'Les réglages évolueront avec les fonctions réellement disponibles dans l’application.',
      status: 'À venir',
      statusClass: 'android-status-planned',
      position: '100% 100%'
    }
  ];

  function select(index) {
    const item = screens[index];
    if (!item) return;

    image.style.backgroundPosition = item.position;
    title.textContent = item.title;
    description.textContent = item.description;
    evidence.textContent = item.evidence;
    status.textContent = item.status;
    status.className = 'android-status ' + item.statusClass;

    tabs.forEach((tab, tabIndex) => {
      const active = tabIndex === index;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    });
  }

  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => select(index));
    tab.addEventListener('keydown', event => {
      if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return;
      event.preventDefault();
      let next = index;
      if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = tabs.length - 1;
      tabs[next].focus();
      select(next);
    });
  });

  select(0);
})();