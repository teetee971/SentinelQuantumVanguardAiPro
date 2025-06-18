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
