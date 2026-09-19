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
      description: 'Vue d’ensemble de la posture mobile et raccourcis vers les protections Sentinel.',
      evidence: 'Base Android native et états de sécurité présents dans le dépôt.',
      status: 'Code intégré',
      statusClass: 'android-status-code',
      position: '0% 0%'
    },
    {
      title: 'Journal et filtrage d’appels',
      description: 'Historique, qualification locale et décisions issues du moteur de réputation téléphonique.',
      evidence: 'CallScreeningService et logique de filtrage présents ; activation utilisateur et validation multi-appareils requises.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '25% 0%'
    },
    {
      title: 'Appel entrant',
      description: 'Alerte contextualisée pendant un appel avec signaux Wangiri, spam ou suspicion.',
      evidence: 'Chemin Caller ID et enrichissement présents ; comportement final dépend des capacités Android et des tests réels.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '50% 0%'
    },
    {
      title: 'Fiche Caller ID',
      description: 'Fiche enrichie avec score indicatif, signaux communautaires et facteurs de risque.',
      evidence: 'Lecture des signaux communautaires et enrichissement opt-in intégrés au code.',
      status: 'Code intégré',
      statusClass: 'android-status-code',
      position: '75% 0%'
    },
    {
      title: 'VPN défensif',
      description: 'Connexion protégée via le client WireGuard Android et états fail-closed.',
      evidence: 'Client WireGuard, catalogue signé et provisioning intégrés ; aucune passerelle Sentinel de sortie n’est encore déployée et validée.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '100% 0%'
    },
    {
      title: 'Choix du pays VPN',
      description: 'Sélection du pays ou connexion rapide selon les passerelles réellement disponibles.',
      evidence: 'Sélection par pays et Quick Connect définis sur catalogue signé ; les pays restent non connectables tant qu’aucun nœud réel n’est validé.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '0% 100%'
    },
    {
      title: 'Anti-publicité et anti-traceurs',
      description: 'Filtrage local de domaines publicitaires, traceurs et domaines malveillants.',
      evidence: 'Moteur de politique de domaines en cours d’intégration au pipeline VPN/DNS.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '25% 100%'
    },
    {
      title: 'SMS sécurisé',
      description: 'Client SMS Sentinel avec analyse locale des liens et garde-fous du rôle Android.',
      evidence: 'Primitives SMS réelles intégrées ; ROLE_SMS reste verrouillé tant que MMS, multi-SIM et validation appareil ne sont pas terminés.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '50% 100%'
    },
    {
      title: 'Veille d’exposition',
      description: 'Suivi des signaux d’exposition numérique et accompagnement de remédiation.',
      evidence: 'Contrôle k-anonyme de mots de passe présent ; surveillance continue e-mail/domaine encore à provisionner côté serveur.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '75% 100%'
    },
    {
      title: 'Paramètres',
      description: 'Consentements, confidentialité, préférences et configuration des protections.',
      evidence: 'Écran de direction produit ; les réglages finaux suivront les capacités réellement distribuées.',
      status: 'Prévu',
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