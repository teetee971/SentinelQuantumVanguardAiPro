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
      title: 'Phone Core · activation',
      description: 'Centre d’activation des rôles Téléphone, filtrage d’appels et SMS avec état runtime explicite.',
      evidence: 'Parcours intégré dans l’application ; chaque rôle et permission passe par Android. Validation sur appareil physique encore requise.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '25% 0%'
    },
    {
      title: 'Appel entrant',
      description: 'Interface Sentinel pour décrocher, refuser, raccrocher, mettre en attente et envoyer des tonalités DTMF.',
      evidence: 'InCallService et UI d’appel sont intégrés au rôle Téléphone ; comportement réel à confirmer sur appareils/opérateurs.',
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
      evidence: 'Client WireGuard intégré ; aucune passerelle Sentinel de sortie n’est encore provisionnée.',
      status: 'En validation',
      statusClass: 'android-status-validation',
      position: '100% 0%'
    },
    {
      title: 'Choix du pays VPN',
      description: 'Sélection du pays ou connexion rapide selon les passerelles réellement disponibles.',
      evidence: 'Architecture multi-régions définie ; les pays resteront non connectables tant qu’aucun nœud réel n’est validé.',
      status: 'Prévu',
      statusClass: 'android-status-planned',
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
      description: 'Client SMS Sentinel avec envoi, réception, conversations locales, analyse des liens et sélection multi-SIM.',
      evidence: 'ROLE_SMS et permissions peuvent être demandés depuis Phone Core ; les pièces jointes MMS complètes restent en validation appareil.',
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