import React, { useMemo, useState } from "react";
import { Link } from "react-router-dom";

function Badge({ children }) {
  return (
    <span className="inline-block text-xxs px-2 py-1 rounded bg-sentinel-accent/20 text-sentinel-accent border border-sentinel-accent/30">
      {children}
    </span>
  );
}

function PlanCard({ title, monthly, yearly, per = "", features = [], cta, highlight = false, badges = [] }) {
  return (
    <div className={`rounded-lg border ${highlight ? "border-sentinel-accent/60 shadow-lg shadow-sentinel-accent/10" : "border-sentinel-glow/20"} bg-black/40 p-5 flex flex-col`}> 
      <div className="mb-2 flex gap-2 flex-wrap">
        {badges.map((b, i) => (
          <Badge key={i}>{b}</Badge>
        ))}
      </div>
      <h3 className="text-lg font-semibold mb-2">{title}</h3>
      <div className="mb-4">
        <div className="text-3xl font-bold text-sentinel-accent">{monthly}</div>
        {yearly && <div className="text-xxs mt-1 text-gray-400">Annuel: {yearly}</div>}
        {per && <div className="text-xxs mt-1 text-gray-400">{per}</div>}
      </div>
      <ul className="text-sm space-y-2 mb-5 text-gray-200">
        {features.map((f, i) => (
          <li key={i} className="flex items-start gap-2">
            <span className="mt-1 inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70" />
            <span>{f}</span>
          </li>
        ))}
      </ul>
      {cta}
    </div>
  );
}

