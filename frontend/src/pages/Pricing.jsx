import React, { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronDown, ChevronUp } from "lucide-react";

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
      <ul className="text-sm space-y-2 mb-5 text-gray-200 flex-grow">  
        {features.map((f, i) => (  
          <li key={i} className="flex items-start gap-2">  
            <span className="mt-1 inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70 flex-shrink-0" />  
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

function FAQItem({ question, answer }) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="border border-sentinel-glow/20 rounded-lg bg-black/30 overflow-hidden">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="w-full flex items-center justify-between p-4 text-left hover:bg-white/5 transition"
        aria-expanded={isOpen}
      >
        <span className="font-semibold">{question}</span>
        {isOpen ? <ChevronUp size={20} aria-hidden="true" /> : <ChevronDown size={20} aria-hidden="true" />}
      </button>
      {isOpen && (
        <div className="px-4 pb-4 text-sm text-gray-300">
          {answer}
        </div>
      )}
    </div>
  );
}

export default function Pricing() {
  const { t, i18n } = useTranslation();
  const [annual, setAnnual] = useState(true);

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'fr' ? 'en' : 'fr');
  };

  const individuals = useMemo(() => {
    return [
      {
        title: t('pricing.plans.freemium.title'),
        monthly: t('pricing.plans.freemium.price_monthly'),
        yearly: t('pricing.plans.freemium.price_yearly'),
        features: t('pricing.plans.freemium.features', { returnObjects: true }),
        badges: [t('pricing.plans.freemium.badge')],
        cta: <Link to="/telechargement?utm_source=pricing&utm_medium=cta&utm_campaign=freemium-start" className="px-3 py-2 rounded bg-white/10 hover:bg-white/20 text-sm text-white text-center block">{t('pricing.plans.freemium.cta')}</Link>,
      },
      {
        title: t('pricing.plans.starter.title'),
        monthly: t('pricing.plans.starter.price_monthly'),
        yearly: t('pricing.plans.starter.price_yearly'),
        features: t('pricing.plans.starter.features', { returnObjects: true }),
        badges: [t('pricing.plans.starter.badge')],
        highlight: true,
        cta: <Link to="/verification/particulier?utm_source=pricing&utm_medium=cta&utm_campaign=starter-signup" className="px-3 py-2 rounded bg-sentinel-accent hover:bg-sentinel-accent/90 text-sm text-black font-semibold text-center block">{t('pricing.plans.starter.cta')}</Link>,
      },
      {
        title: t('pricing.plans.pro.title'),
        monthly: t('pricing.plans.pro.price_monthly'),
        yearly: t('pricing.plans.pro.price_yearly'),
        features: t('pricing.plans.pro.features', { returnObjects: true }),
        badges: [t('pricing.plans.pro.badge')],
        cta: <Link to="/verification/particulier?utm_source=pricing&utm_medium=cta&utm_campaign=pro-trial&utm_content=14d" className="px-3 py-2 rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 text-sm text-white text-center block">{t('pricing.plans.pro.cta')}</Link>,
      },
    ];
  }, [t]);

  const business = useMemo(() => {
    return [
      {
        title: t('pricing.plans.developer.title'),
        monthly: t('pricing.plans.developer.price_monthly'),
        yearly: t('pricing.plans.developer.price_yearly'),
        features: t('pricing.plans.developer.features', { returnObjects: true }),
        badges: t('pricing.plans.developer.badge', { returnObjects: true }),
        cta: <Link to="/verification/professionnel?utm_source=pricing&utm_medium=cta&utm_campaign=dev-freemium" className="px-3 py-2 rounded bg-white/10 hover:bg-white/20 text-sm text-white text-center block">{t('pricing.plans.developer.cta')}</Link>,
      },
      {
        title: t('pricing.plans.team_pro.title'),
        monthly: t('pricing.plans.team_pro.price_monthly'),
        yearly: t('pricing.plans.team_pro.price_yearly'),
        features: t('pricing.plans.team_pro.features', { returnObjects: true }),
        cta: <Link to="/verification/professionnel?utm_source=pricing&utm_medium=cta&utm_campaign=team-pro" className="px-3 py-2 rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 text-sm text-white text-center block">{t('pricing.plans.team_pro.cta')}</Link>,
      },
      {
        title: t('pricing.plans.business.title'),
        monthly: t('pricing.plans.business.price_monthly'),
        yearly: t('pricing.plans.business.price_yearly'),
        features: t('pricing.plans.business.features', { returnObjects: true }),
        badges: t('pricing.plans.business.badge', { returnObjects: true }),
        highlight: true,
        cta: <Link to="/verification/professionnel?utm_source=pricing&utm_medium=cta&utm_campaign=business-trial&utm_content=14d" className="px-3 py-2 rounded bg-sentinel-accent hover:bg-sentinel-accent/90 text-sm text-black font-semibold text-center block">{t('pricing.plans.business.cta')}</Link>,
      },
      {
        title: t('pricing.plans.enterprise.title'),
        monthly: t('pricing.plans.enterprise.price_monthly'),
        yearly: t('pricing.plans.enterprise.price_yearly'),
        features: t('pricing.plans.enterprise.features', { returnObjects: true }),
        cta: <Link to="/verification/professionnel?utm_source=pricing&utm_medium=cta&utm_campaign=enterprise-contact" className="px-3 py-2 rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 text-sm text-white text-center block">{t('pricing.plans.enterprise.cta')}</Link>,
      },
    ];
  }, [t]);

  const usageQuotaHeaders = [
    t('pricing.usage.metrics.events'),
    "Freemium",
    "Starter", 
    "Pro",
    "Business",
    "Enterprise"
  ];

  const usageQuotaRows = [
    [
      t('pricing.usage.metrics.events'),
      t('pricing.usage.quotas.freemium.events'),
      t('pricing.usage.quotas.starter.events'),
      t('pricing.usage.quotas.pro.events'),
      t('pricing.usage.quotas.business.events'),
      t('pricing.usage.quotas.enterprise.events'),
    ],
    [
      t('pricing.usage.metrics.api_calls'),
      t('pricing.usage.quotas.freemium.api_calls'),
      t('pricing.usage.quotas.starter.api_calls'),
      t('pricing.usage.quotas.pro.api_calls'),
      t('pricing.usage.quotas.business.api_calls'),
      t('pricing.usage.quotas.enterprise.api_calls'),
    ],
    [
      t('pricing.usage.metrics.scans'),
      t('pricing.usage.quotas.freemium.scans'),
      t('pricing.usage.quotas.starter.scans'),
      t('pricing.usage.quotas.pro.scans'),
      t('pricing.usage.quotas.business.scans'),
      t('pricing.usage.quotas.enterprise.scans'),
    ],
    [
      t('pricing.usage.metrics.stt_minutes'),
      t('pricing.usage.quotas.freemium.stt_minutes'),
      t('pricing.usage.quotas.starter.stt_minutes'),
      t('pricing.usage.quotas.pro.stt_minutes'),
      t('pricing.usage.quotas.business.stt_minutes'),
      t('pricing.usage.quotas.enterprise.stt_minutes'),
    ],
    [
      t('pricing.usage.metrics.tts_minutes'),
      t('pricing.usage.quotas.freemium.tts_minutes'),
      t('pricing.usage.quotas.starter.tts_minutes'),
      t('pricing.usage.quotas.pro.tts_minutes'),
      t('pricing.usage.quotas.business.tts_minutes'),
      t('pricing.usage.quotas.enterprise.tts_minutes'),
    ],
    [
      t('pricing.usage.metrics.storage_days'),
      t('pricing.usage.quotas.freemium.storage_days'),
      t('pricing.usage.quotas.starter.storage_days'),
      t('pricing.usage.quotas.pro.storage_days'),
      t('pricing.usage.quotas.business.storage_days'),
      t('pricing.usage.quotas.enterprise.storage_days'),
    ],
  ];

  const competitorHeaders = t('pricing.competitor.headers', { returnObjects: true });
  const competitorRows = [
    ["Sentinel — Pro (Perso)", "Particuliers", "9,99 €/mo", "Coach IA + exports + intégrations", "Rétention limitée vs offres d'entreprise"],
    ["Sentinel — Business", "PME/ETI", "49 €/util./mo", "SIEM, SSO, SLA, alerting", "Prix/siège comparé aux bundles tout-en-un"],
    ["Concurrent A", "Particuliers", "~12–15 €/mo", "AV/AM mature", "IA/coach moins contextualisé"],
    ["Concurrent B", "PME/ETI", "~59–79 €/util./mo", "Écosystème large", "Complexité et coûts d'intégration"],
  ];

  const podium = [
    { rank: 1, name: "Sentinel QV AI Pro", score: 92, note: "Innovation IA + simplicité", style: "bg-gradient-to-br from-yellow-500/20 to-yellow-300/10 border-yellow-400/40" },
    { rank: 2, name: "Concurrent A", score: 88, note: "Couverture AV/AM mature", style: "bg-gradient-to-br from-gray-400/20 to-gray-300/10 border-gray-300/40" },
    { rank: 3, name: "Concurrent B", score: 83, note: "Écosystème large", style: "bg-gradient-to-br from-amber-700/20 to-amber-500/10 border-amber-600/40" },
  ];

  const faqItems = t('pricing.faq.items', { returnObjects: true });
  const notes = t('pricing.notes', { returnObjects: true });

  return (
    <main className="relative z-10 p-6 md:p-10 max-w-7xl mx-auto">  
      <header className="mb-6">
        <div className="flex items-center justify-between mb-4">
          <h1 className="text-2xl md:text-3xl font-semibold">{t('pricing.title')}</h1>
          <button
            onClick={toggleLanguage}
            className="px-3 py-1.5 text-xs rounded border border-sentinel-accent/40 hover:bg-sentinel-accent/10 transition"
          >
            {i18n.language === 'fr' ? '🇬🇧 EN' : '🇫🇷 FR'}
          </button>
        </div>
        <p className="text-gray-400 text-sm mt-2">{t('pricing.subtitle')}</p>  
        <div className="mt-4 flex items-center gap-3 text-sm">  
          <span className={!annual ? "text-sentinel-accent" : "text-gray-400"}>{t('pricing.monthly')}</span>  
          <button
            onClick={() => setAnnual(!annual)}
            className={`w-12 h-6 rounded-full relative transition ${annual ? "bg-sentinel-accent/70" : "bg-white/15"}`}
            aria-label="Basculer mensuel/annuel"
          >  
            <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition ${annual ? "right-0.5" : "left-0.5"}`} />  
          </button>  
          <span className={annual ? "text-sentinel-accent" : "text-gray-400"}>{t('pricing.yearly')}</span>  
        </div>  
      </header>  

      {/* Particuliers */}  
      <section id="particuliers" className="mb-10">  
        <h2 className="text-xl font-semibold mb-4">{t('pricing.individuals')}</h2>  
        <div className="grid md:grid-cols-3 gap-4">  
          {individuals.map((p) => (
            <PlanCard key={p.title} {...p} />  
          ))}  
        </div>  
      </section>  

      {/* Professionnels */}  
      <section id="professionnels" className="mb-12">  
        <h2 className="text-xl font-semibold mb-4">{t('pricing.professionals')}</h2>  
        <div className="grid md:grid-cols-4 gap-4">  
          {business.map((p) => (
            <PlanCard key={p.title} {...p} />  
          ))}  
        </div>  
      </section>

      {/* Usage Quotas Table */}
      <section id="usage-quotas" className="mb-10">
        <h2 className="text-xl font-semibold mb-3">{t('pricing.usage.title')}</h2>
        <p className="text-xs text-gray-400 mb-4">{t('pricing.usage.description')}</p>
        <Table headers={usageQuotaHeaders} rows={usageQuotaRows} />
        
        {/* Overage Pricing */}
        <div className="mt-6 p-5 rounded-lg border border-sentinel-glow/20 bg-black/30">
          <h3 className="text-lg font-semibold mb-3">{t('pricing.usage.overage.title')}</h3>
          <p className="text-sm text-gray-300 mb-4">{t('pricing.usage.overage.description')}</p>
          <ul className="text-sm space-y-2 text-gray-200">
            <li className="flex items-center gap-2">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70" />
              <span>{t('pricing.usage.metrics.events')}: {t('pricing.usage.overage.prices.events')}</span>
            </li>
            <li className="flex items-center gap-2">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70" />
              <span>{t('pricing.usage.metrics.api_calls')}: {t('pricing.usage.overage.prices.api_calls')}</span>
            </li>
            <li className="flex items-center gap-2">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70" />
              <span>{t('pricing.usage.metrics.scans')}: {t('pricing.usage.overage.prices.scans')}</span>
            </li>
            <li className="flex items-center gap-2">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70" />
              <span>{t('pricing.usage.metrics.stt_minutes')}: {t('pricing.usage.overage.prices.stt_minutes')}</span>
            </li>
            <li className="flex items-center gap-2">
              <span className="inline-block h-1.5 w-1.5 rounded-full bg-sentinel-accent/70" />
              <span>{t('pricing.usage.metrics.tts_minutes')}: {t('pricing.usage.overage.prices.tts_minutes')}</span>
            </li>
          </ul>
          <p className="text-xs text-gray-500 mt-4">{t('pricing.usage.overage.note')}</p>
        </div>
      </section>

      {/* FAQ Section */}
      <section id="faq" className="mb-10">
        <h2 className="text-xl font-semibold mb-4">{t('pricing.faq.title')}</h2>
        <div className="space-y-3">
          {faqItems.map((item, idx) => (
            <FAQItem key={idx} question={item.question} answer={item.answer} />
          ))}
        </div>
      </section>

      {/* Comparatif concurrence (simulation) */}  
      <section className="mb-10">  
        <h2 className="text-xl font-semibold mb-3">{t('pricing.competitor.title')}</h2>  
        <p className="text-xs text-gray-500 mb-3">{t('pricing.competitor.subtitle')}</p>  
        <Table headers={competitorHeaders} rows={competitorRows} />  
      </section>  

      {/* Podium mondial (simulation) */}  
      <section className="mb-8">  
        <h2 className="text-xl font-semibold mb-3">{t('pricing.podium.title')}</h2>  
        <p className="text-xs text-gray-500 mb-4">{t('pricing.podium.subtitle')}</p>  
        <div className="grid md:grid-cols-3 gap-4">  
          {podium.map((p) => (
            <div key={p.rank} className={`rounded-lg border p-5 ${p.style}`}>  
              <div className="text-3xl font-bold">#{p.rank}</div>  
              <div className="mt-1 text-lg font-semibold">{p.name}</div>  
              <div className="text-sm text-gray-200">{t('pricing.podium.score')}: {p.score}/100</div>  
              <div className="text-xs text-gray-400 mt-1">{p.note}</div>  
            </div>  
          ))}  
        </div>  
      </section>  

      {/* Footer notes */}
      <section className="text-xxs text-gray-500 space-y-1">
        {notes.map((note, idx) => (
          <p key={idx}>{note}</p>
        ))}
      </section>
    </main>
  );
}
