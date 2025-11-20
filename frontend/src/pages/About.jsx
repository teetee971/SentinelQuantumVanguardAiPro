import React from "react";

export default function About() {
  return (
    <main role="main" className="p-4 sm:p-6 md:p-8 max-w-4xl mx-auto">
      <header>
        <h1 className="text-2xl sm:text-3xl font-bold mb-4" style={{ fontSize: 'clamp(28px, 4vw, 40px)' }}>À propos de Sentinel Quantum Vanguard AI Pro</h1>
        <p>
          Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité augmentée par l’IA,
          conçue pour deux publics complémentaires:
        </p>
        <ul>
          <li><strong>Particuliers</strong> — Protéger l’identité numérique, la navigation et les appareils.</li>
          <li><strong>Professionnels</strong> — Visibilité, détection et réponse (XDR/EDR/SOAR léger)
            avec intégrations SIEM, API et gouvernance.</li>
        </ul>
        <p>
          Notre mission: rendre la sécurité avancée accessible, compréhensible et exploitable,
          sans complexité superflue.
        </p>
      </header>

      <hr />

      <section id="particuliers">
        <h2>Valeur pour les particuliers</h2>
        <ul>
          <li>
            <strong>Protection intelligente de la navigation</strong>
            <ul>
              <li>Détection d’URL suspectes et de pages de phishing fréquentes.</li>
              <li>Conseils contextuels: quoi vérifier, comment réagir, étapes de remédiation.</li>
            </ul>
          </li>
          <li>
            <strong>Alerte identité et hygiène numérique</strong>
            <ul>
              <li>Vérifications d’exposition d’email (lorsque disponible) et bonnes pratiques.</li>
              <li>Analyse de configuration (navigateur, autorisations, mises à jour).</li>
            </ul>
          </li>
          <li>
            <strong>Sécurité mobile (vision)</strong>
            <ul>
              <li>Indicateurs de compromission (IoC) courants et conseils pratiques.</li>
              <li>Vérifications de permissions sensibles et durcissement simplifié.</li>
            </ul>
          </li>
          <li>
            <strong>Coach IA sécurité</strong>
            <ul>
              <li>Explications pédagogiques sans jargon, parcours guidés.</li>
              <li>Guidage étape‑par‑étape en cas de suspicion d’incident.</li>
            </ul>
          </li>
          <li>
            <strong>Vie privée</strong>
            <ul>
              <li>Minimisation des données, transparence, aucune revente.</li>
            </ul>
          </li>
        </ul>
        <p>
          <em>Limites honnêtes:</em> Sentinel ne remplace pas un antivirus/antimalware.
          Certaines fonctions mobiles avancées sont en cours d’enrichissement.
        </p>
      </section>

      <section id="professionnels">
        <h2>Valeur pour les professionnels</h2>
        <ul>
          <li>
            <strong>Observabilité et détection</strong>
            <ul>
              <li>Journalisation centralisée (web, API, mobile), vues temporelles, tableaux d’alertes.</li>
              <li>Corrélation simple d’événements (MVP) et priorisation.</li>
            </ul>
          </li>
          <li>
            <strong>Intégrations et écosystème</strong>
            <ul>
              <li>Webhooks, exports; connecteurs SIEM (Splunk/Elastic/Sentinel) selon plan.</li>
              <li>Alerting Slack/Teams/Email; Jira/ServiceNow sur plans supérieurs.</li>
            </ul>
          </li>
          <li>
            <strong>Gouvernance et accès</strong>
            <ul>
              <li>Organisations, rôles (RBAC), audit logs, traçabilité.</li>
              <li>SSO (SAML/OIDC) à partir de Business.</li>
            </ul>
          </li>
          <li>
            <strong>API & SDK</strong>
            <ul>
              <li>OpenAPI, clés API, quotas, exemples (Node/Python).</li>
            </ul>
          </li>
          <li>
            <strong>SLA & support</strong>
            <ul>
              <li>Support standard → prioritaire → 24/7 selon le plan, page statut.</li>
            </ul>
          </li>
        </ul>
      </section>

      <section id="fonctionnalites">
        <h2>Fonctionnalités clés</h2>
        <ul>
          <li>Détection navigation & anti‑phishing (MVP)</li>
          <li>Conseiller sécurité assisté par IA (check‑lists, explications)</li>
          <li>Tableau de bord menaces (vision globale + carte)</li>
          <li>Journal des événements (rétention selon plan)</li>
          <li>Intégrations Slack/Teams, Webhooks; SIEM (Pro/Business+)</li>
          <li>API publique, clés et quotas</li>
          <li>Multi‑tenant (org), RBAC, audit logs (Pro/Business+)</li>
          <li>Voix IA (STT/TTS) — minutes incluses selon plan</li>
          <li>Exports CSV/JSON, rapports PDF (Pro+)</li>
          <li>Roadmap: corrélation enrichie, agents endpoint, MDM léger, DLP basique</li>
        </ul>
      </section>

      <section id="architecture-securite">
        <h2>Architecture & sécurité</h2>
        <ul>
          <li>Frontend distribué via CDN; en‑têtes durcis (CSP, HSTS, X‑Frame‑Options, etc.).</li>
          <li>Edge/Worker pour l’acheminement (robots/sitemap) et endpoints légers.</li>
          <li>Journalisation d’usage et d’événements, séparation par organisation.</li>
          <li>Observabilité: journaux, métriques, traces (OpenTelemetry – en cours), SLO par plan.</li>
          <li>Sécurité applicative: SBOM, SAST/DAST CI progressive, VDP et security.txt.</li>
        </ul>
      </section>

      <section id="confidentialite">
        <h2>Confidentialité & gouvernance des données</h2>
        <ul>
          <li>Minimisation des données; export/suppression sur demande.</li>
          <li>Chiffrement en transit (TLS) et au repos (selon service).</li>
          <li>Journal d’audit pour actions administratives (Pro/Business+).</li>
          <li>DPA/annexes conformité (Enterprise); feuille de route SOC2/ISO.</li>
        </ul>
      </section>

      <section id="tarifs">
        <h2>Tarification & plans (résumé)</h2>
        <ul>
          <li>Freemium: découverte, quotas limités.</li>
          <li>Starter: webhooks/exports, support standard.</li>
          <li>Pro: intégrations, API complète, RBAC basique, rétention accrue.</li>
          <li>Business: SSO, audit avancé, SIEM, SLA 99,9%, support prioritaire.</li>
          <li>Enterprise: dédié, conformité, 24/7, tarifs sur mesure.</li>
        </ul>
        <p>Voir la page <a href="/pricing">Tarifs</a> pour quotas et surcoûts.</p>
      </section>

      <section id="integrations">
        <h2>Intégrations</h2>
        <ul>
          <li>Collaboration: Slack, Microsoft Teams, Email (plans payants).</li>
          <li>SIEM/SOAR: Splunk, Elastic, Microsoft Sentinel (Business+).</li>
          <li>Incident: Jira, ServiceNow (Business/Enterprise).</li>
          <li>Export: CSV/JSON; API pour pipelines ETL.</li>
        </ul>
      </section>

      <section id="support-sla">
        <h2>Support, SLA & statut</h2>
        <ul>
          <li>Support communautaire (Freemium) → prioritaire (Pro/Business) → 24/7 (Enterprise).</li>
          <li>SLA à partir de Business. Page statut publique.</li>
        </ul>
        <p>
          Contacts: <a href="mailto:support@votre-domaine">support@votre‑domaine</a> — <a href="mailto:security@votre-domaine">security@votre‑domaine</a> — Urgences (Enterprise): canal dédié
        </p>
      </section>

      <section id="roadmap">
        <h2>Feuille de route (extraits)</h2>
        <ul>
          <li>Moteur de corrélation enrichi (détections guidées, low‑noise)</li>
          <li>Agents légers endpoint (desktop/mobile)</li>
          <li>Observabilité OTel complète + SLO visibles</li>
          <li>Console d’incident: playbooks, timelines, export</li>
          <li>Internationalisation (FR/EN), accessibilité AA</li>
        </ul>
      </section>

      <section id="faq">
        <h2>FAQ</h2>
        <ul>
          <li>
            <strong>Sentinel remplace‑t‑il un antivirus ?</strong>
            <div>Non. C’est un complément (détection, guidage, intégrations).</div>
          </li>
          <li>
            <strong>Mes données sont‑elles revendues ?</strong>
            <div>Non. Pas d’usage publicitaire. Transparence totale.</div>
          </li>
          <li>
            <strong>Avez‑vous une API ?</strong>
            <div>Oui, avec quotas par plan. Documentation fournie.</div>
          </li>
          <li>
            <strong>Usage en équipe ?</strong>
            <div>Oui: organisations, rôles, audit, SSO (plans supérieurs).</div>
          </li>
        </ul>
      </section>

      <footer style={{ marginTop: 32, color: "#666" }}>
        <p>Mentions légales: Conditions d’utilisation, Politique de confidentialité, DPA (sur demande).</p>
        <p>Dernière mise à jour: 2025‑11‑06</p>
      </footer>
    </main>
  );
}