function Table({ headers, rows }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-sentinel-glow/20 bg-black/30">
      <table className="min-w-full text-sm">
        <thead className="bg-white/5">
          <tr>
            {headers.map((h, i) => (
              <th key={i} className="text-left px-4 py-3 font-semibold">{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-white/5">
              {r.map((c, j) => (
                <td key={j} className="px-4 py-3 text-gray-200">{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function Pricing() {
  const [annual, setAnnual] = useState(true); // bascule visuelle

  const individuals = useMemo(() => {
    return [
      {
        title: "Freemium",
        monthly: "0 €/mo",
        yearly: "0 €/an",
        features: [
          "Protection navigation basique (50 analyses/mois)",
          "Alertes phishing fréquentes",
          "Conseiller IA — 100 requêtes/mois",
          "Rétention des événements: 7 jours",
          "Pas d’exports, pas d’intégrations",
          "Support communautaire",
        ],
        badges: ["Gratuit"],
        cta: <Link to="/telechargement" className="px-3 py-2 rounded bg-white/10 hover:bg-white/20 text-sm text-white text-center">Commencer</Link>,
      },
      {
        title: "Starter",
        monthly: "4,99 €/mo",
        yearly: "47,90 €/an",
        features: [
          "Tout Freemium, plus:",
          "Détection renforcée (MVP)",
          "Conseiller IA — 500 requêtes/mois",
          "Exports basiques (CSV)",
          "Support standard",
        ],
        badges: ["Le plus accessible"],
        highlight: true,
        cta: <Link to="/verification/particulier" className="px-3 py-2 rounded bg-sentinel-accent hover:bg-sentinel-accent/90 text-sm text-black font-semibold text-center">Souscrire</Link>,
      },
      {
        title: "Pro",
        monthly: "9,99 €/mo",
        yearly: "95,90 €/an",
        features: [
          "Tout Starter, plus:",
          "Journal d’événements étendu (30 jours)",
          "Rapports PDF",
          "Intégrations email/Slack",
          "Support prioritaire",
        ],
        badges: ["Essai gratuit 14 jours"],
        cta: <Link to="/verification/particulier" className="px-3 py-2 rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 text-sm text-white text-center">Souscrire</Link>,
      },
    ];
  }, []);

  const business = useMemo(() => {
    return [
      {
        title: "Developer (Freemium)",
        monthly: "0 €/util./mo",
        yearly: "0 €/util./an",
        features: [
          "1 utilisateur",
          "API — 1 000 requêtes/mois",
          "Journal (7 jours)",
          "Pas d’intégrations SIEM",
          "Support communautaire",
        ],
        badges: ["Essais & intégration", "Gratuit"],
        cta: <Link to="/verification/professionnel" className="px-3 py-2 rounded bg-white/10 hover:bg-white/20 text-sm text-white text-center">Commencer</Link>,
      },
      {
        title: "Pro (Équipe)",
        monthly: "19 €/util./mo",
        yearly: "182,40 €/util./an",
        features: [
          "Observabilité & alertes",
          "API & clés (quotas Pro)",
          "RBAC basique, organisations",
          "Exports CSV/JSON",
          "Support standard",
        ],
        cta: <Link to="/verification/professionnel" className="px-3 py-2 rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 text-sm text-white text-center">Contacter/Vérifier</Link>,
      },
      {
        title: "Business",
        monthly: "49 €/util./mo",
        yearly: "470,40 €/util./an",
        features: [
          "SIEM (Splunk/Elastic/Sentinel)",
          "Alerting Slack/Teams/Email",
          "Audit logs, rétention accrue (90 jours)",
          "SSO (SAML/OIDC)",
          "SLA 99,9% + support prioritaire",
        ],
        badges: ["Le plus choisi", "Essai gratuit 14 jours"],
        highlight: true,
        cta: <Link to="/verification/professionnel" className="px-3 py-2 rounded bg-sentinel-accent hover:bg-sentinel-accent/90 text-sm text-black font-semibold text-center">Parler à l’équipe</Link>,
      },
      {
        title: "Enterprise",
        monthly: "Sur devis",
        yearly: "Sur devis",
        features: [
          "Environnements dédiés",
          "Conformité & DPA, annexes",
          "SLA avancé, 24/7",
          "Fonctionnalités sur mesure",
          "Support dédié",
        ],
        cta: <Link to="/verification/professionnel" className="px-3 py-2 rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 text-sm text-white text-center">Obtenir un devis</Link>,
      },
    ];
  }, []);

  const competitorHeaders = ["Offre", "Cible", "Prix indicatif", "Forces", "Limites"];
  const competitorRows = [
    ["Sentinel — Pro (Perso)", "Particuliers", "9,99 €/mo", "Coach IA + exports + intégrations", "Rétention limitée vs offres d’entreprise"],
    ["Sentinel — Business", "PME/ETI", "49 €/util./mo", "SIEM, SSO, SLA, alerting", "Prix/siège comparé aux bundles tout-en-un"],
    ["Concurrent A", "Particuliers", "~12–15 €/mo", "AV/AM mature", "IA/coach moins contextualisé"],
    ["Concurrent B", "PME/ETI", "~59–79 €/util./mo", "Écosystème large", "Complexité et coûts d’intégration"],
  ];

  const podium = [
    { rank: 1, name: "Sentinel QV AI Pro", score: 92, note: "Innovation IA + simplicité", style: "bg-gradient-to-br from-yellow-500/20 to-yellow-300/10 border-yellow-400/40" },
    { rank: 2, name: "Concurrent A", score: 88, note: "Couverture AV/AM mature", style: "bg-gradient-to-br from-gray-400/20 to-gray-300/10 border-gray-300/40" },
    { rank: 3, name: "Concurrent B", score: 83, note: "Écosystème large", style: "bg-gradient-to-br from-amber-700/20 to-amber-500/10 border-amber-600/40" },
  ];

  return (
    <main className="relative z-10 p-6 md:p-10 max-w-6xl mx-auto">
      <header className="mb-6">
        <h1 className="text-2xl md:text-3xl font-semibold">Tarification</h1>
        <p className="text-gray-400 text-sm mt-2">Des offres claires pour particuliers et professionnels. Remise -20% en annuel.</p>
        <div className="mt-4 flex items-center gap-3 text-sm">
          <span className={!annual ? "text-sentinel-accent" : "text-gray-400"}>Mensuel</span>
          <button
            onClick={() => setAnnual(!annual)}
            className={`w-12 h-6 rounded-full relative transition ${annual ? "bg-sentinel-accent/70" : "bg-white/15"}`}
            aria-label="Basculer mensuel/annuel"
          >
            <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition ${annual ? "right-0.5" : "left-0.5"}`} />
          </button>
          <span className={annual ? "text-sentinel-accent" : "text-gray-400"}>Annuel (-20%)</span>
        </div>
      </header>

      {/* Particuliers */}
      <section className="mb-10">
        <h2 className="text-xl font-semibold mb-4">Particuliers</h2>
        <div className="grid md:grid-cols-3 gap-4">
          {individuals.map((p) => (
            <PlanCard key={p.title} title={p.title} monthly={p.monthly} yearly={p.yearly} features={p.features} highlight={p.highlight} badges={p.badges} cta={p.cta} />
          ))}
        </div>
      </section>

      {/* Professionnels */}
      <section className="mb-12">
        <h2 className="text-xl font-semibold mb-4">Professionnels</h2>
        <div className="grid md:grid-cols-4 gap-4">
          {business.map((p) => (
            <PlanCard key={p.title} title={p.title} monthly={p.monthly} yearly={p.yearly} features={p.features} highlight={p.highlight} badges={p.badges} cta={p.cta} />
          ))}
        </div>
      </section>

      {/* Comparatif concurrence (simulation) */}
      <section className="mb-10">
        <h2 className="text-xl font-semibold mb-3">Comparatif concurrence (simulation)</h2>
        <p className="text-xs text-gray-500 mb-3">Illustratif et indicatif: positionnement fonctionnel/prix simulé pour visualisation.</p>
        <Table headers={competitorHeaders} rows={competitorRows} />
      </section>

      {/* Podium mondial (simulation) */}
      <section className="mb-8">
        <h2 className="text-xl font-semibold mb-3">Podium mondial (simulation)</h2>
        <p className="text-xs text-gray-500 mb-4">Classement hypothétique: innovation IA (40%), couverture (30%), simplicité (20%), coût total (10%).</p>
        <div className="grid md:grid-cols-3 gap-4">
          {podium.map((p) => (
            <div key={p.rank} className={`rounded-lg border p-5 ${p.style}`}> 
              <div className="text-3xl font-bold">#{p.rank}</div>
              <div className="mt-1 text-lg font-semibold">{p.name}</div>
              <div className="text-sm text-gray-200">Score: {p.score}/100</div>
              <div className="text-xs text-gray-400 mt-1">{p.note}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="text-xxs text-gray-500 space-y-1">
        <p>Prix en EUR, TVA non incluse le cas échéant. Les tarifs annuels indiquent l’équivalent par an (remise indicative -20%).</p>
        <p>Freemium: usage fair-use; limites techniques et de sécurité peuvent s’appliquer (cap mensuel, antispam, etc.).</p>
        <p>Facturation mensuelle ou annuelle. Annulation possible à tout moment (au terme de la période en cours).</p>
      </section>
    </main>
  );
}
