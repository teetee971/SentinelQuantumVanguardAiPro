export const DEFAULT_LOCALE = 'fr';
export const SUPPORTED_LOCALES = Object.freeze(['fr', 'en']);

const MESSAGES = Object.freeze({
  fr: Object.freeze({
    'page.title': 'Numéros & SMS — Sentinel',
    'language.label': 'Langue',
    'language.fr': 'Français',
    'language.en': 'English',
    'hero.eyebrow': 'Protection téléphonique · Android et Web',
    'hero.title': 'Identifier un SMS ou un numéro suspect',
    'hero.description': 'Analyse explicable, listes personnalisées et signalements locaux. Sentinel n’intercepte pas vos SMS et ne qualifie jamais un numéro de frauduleux sur la seule base de son préfixe.',
    'hero.scope': 'Périmètre actuel : les données restent dans ce navigateur. La liste communautaire quotidienne n’est pas connectée ; aucun signalement n’est publié ni transmis automatiquement.',
    'search.title': 'Recherche par numéro',
    'field.country': 'Pays',
    'field.number': 'Numéro',
    'action.search': 'Rechercher',
    'action.allow': 'Ajouter à la liste blanche',
    'action.block': 'Ajouter au blocage',
    'sms.title': 'Analyse assistée d’un SMS',
    'sms.label': 'Collez le texte du SMS',
    'sms.action': 'Analyser les signaux',
    'sms.disclaimer': 'Le moteur repère des indices simples (lien, urgence, demande de paiement ou d’identifiants). Il ne contacte pas le lien et son résultat n’est pas une preuve.',
    'report.title': 'Créer un signalement local',
    'report.reason': 'Raison',
    'report.operator': 'Opérateur déclaré ou supposé',
    'report.preblocked': 'Ce numéro était déjà bloqué sur mon appareil',
    'report.note': 'Note factuelle (sans donnée sensible)',
    'report.save': 'Enregistrer localement',
    'reason.phishing': 'Hameçonnage',
    'reason.impersonation': 'Usurpation',
    'reason.payment': 'Paiement demandé',
    'reason.spam': 'Spam',
    'reason.other': 'Autre',
    'sources.title': 'Listes et sources',
    'stats.title': 'Statistiques locales',
    'stats.disclaimer': 'Ces chiffres concernent uniquement ce navigateur ; ils ne représentent pas la communauté Sentinel.',
    'action.clear': 'Effacer mes données locales',
    'runtime.invalidNumber': 'Numéro invalide pour le pays sélectionné.',
    'runtime.allowed': 'Autorisé localement',
    'runtime.blocked': 'Bloqué localement',
    'runtime.noDecision': 'Aucune décision locale',
    'runtime.reportCount': '{count} signalement(s) sur cet appareil.',
    'runtime.operators': 'Opérateur(s) déclaré(s) : {operators}',
    'runtime.operatorUnknown': 'Opérateur actuel non déterminé : une attribution ARCEP ne tient pas nécessairement compte de la portabilité.',
    'runtime.risk': 'Niveau indicatif : {level}',
    'runtime.score': 'Score heuristique : {score}. Ce résultat n’est pas une preuve de fraude.',
    'runtime.noSignal': 'Aucun signal simple détecté ; une fraude reste possible.',
    'runtime.needNumber': 'Saisissez d’abord un numéro valide.',
    'runtime.savedLocal': 'Enregistré uniquement sur cet appareil. Rien n’a été transmis à Sentinel, à un opérateur ou au 33700.',
    'runtime.confirmClear': 'Effacer les listes et signalements de cet appareil ?',
    'stats.reports': 'Signalements locaux',
    'stats.allow': 'Liste blanche',
    'stats.block': 'Liste de blocage',
    'stats.countries': 'Pays couverts',
    'risk.élevé': 'élevé',
    'risk.attention': 'attention',
    'risk.faible': 'faible',
    'signal.link': 'Lien détecté',
    'signal.short-link': 'Lien raccourci détecté',
    'signal.urgency': 'Formulation urgente ou coercitive',
    'signal.credentials': 'Demande potentielle d’identifiants ou de données bancaires',
    'signal.payment-or-delivery': 'Référence à un paiement, une livraison ou une pénalité',
    'signal.claimed-authority': 'Référence à une autorité ou une institution',
    'country.FR': 'France (+33)',
    'country.BE': 'Belgique (+32)',
    'country.CH': 'Suisse (+41)',
    'country.CA': 'Canada (+1)'
  }),
  en: Object.freeze({
    'page.title': 'Phone numbers & SMS — Sentinel',
    'language.label': 'Language',
    'language.fr': 'Français',
    'language.en': 'English',
    'hero.eyebrow': 'Phone protection · Android and Web',
    'hero.title': 'Check a suspicious SMS or phone number',
    'hero.description': 'Explainable analysis, personal lists and local reports. Sentinel does not intercept your SMS messages and never labels a number fraudulent solely from its prefix.',
    'hero.scope': 'Current scope: data stays in this browser. The daily community list is not connected; no report is published or transmitted automatically.',
    'search.title': 'Search by phone number',
    'field.country': 'Country',
    'field.number': 'Phone number',
    'action.search': 'Search',
    'action.allow': 'Add to allowlist',
    'action.block': 'Add to blocklist',
    'sms.title': 'Assisted SMS analysis',
    'sms.label': 'Paste the SMS text',
    'sms.action': 'Analyze signals',
    'sms.disclaimer': 'The engine detects simple indicators such as links, urgency, payment requests or credential requests. It does not open links and its result is not proof.',
    'report.title': 'Create a local report',
    'report.reason': 'Reason',
    'report.operator': 'Declared or suspected operator',
    'report.preblocked': 'This number was already blocked on my device',
    'report.note': 'Factual note (no sensitive data)',
    'report.save': 'Save locally',
    'reason.phishing': 'Phishing',
    'reason.impersonation': 'Impersonation',
    'reason.payment': 'Payment requested',
    'reason.spam': 'Spam',
    'reason.other': 'Other',
    'sources.title': 'Lists and sources',
    'stats.title': 'Local statistics',
    'stats.disclaimer': 'These figures only concern this browser; they do not represent the Sentinel community.',
    'action.clear': 'Delete my local data',
    'runtime.invalidNumber': 'Invalid number for the selected country.',
    'runtime.allowed': 'Allowed locally',
    'runtime.blocked': 'Blocked locally',
    'runtime.noDecision': 'No local decision',
    'runtime.reportCount': '{count} report(s) on this device.',
    'runtime.operators': 'Declared operator(s): {operators}',
    'runtime.operatorUnknown': 'Current operator not determined: ARCEP allocation data does not necessarily reflect number portability.',
    'runtime.risk': 'Indicative level: {level}',
    'runtime.score': 'Heuristic score: {score}. This result is not proof of fraud.',
    'runtime.noSignal': 'No simple indicator detected; fraud may still be possible.',
    'runtime.needNumber': 'Enter a valid phone number first.',
    'runtime.savedLocal': 'Saved on this device only. Nothing was sent to Sentinel, an operator or 33700.',
    'runtime.confirmClear': 'Delete the lists and reports stored on this device?',
    'stats.reports': 'Local reports',
    'stats.allow': 'Allowlist',
    'stats.block': 'Blocklist',
    'stats.countries': 'Countries covered',
    'risk.élevé': 'high',
    'risk.attention': 'caution',
    'risk.faible': 'low',
    'signal.link': 'Link detected',
    'signal.short-link': 'Shortened link detected',
    'signal.urgency': 'Urgent or coercive wording',
    'signal.credentials': 'Possible request for credentials or banking data',
    'signal.payment-or-delivery': 'Reference to payment, delivery or a penalty',
    'signal.claimed-authority': 'Reference to an authority or institution',
    'country.FR': 'France (+33)',
    'country.BE': 'Belgium (+32)',
    'country.CH': 'Switzerland (+41)',
    'country.CA': 'Canada (+1)'
  })
});

