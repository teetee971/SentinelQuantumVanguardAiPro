/* ===== dictionnaire complet FR / EN / ES ===== */
const dict = {
  fr: {
    // bandeau
    headline: "Sentinel Quantum Vanguard AI Pro",
    tagline:  "Plateforme IA de cybersécurité nouvelle génération.",

    // modules
    mod1_title:"IA comportementale",
    mod1_txt:"Anticipe les menaces grâce à la prédiction comportementale.",
    mod2_title:"Surveillance OSINT",
    mod2_txt:"Analyse réseaux sociaux, fuites, dark web.",
    mod3_title:"Voix multilingue",
    mod3_txt:"Alertes intelligentes en FR, EN, ES.",
    mod4_title:"Mode Gouvernement",
    mod4_txt:"Accès sécurisé réservé à l’usage institutionnel.",
    mod5_title:"Audio Guardian",
    mod5_txt:"Coupe le micro lorsqu’une appli suspecte l’utilise.",
    mod6_title:"Bouclier cognitif",
    mod6_txt:"Détecte le hameçonnage conversationnel.",
    mod7_title:"Mise à jour automatique",
    mod7_txt:"Correctifs silencieux vérifiés par signature.",
    mod8_title:"Coffre-fort",
    mod8_txt:"Coffre chiffré zéro-knowledge pour mots de passe / preuves.",

    // CTA
    download:"Télécharger pour Windows",
    policy:"Politique propriétaire (PDF)"
  },

  en: {
    headline:"Sentinel Quantum Vanguard AI Pro",
    tagline:"Next-generation AI platform for cybersecurity and real-time alerts.",

    mod1_title:"Behavioral AI",
    mod1_txt:"Anticipates threats using behaviour prediction.",
    mod2_title:"OSINT Monitoring",
    mod2_txt:"Smart analysis of social networks, leaks, dark web.",
    mod3_title:"Multilingual Voice",
    mod3_txt:"Smart alerts in English, French, Spanish.",
    mod4_title:"Government Mode",
    mod4_txt:"Secure access reserved for institutional use.",
    mod5_title:"Audio Guardian",
    mod5_txt:"Real-time microphone watchdog that mutes suspicious apps.",
    mod6_title:"Cognitive Shield",
    mod6_txt:"Detects social-engineering patterns & blocks phishing dialogue.",
    mod7_title:"Auto-Update",
    mod7_txt:"Silent patch delivery with cryptographic signature check.",
    mod8_title:"Vault",
    mod8_txt:"Zero-knowledge encrypted safebox for credentials & evidence.",

    download:"Download for Windows",
    policy:"Proprietary Policy (PDF)"
  },

  es: {
    headline:"Sentinel Quantum Vanguard AI Pro",
    tagline:"Plataforma IA de próxima generación para ciberseguridad y alertas en tiempo real.",

    mod1_title:"IA conductual",
    mod1_txt:"Anticipa amenazas mediante la predicción de comportamiento.",
    mod2_title:"Monitoreo OSINT",
    mod2_txt:"Análisis inteligente de redes, filtraciones y dark web.",
    mod3_title:"Voz multilingüe",
    mod3_txt:"Alertas en inglés, francés y español.",
    mod4_title:"Modo Gobierno",
    mod4_txt:"Acceso seguro reservado al uso institucional.",
    mod5_title:"Audio Guardian",
    mod5_txt:"Silencia en tiempo real las apps sospechosas que usan el micrófono.",
    mod6_title:"Escudo Cognitivo",
    mod6_txt:"Detecta ingeniería social y frena diálogos de phishing.",
    mod7_title:"Actualización automática",
    mod7_txt:"Parches silenciosos con verificación criptográfica.",
    mod8_title:"Bóveda",
    mod8_txt:"Caja fuerte cifrada sin conocimiento para credenciales y evidencias.",

    download:"Descargar para Windows",
    policy:"Política propietaria (PDF)"
  }
};

/* ===== gestion du menu langue ===== */
const btn  = document.getElementById("langBtn");
const list = document.getElementById("langList");
btn.onclick = () => list.classList.toggle("open");

list.querySelectorAll("li").forEach(li => {
  li.addEventListener("click", () => switchLang(li.dataset.lang, li.textContent));
});

/* ===== au premier chargement : utilise <html lang="…"> ===== */
switchLang(document.documentElement.lang);

/* ============ fonctions ============ */
function switchLang(lang, label){
  if (!dict[lang]) return;                              // sécurité

  Object.entries(dict[lang]).forEach(([k, v]) => {
    document.querySelectorAll(`[data-i18n="${k}"]`)
            .forEach(el => el.textContent = v);
  });

  if (label) btn.textContent = label;                   // met à jour l’icône
  list.classList.remove("open");
}
