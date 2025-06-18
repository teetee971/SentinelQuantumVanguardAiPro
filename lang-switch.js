/* ---------- dictionnaire minimal ---------- */
const dict = {
  en: {
    tagline: "Next-generation AI platform for cybersecurity and real-time alerts.",
    mod1_title: "Behavioral AI",
    mod1_txt: "Anticipates threats using behaviour prediction.",
    mod2_title: "OSINT Monitoring",
    mod2_txt: "Smart analysis of social networks, leaks, dark web.",
    mod3_title: "Multilingual Voice",
    mod3_txt: "Smart alerts in English, French, Spanish.",
    mod4_title: "Government Mode",
    mod4_txt: "Secure access reserved for institutional use.",
    download:   "Download for Windows"
  },
  es: {
    tagline: "Plataforma IA de próxima generación para ciberseguridad y alertas en tiempo real.",
    mod1_title: "IA conductual",
    mod1_txt:   "Anticipa amenazas mediante la predicción de comportamiento.",
    mod2_title: "Monitoreo OSINT",
    mod2_txt:   "Análisis inteligente de redes, filtraciones y dark web.",
    mod3_title: "Voz multilingüe",
    mod3_txt:   "Alertas en inglés, francés y español.",
    mod4_title: "Modo Gobierno",
    mod4_txt:   "Acceso seguro reservado al uso institucional.",
    download:   "Descargar para Windows"
  }
};

/* ---------- toggle liste ---------- */
const btn  = document.getElementById("langBtn");
const list = document.getElementById("langList");
btn.onclick = () => list.classList.toggle("open");

/* ---------- changement de langue ---------- */
list.querySelectorAll("li").forEach(li => {
  li.addEventListener("click", () => {
    const lang = li.dataset.lang;           // 'fr' 'en' 'es'
    if (!dict[lang]) return;                // FR = défaut → pas dans dict

    Object.entries(dict[lang]).forEach(([k,v]) => {
      document.querySelectorAll(`[data-i18n="${k}"]`)
              .forEach(el => (el.textContent = v));
    });

    btn.textContent = li.textContent;       // affiche le drapeau choisi
    list.classList.remove("open");
  });
});
Object.assign(dict.en,{
  mod5_title:"Audio Guardian",
  mod5_txt:"Real-time microphone watchdog that mutes suspicious apps.",

  mod6_title:"Cognitive Shield",
  mod6_txt:"Detects social-engineering patterns & prevents phishing dialogue.",

  mod7_title:"Auto-Update",
  mod7_txt:"Silent patch delivery with cryptographic signature check.",

  mod8_title:"Vault",
  mod8_txt:"Zero-knowledge encrypted safebox for credentials & evidence.",

  policy:"Proprietary Policy PDF"
});

Object.assign(dict.es,{
  mod5_title:"Audio Guardian",
  mod5_txt:"Guardian del micrófono en tiempo real que silencia apps sospechosas.",

  mod6_title:"Escudo Cognitivo",
  mod6_txt:"Detecta ingeniería social y frena diálogos de phishing.",

  mod7_title:"Actualización automática",
  mod7_txt:"Parches silenciosos con verificación criptográfica.",

  mod8_title:"Bóveda",
  mod8_txt:"Caja fuerte cifrada sin conocimiento para credenciales y evidencias.",

  policy:"Política propietaria (PDF)"
});

dict.fr = {           // FR entier pour data-i18n
  tagline:"La plateforme IA de cybersécurité de nouvelle génération.",
  mod1_title:"IA comportementale",
  mod1_txt:"Anticipe les menaces grâce à la prédiction comportementale.",
  mod2_title:"Surveillance OSINT",
  mod2_txt:"Analyse intelligente des sources ouvertes : réseaux, fuites, dark web.",
  mod3_title:"Voix multilingue",
  mod3_txt:"Alertes intelligentes en FR, EN, ES 100 % adaptables.",
  mod4_title:"Mode Gouvernement",
  mod4_txt:"Accès sécurisé réservé à l’usage institutionnel.",
  mod5_title:"Audio Guardian",
  mod5_txt:"Surveillance micro en temps réel, coupe les apps suspectes.",
  mod6_title:"Bouclier Cognitif",
  mod6_txt:"Détecte le hameçonnage conversationnel & le bloque.",
  mod7_title:"Mise à jour automatique",
  mod7_txt:"Correctifs silencieux vérifiés par signature.",
  mod8_title:"Coffre-fort",
  mod8_txt:"Coffre chiffré zéro-knowledge pour mots de passe & preuves.",
  download:"Télécharger pour Windows",
  policy:"Politique propriétaire (PDF)"
};