export function normalizeLocale(value) {
  const locale = String(value || '').trim().toLowerCase().split(/[-_]/)[0];
  return SUPPORTED_LOCALES.includes(locale) ? locale : DEFAULT_LOCALE;
}

export function createTranslator(locale = DEFAULT_LOCALE) {
  const resolved = normalizeLocale(locale);
  return (key, values = {}) => {
    const template = MESSAGES[resolved][key] ?? MESSAGES[DEFAULT_LOCALE][key] ?? key;
    return String(template).replace(/\{([a-zA-Z0-9_]+)\}/g, (_, name) => String(values[name] ?? `{${name}}`));
  };
}

export function resolveLocale({ storedLocale, browserLocale } = {}) {
  if (storedLocale && SUPPORTED_LOCALES.includes(normalizeLocale(storedLocale))) return normalizeLocale(storedLocale);
  return normalizeLocale(browserLocale);
}

export function translateDocument(root, locale) {
  if (!root || typeof root.querySelectorAll !== 'function') return;
  const resolved = normalizeLocale(locale);
  const t = createTranslator(resolved);
  const html = root.documentElement || root.ownerDocument?.documentElement;
  if (html) html.lang = resolved;
  if ('title' in root) root.title = t('page.title');
  root.querySelectorAll('[data-i18n]').forEach((element) => {
    element.textContent = t(element.dataset.i18n);
  });
  root.querySelectorAll('[data-i18n-placeholder]').forEach((element) => {
    element.setAttribute('placeholder', t(element.dataset.i18nPlaceholder));
  });
}
