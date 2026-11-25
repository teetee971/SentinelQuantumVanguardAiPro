# Inventaire des branches et des pull requests

Généré le 2026-11-25 après `git fetch origin`. Lecture du code : `6de5a272e0302ad1eccc6c2215157b13f97d485c`.
CI : **non consultée**. Aucune requête check-runs, pour ne pas lancer de workflow.
Statut produit : **COMMERCIAL NO-GO**. Cet inventaire ne qualifie aucune branche.

## Méthode

- Branche distante = référence `refs/remotes/origin/*` sauf `origin/HEAD`.
- `git merge-base` décide si une histoire est commune.
- Avance / retard (`+avant/-arrière`) seulement si l’ancêtre existe. Sinon : histoires disjointes, écart non publié.
- **Divergente** : ancêtre commun avec `main`, et des commits des deux côtés.
- **Contenue dans main** : la pointe est un ancêtre de `main` (branche résiduelle). Comptée obsolète s’il n’y a pas de PR ouverte.
- **Contient main** : `main` est ancêtre de la pointe (en avance seulement, ou identique).
- **Orpheline** : pas de PR ouverte, ni `main`, ni `integration/*`, pointe non contenue dans `main` ni dans `integration/sentinel-v1-commercial-20261118`. Les histoires disjointes sont signalées à part, pas noyées dans un simple retard.
- **Obsolète** : contenue dans `main` sans PR ouverte, ou dernier commit avant le 2026-10-01 sans PR ouverte et hors branches `integration/*`.
- PR fermées : page des 100 mises à jour le plus récemment (pas l’historique complet).

## Chiffres

- Branches distantes : **1150**
- PR ouvertes : **107**, dont brouillons **72**
- PR fermées lues : **100** (fusionnées 63, fermées non fusionnées 37)
- Worktree accessible dans cet environnement : un seul (voir plus bas).
- Branches locales : voir plus bas. `integration/d1-visual-phonecore-20261029` est absente du remote. `fix/v1-phone-core-p1-20261119` est présente (`f036b2b79372`) et n’a pas été extraite.
- La branche de ces documents, `docs/audit-global-phase1-20261119`, a été poussée pendant la génération : elle n’est pas dans l’annexe. Elle part de integration et ne contient que `audit/global/`.

### Rapport à `main`

- divergente : 747
- contenue_dans_main : 233
- contient_main : 122
- histoires_disjointes : 48

### Rapport à `integration/sentinel-v1-commercial-20261118`

- histoires_disjointes : 1141
- contient_integration : 5
- contenue_dans_integration : 3
- divergente : 1

`main` (`935344423b2af2a4a57c58123f2c5c178b46ec03`) et integration (`113862500b9579e2ce476c190ad3195f676ffdda`) n’ont pas d’ancêtre commun. L’API GitHub Compare répond 404 « No common ancestor ». Le diff d’arbres `main` / `6de5a272` porte sur 783 fichiers. Ce n’est pas un simple retard.

## Worktree et branches locales

```
/agent/repos/SentinelQuantumVanguardAiPro  fa12dafec [docs/audit-global-phase1-20261119]
* docs/audit-global-phase1-20261119 fa12dafec [origin/docs/audit-global-phase1-20261119] docs(audit): déposer le focus OSINT et le cadre de la phase 1
  main                              935344423 [origin/main] Merge pull request #1677 from teetee971/fix/community-report-country-main-20261016
```

Le clone de l’utilisateur sur `integration/d1-visual-phonecore-20261029` n’est pas monté ici. Fichiers non suivis signalés et non lus : `app/schemas/`, `ui/design/SentinelEvidenceGraph.kt`, `SentinelHolographicScene.kt`, `SentinelQuantumCore.kt`, `SentinelReferencePanels.kt`, `SentinelVoiceVisualizer.kt`.

## Ligne integration et descendance

| Branche | SHA | Date | Rapport à integration | +/− integration | Sujet |
| --- | --- | --- | --- | --- | --- |
| `cursor/v1-call-screening-audit-536d` | `5a6f7014bdb1` | 2026-11-18 | contient_integration | +4/-0 | Record the screening warm-up commit SHA [skip ci] |
| `cursor/v1-p0-privacy-catalog-c39e` | `6de5a272e030` | 2026-11-18 | contient_integration | +12/-0 | docs(v1): refresh the copyable Play report and align stale drafts [skip ci] |
| `cursor/v1-phone-core-full-audit-20261118` | `e77337d67727` | 2026-11-19 | contient_integration | +15/-0 | [skip ci] Audit: distinguer JVM 100% et PASS physique appel/SMS/MMS |
| `cursor/v1-play-compliance-20261118-c39e` | `29a207f07d6d` | 2026-11-18 | contient_integration | +3/-0 | docs(v1): record the adversarial Play audit and correct Data safety [skip ci] |
| `fix/sentinel-ai-bounded-pairing-20261117` | `0bfb6c26f7f9` | 2026-11-17 | contenue_dans_integration | +0/-13 | docs: retain bounded output pairing evidence and S24 validation limits [skip ci] |
| `fix/sentinel-ai-revocation-barrier-20261118` | `4e444a59a769` | 2026-11-18 | contenue_dans_integration | +0/-6 | Document C11 qualification, S24 procedures and transversal findings [skip ci] |
| `fix/v1-phone-core-p1-20261119` | `f036b2b79372` | 2026-11-19 | contient_integration | +22/-0 | fix: apply UnknownCallerPolicy in call screening after fail-open [skip ci] |
| `integration/sentinel-cve-v5-20261025` | `d51e36403c6a` | 2026-10-25 | histoires_disjointes | non calculable | feat(cve-v5): add separate metadata section to Sentinel threat intelligence page |
| `integration/sentinel-d1-3d-20261022` | `1b21e29d4df7` | 2026-10-22 | histoires_disjointes | non calculable | chore(android): remove unused ZIP import |
| `integration/sentinel-d1-cve-v5-20261025` | `92a469b4069c` | 2026-10-27 | histoires_disjointes | non calculable | fix: separate signed block and silence prefix policies |
| `integration/sentinel-qualification-20261022` | `599bc3a379d1` | 2026-10-22 | histoires_disjointes | non calculable | test(android): integrate MMS role compensation |
| `integration/sentinel-v1-commercial-20261118` | `113862500b95` | 2026-11-18 | contenue_dans_integration | +0/-0 | Record Android runtime advisory coverage and its limits [skip ci] |
| `transfer/sentinel-integral-p0-cycle3-20261116` | `caa43c1392b9` | 2026-11-16 | divergente | +6/-20 | chore: transfer verified local AI HTTP deadline fix [skip ci] |

## Branches d’histoire disjointe de `main`

Ce ne sont pas de simples branches en retard. Leur racine n’est pas celle de `main`.

| Branche | SHA | Date | Auteur | Rapport integration | Sujet |
| --- | --- | --- | --- | --- | --- |
| `copilot/fix-5a816cbb-68ae-4711-bfe8-67ae2d9f87e1` | `0e51831e1b28` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | Create deploy.yml workflow with npm ci for stable builds |
| `copilot/fix-b166ee27-5b56-4625-a050-d92d67aa2089` | `1782c9e6c737` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | 📦 Implement smart npm dependency installation (npm ci vs npm install) |
| `copilot/fix-c64ba2e1-8753-49b8-993e-c84a72bf2653` | `17c5892f3fc2` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | Implement fetchPrixDOM function for French government price monitoring |
| `copilot/fix-8a68307a-9e29-45b4-9b81-5303ed9abfa1` | `f2713db3d606` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | Implement OPMR price monitoring module with full UI integration |
| `copilot/fix-8a2d1256-3bc9-4702-a323-1c38e49e31e6` | `c6b219395214` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | Implement price comparison feature with colored badges for DOM/Métropo |
| `copilot/fix-0f1df187-b3fb-40f4-9729-ad14c303d90a` | `980b5ebd4a0e` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | Implement fetchPrixDOM function and integrate price search functionali |
| `copilot/fix-8d247eb4-cde3-4c78-87f7-8b25cb8b526c` | `37864f858a81` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | Add documentation and final verification for fetchPrixDOM implementati |
| `copilot/fix-35f3e23b-d54b-4bc8-bbf8-223f2c613589` | `9445170732ba` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | Initial plan |
| `copilot/fix-17c4c4be-b857-44c4-84b9-b6ee18c730be` | `b63da24bc1bb` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | Initial plan |
| `copilot/fix-238c2518-9390-47d5-b2c7-ee0e1d181b38` | `2e1a51b2a22e` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | Changes before error encountered |
| `copilot/fix-607a3373-60ac-4dc7-a2d9-d8dea5524478` | `33d260056c8c` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | Initial plan |
| `copilot/fix-eea0c36a-2348-4f0b-a13a-9becffd2f0dd` | `f664a9a32df7` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | Add fix-deps.sh script for Termux/ARM64 compatibility and Cloudflare P |
| `copilot/fix-12d9bb1b-df03-4a53-9a0b-f3eec5755239` | `65813383a7fa` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | Add comprehensive site status monitoring: dashboard, API, and CLI tool |
| `copilot/fix-b2f0c82e-52f6-4aae-846a-6803c12004d1` | `e74a3ab9bf0d` | 2025-09-22 | copilot-swe-agent[bot] | histoires_disjointes | Fix React application structure and add Layout component |
| `copilot/fix-0d968b51-f19d-4980-a2b2-12e8522814ad` | `ecd3a836a1c4` | 2025-09-22 | copilot-swe-agent[bot] | histoires_disjointes | Create test.yml workflow and update upload-artifact from v3 to v4 |
| `copilot/fix-fdf6a450-fc27-4f6f-9450-9e775ca79753` | `06bbf23447bb` | 2025-09-22 | copilot-swe-agent[bot] | histoires_disjointes | Implement actions/upload-artifact@v4 in all Firebase hosting workflows |
| `principal` | `47064a8fae66` | 2025-10-31 | teetee971 | histoires_disjointes | 🚀 Initial Commit FullDeploy v1 |
| `copilot/update-roadmap-progress` | `4cd5b580fc16` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Addressing PR comments |
| `copilot/sync-firestore-dashboard` | `3c74632b5f32` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Apply security improvements and robustness fixes to Firestore sync wor |
| `copilot/add-pegasus-scan-protection-module` | `e4ee9591f298` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Fix code review issues - cleanup imports and prevent memory leaks |
| `copilot/add-documentation-folder-structure` | `7ddd32b7adcc` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Add documentation folder structure with PDF placeholder |
| `copilot/automate-ci-cd-pipeline` | `5320301fac85` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Implement all AI modules (Infrastructure, Voice, Monitoring, Self-Heal |
| `copilot/add-official-pdf-download` | `084f844d5a60` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Add PDF download page at /telechargement with dark theme design |
| `copilot/add-download-link-navbar` | `ccb1ebdef976` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | Changes before error encountered |
| `copilot/add-sentinel-mobile-defender` | `73bb6d034b28` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Complete MODULE 12: Add all security components and Security Dashboard |
| `copilot/add-global-cybersecurity-system` | `34e930ffaa89` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Complete implementation of all modules - dashboard and routing |
| `copilot/implement-infragard-ci-cd` | `dd8ff20606da` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Add comprehensive SENTINEL_MODULES.md documentation |
| `copilot/create-global-threat-journal-page` | `ac05ea4e0bbc` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Use DOMParser for safer HTML sanitization |
| `copilot/add-certification-message-animation` | `5755a9f2cde5` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Implement holographic certification message animation |
| `copilot/fix-95961170-1003429074-d05da60a-d5ea-4155-9c2a-ed1ffefd52b1` | `3ea1954e25b6` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Update package.json configuration with required dependencies and metad |
| `copilot/add-loading-animation-pdf` | `0d7d669f4860` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | Add root package-lock.json and update .gitignore for Cloudflare Pages  |
| `copilot/fix-95961170-1003429074-f7f633f1-b9de-4340-90e5-dbd7bf7d926a` | `1369510132e5` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | fix(voice-srv1): add workflow permissions to satisfy security requirem |
| `copilot/fix-95961170-1003429074-07dd8298-ccd5-4f15-99a5-1b31d52dec16` | `c47a2abdc624` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | chore: prepare branch for PR |
| `merge-2025-11-04-v2` | `2c7e87ec0b5c` | 2025-11-04 | teetee971 | histoires_disjointes | robots.txt + sitemap.xml + exceptions SPA + headers sécurité |
| `merge-2025-11-04` | `066a11bd4dd4` | 2025-11-04 | teetee971 | histoires_disjointes | SEO + sécurité: robots & sitemap + headers |
| `fix/seo-headers-1104` | `ac9ca11f9792` | 2025-11-04 | teetee971 | histoires_disjointes | chore: fix _headers syntax + ignore keys |
| `save/2026-11-10-session` | `0b0fb1067cf4` | 2026-11-10 | teetee971 | histoires_disjointes | Sauvegarde du 10 novembre 2026 : aperçu D.1 et talkie. |
| `transfer/sentinel-integral-p0-cycle3-20261116` | `caa43c1392b9` | 2026-11-16 | Thierry Naud | divergente | chore: transfer verified local AI HTTP deadline fix [skip ci] |
| `transfer/sentinel-cycle9-validation-artifacts-20261117` | `8c171aa118e8` | 2026-11-17 | Thierry Naud | histoires_disjointes | delivery: cycle9 S24 validation APKs and verified source bundle; NO-GO |
| `fix/sentinel-ai-bounded-pairing-20261117` | `0bfb6c26f7f9` | 2026-11-17 | Thierry Naud | contenue_dans_integration | docs: retain bounded output pairing evidence and S24 validation limits |
| `transfer/sentinel-cycle10-validation-20261117` | `a0c82be42314` | 2026-11-17 | Thierry Naud | histoires_disjointes | delivery: cycle10 bounded inference and pairing validation evidence; N |
| `fix/sentinel-ai-revocation-barrier-20261118` | `4e444a59a769` | 2026-11-18 | Thierry Naud | contenue_dans_integration | Document C11 qualification, S24 procedures and transversal findings [s |
| `integration/sentinel-v1-commercial-20261118` | `113862500b95` | 2026-11-18 | Thierry Naud | contenue_dans_integration | Record Android runtime advisory coverage and its limits [skip ci] |
| `cursor/v1-call-screening-audit-536d` | `5a6f7014bdb1` | 2026-11-18 | Cursor Agent | contient_integration | Record the screening warm-up commit SHA [skip ci] |
| `cursor/v1-play-compliance-20261118-c39e` | `29a207f07d6d` | 2026-11-18 | Cursor Agent | contient_integration | docs(v1): record the adversarial Play audit and correct Data safety [s |
| `cursor/v1-p0-privacy-catalog-c39e` | `6de5a272e030` | 2026-11-18 | Cursor Agent | contient_integration | docs(v1): refresh the copyable Play report and align stale drafts [ski |
| `cursor/v1-phone-core-full-audit-20261118` | `e77337d67727` | 2026-11-19 | Cursor Agent | contient_integration | [skip ci] Audit: distinguer JVM 100% et PASS physique appel/SMS/MMS |
| `fix/v1-phone-core-p1-20261119` | `f036b2b79372` | 2026-11-19 | Cursor Agent | contient_integration | fix: apply UnknownCallerPolicy in call screening after fail-open [skip |

## Signaux

- Divergentes vis-à-vis de `main` : **747**.
- Obsolètes selon la règle ci-dessus : **864**.
- Orphelines selon la règle ci-dessus : **805** (inclut des histoires disjointes sans PR).
- Les branches `automation/cisa-kev-refresh-*` et `automation/vulnerability-watch-*` sont des pointes d’automation. Leur contenu n’a pas été extrait.
- Non touchées par cet audit : branches des PR #1767, #1768, #1769, #1771, et toute branche C9/C10/C11. Présentes au remote : `transfer/sentinel-cycle9-validation-artifacts-20261117`, `transfer/sentinel-cycle10-validation-20261117`. Non extraites.

## PR ouvertes

| PR | Brouillon | Base | Tête | SHA | Auteur | Mise à jour | Titre | CI |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [#1773](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1773) | non | `main` | `dependabot/gradle/native-android-app/gradle-minor-patch-657b60946c` | `e55916dc1886` | dependabot[bot] | 2026-11-25 | chore(deps): bump the gradle-minor-patch group across 1 directory with 8 updates | non consultée |
| [#1772](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1772) | oui | `integration/sentinel-v1-commercial-20261118` | `fix/v1-phone-core-p1-20261119` | `f036b2b79372` | teetee971 | 2026-11-19 | Phone Core V1 : filtrage #1767, écran verrouillé, notification refusée, inconnus | non consultée |
| [#1771](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1771) | oui | `cursor/v1-p0-privacy-catalog-c39e` | `cursor/v1-phone-core-full-audit-20261118` | `e77337d67727` | teetee971 | 2026-11-19 | Audit Phone Core V1 : NO-GO, filtrage, manifeste et pistes | non consultée |
| [#1769](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1769) | oui | `cursor/v1-play-compliance-20261118-c39e` | `cursor/v1-p0-privacy-catalog-c39e` | `6de5a272e030` | teetee971 | 2026-11-18 | Corriger les téléchargements de catalogues V1 sans inventer l’URL de confidentialité | non consultée |
| [#1768](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1768) | oui | `integration/sentinel-v1-commercial-20261118` | `cursor/v1-play-compliance-20261118-c39e` | `29a207f07d6d` | teetee971 | 2026-11-18 | Brouillons Play du Phone Core V1 : confidentialité, permissions et Data safety | non consultée |
| [#1767](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1767) | oui | `integration/sentinel-v1-commercial-20261118` | `cursor/v1-call-screening-audit-536d` | `5a6f7014bdb1` | teetee971 | 2026-11-18 | Audit filtrage d’appels V1 : identité E.164, journal et permission contacts | non consultée |
| [#1766](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1766) | non | `codex/p02-consolidation-20261114` | `fix/p02-expiry-fixture-20261114` | `2c3e6cdf5ae0` | teetee971 | 2026-11-14 | Fix/p02 expiry fixture 20261114 | non consultée |
| [#1765](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1765) | oui | `main` | `codex/local-ai-lab-20261113` | `639bc152eec1` | teetee971 | 2026-11-13 | research: add Sentinel local AI benchmark lab | non consultée |
| [#1764](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1764) | oui | `main` | `codex/stic-v1.1-20261113` | `fbed1064b696` | teetee971 | 2026-11-13 | STIC v1.1 : enveloppe cryptographique vérifiée | non consultée |
| [#1763](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1763) | oui | `main` | `docs/roadmap-status-20261112` | `5cd3a433f052` | teetee971 | 2026-11-13 | docs: actualiser la roadmap publique au 12 novembre 2026 | non consultée |
| [#1762](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1762) | non | `main` | `fix/talkie-shared-room` | `f768fe6451ad` | teetee971 | 2026-11-10 | Partager la salle talkie entre deux téléphones | non consultée |
| [#1761](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1761) | non | `main` | `dependabot/github_actions/actions-minor-patch-2aeb733786` | `c440e1e89882` | dependabot[bot] | 2026-11-25 | chore(deps): bump the actions-minor-patch group across 1 directory with 7 updates | non consultée |
| [#1760](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1760) | oui | `fix/roadmap-finalization` | `fix/geointel-multisource` | `30948810f50a` | teetee971 | 2026-11-08 | GeoIntel multisource : relais borné, sources traçables et qualification locale | non consultée |
| [#1759](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1759) | oui | `qualification/google-play-readiness` | `fix/roadmap-finalization` | `0e280e606854` | teetee971 | 2026-11-08 | Integrate bounded Doctor cleaner and isolated lab; preserve Play release gates | non consultée |
| [#1758](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1758) | oui | `fix/disposable-instrumentation-guard` | `qualification/google-play-readiness` | `8169dc50a333` | teetee971 | 2026-11-07 | Prepare Play candidate 1.0.10 with account lifecycle gate and verified qualification | non consultée |
| [#1757](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1757) | oui | `codex/sentinel-audit-20261105` | `fix/disposable-instrumentation-guard` | `6a90af97a22f` | teetee971 | 2026-11-08 | Isolate reputation test preferences and guard destructive setup before fixtures | non consultée |
| [#1756](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1756) | oui | `codex/sentinel-audit-20261105` | `fix/android-cleaner-doctor` | `d120cef62929` | teetee971 | 2026-11-07 | Android cleaner: protect MMS, add scan/select/confirm and verified reports | non consultée |
| [#1755](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1755) | oui | `codex/sentinel-audit-20261105` | `fix/mms-image-compression-v23` | `1d87e205f125` | teetee971 | 2026-11-08 | MMS JPEG v23 : validation entropique stricte, budgets et annulation avant transport | non consultée |
| [#1754](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1754) | oui | `fix/mms-received-thumbnails-20261105` | `codex/sentinel-audit-20261105` | `06076b8bc7e0` | teetee971 | 2026-11-08 | fix(mms): qualify thumbnails and enforce carrier size limits | non consultée |
| [#1753](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1753) | oui | `main` | `qualification/play-android-20261104` | `fed548502c4c` | teetee971 | 2026-11-04 | WIP Play qualification: fail-closed connected-services presentation policy | non consultée |
| [#1752](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1752) | oui | `main` | `plan/verified-email-connected-services-20261104` | `3ea6a81a15ee` | teetee971 | 2026-11-04 | Plan: verified email for connected services, preserve guest local core | non consultée |
| [#1750](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1750) | oui | `main` | `feat/security-lab-apk-discovery-20261030` | `ba1696b07849` | teetee971 | 2026-10-31 | WIP Security Lab Pro: APK discovery foundations | non consultée |
| [#1748](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1748) | oui | `work/phone-core-v1-20261029` | `fix/d1-phone-messages-conformity-20261029` | `454c9c1cf4c1` | teetee971 | 2026-10-30 | Sentinel D.1 : communications, contacts locaux et preuves de requalification [skip ci] | non consultée |
| [#1747](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1747) | oui | `qualification/phone-core-v1-20261029` | `work/phone-core-v1-20261029` | `92d1c916ebbd` | teetee971 | 2026-10-29 | Phone Core V1 : qualification technique, dossier Play et livraison traçable | non consultée |
| [#1745](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1745) | non | `fix/recording-vault-v2-aad-20261028` | `fix/recording-vault-v2-migration-hardening-20261028` | `c852edbfe6f3` | teetee971 | 2026-10-29 | Recording Vault : migration V1 vers V2 durable et reprise sans repli | non consultée |
| [#1744](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1744) | oui | `feature/sentinel-recording-vault-r1-20261028` | `feature/sentinel-recording-vault-r3-persistence-20261028` | `aeadbbcf70fa` | teetee971 | 2026-10-28 | Recording Vault R3 — persistance durcie, protocole Android et preuves de qualification | non consultée |
| [#1741](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1741) | oui | `main` | `integration/sentinel-cve-v5-20261025` | `d51e36403c6a` | teetee971 | 2026-10-25 | CVE V5: qualification manuelle et pipeline durable isolé | non consultée |
| [#1740](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1740) | oui | `feature/sentinel-offline-mesh-foundation-20261024` | `fix/phone-core-concurrency-activity-20261024` | `e121aefad08c` | teetee971 | 2026-10-24 | fix(phone-core): guard async call evidence and activity launches | non consultée |
| [#1739](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1739) | oui | `main` | `feature/sentinel-offline-mesh-foundation-20261024` | `cf892d5b0c8f` | teetee971 | 2026-10-24 | feat(android): build reliable offline mesh BLE transport foundation | non consultée |
| [#1738](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1738) | non | `main` | `fix/d1-auth0-voice-revocation-20261023` | `6ca71a3a7afb` | teetee971 | 2026-10-24 | Fix/d1 auth0 voice revocation 20261023 | non consultée |
| [#1737](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1737) | non | `integration/sentinel-d1-3d-20261022` | `fix/d1-apk-process-guard-20261023` | `c7db265a03da` | teetee971 | 2026-10-23 | fix(android): secure APK inspection process locking | non consultée |
| [#1736](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1736) | oui | `main` | `integration/sentinel-d1-3d-20261022` | `1b21e29d4df7` | teetee971 | 2026-10-23 | [DRAFT] D1 3D Security Lab and APK inspection validation | non consultée |
| [#1735](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1735) | oui | `fix/mms-outgoing-journal-replay-guard-20261022` | `fix/mms-journal-instance-atomicity-20261022` | `416cec507d28` | teetee971 | 2026-10-22 | fix(android): atomic MMS journal claims across instances | non consultée |
| [#1734](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1734) | oui | `fix/mms-provider-role-boundary-20261022` | `fix/mms-outgoing-journal-replay-guard-20261022` | `111a618dcbbc` | teetee971 | 2026-10-22 | fix(android): block outgoing MMS journal token replay | non consultée |
| [#1733](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1733) | oui | `main` | `fix/mms-provider-role-boundary-20261022` | `b4e239b73699` | teetee971 | 2026-10-22 | fix(android): recheck SMS role at MMS provider mutation boundaries | non consultée |
| [#1732](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1732) | oui | `main` | `test/mms-role-revocation-transaction-20261022` | `f6b5517558e1` | teetee971 | 2026-10-22 | test(android): MMS compensation when SMS role becomes unavailable | non consultée |
| [#1731](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1731) | oui | `main` | `fix/android-optional-hardware-play-20261022` | `68012b4f7ff5` | teetee971 | 2026-10-22 | fix(android): explicitly mark optional hardware for Play device filtering | non consultée |
| [#1730](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1730) | oui | `main` | `fix/gradle10-groovy-chatgpt-20261022` | `c4d0eb532e0e` | teetee971 | 2026-10-22 | fix(android): remove Gradle 10 Groovy assignment deprecations | non consultée |
| [#1729](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1729) | oui | `main` | `ops/watchdog-hourly-cost-control-20261022` | `a6704557c054` | teetee971 | 2026-10-22 | chore(ci): reduce Sentinel watchdog from 5 minutes to hourly | non consultée |
| [#1728](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1728) | non | `main` | `fix/android-release-existing-signing-secrets-20261022` | `b67f8dbaf709` | teetee971 | 2026-10-22 | fix(android-release): use existing signing secret names | non consultée |
| [#1727](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1727) | oui | `main` | `audit/play-store-readiness-20261022` | `bba64e13d7fe` | teetee971 | 2026-10-22 | test(play): précontrôle local de configuration Google Play sans CI payante | non consultée |
| [#1726](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1726) | oui | `main` | `fix/security-loop-step-diagnostics-20261022` | `8edd5e6260e6` | teetee971 | 2026-10-22 | ci: rendre les échecs du contrôle sécurité horaire diagnostiquables | non consultée |
| [#1725](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1725) | non | `main` | `ops/hourly-autocontrol-20261022` | `b9f4952f581c` | teetee971 | 2026-10-22 | ci: exécuter l'autocontrôle Sentinel toutes les heures | non consultée |
| [#1724](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1724) | oui | `feature/d1-global-audit-20261020` | `audit/room-schema-evidence-20261022` | `cf06f1de57a5` | teetee971 | 2026-10-22 | test(room): vérifier les exports Room versionnés sans ADB | non consultée |
| [#1722](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1722) | non | `main` | `dependabot/npm_and_yarn/vite-8.3.3` | `7838601cbec2` | dependabot[bot] | 2026-10-21 | chore(deps-dev): bump vite from 8.3.1 to 8.3.3 | non consultée |
| [#1721](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1721) | oui | `main` | `feature/d1-global-audit-20261020` | `ca0b944fc79f` | teetee971 | 2026-10-29 | Android D.1 global — matrice de couverture et migration UX | non consultée |
| [#1720](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1720) | oui | `main` | `docs/network-center-beyond-wifiman-20261020` | `5220c48f19ef` | teetee971 | 2026-10-20 | Network Intelligence: roadmap défensive au-delà de WiFiman | non consultée |
| [#1719](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1719) | oui | `main` | `fix/network-bonded-wearables-20261020` | `5624aa4f618d` | teetee971 | 2026-10-20 | Android Network Center : inventaire Bluetooth associé distinct du scan BLE | non consultée |
| [#1718](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1718) | oui | `main` | `fix/d1-home-hero-priority-20261020` | `383a2afd1c73` | teetee971 | 2026-10-20 | Android: placer le tableau de bord D.1 en tête de l'accueil | non consultée |
| [#1717](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1717) | oui | `main` | `ci/portable-android-validation-20261020` | `e2fad4923293` | teetee971 | 2026-10-20 | ci(android): add independent x86-64 host validation without bypassing gates | non consultée |
| [#1716](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1716) | non | `main` | `fix/bluetooth-scan-terminal-callback-20261019` | `b543f517163c` | teetee971 | 2026-10-20 | fix(bluetooth): finish scan lifecycle on preflight and startup failures | non consultée |
| [#1714](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1714) | non | `main` | `fix/wearable-identity-source-binding-20261019` | `eed531809212` | teetee971 | 2026-10-20 | fix(wearable): reject mismatched identity provenance | non consultée |
| [#1712](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1712) | non | `main` | `fix/wearable-exact-capability-proof-20261019` | `ff57bb22baab` | teetee971 | 2026-10-20 | fix(wearable): enforce exact cryptographic capability snapshot | non consultée |
| [#1711](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1711) | non | `main` | `fix/wearable-unavailable-capabilities-20261019` | `80012c01eb0a` | teetee971 | 2026-10-20 | fix(wearable): unavailable adapter cannot expose usable capabilities | non consultée |
| [#1709](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1709) | non | `main` | `fix/watchdog-hourly-cadence-20261019` | `3bf941f47930` | teetee971 | 2026-10-20 | fix(ci): align Sentinel Watchdog schedule with hourly maintenance | non consultée |
| [#1707](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1707) | non | `feat/global-telecom-regulator-registry` | `feat/global-telecom-intelligence-d1-phone-core` | `e8ab9266822c` | teetee971 | 2026-10-19 | feat(telecom): global intelligence foundation and D.1 Android surface | non consultée |
| [#1706](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1706) | oui | `fix/phone-screening-p0-enforcement-source-guard` | `feat/global-telecom-regulator-registry` | `74019b2ccbd1` | teetee971 | 2026-10-18 | docs(telecom): start global regulator registry without automatic call blocking | non consultée |
| [#1705](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1705) | oui | `feat/sentinel-d1-phase4-visual-fidelity` | `fix/phone-screening-p0-enforcement-source-guard` | `f80a4a1146a4` | teetee971 | 2026-10-18 | fix(phone): guard Telecom enforcement with fail-open decision provenance | non consultée |
| [#1703](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1703) | non | `feat/sentinel-d1-phase3-official-visuals` | `feat/sentinel-d1-phase4-visual-fidelity` | `0944a1f46f05` | teetee971 | 2026-10-18 | feat(android): phase4 visual fidelity for official D.1 references | non consultée |
| [#1702](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1702) | non | `feat/sentinel-d1-3d-total-redesign` | `feat/sentinel-d1-phase3-official-visuals` | `0242647fa6a1` | teetee971 | 2026-10-18 | feat(android): reconstruct official Sentinel D.1 phase 3 visuals | non consultée |
| [#1701](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1701) | non | `feat/integration-consolidated-20261017` | `feat/sentinel-d1-3d-total-redesign` | `6be887adaa8c` | teetee971 | 2026-10-18 | feat(android): deliver Sentinel D.1 3D native redesign | non consultée |
| [#1699](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1699) | oui | `main` | `feat/integration-consolidated-20261017` | `55a60764f91b` | teetee971 | 2026-10-18 | feat(integration): consolidate product truth with cross-module production wiring | non consultée |
| [#1697](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1697) | oui | `feat/integration-foundation-20261017` | `feat/integration-android-wiring-20261017` | `35d0bd3047dd` | teetee971 | 2026-10-17 | feat(intelligence): connect Android evidence producers to integration truth | non consultée |
| [#1696](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1696) | oui | `main` | `feat/integration-foundation-20261017` | `61e0ba934e15` | teetee971 | 2026-10-17 | feat(integration): establish product integration truth foundation | non consultée |
| [#1695](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1695) | oui | `main` | `feat/intelligence-core-cross-module-integration` | `1af12475dbfd` | teetee971 | 2026-10-17 | feat(intelligence): integrate cross-module phone and network evidence | non consultée |
| [#1694](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1694) | oui | `main` | `feat/community-report-intelligence-wiring` | `adbc26a6d6eb` | teetee971 | 2026-10-17 | feat(intelligence): retain community report context | non consultée |
| [#1693](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1693) | oui | `main` | `feat/mms-intelligence-wiring` | `c43e23bc71c8` | teetee971 | 2026-10-17 | feat(intelligence): publish incoming MMS transport evidence | non consultée |
| [#1692](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1692) | oui | `main` | `feat/contact-intelligence-wiring` | `2120ddf20be5` | teetee971 | 2026-10-17 | feat(contacts): publish caller identity evidence | non consultée |
| [#1691](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1691) | oui | `main` | `feat/sms-analysis-intelligence-wiring` | `87341f55504d` | teetee971 | 2026-10-17 | feat(sms): publish incoming analysis evidence | non consultée |
| [#1690](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1690) | oui | `main` | `feat/reputation-intelligence-wiring` | `2fb1cdfd01ec` | teetee971 | 2026-10-17 | feat(intelligence): publish consented reputation evidence | non consultée |
| [#1689](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1689) | oui | `main` | `fix/vpn-runtime-observation-wiring` | `8f95a3714d6f` | teetee971 | 2026-10-17 | feat(vpn): share bounded runtime state with diagnostics | non consultée |
| [#1688](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1688) | oui | `main` | `fix/network-timeline-integration` | `086ce9f644b2` | teetee971 | 2026-10-17 | feat(network): connect local observations to bounded timeline | non consultée |
| [#1687](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1687) | oui | `main` | `fix/phone-core-ci-hardening` | `f0da53e27b87` | teetee971 | 2026-10-17 | fix(ci): replay bounded Phone Core qualification gates | non consultée |
| [#1686](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1686) | oui | `main` | `fix/network-behavior-destination-bound-20261016-2112` | `0cc4616af3a6` | teetee971 | 2026-10-17 | Fix NetworkBehaviorBaseline destination length on current main | non consultée |
| [#1682](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1682) | non | `main` | `fix/build-native-adb-install-timeout-main-20261016` | `2b855662ed92` | teetee971 | 2026-10-17 | fix(ci): replay bounded Build Native adb install on current main | non consultée |
| [#1679](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1679) | non | `main` | `fix/network-behavior-destination-bound-main-20261016` | `edd199dd2a16` | teetee971 | 2026-10-17 | fix(network): reject overlong behavior destinations on current main | non consultée |
| [#1678](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1678) | non | `main` | `fix/phone-core-adb-recovery-main-20261016` | `7d1eeafef19f` | teetee971 | 2026-10-17 | fix(ci): replay bounded ADB evidence recovery on current main | non consultée |
| [#1671](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1671) | non | `main` | `fix/call-screening-cold-start-warmup-20261016` | `ee47d3f89cca` | teetee971 | 2026-10-16 | fix(phone): close cold-start call screening gap | non consultée |
| [#1657](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1657) | oui | `main` | `feat/incall-intelligence-ui` | `652c104d7e9d` | teetee971 | 2026-10-14 | feat(phone-core): surface Intelligence in in-call UI | non consultée |
| [#1655](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1655) | oui | `main` | `feat/sms-local-contact-identity-20261013` | `41bcb7306e79` | teetee971 | 2026-10-13 | feat(core): resolve local contact identity in SMS conversations | non consultée |
| [#1654](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1654) | non | `main` | `fix/ui-product-truth-polish-20261013` | `fcae229385b9` | teetee971 | 2026-10-15 | fix(ui): clarify product truth and mobile privacy surfaces | non consultée |
| [#1653](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1653) | oui | `main` | `fix/incall-dialpad-visible-20261013` | `07b83122a47e` | teetee971 | 2026-10-13 | fix(phone): keep in-call DTMF keypad immediately visible | non consultée |
| [#1646](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1646) | oui | `main` | `fix/ci-bound-adb-reconnect` | `7bfc50ac672f` | teetee971 | 2026-10-17 | fix(ci): bound ADB reconnect recovery | non consultée |
| [#1644](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1644) | oui | `main` | `fix/api37-limited-must-fail-merge-gate` | `2ec7cb554a8b` | teetee971 | 2026-10-13 | fix(phone-core): keep API37 limited incoming-call evidence fail-closed | non consultée |
| [#1643](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1643) | oui | `main` | `fix/phone-core-runtime-script-timeouts` | `1b943ee8b678` | teetee971 | 2026-10-13 | fix(ci): bound Phone Core runtime scripts | non consultée |
| [#1642](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1642) | oui | `main` | `fix/phone-core-stable-effective-permission-probe` | `66b6ad179cd9` | teetee971 | 2026-10-13 | fix(phone-core): use stable effective-permission fallback | non consultée |
| [#1641](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1641) | oui | `main` | `fix/phone-core-evidence-adb-command-timeouts` | `551fd00221d7` | teetee971 | 2026-10-12 | fix(ci): bound Phone Core ADB evidence commands | non consultée |
| [#1637](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1637) | oui | `main` | `fix/sms-submission-gate-observable` | `74e2617fc136` | teetee971 | 2026-10-13 | fix(sms): make submission gate observable across recreation | non consultée |
| [#1635](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1635) | non | `main` | `fix/sms-ingress-dedup` | `9fc86b83653b` | teetee971 | 2026-10-13 | fix(sms): deduplicate inbound delivery replays | non consultée |
| [#1633](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1633) | non | `main` | `automation/arcep-numbering-refresh` | `d2f905c6b4cb` | github-actions[bot] | 2026-10-12 | Phone intelligence: refresh ARCEP numbering data | non consultée |
| [#1627](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1627) | non | `main` | `dependabot/github_actions/actions/download-artifact-8.0.2` | `583e9a50deba` | dependabot[bot] | 2026-10-16 | chore(deps): bump actions/download-artifact from 7.0.0 to 8.0.2 | non consultée |
| [#1614](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1614) | oui | `main` | `fix/phone-core-durable-sms-main-20261010` | `58e5fdc0e7fe` | teetee971 | 2026-10-10 | fix(phone-core): durable SMS ingress on current main | non consultée |
| [#1612](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1612) | oui | `main` | `codex/ptt-livekit-core-20261009` | `5d9fb328cacf` | teetee971 | 2026-10-09 | feat(ptt): add LiveKit push-to-talk core | non consultée |
| [#1610](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1610) | oui | `main` | `codex/phone-core-qualification-20261008` | `73a8e839e70f` | teetee971 | 2026-10-10 | fix(phone-core): durcir l’ingress SMS et le portail de qualification | non consultée |
| [#1609](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1609) | oui | `main` | `hardening/physical-session-failclosed` | `8ac907eafac6` | teetee971 | 2026-10-09 | fix(phone-core): harden physical qualification truth | non consultée |
| [#1604](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1604) | oui | `main` | `audit/emulator-gate-truth-20261007` | `444d80e59ba1` | teetee971 | 2026-10-07 | test(phone-core): harden SEND_SMS revocation qualification truth | non consultée |
| [#1600](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1600) | non | `main` | `fix/api37-incoming-answer-sync` | `7dfefcf2f89e` | teetee971 | 2026-10-10 | fix(phone-core): unify API 36 revocation proof and API 37 answer qualification | non consultée |
| [#1597](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1597) | non | `main` | `fix/production-gate-api-resilience` | `17d28db2b942` | teetee971 | 2026-10-06 | fix(ci): tolerate transient GitHub API failures in production gate | non consultée |
| [#1596](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1596) | non | `main` | `chore/android-debt-pass-1` | `c147ca7366c6` | teetee971 | 2026-10-06 | refactor(android): make duplicate hash grouping null-safe | non consultée |
| [#1594](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1594) | oui | `main` | `fix/android-emulator-truth-gate-20261006` | `76e3610149ac` | teetee971 | 2026-10-06 | test(android): qualify real navigation, response latency and crash evidence | non consultée |
| [#1593](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1593) | oui | `main` | `fix/android-upgrade-safe-apk-20261006` | `d9a1c45d3059` | teetee971 | 2026-10-06 | ci(android): prove signed in-place APK upgrades | non consultée |
| [#1592](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1592) | oui | `main` | `fix/phone-core-durable-sms-ingress-20261006` | `7877d1194ebd` | teetee971 | 2026-10-06 | fix(phone-core): make inbound SMS delivery durable before deferral | non consultée |
| [#1586](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1586) | non | `main` | `dependabot/npm_and_yarn/npm-minor-patch-210587e60a` | `7d42aa84c094` | dependabot[bot] | 2026-11-25 | chore(deps-dev): bump vite from 8.3.1 to 8.3.4 in the npm-minor-patch group across 1 direc | non consultée |
| [#1574](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1574) | non | `fix/phone-number-region-safe-20261004` | `fix/favorites-region-identity-20261004` | `b71c3d67023d` | teetee971 | 2026-10-04 | fix(phone-core): dedupe favorites with observed phone region | non consultée |
| [#1573](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1573) | non | `fix/phone-number-region-safe-20261004` | `fix/quick-reply-default-sim-20261004` | `d82fad7873ca` | teetee971 | 2026-10-04 | fix(phone-core): use active Android default SIM for quick replies | non consultée |
| [#1570](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1570) | non | `main` | `automation/vulnerability-watch-refresh` | `3a148aa4acc5` | github-actions[bot] | 2026-10-16 | Threat intel: refresh vulnerability watch state | non consultée |
| [#1559](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1559) | non | `main` | `fix/phone-number-region-safe-20261004` | `ec956c2d76f0` | teetee971 | 2026-10-04 | WIP(phone-core): make phone-number canonicalization region-safe | non consultée |

## PR fermées non fusionnées (page récente)

| PR | Base | Tête | Mise à jour | Titre |
| --- | --- | --- | --- | --- |
| [#1751](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1751) | `main` | `dependabot/gradle/native-android-app/gradle-minor-patch-dd77283f18` | 2026-11-23 | chore(deps): bump the gradle-minor-patch group across 1 directory with 7 updates |
| [#1742](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1742) | `main` | `dependabot/github_actions/actions-minor-patch-b56d4bd17c` | 2026-11-09 | chore(deps): bump the actions-minor-patch group across 1 directory with 6 updates |
| [#1746](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1746) | `main` | `dependabot/gradle/native-android-app/gradle-minor-patch-074c673dde` | 2026-11-03 | chore(deps): bump the gradle-minor-patch group across 1 directory with 6 updates |
| [#1743](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1743) | `main` | `dependabot/gradle/native-android-app/gradle-minor-patch-1262020273` | 2026-10-29 | chore(deps): bump the gradle-minor-patch group across 1 directory with 5 updates |
| [#1723](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1723) | `main` | `dependabot/gradle/native-android-app/gradle-minor-patch-20f9c8eb66` | 2026-10-26 | chore(deps): bump the gradle-minor-patch group across 1 directory with 4 updates |
| [#1708](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1708) | `main` | `dependabot/github_actions/actions-minor-patch-09117aa1a9` | 2026-10-26 | chore(deps): bump the actions-minor-patch group across 1 directory with 5 updates |
| [#1628](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1628) | `main` | `dependabot/gradle/native-android-app/gradle-minor-patch-e49bdfb56c` | 2026-10-22 | chore(deps): bump the gradle-minor-patch group across 1 directory with 3 updates |
| [#1715](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1715) | `main` | `fix/bluetooth-no-false-randomized-address-20261019` | 2026-10-20 | fix(bluetooth): remove false randomized-address claim for BLE devices |
| [#1626](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1626) | `main` | `dependabot/github_actions/actions-minor-patch-dc4f8b6e13` | 2026-10-19 | chore(deps): bump the actions-minor-patch group across 1 directory with 4 updates |
| [#1698](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1698) | `feat/integration-android-wiring-20261017` | `feat/intelligence-core-cross-module-integration` | 2026-10-17 | feat(integration): apply cross-module production wiring to integration stack |
| [#1684](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1684) | `main` | `sync/pr1675-main-20261016` | 2026-10-16 | Sync/pr1675 main 20261016 |
| [#1638](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1638) | `main` | `fix/ci-bound-build-native-adb-install` | 2026-10-16 | chore(ci): bound Build Native APK install |
| [#1622](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1622) | `main` | `fix/network-numeric-only-local-probes-20261010` | 2026-10-16 | fix(network): reject non-numeric LAN targets and bound local probes |
| [#1648](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1648) | `main` | `fix/network-numeric-probes-main-20261013-1006` | 2026-10-16 | fix(network): reject non-numeric LAN targets and bound local probes (current main) |
| [#1625](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1625) | `main` | `fix/android-id-lifecycle-doc-20261012` | 2026-10-16 | docs(android): clarify ANDROID_ID scope for device binding |
| [#1611](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1611) | `main` | `fix/network-flow-metadata-bounds-main-20261008` | 2026-10-16 | fix(network): saturate flow totals and reject overlong endpoints |
| [#1660](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1660) | `main` | `fix/network-behavior-endpoint-bounds-20261014-1010` | 2026-10-16 | fix(network): reject overlong behavior destinations without aliasing |
| [#1659](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1659) | `main` | `fix/network-flow-bounds-main-20261013-1507` | 2026-10-16 | fix(network): saturate flow totals and reject overlong endpoints (rebased on main) |
| [#1645](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1645) | `main` | `fix/community-report-country-contract-20261013` | 2026-10-16 | fix(community): validate recipient country before report egress |
| [#1624](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1624) | `main` | `fix/email-header-folded-length-20261011` | 2026-10-16 | fix(android): bound unfolded email header length |
| [#1636](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1636) | `main` | `fix/network-flow-bounds-main-20261012-1948` | 2026-10-16 | fix(network): harden flow aggregation bounds on current main |
| [#1640](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1640) | `main` | `fix/phone-core-evidence-adb-recovery` | 2026-10-16 | fix(ci): recover transient ADB offline before Phone Core evidence |
| [#1650](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1650) | `main` | `fix/backup-excludes-remote-reputation-consent-20261013` | 2026-10-16 | fix(privacy): exclude caller reputation consent from preference backups |
| [#1667](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1667) | `main` | `fix/phone-decision-journal-overflow-main-20261015` | 2026-10-16 | fix(phone): make PhoneDecisionJournal future-skew check overflow-safe |
| [#1673](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1673) | `main` | `fix/phone-decision-journal-overflow-main-20261016` | 2026-10-16 | fix(phone): replay decision journal overflow fix on current main |
| [#1661](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1661) | `main` | `fix/phone-decision-journal-overflow-20261014-1710` | 2026-10-15 | fix(android): avoid future-timestamp overflow in PhoneDecisionJournal [superseded] |
| [#1639](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1639) | `main` | `fix/sms-submission-state-recreation` | 2026-10-12 | fix(sms): keep submission UI state across recreation |
| [#1632](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1632) | `main` | `fix/network-flow-bounds-main-20261012-0710` | 2026-10-12 | fix(network): saturate flow totals and reject overlong endpoints (current main) |
| [#1621](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1621) | `main` | `dependabot/pip/backend/voice-api/pip-2118ef368f` | 2026-10-12 | chore(deps): bump pyjwt from 2.10.1 to 2.15.0 in /backend/voice-api in the pip group acros |
| [#1458](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1458) | `main` | `dependabot/github_actions/actions/download-artifact-8.0.1` | 2026-10-12 | chore(deps): bump actions/download-artifact from 7.0.0 to 8.0.2 |
| [#1598](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1598) | `main` | `fix/phone-core-revocation-proof-hardening` | 2026-10-06 | fix(phone-core): harden SEND_SMS revocation qualification proof |
| [#1587](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1587) | `main` | `ci/phone-core-emulation-gate-rebased-20261005` | 2026-10-05 | ci(phone-core): rebase API 24-37 emulator qualification onto current main |
| [#1588](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1588) | `main` | `recovery/phone-core-emulator-phase1-20261005` | 2026-10-05 | test(phone-core): recover emulator proofs without replacing CI gates |
| [#1583](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1583) | `main` | `ci/phone-core-emulation-gate-api37-20261004` | 2026-10-05 | ci(phone-core): make API 24-37 emulator qualification the developer gate |
| [#1582](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1582) | `main` | `ci/phone-core-call-screening-emulator` | 2026-10-05 | CI Phone Core: prouver Call Screening sur Android 16 |
| [#1575](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1575) | `fix/phone-number-region-safe-20261004` | `fix/phone-core-physical-gate-20261004` | 2026-10-05 | ci(phone-core): move developer validation to Android emulator matrix |
| [#1576](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1576) | `main` | `automation/emulator-first-qualification` | 2026-10-04 | CI: make Android emulator the primary developer qualification gate |

## PR fusionnées dans la même page

| PR | Base | Fusion | Titre |
| --- | --- | --- | --- |
| [#1677](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1677) | `main` | 2026-10-17 | fix(community): validate recipient country before egress on current main |
| [#1676](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1676) | `main` | 2026-10-16 | fix(email): bound unfolded header length on current main |
| [#1681](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1681) | `main` | 2026-10-16 | fix(network): replay numeric-only bounded LAN probes on current main |
| [#1675](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1675) | `main` | 2026-10-16 | fix(network): replay flow aggregation bounds on current main |
| [#1685](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1685) | `main` | 2026-10-16 | fix(ci): scope production evidence to current pull request |
| [#1683](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1683) | `main` | 2026-10-16 | fix(ci): refresh API 36 hold recovery observation window |
| [#1674](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1674) | `main` | 2026-10-16 | fix(phone): make PhoneDecisionJournal future-skew check overflow-safe |
| [#1680](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1680) | `main` | 2026-10-16 | docs(android): clarify ANDROID_ID scope on current main |
| [#1672](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1672) | `main` | 2026-10-16 | fix(ci): prevent Android 10 AVD setup timeout |
| [#1668](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1668) | `main` | 2026-10-16 | fix(ci): make REA evidence fail closed |
| [#1669](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1669) | `main` | 2026-10-16 | refactor(ui): continue Sentinel D1 visual hardening |
| [#1670](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1670) | `codex/visual-wave1-finalization` | 2026-10-16 | fix(phone): retry post-response maintenance after scheduling failure |
| [#1665](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1665) | `main` | 2026-10-15 | poc(ci): add REA 6.4 independent Android evidence lane |
| [#1664](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1664) | `main` | 2026-10-15 | feat(intelligence): wire SMS delivery evidence into core |
| [#1663](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1663) | `main` | 2026-10-15 | test(phone): cover remaining pre-response blocking I/O primitives |
| [#1662](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1662) | `main` | 2026-10-15 | test(phone): harden transitive CallScreening pre-response purity |
| [#1658](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1658) | `main` | 2026-10-15 | feat(ui,phone): Sentinel visual wave 1 + CallScreening hardening |
| [#1647](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1647) | `main` | 2026-10-15 | feat(core): bridge caller reputation into SMS risk context |
| [#1649](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1649) | `main` | 2026-10-15 | fix(privacy): purge cached reputation on consent revocation |
| [#1656](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1656) | `main` | 2026-10-15 | feat(intelligence): implement Sentinel Intelligence Core v1 |
| [#1652](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1652) | `main` | 2026-10-15 | release(android): qualify unified reputation privacy stack for APK |
| [#1651](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1651) | `main` | 2026-10-15 | fix(privacy): purge reputation cache when remote mode is disabled |
| [#1634](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1634) | `main` | 2026-10-13 | fix(sms): prevent concurrent composer submissions |
| [#1629](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1629) | `main` | 2026-10-12 | fix(android): pin qualified Auth0 dependency |
| [#1630](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1630) | `main` | 2026-10-12 | fix(voice): bind sessions to authenticated device rooms |
| [#1631](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1631) | `main` | 2026-10-12 | fix(phone-core): wait for ADB after sleep transition |
| [#1623](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1623) | `main` | 2026-10-12 | feat: harden Android release flow and public disclosures |
| [#1620](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1620) | `main` | 2026-10-10 | feat(voice): add fail-closed LiveKit session service |
| [#1618](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1618) | `main` | 2026-10-10 | docs(voice): add production readiness runbook |
| [#1617](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1617) | `main` | 2026-10-10 | fix(ci): make API 24 instrumentation watchdog deterministic |
| [#1616](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1616) | `main` | 2026-10-10 | docs(voice): define fail-closed service session contract |
| [#1615](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1615) | `main` | 2026-10-10 | feat(voice): add fail-closed client push-to-talk gate |
| [#1613](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1613) | `main` | 2026-10-10 | fix(ctu): accept official hyphen-delimited ranges |
| [#1608](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1608) | `fix/api37-incoming-answer-sync` | 2026-10-07 | test(phone-core): preserve AppOp fallback when pm revoke is unavailable |
| [#1607](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1607) | `fix/api37-incoming-answer-sync` | 2026-10-07 | test(ci): make emulator contract verify shared crash oracle |
| [#1606](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1606) | `fix/api37-incoming-answer-sync` | 2026-10-07 | test(ci): harden workflow crash oracles on advanced Phone Core branch |
| [#1605](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1605) | `fix/api37-incoming-answer-sync` | 2026-10-07 | test(phone-core): port gate-truth hardening onto advanced API37 branch |
| [#1602](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1602) | `fix/api37-incoming-answer-sync` | 2026-10-06 | integrate(phone-core): combine API 36 revocation proof with API 37 answer bridge |
| [#1595](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1595) | `main` | 2026-10-06 | fix(phone-core): qualify SEND_SMS denial correctly on Android 17 |
| [#1589](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1589) | `main` | 2026-10-06 | fix(phone-core): harden shadow qualification and runtime truth |
| [#1590](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1590) | `main` | 2026-10-05 | fix(phone-core): make first-run activation role-first |
| [#1585](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1585) | `main` | 2026-10-05 | CI Android: verrouiller la double provenance des APK |
| [#1584](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1584) | `main` | 2026-10-05 | CI Android: fiabiliser le portail émulateur API 24/36/37 |
| [#1581](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1581) | `main` | 2026-10-04 | Phone Core: aligner la vérité du filtrage API 24–28 |
| [#1580](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1580) | `main` | 2026-10-04 | CI Android: rendre l’émulation obligatoire avant merge |
| [#1579](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1579) | `main` | 2026-10-04 | CI Android: qualifier le minSdk API 24 sur émulateur |
| [#1578](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1578) | `main` | 2026-10-04 | Android CI: étendre les contrats Phone Core sur émulateur |
| [#1577](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1577) | `main` | 2026-10-04 | CI Android: ajouter le banc d’essai émulateur API 37 |
| [#1572](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1572) | `main` | 2026-10-04 | fix(site): reposition official Sentinel visual and simplify homepage |
| [#1571](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1571) | `main` | 2026-10-04 | feat(site): auto-sync public product truth from canonical registries |
| [#1569](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1569) | `main` | 2026-10-04 | refactor(android): centralize Sentinel public API origin contract |
| [#1561](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1561) | `main` | 2026-10-04 | fix(phone-core): align activation with version-aware call screening |
| [#1567](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1567) | `main` | 2026-10-04 | feat(commerce): add canonical monetization and proprietary-boundary gates |
| [#1560](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1560) | `main` | 2026-10-04 | fix(phone-core): model pre-Q call screening through default dialer |
| [#1555](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1555) | `main` | 2026-10-04 | fix(phone-core): keep contacts optional in readiness |
| [#1558](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1558) | `main` | 2026-10-04 | fix(android): make missed-call receiver system-only |
| [#1553](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1553) | `main` | 2026-10-04 | fix(call-screening): bound post-response telemetry queue |
| [#1549](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1549) | `main` | 2026-10-04 | test(phone-core): lock counter-free customer status resources |
| [#1548](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1548) | `main` | 2026-10-04 | fix(release): gate Android distribution on Play and legal compliance |
| [#1546](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1546) | `main` | 2026-10-04 | fix(phone-core): separate customer setup from certification UX |
| [#1547](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1547) | `main` | 2026-10-04 | fix(phone-core): harden Bluetooth runtime identity truth |
| [#1545](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1545) | `main` | 2026-10-04 | fix(phone-core): make Bluetooth permission truth fail closed |
| [#1541](https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1541) | `main` | 2026-10-04 | feat(phone-core): project validated incoming MMS into Telephony provider |

Les PR plus anciennes que cette page ne sont pas listées.

## Annexe — toutes les branches distantes

| Branche | SHA | Date | Auteur | vs main | vs integration | Sujet | PR ouverte |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `android-caller-identity-evidence-fusion` | `7d80fa710be9` | 2026-09-24 | teetee971 | divergente +2/-857 | histoires_disjointes | Phone Core: test caller identity trust boundaries | non |
| `android-covert-device-watch-current-main-20260923` | `c92a85829623` | 2026-09-23 | teetee971 | divergente +8/-894 | histoires_disjointes | Android: test covert Wi-Fi heuristics | non |
| `android/app-hygiene-hardened-75f233c` | `6659f74a0b7d` | 2026-09-29 | teetee971 | divergente +6/-670 | histoires_disjointes | feat(android): add truthful app hygiene foundation | non |
| `android/app-hygiene-hardened-de5e0b9e` | `03f760426948` | 2026-09-29 | teetee971 | divergente +6/-667 | histoires_disjointes | Android: replay hardened app hygiene | non |
| `android/app-hygiene-hardened-f60a0c11` | `9d9eab937844` | 2026-09-29 | teetee971 | divergente +6/-666 | histoires_disjointes | Android: replay hardened app hygiene | non |
| `android/app-hygiene-hardened-f832ecef` | `4fe3119bd99e` | 2026-09-29 | teetee971 | divergente +6/-668 | histoires_disjointes | App hygiene: replay SentinelAppUsagePolicyTest.kt | non |
| `android/bound-osint-cache-codec-20260922` | `41513d8ecf55` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Test bounded OSINT cache encoding | non |
| `android/cleanup-owned-hardened-20260929` | `092e09d4ac15` | 2026-09-29 | teetee971 | divergente +9/-671 | histoires_disjointes | cleanup: replay SentinelCleanupPlanTest.kt on current main | non |
| `android/cleanup-owned-hardened-main2-20260929` | `3a16a3ac0c7a` | 2026-09-29 | teetee971 | divergente +22/-670 | histoires_disjointes | test(android): mark fully verified cleanup as executed remov | non |
| `android/duplicate-detection-hardened-75f233c` | `d0bcc30f22d4` | 2026-09-29 | teetee971 | divergente +6/-670 | histoires_disjointes | feat(android): add evidence-based duplicate detection founda | non |
| `android/product-truth-2de3e53f` | `2de3e53fd798` | 2026-09-30 | teetee971 | contenue_dans_main +0/-648 | histoires_disjointes | Android UI: harden consistency and keep call controls reacha | non |
| `android/protection-mobile-design-foundation-20260926` | `7eb69202d8fe` | 2026-09-26 | teetee971 | divergente +1/-802 | histoires_disjointes | Android: establish truthful protection mobile hero | non |
| `android/protection-mobile-design-next-20260926` | `f43d1eb13ffa` | 2026-09-26 | teetee971 | divergente +2/-802 | histoires_disjointes | Android: refine phone risk result card | non |
| `android/pwned-password-bounded-response-eae3ec0` | `77061b75c35b` | 2026-09-27 | teetee971 | divergente +2/-780 | histoires_disjointes | Test bounded Pwned Passwords response handling | non |
| `android/system-doctor-app-hygiene-0ce9040` | `531faed7c10d` | 2026-09-27 | teetee971 | divergente +8/-776 | histoires_disjointes | Test stale evidence cannot drive uninstall offer | non |
| `android/system-doctor-app-hygiene-26a7c99` | `8a0ae1fe8899` | 2026-09-27 | teetee971 | divergente +6/-775 | histoires_disjointes | Replay app hygiene on current main | non |
| `android/system-doctor-app-hygiene-67da797` | `67da79730bfd` | 2026-09-27 | teetee971 | contenue_dans_main +0/-695 | histoires_disjointes | Security: add 2026 URL canonicalization threat gate (#1266) | non |
| `android/system-doctor-cleanup-policy-06b187a` | `f1adb8ff31ea` | 2026-09-27 | teetee971 | divergente +14/-777 | histoires_disjointes | Refuse cleanup target type changes before deletion | non |
| `android/system-doctor-cleanup-policy-0ce9040` | `55aaa8f0f7ca` | 2026-09-27 | teetee971 | divergente +12/-776 | histoires_disjointes | Test production cleanup path policy directly | non |
| `android/system-doctor-cleanup-policy-26a7c99` | `13dfba46f057` | 2026-09-27 | teetee971 | divergente +12/-775 | histoires_disjointes | Remove API 26 path usage from cleanup collector | non |
| `android/system-doctor-cleanup-policy-9fd7027` | `9fd70276fd48` | 2026-09-27 | teetee971 | contenue_dans_main +0/-696 | histoires_disjointes | Merge pull request #1264 from teetee971/site/d1-total-redesi | non |
| `android/system-doctor-dev-posture-361472a` | `85200a1a5267` | 2026-09-27 | teetee971 | divergente +3/-778 | histoires_disjointes | Include developer posture in System Doctor scan | non |
| `android/system-doctor-duplicate-policy-06b187a` | `63fd54d7eae9` | 2026-09-27 | teetee971 | divergente +4/-777 | histoires_disjointes | Test duplicate reclaimable byte accounting | non |
| `android/system-doctor-duplicate-policy-0ce9040` | `bba80cfb0311` | 2026-09-27 | teetee971 | divergente +6/-776 | histoires_disjointes | Test bounded duplicate hashing policy | non |
| `android/system-doctor-duplicate-policy-26a7c99` | `4cb9c5d2d3bc` | 2026-09-27 | teetee971 | divergente +8/-775 | histoires_disjointes | Test contradictory duplicate evidence rejection | non |
| `android/system-doctor-network-posture-06b187a` | `47b79072d0f0` | 2026-09-27 | teetee971 | divergente +3/-777 | histoires_disjointes | Include observable network posture in System Doctor scan | non |
| `android/system-doctor-vpn-correlation-26a7c99` | `71537cf8d2f0` | 2026-09-27 | teetee971 | divergente +2/-775 | histoires_disjointes | Test VPN-only signal does not create risk warning | non |
| `android/system-doctor-vpn-truth-0c2e720` | `1a4ae89d627b` | 2026-09-29 | teetee971 | divergente +3/-675 | histoires_disjointes | test(android): assert VPN-only finding is non-malware eviden | non |
| `android/system-doctor-vpn-truth-4acef4c` | `4acef4c82645` | 2026-09-27 | teetee971 | contenue_dans_main +0/-688 | histoires_disjointes | Phone Core: report ungranted Android roles without guessing  | non |
| `android/system-doctor-vpn-truth-7efe00c` | `7efe00ccb5f4` | 2026-09-29 | teetee971 | contenue_dans_main +0/-674 | histoires_disjointes | Phone Core: lock SMS send permissions to least privilege (#1 | non |
| `android/system-doctor-vpn-truth-ab99fdb` | `ab99fdbccd4b` | 2026-09-27 | teetee971 | contenue_dans_main +0/-683 | histoires_disjointes | Phone Core: make setup role actionability truthful (#1278) | non |
| `android/system-doctor-vpn-truth-main-20260929` | `1887cab14f28` | 2026-09-29 | teetee971 | divergente +2/-672 | histoires_disjointes | Test VPN-only System Doctor truth | non |
| `artifact/sentinel-android-test-55a60764` | `157855898a36` | 2026-10-18 | Thierry Naud | contient_main +294/-0 | histoires_disjointes | chore(release): add APK binary artifact | non |
| `artifact/sentinel-global-telecom-38a6749` | `b7115c00c499` | 2026-10-18 | Thierry Naud | contient_main +344/-0 | histoires_disjointes | artifact: include exact debug APK binary | non |
| `artifact/sentinel-global-telecom-8aac8c5` | `d6529c3a0bec` | 2026-10-18 | Thierry Naud | contient_main +338/-0 | histoires_disjointes | build: include global telecom debug APK binary | non |
| `artifact/sentinel-global-telecom-bbf78ef` | `aa42bdff9873` | 2026-10-18 | Thierry Naud | contient_main +344/-0 | histoires_disjointes | artifact: Sentinel D1 debug APK bbf78ef | non |
| `audit/emulator-gate-truth-20261007` | `444d80e59ba1` | 2026-10-07 | teetee971 | divergente +32/-301 | histoires_disjointes | fix(phone-core): classify Sentinel secondary process failure | oui |
| `audit/play-store-readiness-20261022` | `bba64e13d7fe` | 2026-10-22 | teetee971 | contient_main +1/-0 | histoires_disjointes | test(play): add fail-closed local source configuration prefl | oui |
| `audit/room-schema-evidence-20261022` | `cf06f1de57a5` | 2026-10-22 | teetee971 | contient_main +206/-0 | histoires_disjointes | test(phone): exercise bounded writer FIFO and rejected-task  | oui |
| `audit/telecom-manifest-contract-a5538eb` | `62ea20951dd8` | 2026-09-30 | teetee971 | divergente +1/-624 | histoires_disjointes | CI: lock complete default-dialer manifest contract | non |
| `autoloop/phone-timeline-fail-closed-20260929` | `1cdf301f3549` | 2026-09-29 | teetee971 | divergente +9/-662 | histoires_disjointes | fix(phone-core): surface evidence-store integrity in activat | non |
| `autoloop/phone-timeline-fail-closed-after-1333` | `6a62b03ed41a` | 2026-09-29 | teetee971 | divergente +1/-661 | histoires_disjointes | fix(phone-core): replay fail-closed timeline integrity on cu | non |
| `autoloop/system-doctor-cleanup-wiring-20260929` | `f47fde2b3557` | 2026-09-29 | teetee971 | divergente +7/-663 | histoires_disjointes | fix(cleanup): use scoped Compose weight extension | non |
| `automation/arcep-numbering-34816163756` | `6b16f6bad1a8` | 2026-09-14 | github-actions[bot] | divergente +1/-1389 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data | non |
| `automation/arcep-numbering-35287632166` | `675d928388a6` | 2026-09-17 | github-actions[bot] | divergente +1/-1342 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data | non |
| `automation/arcep-numbering-36390398098` | `1af1faf1d694` | 2026-09-28 | github-actions[bot] | divergente +1/-675 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data | non |
| `automation/arcep-numbering-refresh` | `d2f905c6b4cb` | 2026-10-12 | github-actions[bot] | divergente +1/-291 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data | oui |
| `automation/cisa-kev-refresh-34066238297` | `3fbaf05010cd` | 2026-09-06 | github-actions[bot] | divergente +1/-1441 | histoires_disjointes | Threat intel: refresh CISA KEV catalog | non |
| `automation/cisa-kev-refresh-34815400453` | `3e4d0693eb97` | 2026-09-14 | github-actions[bot] | divergente +1/-1389 | histoires_disjointes | Threat intel: refresh CISA KEV catalog | non |
| `automation/cisa-kev-refresh-35271204045` | `4cd3b3121e74` | 2026-09-18 | teetee971 | divergente +3/-1307 | histoires_disjointes | Merge branch 'main' into automation/cisa-kev-refresh-3527120 | non |
| `automation/emulator-first-qualification` | `322fb76f47e4` | 2026-10-04 | teetee971 | divergente +4/-463 | histoires_disjointes | ci: extend Android qualification wait window | non |
| `automation/patch-1595-evidence-tests` | `5719103f070c` | 2026-10-06 | teetee971 | contenue_dans_main +0/-302 | histoires_disjointes | fix(phone-core): harden shadow qualification and runtime tru | non |
| `automation/replay-vulnerability-watch-20260925-1809a7d` | `d6f3887085c4` | 2026-09-25 | teetee971 | divergente +1/-814 | histoires_disjointes | Threat intel: replay vulnerability watch state on current ma | non |
| `automation/replay-vulnerability-watch-20260925-23a3b1f` | `23a3b1f3df4d` | 2026-09-25 | teetee971 | contenue_dans_main +0/-804 | histoires_disjointes | Wearables: enforce security-owned verified proof (#1207) | non |
| `automation/replay-vulnerability-watch-20260926-c16a1ec` | `65498a64d53e` | 2026-09-26 | github-actions[bot] | divergente +1/-802 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/replay-vulnerability-watch-20260930-3d419019` | `30fc3feec29f` | 2026-09-30 | teetee971 | divergente +1/-647 | histoires_disjointes | Threat intel: replay vulnerability watch refresh | non |
| `automation/vulnerability-watch-35283155772` | `dc903171e88c` | 2026-09-17 | github-actions[bot] | divergente +1/-1348 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-35285633778` | `ed59459eef7f` | 2026-09-17 | github-actions[bot] | divergente +1/-1345 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-35429449400` | `da9c506ffe9a` | 2026-09-19 | github-actions[bot] | divergente +1/-1234 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-35700243059` | `07cf15992cc1` | 2026-09-22 | github-actions[bot] | divergente +1/-1099 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-35832456816` | `5c2120025cbb` | 2026-09-23 | github-actions[bot] | divergente +1/-1007 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-35970369095` | `ff89ae268e01` | 2026-09-24 | github-actions[bot] | divergente +1/-863 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-36108426965` | `6e1235409cf6` | 2026-09-25 | github-actions[bot] | divergente +1/-827 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-36226972670` | `65498a64d53e` | 2026-09-26 | github-actions[bot] | divergente +1/-802 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-36393885741` | `7df2b32767b5` | 2026-09-28 | github-actions[bot] | divergente +1/-675 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-36538070830` | `3159adb1e55d` | 2026-09-29 | github-actions[bot] | divergente +1/-675 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-36685181151` | `634a936600b7` | 2026-09-30 | github-actions[bot] | divergente +1/-648 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-36831851691` | `fa7f10d361eb` | 2026-10-01 | github-actions[bot] | divergente +1/-619 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `automation/vulnerability-watch-refresh` | `3a148aa4acc5` | 2026-10-16 | github-actions[bot] | divergente +1/-27 | histoires_disjointes | Threat intel: refresh vulnerability watch state | oui |
| `autopilot/osint-cache-io-replay-fb098ad` | `9c07e1152ddd` | 2026-09-30 | teetee971 | divergente +1/-623 | histoires_disjointes | Performance: keep OSINT cache decoding off UI thread | non |
| `autopilot/phone-core-contact-directory-main` | `92782a63f442` | 2026-10-02 | teetee971 | divergente +5/-590 | histoires_disjointes | test(android): tolerate multiline Compose directory heading | non |
| `autopilot/phone-core-directory-production-final` | `ee26f27268fb` | 2026-10-02 | teetee971 | divergente +5/-589 | histoires_disjointes | test(phone-core): enforce contact presentation policy | non |
| `autopilot/phone-core-favorites-foundation` | `6546a43cbc82` | 2026-10-02 | teetee971 | divergente +3/-582 | histoires_disjointes | Merge branch 'main' into autopilot/phone-core-favorites-foun | non |
| `autopilot/phone-core-mms-send-transport` | `4dc6ddab2c9d` | 2026-10-02 | teetee971 | divergente +8/-582 | histoires_disjointes | Merge branch 'main' into autopilot/phone-core-mms-send-trans | non |
| `autopilot/phone-core-outgoing-mms-policy` | `20f16b70057b` | 2026-10-02 | teetee971 | divergente +3/-582 | histoires_disjointes | Merge branch 'main' into autopilot/phone-core-outgoing-mms-p | non |
| `autopilot/phone-core-sms-inbox-permissions-main` | `f3b34dd5f7d7` | 2026-10-02 | teetee971 | divergente +4/-590 | histoires_disjointes | test(phone-core): cover fail-closed SMS inbox permissions | non |
| `autopilot/phone-core-sms-io-off-main-thread` | `06f5303ea274` | 2026-10-02 | teetee971 | divergente +1/-590 | histoires_disjointes | fix(phone-core): move SMS/MMS provider I/O off UI thread | non |
| `backup/non-validee-phone-role-diagnostics-20261027` | `9d093badf024` | 2026-10-27 | Thierry Naud | contient_main +190/-0 | histoires_disjointes | fix: import rememberSaveable for phone role diagnostics | non |
| `backup/non-validee-signed-call-rule-verifier-20261027` | `7ffc3e688ef1` | 2026-10-27 | Thierry Naud | contient_main +188/-0 | histoires_disjointes | chore: sauvegarde NON_VALIDEE du correctif des préfixes sign | non |
| `build/download-artifact-v8-current-main` | `f1ea56ee769a` | 2026-10-03 | teetee971 | contenue_dans_main +0/-513 | histoires_disjointes | Merge pull request #1526 from teetee971/fix/sms-respond-via- | non |
| `chore/actions-cleanup-canary-cadence` | `df18ae66c07f` | 2026-09-20 | teetee971 | divergente +1/-1161 | histoires_disjointes | Stop redundant hourly CI canary runs | non |
| `chore/actions-cleanup-canary-cadence-v2` | `e3f7dba17c4a` | 2026-09-20 | teetee971 | divergente +1/-1161 | histoires_disjointes | Stop redundant hourly CI canary runs | non |
| `chore/actions-cleanup-cisa-duplicate` | `b28f602b0033` | 2026-09-20 | teetee971 | divergente +1/-1162 | histoires_disjointes | Avoid duplicate automatic CISA KEV refreshes | non |
| `chore/actions-cleanup-watchdog-cadence` | `86144a418c69` | 2026-09-20 | teetee971 | divergente +1/-1162 | histoires_disjointes | Reduce redundant watchdog Actions cadence | non |
| `chore/actions-cleanup-watchdog-cadence-v2` | `7bc3f73de399` | 2026-09-20 | teetee971 | divergente +1/-1161 | histoires_disjointes | Reduce redundant watchdog Actions cadence | non |
| `chore/actions-cleanup-watchdog-cadence-v3` | `0b4d72ac0c9d` | 2026-09-20 | teetee971 | divergente +1/-1160 | histoires_disjointes | Reduce redundant watchdog Actions cadence | non |
| `chore/android-debt-pass-1` | `c147ca7366c6` | 2026-10-06 | teetee971 | divergente +2/-302 | histoires_disjointes | test(android): cover missing duplicate hash evidence | oui |
| `chore/download-artifact-8-0-1-current-main-20261003` | `c8f1c44e78e1` | 2026-10-02 | teetee971 | contenue_dans_main +0/-556 | histoires_disjointes | Merge PR #1508: truthful Phone Core protection lists | non |
| `chore/download-artifact-8.0.1-main-20261004` | `a171b17359dc` | 2026-10-04 | teetee971 | contenue_dans_main +0/-463 | histoires_disjointes | fix(site): reposition official Sentinel visual and simplify  | non |
| `chore/pin-wangiri-actions` | `cdb4adf7387b` | 2026-09-20 | teetee971 | divergente +1/-1161 | histoires_disjointes | Pin Wangiri workflow Actions by commit SHA | non |
| `chore/pin-wangiri-actions-v2` | `9092d3efc37f` | 2026-09-20 | teetee971 | divergente +1/-1160 | histoires_disjointes | Pin Wangiri workflow Actions by commit SHA | non |
| `chore/replay-download-artifact-8.0.1-main-20261004` | `0b1eec00ea01` | 2026-10-03 | teetee971 | contenue_dans_main +0/-475 | histoires_disjointes | Merge #1547: harden Bluetooth runtime identity truth | non |
| `chore/replay-download-artifact-8.0.1-main-20261004-0806` | `83f506d60184` | 2026-10-04 | teetee971 | contenue_dans_main +0/-474 | histoires_disjointes | fix(phone-core): separate customer setup from certification  | non |
| `chore/vulnerability-watch-state-seq2` | `c78cec22c6da` | 2026-09-17 | teetee971 | divergente +3/-1347 | histoires_disjointes | Sync vulnerability-watch.json collection evidence | non |
| `ci-merge-group-current-main-20260922` | `d4e0ad7b8233` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | CI: cover merge groups in AI governance | non |
| `ci-sonarqube-observational-a986b04-20260925` | `6e9ae4aa83cf` | 2026-09-25 | teetee971 | divergente +2/-815 | histoires_disjointes | CI: add observational SonarQube analysis | non |
| `ci/aab-archive-fail-closed-20260930` | `b1392b0243b8` | 2026-09-30 | teetee971 | divergente +2/-641 | histoires_disjointes | test(ci): fix AAB archive metadata assertion escaping | non |
| `ci/android37-functional-gate` | `7bc068ed4a4f` | 2026-10-04 | teetee971 | divergente +2/-457 | histoires_disjointes | test(android): verrouiller la double provenance APK | non |
| `ci/classify-soc-dormant-libraries-20260927` | `01bef5d168a0` | 2026-09-27 | teetee971 | divergente +1/-775 | histoires_disjointes | CI: classify dormant SOC policy and passive pipeline librari | non |
| `ci/codeql-merge-revision-verification-20260923` | `fc9f093e1327` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | CI: run CodeQL on explicit merge-verification branches | non |
| `ci/merge-group-coverage-20260922` | `e72b10b103d1` | 2026-09-22 | teetee971 | divergente +9/-1088 | histoires_disjointes | CI: run required workflow on merge groups | non |
| `ci/merge-group-coverage-current-main-20260923` | `d90aefad0519` | 2026-09-23 | teetee971 | contenue_dans_main +0/-1007 | histoires_disjointes | Merge pull request #1074 from teetee971/phone-core-sms-deliv | non |
| `ci/merge-verification/pr-1113` | `0d46b6ff68c9` | 2026-09-23 | teetee971 | divergente +2/-894 | histoires_disjointes | Merge fc9f093e1327245c03156b0efa35f2e741088219 into 6fca93fb | non |
| `ci/merge-verification/pr-1114` | `4b3b4f751515` | 2026-09-23 | teetee971 | divergente +1/-891 | histoires_disjointes | Merge 2322a433f66b213137bbe51f53e9d709ab047fee into 5b0d18b5 | non |
| `ci/merge-verification/pr-1115-b33906f5` | `40bb03c7a6b0` | 2026-09-23 | teetee971 | divergente +1/-883 | histoires_disjointes | Merge b33906f508edc4e72370dd912c775691f98aff52 into 00f5d5b3 | non |
| `ci/merge-verification/pr-1117-0e64fb6e` | `f0145ba9c672` | 2026-09-23 | teetee971 | divergente +1/-881 | histoires_disjointes | Merge 0e64fb6e9de47298f974bc89629cb23cb8dd3a62 into e11f5ed3 | non |
| `ci/merge-verification/pr-1118-597dac94` | `29b888f0f051` | 2026-09-23 | teetee971 | divergente +1/-879 | histoires_disjointes | Merge 597dac94dc767f43b70ad6c14d30b58820a80aab into ecccb198 | non |
| `ci/merge-verification/pr-1214-f1f70b7d` | `f1f70b7dd1a8` | 2026-09-26 | teetee971 | divergente +8/-802 | histoires_disjointes | Merge c3226c0b2866d39b2ea551bc8a2dec1b09c17c3d into c16a1ec0 | non |
| `ci/phone-core-call-screening-emulator` | `711f95136ce7` | 2026-10-04 | teetee971 | divergente +4/-458 | histoires_disjointes | test(ci): require persisted screening evidence in emulator f | non |
| `ci/phone-core-emulation-gate-api37-20261004` | `8fd1c1041771` | 2026-10-05 | teetee971 | divergente +51/-458 | histoires_disjointes | test(phone-core): make revocation proof reflect Android role | non |
| `ci/phone-core-emulation-gate-rebased-20261005` | `92831e2899e0` | 2026-10-05 | teetee971 | divergente +2/-456 | histoires_disjointes | fix(phone-core): harden API 29 role and revocation gates | non |
| `ci/phone-core-first-launch-race-20260926` | `c16a1ec08b92` | 2026-09-26 | dependabot[bot] | contenue_dans_main +0/-802 | histoires_disjointes | Bump the gradle-minor-patch group across 1 directory with 2  | non |
| `ci/phone-core-first-run-readiness-20260926` | `98d25fe26aa9` | 2026-09-26 | teetee971 | divergente +1/-802 | histoires_disjointes | CI: wait for Phone Core first-run decision | non |
| `ci/portable-android-validation-20261020` | `e2fad4923293` | 2026-10-20 | teetee971 | contient_main +8/-0 | histoires_disjointes | fix(ci): persist BLOCKED verdict for all failed preflight ch | oui |
| `ci/refresh-pinned-actions-20260921` | `0b5e14bf47ab` | 2026-09-21 | teetee971 | divergente +8/-1104 | histoires_disjointes | CI: remove synthetic required-check attestation | non |
| `ci/refresh-pinned-actions-after-949-20260921` | `6ae2023f3369` | 2026-09-21 | teetee971 | divergente +6/-1103 | histoires_disjointes | CI: refresh pinned setup-python action | non |
| `ci/sonarqube-observational-20260922` | `6ae653bea10d` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Docs: document SonarQube CI setup | non |
| `codex/call-rules-p0-validated-20261113` | `51b62dbc9540` | 2026-11-13 | Thierry Naud | contient_main +193/-0 | histoires_disjointes | fix(phone-core): serialize call rule mutations | non |
| `codex/call-rules-p0.2-20261113` | `ba4b2b4a2cbd` | 2026-11-13 | Thierry Naud | contient_main +194/-0 | histoires_disjointes | fix(phone-core): separate simulated snapshot time | non |
| `codex/local-ai-lab-20261113` | `639bc152eec1` | 2026-11-13 | Thierry Naud | contient_main +263/-0 | histoires_disjointes | research: add local AI benchmark lab | oui |
| `codex/p02-android-isolated-20261113` | `a0a6a9b0728b` | 2026-11-13 | Thierry Naud | contient_main +195/-0 | histoires_disjointes | test(android): isolate p02 call-rule instrumentation | non |
| `codex/p02-arm64-strip-fix-20261113` | `87d17a670504` | 2026-11-13 | Thierry Naud | contient_main +196/-0 | histoires_disjointes | fix(android): avoid arm64 host native stripping for p02 | non |
| `codex/p02-consolidation-20261114` | `b270b1ea87fc` | 2026-11-14 | Thierry Naud | contient_main +205/-0 | histoires_disjointes | fix(qa): align evidence checks with corrected test set | non |
| `codex/p02-instrumentation-source-set-20261114` | `5a78620ee28d` | 2026-11-14 | Thierry Naud | contient_main +197/-0 | histoires_disjointes | fix(android): include p02 isolation instrumentation source | non |
| `codex/phone-core-qualification-20261008` | `73a8e839e70f` | 2026-10-10 | Thierry Naud | divergente +331/-301 | histoires_disjointes | fix(ci): verify physical source without fetching untrusted c | oui |
| `codex/ptt-livekit-core-20261009` | `5d9fb328cacf` | 2026-10-09 | teetee971 | divergente +239/-301 | histoires_disjointes | refactor(ptt): make muted connect part of transport contract | oui |
| `codex/sentinel-audit-20261105` | `06076b8bc7e0` | 2026-11-06 | teetee971 | contient_main +274/-0 | histoires_disjointes | fix(qualification): derive signing report version from verif | oui |
| `codex/stic-v1.1-20261113` | `fbed1064b696` | 2026-11-13 | Thierry Naud | contient_main +262/-0 | histoires_disjointes | feat(stic): add persistent fail-closed sequence guard | oui |
| `communications-external-links-core` | `c28f99fbb69e` | 2026-09-21 | teetee971 | divergente +2/-1119 | histoires_disjointes | Test external messaging link safety boundaries | non |
| `communications-external-links-v2` | `18e9d0febce1` | 2026-09-21 | teetee971 | divergente +2/-1118 | histoires_disjointes | Test external messaging link boundaries | non |
| `communications-external-links-v3` | `1ed4189787f3` | 2026-09-21 | teetee971 | divergente +2/-1117 | histoires_disjointes | Test external messaging link boundaries | non |
| `communications-external-links-v4` | `a5f5c7af5a67` | 2026-09-21 | teetee971 | divergente +2/-1115 | histoires_disjointes | Communications: test external messaging links | non |
| `communications-hub-activity` | `a94541cecbd8` | 2026-09-21 | teetee971 | divergente +3/-1117 | histoires_disjointes | Communications: make hub content scrollable | non |
| `communications-hub-activity-v2` | `e5608ad12839` | 2026-09-21 | teetee971 | divergente +3/-1115 | histoires_disjointes | Fix Communications Hub Material3 opt-in | non |
| `communications-hub-core` | `4968870acd2a` | 2026-09-21 | teetee971 | divergente +2/-1120 | histoires_disjointes | Test communications hub privacy boundaries | non |
| `communications-hub-core-v2` | `7d45dc1904bd` | 2026-09-21 | teetee971 | divergente +2/-1119 | histoires_disjointes | Test communications hub privacy boundaries | non |
| `compliance/privacy-contact-20261104` | `f5ee6418279e` | 2026-11-04 | teetee971 | contient_main +2/-0 | histoires_disjointes | docs(legal): identify individual publisher and official supp | non |
| `copilot/add-certification-message-animation` | `5755a9f2cde5` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement holographic certification message animation | non |
| `copilot/add-cybersecurity-glossary` | `225680502dd4` | 2025-12-14 | copilot-swe-agent[bot] | divergente +8/-2294 | histoires_disjointes | Add institutional usage page with legal framework and use ca | non |
| `copilot/add-dashboard-and-feedback-section` | `0c4e6397cc26` | 2025-12-14 | github-actions[bot] | divergente +23/-2297 | histoires_disjointes | Update demo logs data [automated] | non |
| `copilot/add-documentation-folder-structure` | `7ddd32b7adcc` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add documentation folder structure with PDF placeholder | non |
| `copilot/add-download-link-navbar` | `ccb1ebdef976` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Changes before error encountered | non |
| `copilot/add-e2e-ci-with-playwright` | `b7ddfcd3596f` | 2025-11-06 | copilot-swe-agent[bot] | divergente +2/-3538 | histoires_disjointes | chore(ci): add Playwright E2E GitHub Actions workflow | non |
| `copilot/add-global-cybersecurity-system` | `34e930ffaa89` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Complete implementation of all modules - dashboard and routi | non |
| `copilot/add-known-exploited-vulnerabilities` | `3f357edee2b2` | 2025-12-21 | copilot-swe-agent[bot] | divergente +1/-2248 | histoires_disjointes | Initial plan | non |
| `copilot/add-loading-animation-pdf` | `0d7d669f4860` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add root package-lock.json and update .gitignore for Cloudfl | non |
| `copilot/add-official-pdf-download` | `084f844d5a60` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add PDF download page at /telechargement with dark theme des | non |
| `copilot/add-osint-notifications-workmanager` | `01b7163d0045` | 2026-09-08 | teetee971 | divergente +5/-1407 | histoires_disjointes | Set onClick to null for RadioButton | non |
| `copilot/add-pegasus-scan-protection-module` | `e4ee9591f298` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Fix code review issues - cleanup imports and prevent memory  | non |
| `copilot/add-sentinel-mobile-defender` | `73bb6d034b28` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Complete MODULE 12: Add all security components and Security | non |
| `copilot/add-sms-link-scanner-screen` | `5de44e8f5520` | 2026-09-08 | teetee971 | divergente +2/-1406 | histoires_disjointes | Merge branch 'main' into copilot/add-sms-link-scanner-screen | non |
| `copilot/address-pr-comments` | `b31934e1ff98` | 2025-11-08 | copilot-swe-agent[bot] | divergente +4/-3519 | histoires_disjointes | Address PR review comments - use prepare hook and --prefix | non |
| `copilot/audit-modules-non-finalises` | `c224dbdeb1f3` | 2026-09-07 | copilot-swe-agent[bot] | divergente +5/-1425 | histoires_disjointes | Aligner produit.html, pricing.html, faq.html et download-gui | non |
| `copilot/audit-modules-sentinel-quantum-vanguard-ai-pro` | `edefb9fa9a1e` | 2026-09-07 | teetee971 | contenue_dans_main +0/-1425 | histoires_disjointes | Security: add atomic distributed Redis rate limits (#345) | non |
| `copilot/automate-ci-cd-pipeline` | `5320301fac85` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement all AI modules (Infrastructure, Voice, Monitoring, | non |
| `copilot/close-redundant-prs` | `a202a931fe83` | 2026-09-07 | teetee971 | contenue_dans_main +0/-1424 | histoires_disjointes | Refonte complète de la vitrine publique Sentinel (design, mo | non |
| `copilot/copilot-redesign-public-showcase` | `00810272c361` | 2026-09-07 | copilot-swe-agent[bot] | divergente +3/-1425 | histoires_disjointes | Replace module emojis with SVG icons | non |
| `copilot/copilotadd-sms-scanner-and-call-history` | `28002227010c` | 2026-09-09 | teetee971 | divergente +4/-1397 | histoires_disjointes | Merge branch 'main' into copilot/copilotadd-sms-scanner-and- | non |
| `copilot/copilotfix-broken-links-and-pages` | `a202a931fe83` | 2026-09-07 | teetee971 | contenue_dans_main +0/-1424 | histoires_disjointes | Refonte complète de la vitrine publique Sentinel (design, mo | non |
| `copilot/copilotredesign-public-showcase` | `db9aab411972` | 2026-09-07 | copilot-swe-agent[bot] | divergente +3/-1425 | histoires_disjointes | Add controlled interference defense showcase | non |
| `copilot/copilotredesign-public-showcase-again` | `00810272c361` | 2026-09-07 | copilot-swe-agent[bot] | divergente +3/-1425 | histoires_disjointes | Replace module emojis with SVG icons | non |
| `copilot/copilotredesign-public-showcase-another-one` | `34b256892b75` | 2026-09-07 | copilot-swe-agent[bot] | divergente +2/-1425 | histoires_disjointes | Refondre la vitrine publique SaaS | non |
| `copilot/create-90-day-roadmap` | `9681c263e7c4` | 2025-11-09 | copilot-swe-agent[bot] | divergente +2/-3406 | histoires_disjointes | Create comprehensive 90-day roadmap and sub-issues for A KI  | non |
| `copilot/create-global-threat-journal-page` | `ac05ea4e0bbc` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Use DOMParser for safer HTML sanitization | non |
| `copilot/enhance-email-analysis` | `07c1212bd5f4` | 2026-09-08 | teetee971 | divergente +3/-1405 | histoires_disjointes | Merge branch 'main' into copilot/enhance-email-analysis | non |
| `copilot/establish-cicd-pipeline` | `021cd4acec0f` | 2025-11-06 | copilot-swe-agent[bot] | divergente +4/-3520 | histoires_disjointes | Fix grammar typo in deploy script success message | non |
| `copilot/finalize-sentinel-quantum-vanguard` | `8c81235a78a7` | 2025-12-15 | copilot-swe-agent[bot] | divergente +6/-2283 | histoires_disjointes | Add comprehensive finalization summary document | non |
| `copilot/fix-0d968b51-f19d-4980-a2b2-12e8522814ad` | `ecd3a836a1c4` | 2025-09-22 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Create test.yml workflow and update upload-artifact from v3  | non |
| `copilot/fix-0f1df187-b3fb-40f4-9729-ad14c303d90a` | `980b5ebd4a0e` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement fetchPrixDOM function and integrate price search f | non |
| `copilot/fix-12d9bb1b-df03-4a53-9a0b-f3eec5755239` | `65813383a7fa` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add comprehensive site status monitoring: dashboard, API, an | non |
| `copilot/fix-17c4c4be-b857-44c4-84b9-b6ee18c730be` | `b63da24bc1bb` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Initial plan | non |
| `copilot/fix-238c2518-9390-47d5-b2c7-ee0e1d181b38` | `2e1a51b2a22e` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Changes before error encountered | non |
| `copilot/fix-35f3e23b-d54b-4bc8-bbf8-223f2c613589` | `9445170732ba` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Initial plan | non |
| `copilot/fix-5a816cbb-68ae-4711-bfe8-67ae2d9f87e1` | `0e51831e1b28` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Create deploy.yml workflow with npm ci for stable builds | non |
| `copilot/fix-607a3373-60ac-4dc7-a2d9-d8dea5524478` | `33d260056c8c` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Initial plan | non |
| `copilot/fix-8a2d1256-3bc9-4702-a323-1c38e49e31e6` | `c6b219395214` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement price comparison feature with colored badges for D | non |
| `copilot/fix-8a68307a-9e29-45b4-9b81-5303ed9abfa1` | `f2713db3d606` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement OPMR price monitoring module with full UI integrat | non |
| `copilot/fix-8d247eb4-cde3-4c78-87f7-8b25cb8b526c` | `37864f858a81` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add documentation and final verification for fetchPrixDOM im | non |
| `copilot/fix-95961170-1003429074-07dd8298-ccd5-4f15-99a5-1b31d52dec16` | `c47a2abdc624` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | chore: prepare branch for PR | non |
| `copilot/fix-95961170-1003429074-d05da60a-d5ea-4155-9c2a-ed1ffefd52b1` | `3ea1954e25b6` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Update package.json configuration with required dependencies | non |
| `copilot/fix-95961170-1003429074-f7f633f1-b9de-4340-90e5-dbd7bf7d926a` | `1369510132e5` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | fix(voice-srv1): add workflow permissions to satisfy securit | non |
| `copilot/fix-b166ee27-5b56-4625-a050-d92d67aa2089` | `1782c9e6c737` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | 📦 Implement smart npm dependency installation (npm ci vs npm | non |
| `copilot/fix-b2f0c82e-52f6-4aae-846a-6803c12004d1` | `e74a3ab9bf0d` | 2025-09-22 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Fix React application structure and add Layout component | non |
| `copilot/fix-c64ba2e1-8753-49b8-993e-c84a72bf2653` | `17c5892f3fc2` | 2025-09-20 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement fetchPrixDOM function for French government price  | non |
| `copilot/fix-codeql-detection-error` | `be1f6446a859` | 2025-12-16 | copilot-swe-agent[bot] | divergente +2/-2268 | histoires_disjointes | Restrict CodeQL analysis to JavaScript/TypeScript only and d | non |
| `copilot/fix-eea0c36a-2348-4f0b-a13a-9becffd2f0dd` | `f664a9a32df7` | 2025-09-21 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add fix-deps.sh script for Termux/ARM64 compatibility and Cl | non |
| `copilot/fix-failing-github-actions-job` | `7dd58874832c` | 2026-09-08 | copilot-swe-agent[bot] | divergente +3/-1407 | histoires_disjointes | Fix Android notification permission lint failure | non |
| `copilot/fix-fdf6a450-fc27-4f6f-9450-9e775ca79753` | `06bbf23447bb` | 2025-09-22 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Implement actions/upload-artifact@v4 in all Firebase hosting | non |
| `copilot/fix-github-actions-job` | `561e28b0d925` | 2026-09-08 | copilot-swe-agent[bot] | divergente +5/-1406 | histoires_disjointes | Fix Android lint failures | non |
| `copilot/fix-missing-post-notifications-permission` | `2412a1ff6959` | 2026-09-08 | copilot-swe-agent[bot] | divergente +6/-1405 | histoires_disjointes | Fix: ajouter POST_NOTIFICATIONS + garde runtime pour lint An | non |
| `copilot/implement-ci-cd-for-cloudflare` | `16c0a13ff90e` | 2025-11-06 | copilot-swe-agent[bot] | divergente +4/-3519 | histoires_disjointes | Fix spelling: 'corrects' to 'correct' in deploy script | non |
| `copilot/implement-cicd-cloudflare-pages` | `ab4aa43ad201` | 2025-11-06 | copilot-swe-agent[bot] | divergente +4/-3519 | histoires_disjointes | Add explicit permissions to site-health workflow for securit | non |
| `copilot/implement-infragard-ci-cd` | `dd8ff20606da` | 2025-11-03 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Add comprehensive SENTINEL_MODULES.md documentation | non |
| `copilot/improve-accessibility-standards` | `51d01cd749be` | 2025-11-08 | copilot-swe-agent[bot] | divergente +3/-3434 | histoires_disjointes | Fix heading hierarchy in Telechargement and TestIA pages | non |
| `copilot/improve-ux-and-navigation` | `426e82494135` | 2025-12-17 | copilot-swe-agent[bot] | divergente +11/-2259 | histoires_disjointes | Add complete AndroidManifest.xml with Application class, Ser | non |
| `copilot/optimize-performance-assets` | `f1418f71b5c4` | 2025-11-08 | copilot-swe-agent[bot] | divergente +5/-3434 | histoires_disjointes | Add comprehensive optimization summary document | non |
| `copilot/prepare-play-store-release` | `281097ba5121` | 2026-09-08 | copilot-swe-agent[bot] | divergente +1/-1408 | histoires_disjointes | Initial plan | non |
| `copilot/responsive-design-checks` | `4792ad9a6ee8` | 2025-11-08 | copilot-swe-agent[bot] | divergente +5/-3434 | histoires_disjointes | Add visual summary documentation | non |
| `copilot/set-up-cloudflare-pages-deployment` | `899a467b41b1` | 2025-11-06 | copilot-swe-agent[bot] | divergente +2/-3520 | histoires_disjointes | Add Cloudflare Pages deployment automation with GitHub Actio | non |
| `copilot/setup-ci-cd-cloudflare-pages` | `fca92580cc6d` | 2025-11-06 | copilot-swe-agent[bot] | divergente +4/-3520 | histoires_disjointes | Add explicit permissions to site-health workflow for securit | non |
| `copilot/setup-ci-cd-cloudflare-pages-again` | `5431cd5aa9d3` | 2025-11-06 | copilot-swe-agent[bot] | divergente +3/-3519 | histoires_disjointes | Add explicit permissions to workflows for security | non |
| `copilot/sync-firestore-dashboard` | `3c74632b5f32` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Apply security improvements and robustness fixes to Firestor | non |
| `copilot/update-roadmap-progress` | `4cd5b580fc16` | 2025-11-02 | copilot-swe-agent[bot] | histoires_disjointes | histoires_disjointes | Addressing PR comments | non |
| `copilot/update-static-site-structure` | `487d62207a23` | 2025-12-14 | copilot-swe-agent[bot] | divergente +21/-2296 | histoires_disjointes | Add phone dialer, contacts, SMS security and permissions man | non |
| `copilot/update-worker-to-serve-robot-sitemap` | `08009effec6a` | 2025-11-06 | copilot-swe-agent[bot] | divergente +3/-3578 | histoires_disjointes | Complete audit and repair: Add error handling, method valida | non |
| `cursor/v1-call-screening-audit-536d` | `5a6f7014bdb1` | 2026-11-18 | Cursor Agent | histoires_disjointes | contient_integration +4/-0 | Record the screening warm-up commit SHA [skip ci] | oui |
| `cursor/v1-p0-privacy-catalog-c39e` | `6de5a272e030` | 2026-11-18 | Cursor Agent | histoires_disjointes | contient_integration +12/-0 | docs(v1): refresh the copyable Play report and align stale d | oui |
| `cursor/v1-phone-core-full-audit-20261118` | `e77337d67727` | 2026-11-19 | Cursor Agent | histoires_disjointes | contient_integration +15/-0 | [skip ci] Audit: distinguer JVM 100% et PASS physique appel/ | oui |
| `cursor/v1-play-compliance-20261118-c39e` | `29a207f07d6d` | 2026-11-18 | Cursor Agent | histoires_disjointes | contient_integration +3/-0 | docs(v1): record the adversarial Play audit and correct Data | oui |
| `dependabot-kotlin-compose-2.4.20-current-main-20260923` | `936f9a3fe8b5` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | build: bump Kotlin Compose plugin to 2.4.20 | non |
| `dependabot/github_actions/actions-minor-patch-2aeb733786` | `c440e1e89882` | 2026-11-09 | dependabot[bot] | contient_main +1/-0 | histoires_disjointes | chore(deps): bump the actions-minor-patch group across 1 dir | oui |
| `dependabot/github_actions/actions/download-artifact-8.0.2` | `583e9a50deba` | 2026-10-16 | dependabot[bot] | divergente +1/-27 | histoires_disjointes | chore(deps): bump actions/download-artifact from 7.0.0 to 8. | oui |
| `dependabot/gradle/native-android-app/gradle-minor-patch-657b60946c` | `e55916dc1886` | 2026-11-23 | dependabot[bot] | contient_main +1/-0 | histoires_disjointes | chore(deps): bump the gradle-minor-patch group across 1 dire | oui |
| `dependabot/npm_and_yarn/npm-minor-patch-210587e60a` | `7d42aa84c094` | 2026-10-19 | dependabot[bot] | contient_main +1/-0 | histoires_disjointes | chore(deps-dev): bump vite | oui |
| `dependabot/npm_and_yarn/vite-8.3.3` | `7838601cbec2` | 2026-10-21 | dependabot[bot] | contient_main +1/-0 | histoires_disjointes | chore(deps-dev): bump vite from 8.3.1 to 8.3.3 | oui |
| `deps/agp-9-4-1-codeql-compatible-20260922` | `401085eecd6b` | 2026-09-22 | teetee971 | divergente +1/-1097 | histoires_disjointes | Deps: bump Android Gradle Plugin to 9.4.1 | non |
| `deps/android-gradle-refresh-75f233c` | `fe6f6e49bf0d` | 2026-09-29 | teetee971 | divergente +2/-670 | histoires_disjointes | deps(android): refresh Gradle and AndroidX patch versions | non |
| `deps/codeql-action-refresh-75f233c` | `eaf3ce343d60` | 2026-09-29 | teetee971 | divergente +1/-670 | histoires_disjointes | deps(actions): refresh pinned CodeQL action | non |
| `deps/download-artifact-8.0.1-main-1529` | `708747cbfa3f` | 2026-10-03 | teetee971 | contenue_dans_main +0/-512 | histoires_disjointes | fix(phone-core): make outgoing MMS callback states factual | non |
| `deps/vite-8.3.1-0a007919` | `0a0079196aff` | 2026-09-29 | teetee971 | contenue_dans_main +0/-655 | histoires_disjointes | GeoIntel: add verifiable world-monitor foundation (#1334) | non |
| `design/defensive-red-team-branding` | `1a1d8c316d8e` | 2026-09-06 | teetee971 | divergente +1/-1510 | histoires_disjointes | Design: align Red Team page with defensive Sentinel branding | non |
| `design/device-inventory-20261029` | `04032588f15f` | 2026-10-29 | Thierry Naud | contient_main +2/-0 | histoires_disjointes | docs(network): plan detailed inventory and optional Orange c | non |
| `design/ui-drift-264` | `9f9d04503c8f` | 2026-09-07 | teetee971 | contenue_dans_main +0/-1414 | histoires_disjointes | chore(ci): remove obsolete MSDO placeholder (#358) | non |
| `diagnostic/ci-canary-minimal` | `ef183398c932` | 2026-09-03 | teetee971 | divergente +1/-1629 | histoires_disjointes | diagnostic: add minimal CI canary | non |
| `diagnostic/ci-v4-isolation` | `8b2a5b54ff31` | 2026-09-03 | teetee971 | divergente +82/-1629 | histoires_disjointes | diagnostic: isolate CI action runtime with stable v4 actions | non |
| `docs-credibility-transparency` | `da450246431b` | 2025-12-13 | Copilot | contenue_dans_main +0/-2297 | histoires_disjointes | Repository audit, module documentation, and Cloudflare Pages | non |
| `docs/android-api-support-boundary` | `3f6fc4315240` | 2026-09-17 | teetee971 | divergente +4/-1337 | histoires_disjointes | Document Android call-screening support boundary | non |
| `docs/android-build-baseline-2026-09-17` | `cf8f1b2817eb` | 2026-09-17 | teetee971 | divergente +1/-1338 | histoires_disjointes | Align Android README with build baseline | non |
| `docs/android-delivery-play-status` | `01968148385c` | 2026-09-18 | teetee971 | divergente +2/-1322 | histoires_disjointes | docs: align Play Store draft with current Android capabiliti | non |
| `docs/collective-defense-design` | `6a6ea1285e03` | 2026-10-12 | teetee971 | divergente +2/-291 | histoires_disjointes | docs: add Collective Defense implementation plan | non |
| `docs/contributing-pr-only` | `6990a7fd375f` | 2026-09-07 | teetee971 | divergente +1/-1414 | histoires_disjointes | docs: add CONTRIBUTING.md enforcing PR-only workflow | non |
| `docs/defensive-core-notices` | `80bcfab379e1` | 2026-09-06 | teetee971 | divergente +4/-1505 | histoires_disjointes | Docs: align decision support type notice with defensive posi | non |
| `docs/defensive-engine-notices` | `e2a0d3870c08` | 2026-09-06 | teetee971 | divergente +3/-1504 | histoires_disjointes | Docs: align decision support engine notice | non |
| `docs/digital-exposure-current-status` | `7d56cf39a6a1` | 2026-09-18 | teetee971 | divergente +1/-1318 | histoires_disjointes | docs: distinguish active password exposure check from planne | non |
| `docs/digital-exposure-current-status2` | `9f2bf1f8492e` | 2026-09-18 | teetee971 | divergente +1/-1317 | histoires_disjointes | docs: rebase digital exposure status | non |
| `docs/digital-exposure-current-status3` | `0327f2e4c5b4` | 2026-09-18 | teetee971 | divergente +1/-1316 | histoires_disjointes | rebase: Clarify digital exposure monitoring on latest main | non |
| `docs/international-numbering-source-expansion-2026-10-01` | `53f6a395eb29` | 2026-10-01 | teetee971 | divergente +5/-597 | histoires_disjointes | docs(phone-intel): qualify Spain Norway Belgium and Italy | non |
| `docs/international-numbering-source-expansion-replay-2026-10-01` | `9c4eb87cb936` | 2026-10-01 | teetee971 | divergente +1/-598 | histoires_disjointes | docs(phone-intel): expand official international numbering s | non |
| `docs/international-numbering-source-expansion-v2-2026-10-01` | `88111cec2d9e` | 2026-10-02 | teetee971 | divergente +10/-594 | histoires_disjointes | fix(numbering): preserve monitor idempotence and qualify cat | non |
| `docs/mesh-runtime-status-current` | `2ee92463be95` | 2026-09-18 | teetee971 | divergente +5/-1300 | histoires_disjointes | Update public product contract for implemented VPN client | non |
| `docs/network-center-beyond-wifiman-20261020` | `5220c48f19ef` | 2026-10-20 | teetee971 | contient_main +41/-0 | histoires_disjointes | fix(network): use distinct local observation source type to  | oui |
| `docs/refresh-project-overview` | `c4f941b3b740` | 2026-09-06 | teetee971 | divergente +1/-1525 | histoires_disjointes | docs: refresh project overview and validation model | non |
| `docs/refresh-strategic-limits` | `3eeb3c39341f` | 2026-09-06 | teetee971 | divergente +1/-1508 | histoires_disjointes | Docs: refresh Sentinel strategic limits | non |
| `docs/remove-unproven-ui-claims` | `2a12a59c9ec1` | 2026-09-05 | teetee971 | contenue_dans_main +0/-1532 | histoires_disjointes | Threat intel: harden exact source and calendar validation (# | non |
| `docs/roadmap-post-keystore-fix` | `aa1734272a01` | 2026-09-17 | teetee971 | divergente +1/-1337 | histoires_disjointes | Update roadmap after call-screening Keystore fix | non |
| `docs/roadmap-status-20261112` | `5cd3a433f052` | 2026-11-12 | teetee971 | contient_main +3/-0 | histoires_disjointes | test(roadmap): avoid freezing future verified capability ava | oui |
| `docs/sentinel-integration-core-design-20261017` | `8c20b46bfdf6` | 2026-10-17 | teetee971 | contient_main +6/-0 | histoires_disjointes | docs(plan): add Sentinel integration master execution plan | non |
| `docs/sentinel-intelligence-core-v1-20261013` | `42fe23aeb029` | 2026-10-13 | teetee971 | contenue_dans_main +0/-241 | histoires_disjointes | docs(plan): define Sentinel Intelligence Core v1 implementat | non |
| `docs/update-main-ruleset-instructions` | `647d48bc02cb` | 2026-09-17 | teetee971 | divergente +1/-1335 | histoires_disjointes | Update main-ruleset issue guidance | non |
| `feat/android-call-history-privacy` | `f4748b2bfbe1` | 2026-09-19 | teetee971 | divergente +2/-1243 | histoires_disjointes | test(android): enforce local-only call history boundary | non |
| `feat/android-call-history-privacy-v2` | `d4d9c78aa755` | 2026-09-19 | teetee971 | divergente +2/-1236 | histoires_disjointes | test(android): lock call history privacy boundary | non |
| `feat/android-caller-id-provenance` | `db23de98f0ea` | 2026-09-19 | teetee971 | divergente +2/-1238 | histoires_disjointes | test(android): enforce caller ID provenance semantics | non |
| `feat/android-caller-id-provenance-ui` | `abc0d92e5386` | 2026-09-19 | teetee971 | divergente +1/-1235 | histoires_disjointes | feat(android): surface caller ID evidence provenance | non |
| `feat/android-caller-id-provenance-ui-v2` | `7bad7143e762` | 2026-09-19 | teetee971 | divergente +1/-1234 | histoires_disjointes | feat(android): replay caller ID provenance UI on latest main | non |
| `feat/android-caller-id-provenance-ui-v3` | `ba36f8b1d147` | 2026-09-19 | teetee971 | divergente +1/-1233 | histoires_disjointes | feat(android): replay caller ID provenance UI on latest main | non |
| `feat/android-caller-id-provenance-v2` | `f68733e07d1d` | 2026-09-19 | teetee971 | divergente +2/-1237 | histoires_disjointes | test(android): enforce caller ID provenance semantics | non |
| `feat/android-incall-controls-v3` | `b0c484203470` | 2026-09-19 | teetee971 | divergente +1/-1237 | histoires_disjointes | feat(android): expose bounded in-call state and controls | non |
| `feat/android-incall-controls-v4` | `ff49e67612c1` | 2026-09-19 | teetee971 | divergente +1/-1235 | histoires_disjointes | feat(android): add bounded in-call controls and snapshots | non |
| `feat/android-incall-state` | `9eee6a04d6c3` | 2026-09-19 | teetee971 | divergente +4/-1244 | histoires_disjointes | fix(android): harden in-call lifecycle and DTMF handling | non |
| `feat/android-incall-state-v2` | `40428dbf441d` | 2026-09-19 | teetee971 | divergente +1/-1242 | histoires_disjointes | feat(android): add bounded in-call state and controls | non |
| `feat/android-incall-ui-foundation` | `6cfea1ceddb2` | 2026-09-19 | teetee971 | divergente +3/-1237 | histoires_disjointes | feat(android): register Sentinel in-call activity | non |
| `feat/android-incall-ui-v2` | `3905fbbfe857` | 2026-09-19 | teetee971 | divergente +3/-1235 | histoires_disjointes | feat(android): register native in-call activity | non |
| `feat/android-incall-ui-v3` | `6d4d5e082bbd` | 2026-09-19 | teetee971 | divergente +2/-1233 | histoires_disjointes | Register bounded Sentinel in-call activity | non |
| `feat/android-incall-ui-v4` | `61ef63f22540` | 2026-09-19 | teetee971 | divergente +2/-1232 | histoires_disjointes | Register bounded Sentinel in-call activity | non |
| `feat/android-protection-mode` | `9dad8fab5972` | 2026-09-19 | teetee971 | divergente +2/-1238 | histoires_disjointes | test(android): lock protection mode privacy rules | non |
| `feat/android-protection-mode-v2` | `aa0eb8213dd9` | 2026-09-19 | teetee971 | divergente +2/-1237 | histoires_disjointes | test(android): lock protection mode privacy rules | non |
| `feat/android-protection-mode-v3` | `6b1625f5ab8a` | 2026-09-19 | teetee971 | divergente +2/-1236 | histoires_disjointes | test(android): lock protection mode privacy rules | non |
| `feat/android-protection-mode-v4` | `110fbb572559` | 2026-09-19 | teetee971 | divergente +2/-1235 | histoires_disjointes | feat(android): add fail-closed protection mode policy | non |
| `feat/android-protection-mode-v5` | `67b4753f8bf7` | 2026-09-19 | teetee971 | divergente +2/-1234 | histoires_disjointes | feat(android): replay fail-closed protection mode policy | non |
| `feat/android-protection-mode-v6` | `954515223c4f` | 2026-09-19 | teetee971 | divergente +2/-1232 | histoires_disjointes | Test fail-closed protection modes | non |
| `feat/android-protection-provenance` | `ff1a14f6e47e` | 2026-09-19 | teetee971 | divergente +2/-1242 | histoires_disjointes | test(android): enforce provenance evidence boundary | non |
| `feat/android-sms-local-risk` | `fb8f5a6ba3bc` | 2026-09-19 | teetee971 | divergente +2/-1246 | histoires_disjointes | feat(android): surface local SMS risk signals | non |
| `feat/android-sms-local-risk-v2` | `c7f822a945ec` | 2026-09-19 | teetee971 | divergente +1/-1242 | histoires_disjointes | feat(android): show local SMS risk signals in conversations | non |
| `feat/android-sms-local-risk-v3` | `1d5592a83e88` | 2026-09-19 | teetee971 | divergente +1/-1240 | histoires_disjointes | feat(android): show local SMS risk signals in conversations | non |
| `feat/android-sms-otp-indicator-v2` | `397a82c6ca7f` | 2026-09-19 | teetee971 | divergente +1/-1235 | histoires_disjointes | feat(android): surface local-only SMS OTP privacy indicator | non |
| `feat/android-sms-otp-indicator-v3` | `4537e0adb17f` | 2026-09-19 | teetee971 | divergente +1/-1234 | histoires_disjointes | feat(android): replay local-only SMS OTP indicator | non |
| `feat/android-sms-otp-indicator-v4` | `f89c1cef516d` | 2026-09-19 | teetee971 | divergente +1/-1232 | histoires_disjointes | Surface local-only OTP protection on current main | non |
| `feat/android-vpn-country-selection-policy` | `895e06311515` | 2026-09-19 | teetee971 | divergente +3/-1236 | histoires_disjointes | fix(android): avoid stale VPN gateway counts | non |
| `feat/android-vpn-country-selection-policy-v2` | `2f5234c2ea23` | 2026-09-19 | teetee971 | divergente +2/-1232 | histoires_disjointes | Test signed VPN country selection | non |
| `feat/call-screening-latency-current` | `ee9c79063eee` | 2026-09-18 | teetee971 | divergente +1/-1318 | histoires_disjointes | android: record call-screening response latency locally | non |
| `feat/call-screening-latency-current2` | `5a5b09842bbc` | 2026-09-18 | teetee971 | divergente +1/-1317 | histoires_disjointes | rebase: SentinelCallScreeningService.kt | non |
| `feat/call-screening-latency-current3` | `c39253bedef8` | 2026-09-18 | teetee971 | divergente +1/-1316 | histoires_disjointes | rebase: Record call-screening response latency on latest mai | non |
| `feat/call-screening-latency-evidence` | `6bb6d797245e` | 2026-09-17 | teetee971 | divergente +1/-1335 | histoires_disjointes | Record call-screening response latency after platform respon | non |
| `feat/caller-id-premium-ui-20260930` | `5456fc412e4f` | 2026-09-30 | teetee971 | contenue_dans_main +0/-649 | histoires_disjointes | Android UI: close remaining visual consistency gaps (#1344) | non |
| `feat/campaign-correlation-v3-20261002` | `d851448c9021` | 2026-10-02 | teetee971 | divergente +5/-582 | histoires_disjointes | test(intelligence): align campaign evidence with returned no | non |
| `feat/cnmc-spain-numbering-refresh-2026-10-01` | `6a733a61217f` | 2026-10-01 | teetee971 | divergente +3/-597 | histoires_disjointes | Phone intelligence: automate daily CNMC Spain refresh | non |
| `feat/collective-threat-graph-v2-20261002` | `f39b9b51fd51` | 2026-10-02 | teetee971 | divergente +8/-589 | histoires_disjointes | test(intelligence): cover graph auth stale adjacency and out | non |
| `feat/community-report-intelligence-wiring` | `adbc26a6d6eb` | 2026-10-17 | Thierry Naud | contient_main +1/-0 | histoires_disjointes | feat(intelligence): retain community report context | oui |
| `feat/contact-intelligence-wiring` | `2120ddf20be5` | 2026-10-17 | Thierry Naud | contient_main +2/-0 | histoires_disjointes | Merge remote-tracking branch 'origin/main' into feat/contact | oui |
| `feat/d1-apk-discovery-20261031` | `7ad6e5e01327` | 2026-10-31 | ton-username | contient_main +254/-0 | histoires_disjointes | fix(lab): remove unnecessary sensitive permissions | non |
| `feat/family-safety-policy-replay-v4` | `323d8102f158` | 2026-09-19 | teetee971 | divergente +2/-1209 | histoires_disjointes | Test assisted call safety policy boundaries | non |
| `feat/global-numbering-registry-replay-20261004` | `f0f0aa479af1` | 2026-10-04 | teetee971 | divergente +1/-463 | histoires_disjointes | feat(numbering): add canonical authority qualification regis | non |
| `feat/global-telecom-intelligence-d1-phone-core` | `e8ab9266822c` | 2026-10-19 | teetee971 | contient_main +562/-0 | histoires_disjointes | ci(android): gate candidate APK on email analyzer UI regress | oui |
| `feat/global-telecom-regulator-registry` | `74019b2ccbd1` | 2026-10-18 | teetee971 | contient_main +330/-0 | histoires_disjointes | test(telecom): validate registry schema, HTTPS provenance an | oui |
| `feat/incall-intelligence-ui` | `652c104d7e9d` | 2026-10-14 | teetee971 | divergente +6/-191 | histoires_disjointes | fix(ci): port API24 non-streaming qualification transport | oui |
| `feat/incall-intelligence-ui-active` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-actual` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-final` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-impl` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-mainwork` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-one` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-red` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-run` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-tdd` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-work` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-x` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/incall-intelligence-ui-z` | `2b9ae457ffe9` | 2026-10-13 | teetee971 | contenue_dans_main +0/-193 | histoires_disjointes | test(phone-core): remove redundant physical evidence contrac | non |
| `feat/integration-android-wiring-20261017` | `35d0bd3047dd` | 2026-10-17 | teetee971 | contient_main +16/-0 | histoires_disjointes | test(integration): require Android evidence wiring contracts | oui |
| `feat/integration-consolidated-20261017` | `55a60764f91b` | 2026-10-18 | Thierry Naud | contient_main +292/-0 | histoires_disjointes | fix(ci): align Android truth guards with current sources | oui |
| `feat/integration-foundation-20261017` | `61e0ba934e15` | 2026-10-17 | teetee971 | contient_main +14/-0 | histoires_disjointes | ci(integration): require integration product truth | oui |
| `feat/intelligence-core-cross-module-integration` | `1af12475dbfd` | 2026-10-17 | Thierry Naud | contient_main +13/-0 | histoires_disjointes | fix(caller-id): prepare reputation evidence before consent l | oui |
| `feat/itu-global-numbering-registry-2026-10-02` | `6cd75597bbee` | 2026-10-02 | teetee971 | divergente +4/-582 | histoires_disjointes | Merge branch 'main' into feat/itu-global-numbering-registry- | non |
| `feat/local-itu-numbering-lookup-2026-10-02` | `a0470a002647` | 2026-10-02 | teetee971 | divergente +4/-592 | histoires_disjointes | Merge current main for protected local lookup validation | non |
| `feat/mail-shield-pipeline-v1` | `6b253dc747ea` | 2026-09-19 | teetee971 | divergente +3/-1228 | histoires_disjointes | Add Sentinel Mail Shield local-first pipeline | non |
| `feat/mail-shield-relay-provenance-v1` | `0ff0aa917750` | 2026-09-19 | teetee971 | divergente +2/-1226 | histoires_disjointes | Test bounded relay provenance extraction | non |
| `feat/mms-incoming-provider-1532` | `7ab6c7755910` | 2026-10-03 | teetee971 | divergente +6/-508 | histoires_disjointes | fix(phone-core): scope MMS idempotence to delivery correlati | non |
| `feat/mms-intelligence-wiring` | `c43e23bc71c8` | 2026-10-17 | Thierry Naud | contient_main +1/-0 | histoires_disjointes | feat(intelligence): publish incoming MMS transport evidence | oui |
| `feat/nkom-norway-numbering-refresh-2026-10-01` | `00ae50da0636` | 2026-10-01 | teetee971 | divergente +3/-596 | histoires_disjointes | Phone intelligence: automate Nkom Norway refresh | non |
| `feat/phone-core-diagnostics-v1` | `160652bfd854` | 2026-09-19 | teetee971 | divergente +2/-1220 | histoires_disjointes | Test fail-closed Phone Core diagnostics | non |
| `feat/phone-core-diagnostics-v2` | `520d6318bf9d` | 2026-09-19 | teetee971 | divergente +2/-1219 | histoires_disjointes | Replay fail-closed Phone Core diagnostics on current main | non |
| `feat/phone-core-diagnostics-v3` | `c05d286c2a43` | 2026-09-19 | teetee971 | divergente +2/-1218 | histoires_disjointes | Replay Phone Core diagnostics on current main | non |
| `feat/phone-core-diagnostics-v4` | `463b2406797c` | 2026-09-19 | teetee971 | divergente +2/-1217 | histoires_disjointes | Replay Phone Core diagnostics after emergency guard | non |
| `feat/phone-core-emergency-guard-v1` | `4480944d54d1` | 2026-09-19 | teetee971 | divergente +2/-1219 | histoires_disjointes | Test emergency call bypass invariant | non |
| `feat/phone-core-family-policy-v1` | `d860a0ace704` | 2026-09-19 | teetee971 | divergente +2/-1217 | histoires_disjointes | Test voluntary assisted Phone Core safety profile | non |
| `feat/phone-core-family-policy-v2` | `99978fed1a60` | 2026-09-19 | teetee971 | divergente +2/-1216 | histoires_disjointes | Replay assisted Phone Core safety profile after unknown-call | non |
| `feat/phone-core-family-policy-v3` | `9a66ef083b09` | 2026-09-19 | teetee971 | divergente +2/-1215 | histoires_disjointes | Replay assisted Phone Core safety profile after diagnostics | non |
| `feat/phone-core-privacy-firewall-v1` | `20f39dd331a7` | 2026-09-19 | teetee971 | divergente +2/-1218 | histoires_disjointes | Test fail-closed Phone Core privacy firewall | non |
| `feat/phone-core-privacy-firewall-v2` | `fba17633fe80` | 2026-09-19 | teetee971 | divergente +2/-1217 | histoires_disjointes | Replay Phone Core privacy firewall after emergency guard | non |
| `feat/phone-core-privacy-firewall-v3` | `01027a6b082c` | 2026-09-19 | teetee971 | divergente +2/-1216 | histoires_disjointes | Replay Phone Core privacy firewall after unknown-caller poli | non |
| `feat/phone-core-privacy-firewall-v4` | `2b6c6182bea1` | 2026-09-19 | teetee971 | divergente +2/-1215 | histoires_disjointes | Replay Phone Core privacy firewall after diagnostics | non |
| `feat/phone-core-private-timeline-v1` | `8a4144bcffbe` | 2026-09-19 | teetee971 | divergente +2/-1220 | histoires_disjointes | Test bounded Phone Core event correlation | non |
| `feat/phone-core-private-timeline-v2` | `b6f1c73d1750` | 2026-09-19 | teetee971 | divergente +2/-1219 | histoires_disjointes | Replay private Phone Core timeline on current main | non |
| `feat/phone-core-private-timeline-v3` | `fe6cfbf61d0b` | 2026-09-19 | teetee971 | divergente +2/-1218 | histoires_disjointes | Replay private Phone Core timeline on current main | non |
| `feat/phone-core-private-timeline-v4` | `5a82e35865c8` | 2026-09-19 | teetee971 | divergente +2/-1217 | histoires_disjointes | Replay private Phone Core timeline after emergency guard | non |
| `feat/phone-core-private-timeline-v5` | `934f154f974b` | 2026-09-19 | teetee971 | divergente +2/-1216 | histoires_disjointes | Replay private Phone Core timeline after unknown-caller poli | non |
| `feat/phone-core-trust-foundation-v1` | `d96b2791be2c` | 2026-09-19 | teetee971 | divergente +2/-1221 | histoires_disjointes | Test Phone Core trust semantics | non |
| `feat/phone-core-unknown-policy-v1` | `e3c6a4962aa8` | 2026-09-19 | teetee971 | divergente +2/-1219 | histoires_disjointes | Test optional unknown caller policy | non |
| `feat/phone-core-unknown-policy-v2` | `7b97f110c733` | 2026-09-19 | teetee971 | divergente +2/-1218 | histoires_disjointes | Replay optional unknown-caller protection on current main | non |
| `feat/reputation-intelligence-wiring` | `2fb1cdfd01ec` | 2026-10-17 | Thierry Naud | contient_main +2/-0 | histoires_disjointes | Merge remote-tracking branch 'origin/main' into feat/reputat | oui |
| `feat/security-lab-apk-discovery-20261030` | `ba1696b07849` | 2026-10-31 | teetee971 | contient_main +7/-0 | histoires_disjointes | docs(security-lab): record verified navigation gap and relea | oui |
| `feat/sentinel-d1-3d-total-redesign` | `6be887adaa8c` | 2026-10-18 | Thierry Naud | contient_main +299/-0 | histoires_disjointes | docs(android): record final d1 apk qualification | oui |
| `feat/sentinel-d1-phase3-official-visuals` | `0242647fa6a1` | 2026-10-18 | Thierry Naud | contient_main +307/-0 | histoires_disjointes | test(android): update home visual contract for phase3 scene | oui |
| `feat/sentinel-d1-phase4-visual-fidelity` | `0944a1f46f05` | 2026-10-18 | Thierry Naud | contient_main +313/-0 | histoires_disjointes | fix(android): prevent legacy hero text truncation | oui |
| `feat/sms-analysis-intelligence-wiring` | `87341f55504d` | 2026-10-17 | Thierry Naud | contient_main +2/-0 | histoires_disjointes | Merge remote-tracking branch 'origin/main' into feat/sms-ana | oui |
| `feat/sms-local-contact-identity-20261013` | `41bcb7306e79` | 2026-10-13 | teetee971 | divergente +4/-243 | histoires_disjointes | feat(core): resolve local contacts in SMS conversations | oui |
| `feat/unified-number-card-caller-id-v1` | `c28bfc5a7818` | 2026-09-19 | teetee971 | divergente +1/-1208 | histoires_disjointes | Wire unified Sentinel Number Card into Caller ID UI | non |
| `feat/wifi-radio-survey-20261111` | `aecbc3c30013` | 2026-11-11 | teetee971 | contient_main +357/-0 | histoires_disjointes | Also keep debug JNI symbols in the wear app, including a bar | non |
| `feature/active-phone-security-v2` | `d359ad37f960` | 2026-09-18 | teetee971 | divergente +4/-1327 | histoires_disjointes | Merge branch 'main' into feature/active-phone-security-v2 | non |
| `feature/ad-tracker-filter-engine` | `02d35fd533eb` | 2026-09-18 | teetee971 | divergente +4/-1329 | histoires_disjointes | test: cover bounded filter-list parsing | non |
| `feature/ad-tracker-filter-engine-current` | `01b3aaaf1882` | 2026-09-18 | teetee971 | divergente +4/-1323 | histoires_disjointes | android: rebase ad/tracker filter core (DomainFilterPolicyTe | non |
| `feature/ad-tracker-filter-engine-rebased` | `fd229f231aab` | 2026-09-18 | teetee971 | divergente +4/-1328 | histoires_disjointes | android: rebase ad/tracker filter engine (DomainFilterListPa | non |
| `feature/ad-tracker-filter-engine-rebased2` | `9e5fc27097f3` | 2026-09-18 | teetee971 | divergente +4/-1327 | histoires_disjointes | android: rebase ad/tracker engine (DomainFilterListParserTes | non |
| `feature/android-mesh-client` | `6eecb241711f` | 2026-09-18 | teetee971 | divergente +11/-1304 | histoires_disjointes | Merge branch 'main' into feature/android-mesh-client | non |
| `feature/android-mesh-client-current` | `60a4167b4516` | 2026-09-18 | teetee971 | divergente +5/-1305 | histoires_disjointes | android: test one-time Mesh enrollment parsing | non |
| `feature/android-mesh-wireguard-identity` | `6d9eed28c60d` | 2026-09-18 | teetee971 | divergente +7/-1300 | histoires_disjointes | Merge branch 'main' into feature/android-mesh-wireguard-iden | non |
| `feature/android-mesh-wireguard-identity-current` | `7ec8527f2a91` | 2026-09-18 | teetee971 | divergente +1/-1303 | histoires_disjointes | Add encrypted Android Mesh WireGuard identity | non |
| `feature/android-mesh-wireguard-identity-current2` | `3ecb935aa60d` | 2026-09-18 | teetee971 | divergente +3/-1301 | histoires_disjointes | Merge branch 'main' into feature/android-mesh-wireguard-iden | non |
| `feature/android-preview-interactive` | `c75dd10160af` | 2026-09-18 | teetee971 | divergente +3/-1325 | histoires_disjointes | site: add accessible Android showcase interaction | non |
| `feature/android-protection-active-v1` | `c091ff56fb6e` | 2026-09-18 | teetee971 | divergente +21/-1330 | histoires_disjointes | Align public VPN contract tests with DNS-only implementation | non |
| `feature/android-vpn-status-ui` | `969ac08923dc` | 2026-09-19 | teetee971 | divergente +4/-1247 | histoires_disjointes | feat(android): expose VPN status from home | non |
| `feature/android-vpn-status-ui-main` | `5daf77bab086` | 2026-09-19 | teetee971 | divergente +6/-1246 | histoires_disjointes | fix(android): repair VPN home card Kotlin syntax | non |
| `feature/community-report-pending-current` | `ec0fd6ab39a4` | 2026-09-18 | teetee971 | divergente +9/-1317 | histoires_disjointes | docs: document server-side public report key derivation | non |
| `feature/community-report-pending-current2` | `9029283bad7e` | 2026-09-18 | teetee971 | divergente +11/-1316 | histoires_disjointes | docs: document explicit community moderation workflow | non |
| `feature/d1-global-audit-20261020` | `ca0b944fc79f` | 2026-10-22 | teetee971 | contient_main +189/-0 | histoires_disjointes | feat(d1): add crystalline microstructure lighting to GPU vol | oui |
| `feature/default-sms-client` | `29d76a3a13ac` | 2026-09-18 | teetee971 | divergente +19/-1330 | histoires_disjointes | Merge branch 'main' into feature/default-sms-client | non |
| `feature/default-sms-client-foundation` | `504abdbeacf2` | 2026-09-18 | teetee971 | divergente +11/-1328 | histoires_disjointes | test: prove staged SMS client cannot unlock ROLE_SMS | non |
| `feature/default-sms-client-v2` | `2cb57a7af6b0` | 2026-09-18 | teetee971 | divergente +6/-1330 | histoires_disjointes | Reapply SMS client SmsRoleMigrationPolicy.kt on current main | non |
| `feature/default-sms-handler-v1` | `3c0060183166` | 2026-09-18 | teetee971 | divergente +23/-1330 | histoires_disjointes | Align docs with default SMS client v1 | non |
| `feature/mesh-container-deployment` | `b926e1147cc5` | 2026-09-18 | teetee971 | divergente +5/-1304 | histoires_disjointes | Merge branch 'main' into feature/mesh-container-deployment | non |
| `feature/mesh-container-deployment-current` | `3e21788eef92` | 2026-09-18 | teetee971 | divergente +4/-1304 | histoires_disjointes | Rebase Mesh deployment: README.md | non |
| `feature/mesh-node-auth-nat-candidates` | `e9a63c6cea53` | 2026-09-18 | teetee971 | divergente +14/-1310 | histoires_disjointes | Document node authentication and NAT candidate flow | non |
| `feature/mesh-oidc-foundation` | `0e7410e63a9d` | 2026-09-18 | teetee971 | divergente +4/-1304 | histoires_disjointes | Document generic OIDC identity foundation | non |
| `feature/mesh-oidc-idtoken` | `68c761f93ed0` | 2026-09-18 | teetee971 | divergente +15/-1304 | histoires_disjointes | Test OIDC key-type and metadata URL hardening | non |
| `feature/mesh-oidc-idtoken-current2` | `f2e4c1d6fa20` | 2026-09-18 | teetee971 | divergente +1/-1303 | histoires_disjointes | Rebase hardened Mesh OIDC flow onto current main | non |
| `feature/mesh-overlay-addresses` | `858981cfa4de` | 2026-09-18 | teetee971 | divergente +5/-1304 | histoires_disjointes | Document explicit Mesh overlay addressing | non |
| `feature/mesh-overlay-addresses-current2` | `d7b39f7ac0ff` | 2026-09-18 | teetee971 | divergente +1/-1303 | histoires_disjointes | Rebase collision-safe Mesh overlay addressing onto current m | non |
| `feature/mesh-overlay-addresses-current3` | `b7a1cd591c2f` | 2026-09-18 | teetee971 | divergente +1/-1302 | histoires_disjointes | Rebase collision-safe Mesh overlay addressing onto TLS-enabl | non |
| `feature/mesh-relay-data-plane` | `b64bc70fc71b` | 2026-09-18 | teetee971 | divergente +18/-1307 | histoires_disjointes | Document bounded UDP relay data plane | non |
| `feature/mesh-scim-foundation` | `92dc42c6e1b7` | 2026-09-18 | teetee971 | divergente +4/-1304 | histoires_disjointes | Document read-only SCIM identity synchronization foundation | non |
| `feature/mesh-spiffe-foundation` | `abf6693b3ed6` | 2026-09-18 | teetee971 | divergente +5/-1304 | histoires_disjointes | Document SPIFFE workload and AI-agent identity foundation | non |
| `feature/mesh-spiffe-foundation-current` | `4029a044a491` | 2026-09-18 | teetee971 | divergente +6/-1298 | histoires_disjointes | Document fail-closed SPIFFE SVID verification boundary | non |
| `feature/mesh-x509-svid-proof` | `2326741b4ff1` | 2026-09-18 | teetee971 | divergente +20/-1297 | histoires_disjointes | Align X.509 SVID documentation with SPIFFE SAN semantics | non |
| `feature/mesh-x509-svid-verifier` | `e9ff3c080e5f` | 2026-09-18 | teetee971 | divergente +4/-1296 | histoires_disjointes | Merge branch 'main' into feature/mesh-x509-svid-verifier | non |
| `feature/mobile-protection-runtime-activation` | `2d381b841f60` | 2026-09-18 | teetee971 | divergente +6/-1330 | histoires_disjointes | test: remove Android JSONObject JVM-only false failures | non |
| `feature/mobile-protection-runtime-activation-rebased` | `ee7fe99278cc` | 2026-09-18 | teetee971 | divergente +3/-1329 | histoires_disjointes | android: rebase mobile protection runtime changes (PhoneSecu | non |
| `feature/sentinel-d1-official-ui-20261024` | `e848f0d546c2` | 2026-10-25 | teetee971 | contient_main +164/-0 | histoires_disjointes | ci(threat-intel): add read-only manual CVE V5 qualification  | non |
| `feature/sentinel-offline-mesh-foundation-20261024` | `cf892d5b0c8f` | 2026-10-24 | Thierry Naud | contient_main +134/-0 | histoires_disjointes | fix(apk): guard API 28 signing inspection | oui |
| `feature/sentinel-recording-vault-r1-20261028` | `7b4b84363fea` | 2026-10-28 | ton-username | contient_main +3/-0 | histoires_disjointes | feat(recording-vault): add isolated encrypted R2 storage and | non |
| `feature/sentinel-recording-vault-r3-persistence-20261028` | `aeadbbcf70fa` | 2026-10-28 | teetee971 | contient_main +4/-0 | histoires_disjointes | fix(recording-vault): harden R3 persistence and add reproduc | oui |
| `feature/smart-home-global-ecosystems` | `5ecbde517e6a` | 2026-09-18 | teetee971 | divergente +3/-1257 | histoires_disjointes | Document global smart-home ecosystem coverage | non |
| `feature/sms-conversation-threads` | `fe05cf300963` | 2026-09-19 | teetee971 | divergente +1/-1248 | histoires_disjointes | feat(android): add role-gated SMS thread summaries | non |
| `feature/sms-conversations-current` | `af6a51f6223f` | 2026-09-18 | teetee971 | divergente +5/-1316 | histoires_disjointes | Merge branch 'main' into feature/sms-conversations-current | non |
| `feature/sms-routing-safety-current` | `00ee7df7c310` | 2026-09-18 | teetee971 | divergente +6/-1315 | histoires_disjointes | test: require true multi-SIM before SMS role unlock | non |
| `feature/sms-status-ui-current` | `283ecb6b66bb` | 2026-09-18 | teetee971 | divergente +5/-1315 | histoires_disjointes | android: add default SMS status labels | non |
| `feature/spiffe-crl-validation` | `9cdd4d045d75` | 2026-09-18 | teetee971 | divergente +35/-1292 | histoires_disjointes | Test persisted SPIFFE CRL rotation and anti-rollback | non |
| `feature/spiffe-trust-bundle-rotation` | `771f833a12cc` | 2026-09-18 | teetee971 | divergente +30/-1295 | histoires_disjointes | Document persisted sequence and full-set SPIFFE redaction se | non |
| `feature/spiffe-workload-api-boundary` | `896943eb951a` | 2026-09-18 | teetee971 | divergente +38/-1295 | histoires_disjointes | Sync Workload API branch with base: server.test.js | non |
| `feature/spiffe-workload-api-boundary-current` | `76ffb041449f` | 2026-09-18 | teetee971 | divergente +16/-1294 | histoires_disjointes | Document hardened Workload API endpoint transport rules | non |
| `feature/spiffe-workload-grpc-transport` | `d9b33f4f7941` | 2026-09-18 | teetee971 | divergente +11/-1294 | histoires_disjointes | Document rebuilt SPIFFE Workload API boundary | non |
| `feature/spiffe-workload-grpc-transport-current` | `9c978a5d7764` | 2026-09-18 | teetee971 | divergente +22/-1294 | histoires_disjointes | Document native SPIFFE Workload gRPC bundle transport | non |
| `feature/spiffe-workload-grpc-transport-current2` | `d121ae54141a` | 2026-09-18 | teetee971 | divergente +19/-1293 | histoires_disjointes | Document runtime activation of SPIFFE Workload bundle sync | non |
| `feature/system-doctor-malware-foundation-20261002` | `cfbd481331b2` | 2026-10-02 | teetee971 | divergente +108/-594 | histoires_disjointes | security(android): define server-side reputation boundary | non |
| `feature/update-site-vpn-status-current` | `30e0b24ab1fa` | 2026-09-18 | teetee971 | divergente +5/-1259 | histoires_disjointes | Update public VPN claim regression tests | non |
| `feature/update-vanguard` | `5ccca0ccde07` | 2026-09-14 | root | divergente +26/-1389 | histoires_disjointes | ci: strip inline version comments from pinned actions | non |
| `feature/voice-studio-addon-20260930` | `29300749e90d` | 2026-09-30 | teetee971 | divergente +16/-646 | histoires_disjointes | fix(voice): harden preview stop, errors and memory cleanup | non |
| `feature/voice-studio-addon-v2-20260930` | `75c904fd47bb` | 2026-09-30 | teetee971 | divergente +23/-645 | histoires_disjointes | ux(voice): start preview immediately after explicit micropho | non |
| `feature/vpn-android-runtime-coordinator-current` | `32b537549432` | 2026-09-18 | teetee971 | divergente +3/-1262 | histoires_disjointes | Merge branch 'main' into feature/vpn-android-runtime-coordin | non |
| `feature/vpn-catalog-https-transport-current` | `77d055562f6d` | 2026-09-18 | teetee971 | divergente +2/-1263 | histoires_disjointes | Test VPN gateway catalog transport guards | non |
| `feature/vpn-dns-pinning` | `c64fe117390a` | 2026-09-18 | teetee971 | divergente +2/-1319 | histoires_disjointes | test: enforce gateway DNS pinning for WireGuard | non |
| `feature/vpn-dns-pinning-current` | `60f3fb990404` | 2026-09-18 | teetee971 | divergente +2/-1318 | histoires_disjointes | android: rebase VPN DNS pinning (SentinelVpnControllerTest.k | non |
| `feature/vpn-france-scaleway-iac-current` | `174d0128a0f9` | 2026-09-18 | teetee971 | divergente +5/-1264 | histoires_disjointes | Scan Terraform files for hardcoded secrets | non |
| `feature/vpn-gateway-bootstrap` | `61419fda0827` | 2026-09-18 | teetee971 | divergente +1/-1327 | histoires_disjointes | ops: add hardened WireGuard gateway bootstrap | non |
| `feature/vpn-gateway-bootstrap-current` | `d42d1e98b0f3` | 2026-09-18 | teetee971 | divergente +2/-1323 | histoires_disjointes | docs: define VPN gateway bootstrap safety and acceptance gat | non |
| `feature/vpn-gateway-bootstrap-current2` | `0239536cfe43` | 2026-09-18 | teetee971 | divergente +5/-1321 | histoires_disjointes | ci: validate VPN gateway bootstrap shell syntax | non |
| `feature/vpn-gateway-filtering-dns` | `906b9428cadd` | 2026-09-18 | teetee971 | divergente +12/-1320 | histoires_disjointes | ci: test VPN DNS blocklist renderer | non |
| `feature/vpn-lease-state-store-current` | `5ae20dabe1ba` | 2026-09-18 | teetee971 | divergente +3/-1264 | histoires_disjointes | Classify VPN lease state store | non |
| `feature/vpn-lease-state-store-v2` | `77087d04e459` | 2026-09-18 | teetee971 | divergente +3/-1262 | histoires_disjointes | Classify VPN lease state store on current main | non |
| `feature/vpn-provisioning-runtime-entrypoint-current` | `52be3c30bd85` | 2026-09-18 | teetee971 | divergente +5/-1265 | histoires_disjointes | Remove stale VPN provisioning server orphan classification | non |
| `feature/vpn-provisioning-runtime-entrypoint-current-v2` | `22aaf51146a2` | 2026-09-18 | teetee971 | divergente +5/-1259 | histoires_disjointes | Document VPN provisioning runtime | non |
| `feature/vpn-ui-current` | `4690f1de8b89` | 2026-09-18 | teetee971 | divergente +5/-1315 | histoires_disjointes | android: add VPN screen labels | non |
| `feature/vpn-ui-current2` | `67a1021f38eb` | 2026-09-18 | teetee971 | divergente +5/-1314 | histoires_disjointes | Rebase VPN UI: VpnSecurityScreen.kt | non |
| `feature/vpn-ui-current3` | `3951349cb94b` | 2026-09-18 | teetee971 | divergente +5/-1314 | histoires_disjointes | android: rebase VPN UI (strings.xml) | non |
| `feature/vpn-ui-current4` | `57859d2ddeb6` | 2026-09-18 | teetee971 | divergente +7/-1275 | histoires_disjointes | Merge branch 'main' into feature/vpn-ui-current4 | non |
| `feature/whatsapp-click-to-chat-df11b2c` | `a0b70c988c7f` | 2026-09-30 | teetee971 | divergente +7/-625 | histoires_disjointes | WhatsApp: fail safely when external handoff is unavailable | non |
| `finalize/release-evidence-replay-a2ab` | `194b6afb11b3` | 2026-10-01 | teetee971 | divergente +1/-610 | histoires_disjointes | Finalization: bind Android release evidence and eliminate re | non |
| `fix-expired-block-metadata` | `eaa17a160113` | 2026-09-21 | teetee971 | divergente +1/-1117 | histoires_disjointes | Fix expired block metadata filtering | non |
| `fix-expired-block-metadata-v2` | `6cd6c7b288d8` | 2026-09-21 | teetee971 | divergente +1/-1116 | histoires_disjointes | Fix expired call blocks remaining active | non |
| `fix/android-cleaner-doctor` | `d120cef62929` | 2026-11-07 | teetee971 | contient_main +274/-0 | histoires_disjointes | Record cleaner host qualification, artifact hashes and physi | oui |
| `fix/android-dialer-legacy-role-result` | `baade3fbd7c9` | 2026-09-19 | teetee971 | divergente +1/-1242 | histoires_disjointes | fix(android): resume pending call after legacy dialer role r | non |
| `fix/android-emulator-truth-gate-20261006` | `76e3610149ac` | 2026-10-06 | teetee971 | divergente +14/-302 | histoires_disjointes | test(android-ci): reproduce progress-only adb pull aborts | oui |
| `fix/android-id-lifecycle-doc-20261012` | `f09e66f36348` | 2026-10-12 | teetee971 | divergente +67/-295 | histoires_disjointes | docs(android): clarify ANDROID_ID lifecycle and enrollment l | non |
| `fix/android-id-scope-main-20261012` | `aaa96b110f75` | 2026-10-12 | teetee971 | contenue_dans_main +0/-293 | histoires_disjointes | fix(phone-core): stabilize emulator Telecom qualification | non |
| `fix/android-legacy-dialer-role-v2` | `762a7e09054a` | 2026-09-19 | teetee971 | divergente +1/-1236 | histoires_disjointes | fix(android): resume pending call after legacy dialer select | non |
| `fix/android-legacy-dialer-role-v3` | `9294a8fd9db5` | 2026-09-19 | teetee971 | divergente +1/-1235 | histoires_disjointes | fix(android): resume pending call after legacy dialer select | non |
| `fix/android-legacy-dialer-role-v4` | `d2a9b7361c35` | 2026-09-19 | teetee971 | divergente +1/-1234 | histoires_disjointes | fix(android): replay legacy dialer role callback on latest m | non |
| `fix/android-optional-hardware-play-20261022` | `68012b4f7ff5` | 2026-10-22 | teetee971 | contient_main +1/-0 | histoires_disjointes | fix(android): declare optional hardware explicitly for Play  | oui |
| `fix/android-release-existing-signing-secrets-20261022` | `b67f8dbaf709` | 2026-10-22 | teetee971 | contient_main +1/-0 | histoires_disjointes | fix(android-release): map existing repository signing secret | oui |
| `fix/android-upgrade-safe-apk-20261006` | `d9a1c45d3059` | 2026-10-06 | teetee971 | divergente +13/-302 | histoires_disjointes | test(release): lock Phone Core and scoped security qualifica | oui |
| `fix/api37-emulation-truth-gate` | `5719103f070c` | 2026-10-06 | teetee971 | contenue_dans_main +0/-302 | histoires_disjointes | fix(phone-core): harden shadow qualification and runtime tru | non |
| `fix/api37-incoming-answer-sync` | `7dfefcf2f89e` | 2026-10-07 | teetee971 | divergente +151/-301 | histoires_disjointes | fix(phone-core): start timeout at causal answer transaction | oui |
| `fix/api37-limited-must-fail-merge-gate` | `2ec7cb554a8b` | 2026-10-13 | teetee971 | divergente +43/-291 | histoires_disjointes | chore(ci): restore workflow newline | oui |
| `fix/arcep-refresh-noop-churn` | `cb8178663570` | 2026-09-17 | github-actions[bot] | contenue_dans_main +0/-1341 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data (#468) | non |
| `fix/audit-build-identity` | `e48162609498` | 2026-09-20 | teetee971 | divergente +1/-1167 | histoires_disjointes | Expose Android version code in local security audit | non |
| `fix/audit-product-lifecycle` | `c1ff5ef559bb` | 2026-10-03 | teetee971 | divergente +1/-532 | histoires_disjointes | fix: enforce cached rule expiry and durable lifecycle mutati | non |
| `fix/backup-excludes-remote-reputation-consent-20261013` | `29f2df93299b` | 2026-10-13 | teetee971 | divergente +2/-250 | histoires_disjointes | fix(privacy): never export caller reputation consent | non |
| `fix/bluetooth-no-false-randomized-address-20261019` | `e28bce77aff5` | 2026-10-19 | teetee971 | contient_main +2/-0 | histoires_disjointes | fix(ui): remove unsubstantiated Bluetooth address randomizat | non |
| `fix/bluetooth-scan-terminal-callback-20261019` | `b543f517163c` | 2026-10-19 | teetee971 | contient_main +8/-0 | histoires_disjointes | fix(ui): consolidate removal of unsupported BLE randomizatio | oui |
| `fix/build-native-adb-install-timeout-20261016-b` | `2c34735a3ee6` | 2026-10-16 | teetee971 | divergente +1/-15 | histoires_disjointes | fix(ci): bound both Build Native emulator APK installs | non |
| `fix/build-native-adb-install-timeout-20261017` | `935344423b2a` | 2026-10-16 | teetee971 | contenue_dans_main +0/-0 | histoires_disjointes | Merge pull request #1677 from teetee971/fix/community-report | non |
| `fix/build-native-adb-install-timeout-20261017-0707` | `935344423b2a` | 2026-10-16 | teetee971 | contenue_dans_main +0/-0 | histoires_disjointes | Merge pull request #1677 from teetee971/fix/community-report | non |
| `fix/build-native-adb-install-timeout-20261017-1413` | `935344423b2a` | 2026-10-16 | teetee971 | contenue_dans_main +0/-0 | histoires_disjointes | Merge pull request #1677 from teetee971/fix/community-report | non |
| `fix/build-native-adb-install-timeout-main-20261016` | `2b855662ed92` | 2026-10-17 | teetee971 | contient_main +2/-0 | histoires_disjointes | test(ci): register bounded Build Native adb install contract | oui |
| `fix/call-screening-cold-start-warmup-20261016` | `ee47d3f89cca` | 2026-10-16 | teetee971 | divergente +10/-27 | histoires_disjointes | Merge main into cold-start Phone Core fix without changing s | oui |
| `fix/call-screening-keystore-critical-path` | `09db71e9ec5b` | 2026-09-17 | teetee971 | divergente +6/-1339 | histoires_disjointes | Gate screening callback against Keystore regressions | non |
| `fix/call-screening-post-response-persistence-f832ecef` | `326f32523c47` | 2026-09-29 | teetee971 | divergente +1/-668 | histoires_disjointes | Fix: isolate post-response call timeline persistence failure | non |
| `fix/ci-bound-adb-reconnect` | `7bfc50ac672f` | 2026-10-17 | Thierry Naud | divergente +6/-8 | histoires_disjointes | test(ci): retain bounded recovery contract on main sync | oui |
| `fix/ci-bound-api24-adb-20261012` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/ci-bound-build-native-adb-install` | `12549bc1d100` | 2026-10-12 | teetee971 | divergente +5/-291 | histoires_disjointes | chore(ci): preserve Build Native formatting | non |
| `fix/ci-codeql-poll-60s-20261014-0507` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/ci-codeql-poll-rate-20261014` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/codeql-bounded-pr-gate-polling-20261014` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/community-country-ascii-before-uppercase-20261016` | `490be0cd694e` | 2026-10-16 | teetee971 | contenue_dans_main +0/-27 | histoires_disjointes | Merge pull request #1672 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-ascii-before-uppercase-20261016-1205` | `0b0e4ade86d8` | 2026-10-16 | teetee971 | contenue_dans_main +0/-18 | histoires_disjointes | Merge pull request #1683 from teetee971/fix/phone-core-api36 | non |
| `fix/community-country-ascii-egress-20261015-1908` | `2c0693ba3642` | 2026-10-15 | teetee971 | contenue_dans_main +0/-62 | histoires_disjointes | poc(ci): add REA 6.4 independent Android evidence lane (#166 | non |
| `fix/community-country-ascii-egress-20261016-0313` | `ed5e01f27464` | 2026-10-16 | teetee971 | contenue_dans_main +0/-29 | histoires_disjointes | Merge pull request #1669 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-ascii-egress-20261016-0512` | `ed5e01f27464` | 2026-10-16 | teetee971 | contenue_dans_main +0/-29 | histoires_disjointes | Merge pull request #1669 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-ascii-egress-main-20261016-0714` | `ed5e01f27464` | 2026-10-16 | teetee971 | contenue_dans_main +0/-29 | histoires_disjointes | Merge pull request #1669 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-ascii-pre-normalization-20261016-0606` | `ed5e01f27464` | 2026-10-16 | teetee971 | contenue_dans_main +0/-29 | histoires_disjointes | Merge pull request #1669 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-ascii-pre-normalize-20261016-1413` | `222629672eec` | 2026-10-16 | teetee971 | contenue_dans_main +0/-15 | histoires_disjointes | Merge pull request #1685 from teetee971/fix/production-gate- | non |
| `fix/community-country-ascii-prevalidate-20261016-1305` | `222629672eec` | 2026-10-16 | teetee971 | contenue_dans_main +0/-15 | histoires_disjointes | Merge pull request #1685 from teetee971/fix/production-gate- | non |
| `fix/community-country-ascii-prevalidation-20261016-0813` | `490be0cd694e` | 2026-10-16 | teetee971 | contenue_dans_main +0/-27 | histoires_disjointes | Merge pull request #1672 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-strict-ascii-20261015-2105` | `2c0693ba3642` | 2026-10-15 | teetee971 | contenue_dans_main +0/-62 | histoires_disjointes | poc(ci): add REA 6.4 independent Android evidence lane (#166 | non |
| `fix/community-country-strict-ascii-20261016-1009` | `55b9ccc6d2e2` | 2026-10-16 | teetee971 | contenue_dans_main +0/-22 | histoires_disjointes | Merge pull request #1674 from teetee971/fix/phone-decision-j | non |
| `fix/community-country-strict-ascii-egress-20261016-1505` | `d4a1f865496a` | 2026-10-16 | teetee971 | contenue_dans_main +0/-12 | histoires_disjointes | Merge pull request #1675 from teetee971/fix/network-flow-bou | non |
| `fix/community-country-strict-ascii-main-20261016-0405` | `ed5e01f27464` | 2026-10-16 | teetee971 | contenue_dans_main +0/-29 | histoires_disjointes | Merge pull request #1669 from teetee971/codex/visual-wave1-f | non |
| `fix/community-country-validate-main-20261014-1214` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/community-country-validation-20261015-1706` | `2c0693ba3642` | 2026-10-15 | teetee971 | contenue_dans_main +0/-62 | histoires_disjointes | poc(ci): add REA 6.4 independent Android evidence lane (#166 | non |
| `fix/community-recipient-country-ascii-current-main-20261016` | `6b6763c26c7c` | 2026-10-16 | teetee971 | contenue_dans_main +0/-4 | histoires_disjointes | Merge pull request #1676 from teetee971/fix/email-header-fol | non |
| `fix/community-report-ascii-egress-20261016` | `2c0693ba3642` | 2026-10-15 | teetee971 | contenue_dans_main +0/-62 | histoires_disjointes | poc(ci): add REA 6.4 independent Android evidence lane (#166 | non |
| `fix/community-report-country-ascii-egress-20261016-1108` | `0b0e4ade86d8` | 2026-10-16 | teetee971 | contenue_dans_main +0/-18 | histoires_disjointes | Merge pull request #1683 from teetee971/fix/phone-core-api36 | non |
| `fix/community-report-country-contract-20261013` | `14ae188bc070` | 2026-10-13 | teetee971 | divergente +2/-291 | histoires_disjointes | fix(community): validate recipient country before egress | non |
| `fix/community-report-country-main-20261014-1306` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/community-report-country-replay-20261014-1505` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/community-report-country-validation-20261015-0713` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/community-report-country-validation-20261015-1111` | `864749e7ca6c` | 2026-10-15 | teetee971 | contenue_dans_main +0/-77 | histoires_disjointes | Merge pull request #1658 from teetee971/feat/full-visual-ref | non |
| `fix/community-report-country-validation-20261015-1212` | `b64e209a5546` | 2026-10-15 | teetee971 | contenue_dans_main +0/-75 | histoires_disjointes | Merge pull request #1662 from teetee971/fix/callscreening-pr | non |
| `fix/community-report-country-validation-20261015-1308` | `818458be5b60` | 2026-10-15 | teetee971 | contenue_dans_main +0/-72 | histoires_disjointes | Merge pull request #1663 from teetee971/test/callscreening-p | non |
| `fix/community-report-country-validation-20261015-1408` | `2c0693ba3642` | 2026-10-15 | teetee971 | contenue_dans_main +0/-62 | histoires_disjointes | poc(ci): add REA 6.4 independent Android evidence lane (#166 | non |
| `fix/community-report-country-validation-20261015-1615` | `2c0693ba3642` | 2026-10-15 | teetee971 | contenue_dans_main +0/-62 | histoires_disjointes | poc(ci): add REA 6.4 independent Android evidence lane (#166 | non |
| `fix/contact-name-number-detection` | `467988847f1d` | 2026-09-20 | teetee971 | divergente +1/-1164 | histoires_disjointes | Preserve contact names that contain digits | non |
| `fix/ctu-bounded-official-refresh-2026-10-02` | `e3399ef45f93` | 2026-10-02 | teetee971 | divergente +2/-594 | histoires_disjointes | fix(numbering): retain fail-closed CTU diagnostics with reso | non |
| `fix/d1-apk-process-guard-20261023` | `c7db265a03da` | 2026-10-23 | teetee971 | contient_main +39/-0 | histoires_disjointes | fix(android): restrict exported SMS composer prefill to SEND | oui |
| `fix/d1-auth0-voice-revocation-20261023` | `6ca71a3a7afb` | 2026-10-23 | teetee971 | contient_main +64/-0 | histoires_disjointes | test(android): ensure PTT close exception cannot skip Voice  | oui |
| `fix/d1-home-hero-priority-20261020` | `383a2afd1c73` | 2026-10-20 | teetee971 | contient_main +3/-0 | histoires_disjointes | fix(d1): keep primary Phone Core action above hero per sourc | oui |
| `fix/d1-incall-active-notification-20261027` | `e8393f93758f` | 2026-10-27 | teetee971 | contient_main +194/-0 | histoires_disjointes | fix(phone): reuse verified launcher resource for call action | non |
| `fix/d1-mms-pdu-readable-gate-20261027` | `553130045bc5` | 2026-10-27 | teetee971 | contient_main +195/-0 | histoires_disjointes | fix(mms): fail closed on unreadable or corrupted staged PDU  | non |
| `fix/d1-phone-messages-conformity-20261029` | `454c9c1cf4c1` | 2026-10-30 | Thierry Naud | contient_main +240/-0 | histoires_disjointes | docs(s24): record outgoing call UI evidence and InCall photo | oui |
| `fix/dialer-contact-compose-state` | `4efddf577168` | 2026-09-20 | teetee971 | divergente +1/-1164 | histoires_disjointes | Avoid contact state mutation during Compose rendering | non |
| `fix/dialer-contact-compose-state-v2` | `6d2361c5f08c` | 2026-09-20 | teetee971 | divergente +1/-1163 | histoires_disjointes | Avoid contact state mutation during Dialer composition | non |
| `fix/disposable-instrumentation-guard` | `6a90af97a22f` | 2026-11-07 | teetee971 | contient_main +278/-0 | histoires_disjointes | Deliver verified test-only local signing kit without main AP | oui |
| `fix/duplicate-hash-nullsafe-main-20261009` | `bce38981c272` | 2026-10-09 | teetee971 | divergente +2/-301 | histoires_disjointes | test(android): cover missing duplicate SHA-256 evidence | non |
| `fix/duplicate-hash-nullsafe-main-20261010` | `5629cbc71983` | 2026-10-10 | teetee971 | divergente +2/-300 | histoires_disjointes | test(android): cover missing and contradictory duplicate evi | non |
| `fix/duplicate-hash-nullsafe-main-20261010-1610` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/duplicate-hash-nullsafe-main-20261010-r2` | `d4b7320c9760` | 2026-10-10 | teetee971 | contenue_dans_main +0/-299 | histoires_disjointes | feat(voice): add fail-closed client push-to-talk gate | non |
| `fix/duplicate-hash-nullsafe-main-20261010-r3` | `6783f6aa9c15` | 2026-10-10 | teetee971 | contenue_dans_main +0/-298 | histoires_disjointes | docs(voice): define fail-closed service session contract | non |
| `fix/duplicate-hash-nullsafe-main-20261010-r4` | `27180d7d25fa` | 2026-10-10 | teetee971 | contenue_dans_main +0/-297 | histoires_disjointes | fix(ci): harden Android emulator qualification watchdog | non |
| `fix/email-header-folded-length-20261011` | `d662fe856f17` | 2026-10-11 | teetee971 | divergente +2/-295 | histoires_disjointes | test(android): cover unfolded email header length bound | non |
| `fix/esbuild-audit-279` | `9f9d04503c8f` | 2026-09-07 | teetee971 | contenue_dans_main +0/-1414 | histoires_disjointes | chore(ci): remove obsolete MSDO placeholder (#358) | non |
| `fix/favorites-region-identity-20261004` | `b71c3d67023d` | 2026-10-04 | teetee971 | divergente +104/-463 | histoires_disjointes | test(phone-core): cover region-aware favorite identity | oui |
| `fix/geointel-multisource` | `30948810f50a` | 2026-11-08 | teetee971 | contient_main +289/-0 | histoires_disjointes | Record automatic GeoIntel preview deployment and verified Cl | oui |
| `fix/geointel-source-boundaries` | `62c451b4604f` | 2026-10-02 | teetee971 | divergente +7/-575 | histoires_disjointes | Merge main into GeoIntel production hardening | non |
| `fix/gradle10-groovy-chatgpt-20261022` | `c4d0eb532e0e` | 2026-10-22 | teetee971 | contient_main +2/-0 | histoires_disjointes | fix(android): replace deprecated Groovy property syntax | oui |
| `fix/in-call-ui-launch-20260921` | `2da5b9a12887` | 2026-09-21 | teetee971 | divergente +1/-1108 | histoires_disjointes | Phone: surface Sentinel in-call UI from InCallService | non |
| `fix/incall-dialpad-visible-20261013` | `07b83122a47e` | 2026-10-13 | teetee971 | divergente +3/-243 | histoires_disjointes | fix(phone): keep active-call dialpad immediately visible | oui |
| `fix/incall-evidence-persistence-de5e0b9e` | `1b5281511cc3` | 2026-09-29 | teetee971 | divergente +1/-667 | histoires_disjointes | Fix: isolate in-call evidence persistence failures | non |
| `fix/incall-evidence-persistence-f60a0c11` | `b0e42c756fb1` | 2026-09-29 | teetee971 | divergente +1/-666 | histoires_disjointes | Fix: isolate in-call evidence persistence failures | non |
| `fix/incoming-mms-canonical-body-1532` | `81cf6547aa48` | 2026-10-03 | teetee971 | divergente +2/-502 | histoires_disjointes | test(phone-core): lock canonical MMS body offset decoding | non |
| `fix/license-placeholder-20260930` | `dddaaf72b5e8` | 2026-10-01 | teetee971 | divergente +6/-619 | histoires_disjointes | test(pricing): reject escaped newline markup regressions | non |
| `fix/mail-relay-no-dns-numeric-parser-20261011-1608` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/mail-relay-numeric-ip-only-20261011` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/mail-relay-numeric-ip-only-20261011-1506` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/mail-relay-numeric-only-20261011-1706` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/mms-attachment-ui-1532` | `cd3679a2aa53` | 2026-10-03 | teetee971 | divergente +16/-512 | histoires_disjointes | test(phone-core): lock MMS attachment bounds and format chec | non |
| `fix/mms-auxiliary-effects-f60a0c11` | `aa6da3062900` | 2026-09-29 | teetee971 | divergente +1/-666 | histoires_disjointes | Fix: isolate MMS timeline persistence from delivery truth | non |
| `fix/mms-download-durability-20261111` | `efe64c5b707d` | 2026-11-11 | teetee971 | contient_main +324/-0 | histoires_disjointes | Journal an incoming MMS digest only after the private copy i | non |
| `fix/mms-download-timeline-f60a0c11` | `6ae19e1d8e91` | 2026-09-29 | teetee971 | divergente +1/-666 | histoires_disjointes | Fix: isolate downloaded MMS timeline persistence | non |
| `fix/mms-finalization-p0-durability-20261111` | `de388eaf0e47` | 2026-11-11 | teetee971 | contient_main +327/-0 | histoires_disjointes | fix(mms): lock prune and journal removal behind confirmed re | non |
| `fix/mms-image-compression-v23` | `1d87e205f125` | 2026-11-08 | teetee971 | contient_main +281/-0 | histoires_disjointes | Save complete 8 November report, source backup links and red | oui |
| `fix/mms-journal-instance-atomicity-20261022` | `416cec507d28` | 2026-10-22 | teetee971 | contient_main +7/-0 | histoires_disjointes | test(android): concurrent MMS journal claims across instance | oui |
| `fix/mms-outgoing-journal-replay-guard-20261022` | `111a618dcbbc` | 2026-10-22 | teetee971 | contient_main +5/-0 | histoires_disjointes | test(android): instrument outgoing MMS journal replay preser | oui |
| `fix/mms-provider-persistence-1455` | `71c4482a9f1a` | 2026-10-03 | teetee971 | divergente +15/-513 | histoires_disjointes | fix(phone-core): distinguish pre-transport MMS failures from | non |
| `fix/mms-provider-persistence-1455-v2` | `d54fcb4a2003` | 2026-10-03 | teetee971 | divergente +10/-512 | histoires_disjointes | fix(phone-core): bound and monotonicize MMS provider journal | non |
| `fix/mms-provider-role-boundary-20261022` | `b4e239b73699` | 2026-10-22 | teetee971 | contient_main +3/-0 | histoires_disjointes | fix(android): return non-null Boolean from incoming MMS stre | oui |
| `fix/mms-received-thumbnails-20261105` | `6315d3da7a62` | 2026-11-05 | ton-username | contient_main +223/-0 | histoires_disjointes | feat(mms): add tested thumbnail decoder and validation evide | non |
| `fix/mms-send-strict-ttl-1455` | `c6e9585037a9` | 2026-10-03 | teetee971 | divergente +3/-511 | histoires_disjointes | test(phone-core): lock token-scoped MMS cleanup deadlines | non |
| `fix/network-behavior-destination-bound-20261016-2014` | `ed66458c0464` | 2026-10-16 | teetee971 | divergente +1/-4 | histoires_disjointes | fix(network): reject overlong behavior destinations without  | non |
| `fix/network-behavior-destination-bound-20261016-2112` | `0cc4616af3a6` | 2026-10-16 | teetee971 | contient_main +1/-0 | histoires_disjointes | fix(network): reject overlong behavior destinations without  | oui |
| `fix/network-behavior-destination-bound-main-20261016` | `edd199dd2a16` | 2026-10-17 | teetee971 | contient_main +1/-0 | histoires_disjointes | test(network): reject overlong behavior destination aliasing | oui |
| `fix/network-behavior-endpoint-bounds-20261014` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/network-behavior-endpoint-bounds-20261014-1010` | `12dd76243e76` | 2026-10-14 | teetee971 | divergente +2/-290 | histoires_disjointes | test(network): prove endpoint length boundaries in local bas | non |
| `fix/network-bonded-wearables-20261020` | `5624aa4f618d` | 2026-10-20 | teetee971 | contient_main +15/-0 | histoires_disjointes | test(bluetooth): verify distinct inventory statuses and empt | oui |
| `fix/network-flow-bounds-main-20261012-0214` | `e67b151e910d` | 2026-10-12 | teetee971 | divergente +2/-295 | histoires_disjointes | test(network): cover saturated destination totals and overlo | non |
| `fix/network-flow-bounds-main-20261012-0710` | `4cad1a671221` | 2026-10-12 | teetee971 | divergente +2/-294 | histoires_disjointes | test(network): cover flow counter saturation and endpoint bo | non |
| `fix/network-flow-bounds-main-20261012-0717` | `710615edb210` | 2026-10-12 | teetee971 | contenue_dans_main +0/-294 | histoires_disjointes | feat: harden Android release flow and public disclosures (#1 | non |
| `fix/network-flow-bounds-main-20261012-1018` | `aaa96b110f75` | 2026-10-12 | teetee971 | contenue_dans_main +0/-293 | histoires_disjointes | fix(phone-core): stabilize emulator Telecom qualification | non |
| `fix/network-flow-bounds-main-20261012-1514` | `94ad50a80baf` | 2026-10-12 | teetee971 | contenue_dans_main +0/-292 | histoires_disjointes | fix(voice): bind sessions to authenticated device rooms | non |
| `fix/network-flow-bounds-main-20261012-1618` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-flow-bounds-main-20261012-1810` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-flow-bounds-main-20261012-1914` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-flow-bounds-main-20261012-1948` | `509e1acd4f1b` | 2026-10-12 | teetee971 | divergente +1/-291 | histoires_disjointes | fix(network): harden flow aggregation bounds on current main | non |
| `fix/network-flow-bounds-main-20261013-1405` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/network-flow-bounds-main-20261013-1507` | `bc6fb8909a06` | 2026-10-13 | teetee971 | divergente +1/-290 | histoires_disjointes | fix(network): saturate flow totals and reject overlong endpo | non |
| `fix/network-flow-evidence-bounds-20261007` | `1ef03996347f` | 2026-10-06 | teetee971 | contenue_dans_main +0/-301 | histoires_disjointes | fix(phone-core): qualify SEND_SMS denial correctly on Androi | non |
| `fix/network-flow-metadata-bounds-20261008` | `1ef03996347f` | 2026-10-06 | teetee971 | contenue_dans_main +0/-301 | histoires_disjointes | fix(phone-core): qualify SEND_SMS denial correctly on Androi | non |
| `fix/network-flow-metadata-bounds-main-20261008` | `ce1589ab60c0` | 2026-10-08 | teetee971 | divergente +1/-301 | histoires_disjointes | fix(network): bound destination metadata and saturate counte | non |
| `fix/network-flow-metadata-bounds-main-20261011-1107` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/network-flow-metadata-bounds-main-20261012` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/network-flow-metadata-bounds-replay-20261011` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/network-lan-numeric-targets-v3-20261010` | `6783f6aa9c15` | 2026-10-10 | teetee971 | contenue_dans_main +0/-298 | histoires_disjointes | docs(voice): define fail-closed service session contract | non |
| `fix/network-lan-numeric-targets-v3-20261010-r2` | `924f06899fdb` | 2026-10-10 | teetee971 | contenue_dans_main +0/-296 | histoires_disjointes | docs(voice): add production readiness runbook | non |
| `fix/network-lan-probe-main-20261012-1913` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-lan-probe-numeric-targets-20261009` | `1ef03996347f` | 2026-10-06 | teetee971 | contenue_dans_main +0/-301 | histoires_disjointes | fix(phone-core): qualify SEND_SMS denial correctly on Androi | non |
| `fix/network-numeric-lan-probes-main-20261013-0213` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-numeric-only-local-probes-20261010` | `6dd22c2d725b` | 2026-10-10 | teetee971 | divergente +2/-295 | histoires_disjointes | test(network): reject actual host control characters | non |
| `fix/network-numeric-probes-main-20261013-0112` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-numeric-probes-main-20261013-0206` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-numeric-probes-main-20261013-0308` | `54d2475de633` | 2026-10-13 | teetee971 | divergente +1/-291 | histoires_disjointes | fix(network): reject non-numeric LAN targets and bound local | non |
| `fix/network-numeric-probes-main-20261013-0313` | `faf55c2ee071` | 2026-10-12 | teetee971 | contenue_dans_main +0/-291 | histoires_disjointes | fix(auth): pin qualified Auth0 Android dependency | non |
| `fix/network-numeric-probes-main-20261013-0811` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/network-numeric-probes-main-20261013-0907` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/network-numeric-probes-main-20261013-1006` | `8d7f52308858` | 2026-10-13 | teetee971 | divergente +1/-290 | histoires_disjointes | fix(network): reject non-numeric LAN targets and bound local | non |
| `fix/network-timeline-integration` | `086ce9f644b2` | 2026-10-17 | Thierry Naud | contient_main +3/-0 | histoires_disjointes | Merge remote-tracking branch 'origin/main' into fix/network- | oui |
| `fix/network-ula-local-scope-20261005` | `c3347325c0d1` | 2026-10-05 | teetee971 | contenue_dans_main +0/-456 | histoires_disjointes | CI Android: verrouiller la double provenance des APK (#1585) | non |
| `fix/network-volume-zero-median-20261018` | `2d597235c39c` | 2026-10-18 | teetee971 | contient_main +1/-0 | histoires_disjointes | fix(network): detect volume spikes after zero-byte baselines | non |
| `fix/nvd-cvss-generation-precedence` | `8ea5dcf428f6` | 2026-09-17 | teetee971 | divergente +2/-1354 | histoires_disjointes | Test NVD CVSS generation precedence | non |
| `fix/p02-expiry-fixture-20261114` | `2c3e6cdf5ae0` | 2026-11-14 | ton-username | contient_main +206/-0 | histoires_disjointes | test(android): correct P02 expiry fixture timestamp | oui |
| `fix/pages-robots-sitemap-fallback` | `7013cb700379` | 2025-11-05 | teetee971 | divergente +2/-3586 | histoires_disjointes | Create a pull request for robots.txt and sitemap.xml correct | non |
| `fix/phone-core-adb-recovery-main-20261016` | `7d1eeafef19f` | 2026-10-16 | teetee971 | contient_main +2/-0 | histoires_disjointes | fix(ci): add bounded ADB evidence recovery | oui |
| `fix/phone-core-appop-evidence-fail-closed-20261014-1710` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/phone-core-appop-evidence-unambiguous-20261015-1111` | `864749e7ca6c` | 2026-10-15 | teetee971 | contenue_dans_main +0/-77 | histoires_disjointes | Merge pull request #1658 from teetee971/feat/full-visual-ref | non |
| `fix/phone-core-appop-proof-fail-closed-20261015-0314` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/phone-core-background-oracle-all-resumed` | `7dfefcf2f89e` | 2026-10-07 | teetee971 | divergente +151/-301 | histoires_disjointes | fix(phone-core): start timeout at causal answer transaction | non |
| `fix/phone-core-background-oracle-all-resumed-20261008` | `7dfefcf2f89e` | 2026-10-07 | teetee971 | divergente +151/-301 | histoires_disjointes | fix(phone-core): start timeout at causal answer transaction | non |
| `fix/phone-core-background-oracle-all-resumed-20261010` | `7dfefcf2f89e` | 2026-10-07 | teetee971 | divergente +151/-301 | histoires_disjointes | fix(phone-core): start timeout at causal answer transaction | non |
| `fix/phone-core-background-resumed-oracle-20261007` | `18e0166df0df` | 2026-10-07 | teetee971 | divergente +145/-301 | histoires_disjointes | test(phone-core): cover modern resumed activity dumps | non |
| `fix/phone-core-bounded-local-logger-20261006` | `5719103f070c` | 2026-10-06 | teetee971 | contenue_dans_main +0/-302 | histoires_disjointes | fix(phone-core): harden shadow qualification and runtime tru | non |
| `fix/phone-core-capability-truth` | `46b4b714c15f` | 2026-10-03 | teetee971 | divergente +6/-556 | histoires_disjointes | test(phone): reject implicit France country fabrication | non |
| `fix/phone-core-certification-schema-v4-docs` | `7b9afaf91d11` | 2026-10-01 | teetee971 | divergente +8/-618 | histoires_disjointes | Merge branch 'main' into fix/phone-core-certification-schema | non |
| `fix/phone-core-ci-hardening` | `f0da53e27b87` | 2026-10-17 | teetee971 | contient_main +20/-0 | histoires_disjointes | test(ci): exercise adb timeout through executable fixture | oui |
| `fix/phone-core-concurrency-activity-20261024` | `e121aefad08c` | 2026-10-24 | Thierry Naud | contient_main +101/-0 | histoires_disjointes | fix(phone-core): guard async call evidence and activity laun | oui |
| `fix/phone-core-durable-sms-ingress-20261006` | `7877d1194ebd` | 2026-10-06 | teetee971 | divergente +17/-302 | histoires_disjointes | test(phone-core): lock replay-safe synchronous SMS fallback | oui |
| `fix/phone-core-durable-sms-main-20261010` | `58e5fdc0e7fe` | 2026-10-10 | teetee971 | divergente +9/-300 | histoires_disjointes | fix(phone-core): port durable SMS ingress onto current main | oui |
| `fix/phone-core-emulation-gate-rebase-20261005` | `c1a7f2f6283b` | 2026-10-05 | teetee971 | divergente +1/-456 | histoires_disjointes | test(phone-core): decouple fail-closed proof from localized  | non |
| `fix/phone-core-essential-permissions-20261004` | `dd1def8228f8` | 2026-10-04 | teetee971 | contenue_dans_main +0/-464 | histoires_disjointes | feat(site): auto-sync public product truth from canonical re | non |
| `fix/phone-core-evidence-adb-command-timeouts` | `551fd00221d7` | 2026-10-12 | teetee971 | divergente +38/-291 | histoires_disjointes | fix(ci): bound adb evidence collection commands | oui |
| `fix/phone-core-evidence-adb-recovery` | `f36298ed9f60` | 2026-10-13 | teetee971 | divergente +2/-290 | histoires_disjointes | test(ci): restore evidence recovery contract on merged main | non |
| `fix/phone-core-finalization-consolidated-20261001` | `44a6e64f3799` | 2026-10-01 | teetee971 | divergente +32/-594 | histoires_disjointes | fix(phone-core): require UI and two-party recording proofs | non |
| `fix/phone-core-main-thread-io-20261001` | `b73688b91aed` | 2026-10-01 | teetee971 | divergente +1/-594 | histoires_disjointes | fix(phone-core): move SMS and MMS provider IO off main threa | non |
| `fix/phone-core-optional-contacts-wizard` | `dd1def8228f8` | 2026-10-04 | teetee971 | contenue_dans_main +0/-464 | histoires_disjointes | feat(site): auto-sync public product truth from canonical re | non |
| `fix/phone-core-p0-20261110` | `b1f698d58acc` | 2026-11-11 | teetee971 | contient_main +322/-0 | histoires_disjointes | Restore Phone Core observation and in-call lifecycle guarant | non |
| `fix/phone-core-physical-gate-20261004` | `b6a97df719ad` | 2026-10-04 | teetee971 | divergente +140/-463 | histoires_disjointes | fix(phone-core): compile navigation smoke test | non |
| `fix/phone-core-provenance-fixtures-20261008` | `e510f0e4c229` | 2026-10-08 | Thierry Naud | divergente +32/-301 | histoires_disjointes | docs(phone-core): define signed physical evidence bundle | non |
| `fix/phone-core-quick-reply-active-default-sim-20261005` | `1b93313b6735` | 2026-10-05 | teetee971 | divergente +1/-456 | histoires_disjointes | fix(phone-core): allow active Android default SIM for platfo | non |
| `fix/phone-core-quick-reply-routing-test-20261003` | `f1ea56ee769a` | 2026-10-03 | teetee971 | contenue_dans_main +0/-513 | histoires_disjointes | Merge pull request #1526 from teetee971/fix/sms-respond-via- | non |
| `fix/phone-core-recents-provenance-actions` | `9878230a6e70` | 2026-10-04 | teetee971 | contenue_dans_main +0/-473 | histoires_disjointes | fix(release): gate Android distribution on Play and legal co | non |
| `fix/phone-core-remove-aggregate-sms-permissions` | `f5fc8ba8f6e8` | 2026-10-03 | teetee971 | contenue_dans_main +0/-532 | histoires_disjointes | Merge PR #1511: enforce canonical product truth and Android  | non |
| `fix/phone-core-remove-aggregate-sms-permissions-v2` | `f5fc8ba8f6e8` | 2026-10-03 | teetee971 | contenue_dans_main +0/-532 | histoires_disjointes | Merge PR #1511: enforce canonical product truth and Android  | non |
| `fix/phone-core-revocation-proof-hardening` | `60d2c75268ce` | 2026-10-06 | teetee971 | divergente +21/-301 | histoires_disjointes | fix(phone-core): persist SEND_SMS AppOp denial across relaun | non |
| `fix/phone-core-revoke-fallback-after-ee8675-pr` | `1518ead956da` | 2026-10-07 | teetee971 | divergente +82/-301 | histoires_disjointes | chore: remove temporary probe file | non |
| `fix/phone-core-revoke-fallback-red` | `1518ead956da` | 2026-10-07 | teetee971 | divergente +82/-301 | histoires_disjointes | chore: remove temporary probe file | non |
| `fix/phone-core-runtime-script-timeouts` | `1b943ee8b678` | 2026-10-12 | teetee971 | divergente +40/-291 | histoires_disjointes | fix(ci): bound Phone Core runtime scripts | oui |
| `fix/phone-core-safe-sms-swipe-1504` | `c8f1c44e78e1` | 2026-10-02 | teetee971 | contenue_dans_main +0/-556 | histoires_disjointes | Merge PR #1508: truthful Phone Core protection lists | non |
| `fix/phone-core-send-sms-oracle-consistency-20261009` | `0adf0d3a8ff3` | 2026-10-09 | teetee971 | divergente +187/-301 | histoires_disjointes | fix(phone-core): reject ambiguous SEND_SMS permission snapsh | non |
| `fix/phone-core-send-sms-snapshot-oracles-20261008` | `c58ee35030e0` | 2026-10-08 | teetee971 | divergente +186/-301 | histoires_disjointes | test(phone-core): reject contradictory and unreadable SEND_S | non |
| `fix/phone-core-send-sms-uid-appop-proof-20261014` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/phone-core-sms-inbox-main-final` | `da0a2d5fb1fc` | 2026-10-02 | teetee971 | contenue_dans_main +0/-575 | histoires_disjointes | fix(phone-core): consolidate production readiness gate (#149 | non |
| `fix/phone-core-sms-least-privilege-20261003` | `f5fc8ba8f6e8` | 2026-10-03 | teetee971 | contenue_dans_main +0/-532 | histoires_disjointes | Merge PR #1511: enforce canonical product truth and Android  | non |
| `fix/phone-core-sms-least-privilege-20261003b` | `f5fc8ba8f6e8` | 2026-10-03 | teetee971 | contenue_dans_main +0/-532 | histoires_disjointes | Merge PR #1511: enforce canonical product truth and Android  | non |
| `fix/phone-core-sms-least-privilege-20261003c` | `e898e03364ab` | 2026-10-03 | teetee971 | contenue_dans_main +0/-520 | histoires_disjointes | ci: require product capability truth before CodeQL gate pass | non |
| `fix/phone-core-sms-least-privilege-20261003d` | `6792aec9f500` | 2026-10-03 | teetee971 | contenue_dans_main +0/-519 | histoires_disjointes | fix(vpn): bind provisioning persistence to the PostgreSQL se | non |
| `fix/phone-core-sms-least-privilege-gate` | `f5fc8ba8f6e8` | 2026-10-03 | teetee971 | contenue_dans_main +0/-532 | histoires_disjointes | Merge PR #1511: enforce canonical product truth and Android  | non |
| `fix/phone-core-sms-permission-oracle-20261008` | `4967bbb881e6` | 2026-10-08 | Thierry Naud | divergente +41/-301 | histoires_disjointes | fix(phone-core): align banner with MMS lock state | non |
| `fix/phone-core-sms-permission-phases-20261003` | `f1ea56ee769a` | 2026-10-03 | teetee971 | contenue_dans_main +0/-513 | histoires_disjointes | Merge pull request #1526 from teetee971/fix/sms-respond-via- | non |
| `fix/phone-core-sms-permission-phases-20261003-e` | `708747cbfa3f` | 2026-10-03 | teetee971 | contenue_dans_main +0/-512 | histoires_disjointes | fix(phone-core): make outgoing MMS callback states factual | non |
| `fix/phone-core-sms-permission-phases-20261003e` | `df46bac34489` | 2026-10-03 | teetee971 | contenue_dans_main +0/-508 | histoires_disjointes | fix(phone-core): make MMS temporary cleanup durable per file | non |
| `fix/phone-core-sms-permission-ux` | `c8f1c44e78e1` | 2026-10-02 | teetee971 | contenue_dans_main +0/-556 | histoires_disjointes | Merge PR #1508: truthful Phone Core protection lists | non |
| `fix/phone-core-sms-staged-permissions-20261003` | `8cce55c1e3b6` | 2026-10-03 | teetee971 | contenue_dans_main +0/-501 | histoires_disjointes | Merge PR #1538: fail-closed incoming MMS envelope parser | non |
| `fix/phone-core-stable-effective-permission-probe` | `66b6ad179cd9` | 2026-10-12 | teetee971 | divergente +45/-291 | histoires_disjointes | fix(phone-core): stabilize effective permission evidence | oui |
| `fix/phone-decision-journal-clock-overflow-20261014` | `7e8cd5f02298` | 2026-10-13 | teetee971 | contenue_dans_main +0/-290 | histoires_disjointes | fix(sms): prevent concurrent composer submissions | non |
| `fix/phone-decision-journal-overflow-20261014-1710` | `01be6dd9d71b` | 2026-10-14 | teetee971 | divergente +2/-290 | histoires_disjointes | test(phone): cover future timestamp skew at Long.MAX_VALUE | non |
| `fix/phone-decision-journal-overflow-main-20261015` | `bb023e0dc3e7` | 2026-10-15 | teetee971 | divergente +2/-62 | histoires_disjointes | fix(phone): avoid future timestamp overflow | non |
| `fix/phone-decision-journal-overflow-main-20261016` | `f5e314cc8c8b` | 2026-10-16 | teetee971 | divergente +2/-29 | histoires_disjointes | fix(phone): make decision journal skew check overflow-safe | non |
| `fix/phone-number-region-safe-20261004` | `ec956c2d76f0` | 2026-10-04 | teetee971 | divergente +101/-463 | histoires_disjointes | fix(branch): preserve current main frontend while qualifying | oui |
| `fix/phone-screening-p0-enforcement-source-guard` | `f80a4a1146a4` | 2026-10-18 | teetee971 | contient_main +327/-0 | histoires_disjointes | fix(phone): require explicit confirmation before broad count | oui |
| `fix/product-logic-truth-audit` | `8ac8380698f0` | 2026-10-03 | teetee971 | divergente +14/-532 | histoires_disjointes | Merge main into OSINT truth fixes without regressing product | non |
| `fix/production-gate-api-resilience` | `17d28db2b942` | 2026-10-06 | teetee971 | divergente +6/-302 | histoires_disjointes | fix(ci): bound GitHub retries by merge-gate deadline | oui |
| `fix/production-gate-api-resilience-main-20261006` | `1ef03996347f` | 2026-10-06 | teetee971 | contenue_dans_main +0/-301 | histoires_disjointes | fix(phone-core): qualify SEND_SMS denial correctly on Androi | non |
| `fix/quick-reply-default-sim-20261004` | `d82fad7873ca` | 2026-10-04 | teetee971 | divergente +107/-463 | histoires_disjointes | test(phone-core): pin quick replies to scoped default-SIM mo | oui |
| `fix/readme-mms-truth-20260930` | `b2cf3b42695a` | 2026-10-01 | teetee971 | divergente +3/-618 | histoires_disjointes | Merge branch 'main' into fix/readme-mms-truth-20260930 | non |
| `fix/recording-vault-r3-no-replace-20261028` | `6769b022e5ee` | 2026-10-28 | ton-username | contient_main +5/-0 | histoires_disjointes | fix(recording-vault-r3): restore atomic publication on Andro | non |
| `fix/recording-vault-v2-aad-20261028` | `601f7baedc4e` | 2026-10-28 | ton-username | contient_main +8/-0 | histoires_disjointes | fix(recording-vault): bind V2 ciphertext to recording identi | non |
| `fix/recording-vault-v2-migration-hardening-20261028` | `c852edbfe6f3` | 2026-10-29 | Thierry Naud | contient_main +20/-0 | histoires_disjointes | docs(vault): verify received Galaxy and reboot evidence | oui |
| `fix/roadmap-finalization` | `0e280e606854` | 2026-11-07 | teetee971 | contient_main +284/-0 | histoires_disjointes | Deliver verified Doctor roadmap artifacts and preserve remai | oui |
| `fix/rule-sync-locked-settings` | `5ad332960cc2` | 2026-09-20 | teetee971 | divergente +1/-1168 | histoires_disjointes | Lock signed-rule sync setting until production trust is prov | non |
| `fix/security-loop-step-diagnostics-20261022` | `8edd5e6260e6` | 2026-10-22 | teetee971 | contient_main +2/-0 | histoires_disjointes | ci: format per-stage summary with actual newlines | oui |
| `fix/selected-apk-inspector-api28-20261024` | `b4b6564a7713` | 2026-10-24 | Thierry Naud | contient_main +127/-0 | histoires_disjointes | fix(apk): guard API 28 signing inspection | non |
| `fix/sentinel-ai-bounded-pairing-20261117` | `0bfb6c26f7f9` | 2026-11-17 | Thierry Naud | histoires_disjointes | contenue_dans_integration +0/-13 | docs: retain bounded output pairing evidence and S24 validat | non |
| `fix/sentinel-ai-revocation-barrier-20261118` | `4e444a59a769` | 2026-11-18 | Thierry Naud | histoires_disjointes | contenue_dans_integration +0/-6 | Document C11 qualification, S24 procedures and transversal f | non |
| `fix/sentinel-ai-v3-security-20261116` | `0cb90a6300b0` | 2026-11-16 | Codex | contient_main +361/-0 | histoires_disjointes | test(ai): preserve JVM evidence and Android integration qual | non |
| `fix/seo-headers-1104` | `ac9ca11f9792` | 2025-11-04 | teetee971 | histoires_disjointes | histoires_disjointes | chore: fix _headers syntax + ignore keys | non |
| `fix/settings-backup-io-20261001` | `a7a754e31e0a` | 2026-10-01 | teetee971 | divergente +1/-594 | histoires_disjointes | fix(settings): move backup restore and log IO off main threa | non |
| `fix/settings-backup-io-current-main-20261002` | `4dca43a2b0e9` | 2026-10-02 | teetee971 | divergente +1/-567 | histoires_disjointes | fix(settings): move backup/restore I/O off UI thread | non |
| `fix/settings-consume-uninstall-intent-20261011` | `af9ed89aceb9` | 2026-10-11 | Thierry Naud | divergente +35/-295 | histoires_disjointes | fix(android): stabilize runtime permission revocation probe | non |
| `fix/settings-uninstall-export-intent-20261011` | `efc17a70c635` | 2026-10-11 | teetee971 | divergente +23/-295 | histoires_disjointes | fix(android): consume uninstall intent for every backup expo | non |
| `fix/settings-uninstall-intent-followup-20261011` | `27f2c3417394` | 2026-10-11 | teetee971 | divergente +24/-295 | histoires_disjointes | fix(android): consume pending uninstall intent before backup | non |
| `fix/settings-uninstall-intent-one-shot-20261011` | `be0831282050` | 2026-10-11 | Thierry Naud | divergente +36/-295 | histoires_disjointes | fix(android): fall back when role restores SMS permission | non |
| `fix/settings-uninstall-one-shot-20261012` | `a258fc871ce1` | 2026-10-11 | teetee971 | divergente +60/-295 | histoires_disjointes | fix(auth): make local logout independent of remote SSO | non |
| `fix/silence-unknown-callers-20261110` | `23723f607bf1` | 2026-11-10 | teetee971 | contient_main +29/-0 | histoires_disjointes | Drop the walkie-talkie microphone when LiveKit leaves or rec | non |
| `fix/sms-activation-complete-permissions-ux` | `7db754aee2b8` | 2026-10-01 | teetee971 | contenue_dans_main +0/-599 | histoires_disjointes | feat(phone-core): native communications redesign, inline rep | non |
| `fix/sms-activation-main-sync-20261002` | `ed836681d69d` | 2026-10-02 | teetee971 | contenue_dans_main +0/-592 | histoires_disjointes | feat(numbering): discover Traficom source and licence candid | non |
| `fix/sms-activation-main-sync-20261002-v2` | `209adda5f7be` | 2026-10-02 | teetee971 | divergente +1/-591 | histoires_disjointes | fix(phone-core): sync SMS activation least-privilege onto cu | non |
| `fix/sms-activation-missing-permissions-ux` | `9019e98e2abb` | 2026-10-01 | teetee971 | contenue_dans_main +0/-600 | histoires_disjointes | fix(phone-core): report anomalous SMS dates and truthful idl | non |
| `fix/sms-activation-runtime-permissions-20261001` | `1a6ab3235d1f` | 2026-10-02 | teetee971 | divergente +9/-594 | histoires_disjointes | test(phone-core): prove SMS inbox role gate fails closed | non |
| `fix/sms-activation-ui-wiring` | `c449ab60d600` | 2026-09-20 | teetee971 | divergente +2/-1170 | histoires_disjointes | Merge branch 'main' into fix/sms-activation-ui-wiring | non |
| `fix/sms-ingress-dedup` | `9fc86b83653b` | 2026-10-13 | Thierry Naud | divergente +36/-290 | histoires_disjointes | chore(sms): sync ingress dedup with main | oui |
| `fix/sms-io-current-main-2026-10-02` | `ed836681d69d` | 2026-10-02 | teetee971 | contenue_dans_main +0/-592 | histoires_disjointes | feat(numbering): discover Traficom source and licence candid | non |
| `fix/sms-respond-via-mms-schemes-20261003` | `44e7888b20af` | 2026-10-03 | teetee971 | divergente +7/-519 | histoires_disjointes | test(sms): lock quick-reply transport routing by URI scheme | non |
| `fix/sms-single-sim-compose-state` | `4906c52215eb` | 2026-09-20 | teetee971 | divergente +1/-1167 | histoires_disjointes | Keep SMS SIM selection out of Compose rendering | non |
| `fix/sms-status-timeline-f60a0c11` | `f496df4b0ae7` | 2026-09-29 | teetee971 | divergente +1/-666 | histoires_disjointes | Fix: isolate SMS status timeline persistence | non |
| `fix/sms-submission-gate-observable` | `74e2617fc136` | 2026-10-13 | teetee971 | divergente +2/-290 | histoires_disjointes | test(sms): replay observable gate regression on merged main | oui |
| `fix/sms-submission-state-recreation` | `94b8486de589` | 2026-10-12 | teetee971 | divergente +35/-291 | histoires_disjointes | fix(sms): expose process submission state | non |
| `fix/talkie-shared-room` | `f768fe6451ad` | 2026-11-10 | teetee971 | contient_main +1/-0 | histoires_disjointes | Let two entitled phones share a known talkie room. | oui |
| `fix/timeline-resilience-34bd494` | `949767e72015` | 2026-09-29 | teetee971 | divergente +4/-665 | histoires_disjointes | timeline: isolate and log SentinelSmsStatusReceiver.kt persi | non |
| `fix/ui-product-truth-polish-20261013` | `fcae229385b9` | 2026-10-14 | teetee971 | divergente +19/-243 | histoires_disjointes | fix(ui): avoid double-counting system navigation insets | oui |
| `fix/v1-phone-core-p1-20261119` | `f036b2b79372` | 2026-11-19 | Cursor Agent | histoires_disjointes | contient_integration +22/-0 | fix: apply UnknownCallerPolicy in call screening after fail- | oui |
| `fix/vpn-dns-literal-parser-main-20261011-0614` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/vpn-dns-no-implicit-lookup-20261011-1011` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/vpn-dns-numeric-no-lookup-20261010` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/vpn-dns-numeric-no-lookup-20261011` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/vpn-dns-numeric-no-lookup-20261011-qualification` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/vpn-monotonic-sequence-authority-20261003` | `2b2023d7ca4b` | 2026-10-03 | teetee971 | divergente +27/-532 | histoires_disjointes | test(vpn): verify sequence and digest on trusted restore | non |
| `fix/vpn-numeric-dns-no-resolution-20261011-0909` | `bd717b0d84af` | 2026-10-10 | teetee971 | contenue_dans_main +0/-295 | histoires_disjointes | feat(voice): add fail-closed LiveKit session service | non |
| `fix/vpn-runtime-observation-wiring` | `8f95a3714d6f` | 2026-10-17 | Thierry Naud | contient_main +2/-0 | histoires_disjointes | Merge remote-tracking branch 'origin/main' into fix/vpn-runt | oui |
| `fix/watchdog-hourly-cadence-20261019` | `3bf941f47930` | 2026-10-19 | teetee971 | contient_main +1/-0 | histoires_disjointes | fix(ci): reduce read-only watchdog schedule to hourly | oui |
| `fix/wearable-exact-capability-proof-20261019` | `ff57bb22baab` | 2026-10-19 | teetee971 | contient_main +2/-0 | histoires_disjointes | test(wearable): reject extra signed capabilities at activati | oui |
| `fix/wearable-identity-source-binding-20261019` | `eed531809212` | 2026-10-19 | teetee971 | contient_main +2/-0 | histoires_disjointes | test(wearable): reject provenance mismatch despite matching  | oui |
| `fix/wearable-unavailable-capabilities-20261019` | `80012c01eb0a` | 2026-10-19 | teetee971 | contient_main +2/-0 | histoires_disjointes | test(wearable): guard unavailable adapter capability truth | oui |
| `fix/wifi-location-permission-20261111` | `681347ed8679` | 2026-11-11 | teetee971 | contient_main +325/-0 | histoires_disjointes | Request approximate location together with precise location. | non |
| `fix/wifi-timeline-truth-0b54a509` | `35a094491fd2` | 2026-09-29 | teetee971 | divergente +1/-663 | histoires_disjointes | wifi: isolate timeline persistence from scan truth | non |
| `fix/wifi-timeline-truth-de5e0b9e` | `9e270f7ace0d` | 2026-09-29 | teetee971 | divergente +1/-667 | histoires_disjointes | Fix: keep Wi-Fi scan truth independent from timeline persist | non |
| `fix/wifi-timeline-truth-f60a0c11` | `4436e75862bc` | 2026-09-29 | teetee971 | divergente +1/-666 | histoires_disjointes | Fix: keep Wi-Fi scan truth independent from timeline persist | non |
| `hardening/local-discovery-manifest-wording-20260921` | `25ef628b5a82` | 2026-09-21 | teetee971 | divergente +1/-1107 | histoires_disjointes | Security: clarify consent-bounded local discovery | non |
| `hardening/physical-session-failclosed` | `8ac907eafac6` | 2026-10-09 | teetee971 | divergente +288/-301 | histoires_disjointes | test(phone-core): match legacy SMS role availability to devi | oui |
| `hardening/vpn-docs-and-unsigned-build` | `7b3b64d7dc9f` | 2026-09-30 | teetee971 | divergente +88/-621 | histoires_disjointes | CI: include VPN documentation truth in Android product gate | non |
| `integrate/android-socle-20261110` | `0d539872ca70` | 2026-11-10 | teetee971 | contient_main +321/-0 | histoires_disjointes | Require consent for catalog refresh and label near contacts. | non |
| `integration/sentinel-cve-v5-20261025` | `d51e36403c6a` | 2026-10-25 | teetee971 | contient_main +18/-0 | histoires_disjointes | feat(cve-v5): add separate metadata section to Sentinel thre | oui |
| `integration/sentinel-d1-3d-20261022` | `1b21e29d4df7` | 2026-10-22 | teetee971 | contient_main +30/-0 | histoires_disjointes | chore(android): remove unused ZIP import | oui |
| `integration/sentinel-d1-cve-v5-20261025` | `92a469b4069c` | 2026-10-27 | Thierry Naud | contient_main +188/-0 | histoires_disjointes | fix: separate signed block and silence prefix policies | non |
| `integration/sentinel-qualification-20261022` | `599bc3a379d1` | 2026-10-22 | root | contient_main +4/-0 | histoires_disjointes | test(android): integrate MMS role compensation | non |
| `integration/sentinel-v1-commercial-20261118` | `113862500b95` | 2026-11-18 | Thierry Naud | histoires_disjointes | contenue_dans_integration +0/-0 | Record Android runtime advisory coverage and its limits [ski | non |
| `legal/site-app-foundation-fcecff2` | `be8fbb534da0` | 2026-09-26 | teetee971 | divergente +5/-785 | histoires_disjointes | Android: expose legal and privacy documents in settings | non |
| `main` | `935344423b2a` | 2026-10-16 | teetee971 | contenue_dans_main +0/-0 | histoires_disjointes | Merge pull request #1677 from teetee971/fix/community-report | non |
| `maintenance/cleanup-hardening-2026-09` | `5be5d9231c2d` | 2026-09-02 | teetee971 | divergente +10/-1849 | histoires_disjointes | docs: make canonical Sentinel isolation architecture explici | non |
| `merge-2025-11-04` | `066a11bd4dd4` | 2025-11-04 | teetee971 | histoires_disjointes | histoires_disjointes | SEO + sécurité: robots & sitemap + headers | non |
| `merge-2025-11-04-v2` | `2c7e87ec0b5c` | 2025-11-04 | teetee971 | histoires_disjointes | histoires_disjointes | robots.txt + sitemap.xml + exceptions SPA + headers sécurité | non |
| `mesh/cancel-inflight-control-plane-20260926` | `2fd2d926675d` | 2026-09-26 | teetee971 | divergente +9/-786 | histoires_disjointes | Fix fake OkHttp Call identity methods in registry test | non |
| `network-alert-policy-current-main-20260923` | `9ba4b76b6bb7` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay alert prioritization and anti-noise policy o | non |
| `network-authorized-lan-probe-current-main-20260923` | `280a35abfd62` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay bounded authorized LAN probe on latest main | non |
| `network-behavior-baseline-ae49869` | `6f33434a5acc` | 2026-09-25 | teetee971 | divergente +2/-827 | histoires_disjointes | Test local network behavior baseline | non |
| `network-behavior-baseline-current-main-20260923` | `ea0b9ee7ca76` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay behavior baseline on latest main | non |
| `network-correlation-main-1594c95` | `1594c95c7a77` | 2026-09-24 | teetee971 | contenue_dans_main +0/-845 | histoires_disjointes | Merge pull request #1144 from teetee971/ci-android-emulator- | non |
| `network-correlation-replay-main-790b87d` | `9a7c5587aea9` | 2026-09-24 | teetee971 | divergente +2/-849 | histoires_disjointes | Test bounded multi-source network evidence correlation | non |
| `network-correlation-replay-main-9710301` | `4186dcbf0575` | 2026-09-24 | teetee971 | divergente +2/-847 | histoires_disjointes | Test bounded multi-source network evidence correlation | non |
| `network-device-dna-flow-current-main-20260923` | `b2916cab9478` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay Device DNA, presence and flow foundation on  | non |
| `network-device-registry-current-main-20260923` | `fdaf41bc3837` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay event-driven device registry on latest main | non |
| `network-evidence-correlation-current-main-20260923` | `6832a930f58b` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay multi-source evidence correlation on latest  | non |
| `network-local-agent-current-main-20260923` | `f53e9f3a53ec` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay Local Agent heartbeat and protocol on latest | non |
| `network-risk-dossier-current-main-20260923` | `cd5cc7bb5610` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay explainable local risk dossier on latest mai | non |
| `network-snapshot-timeline-current-main-20260923` | `e57f13269ad7` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay snapshot diffs to local timeline on latest m | non |
| `network-snapshot-timeline-current-main-20260923b` | `7d1e018a3805` | 2026-09-23 | teetee971 | divergente +2/-872 | histoires_disjointes | Restore focused snapshot timeline tests on current main | non |
| `network-snapshot-timeline-current-main-20260924` | `2416dbec8dd5` | 2026-09-23 | teetee971 | divergente +1/-869 | histoires_disjointes | Replay local network snapshot timeline adapter | non |
| `network-snapshot-timeline-main-01a6b18-20260924` | `5d27a210f863` | 2026-09-23 | teetee971 | divergente +1/-867 | histoires_disjointes | Network: replay snapshot timeline adapter on current main | non |
| `network-topology-evidence-current-main-20260923` | `339f59ece257` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Network: replay explicit topology evidence on latest main | non |
| `network-topology-evidence-main-1594c95` | `1594c95c7a77` | 2026-09-24 | teetee971 | contenue_dans_main +0/-845 | histoires_disjointes | Merge pull request #1144 from teetee971/ci-android-emulator- | non |
| `network-topology-main-1594c95` | `0eac3f387336` | 2026-09-24 | teetee971 | divergente +2/-845 | histoires_disjointes | Test explicit topology evidence replay | non |
| `network-topology-replay-main-9710301` | `fc04ee93d700` | 2026-09-24 | teetee971 | divergente +2/-847 | histoires_disjointes | Test explicit topology evidence replay | non |
| `network/alert-policy-after-952-20260922` | `c28b4913eb11` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Replay network alert policy after Phone main move | non |
| `network/alert-policy-after-984-20260922` | `9fbd8346eeb6` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay alert policy after snapshot merge | non |
| `network/authorized-active-probe-ac52ef4-20260922` | `01ad8f6481c0` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Test authorized LAN active probe boundaries | non |
| `network/behavior-baseline-after-984-20260922` | `cebdd4377c92` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay behavior baseline after snapshot diff merge | non |
| `network/behavior-baseline-foundation-20260922` | `31607ecbb70e` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Test: validate local network behavior baseline | non |
| `network/bound-risk-scores-20260922` | `178af5de70ff` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Test fail-closed oversized risk maps | non |
| `network/change-timeline-foundation-20260922` | `4a9f75b2be22` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Test: validate local network change timeline | non |
| `network/correlation-after-952-20260922` | `5a3a6439cdf9` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Replay evidence correlation after Phone main move | non |
| `network/correlation-after-984-20260922` | `6e0903057322` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay evidence correlation after snapshot merge | non |
| `network/device-dna-flow-after-952-20260922` | `efc852d7fe40` | 2026-09-21 | teetee971 | divergente +6/-1102 | histoires_disjointes | Replay Network Intelligence tests after Phone main move | non |
| `network/device-dna-flow-after-984-20260922` | `e45dcf5bed11` | 2026-09-22 | teetee971 | divergente +6/-1097 | histoires_disjointes | Replay Device DNA and flow foundation after snapshot merge | non |
| `network/device-dna-flow-foundation-20260921` | `1a1b1b56f99a` | 2026-09-21 | teetee971 | divergente +6/-1103 | histoires_disjointes | Test: validate bounded flow metadata aggregation | non |
| `network/device-event-registry-foundation-20260921` | `57304747b190` | 2026-09-21 | teetee971 | divergente +2/-1103 | histoires_disjointes | Test: verify local device registry event reduction | non |
| `network/device-registry-after-952-20260922` | `fc6f4569533b` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Replay device registry after Phone main move | non |
| `network/device-registry-after-984-20260922` | `bc901cb49e50` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay device registry after snapshot merge | non |
| `network/evidence-correlation-engine-20260921` | `1d515c9490e3` | 2026-09-21 | teetee971 | divergente +2/-1103 | histoires_disjointes | Test: verify multi-source network evidence correlation | non |
| `network/explainable-risk-dossier-20260922` | `747cbcba0fa0` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Test: validate explainable network risk dossier | non |
| `network/ipv6-ula-local-scope-c7d21fa` | `c7d21fac2c96` | 2026-09-26 | teetee971 | contenue_dans_main +0/-784 | histoires_disjointes | Phone Core: keep SMS composer permissions minimal (#1239) | non |
| `network/local-agent-after-952-20260922` | `c16542d53f71` | 2026-09-21 | teetee971 | divergente +5/-1102 | histoires_disjointes | Replay Local Agent foundation after Phone main move | non |
| `network/local-agent-after-984-20260922` | `1814e968b297` | 2026-09-22 | teetee971 | divergente +5/-1097 | histoires_disjointes | Replay Local Agent foundation after snapshot merge | non |
| `network/local-agent-heartbeat-foundation-20260921` | `6ccc581a7ebc` | 2026-09-21 | teetee971 | divergente +5/-1103 | histoires_disjointes | Docs: define Sentinel Local Agent trust boundary | non |
| `network/network-alert-policy-foundation-20260921` | `cfa143b5626f` | 2026-09-21 | teetee971 | divergente +2/-1103 | histoires_disjointes | Test: enforce network alert severity and cooldown rules | non |
| `network/replay-change-timeline-20260922-0208` | `c43bd688f0ac` | 2026-09-22 | teetee971 | divergente +2/-1100 | histoires_disjointes | Test: replay network change timeline coverage | non |
| `network/risk-dossier-after-984-20260922` | `57663692b3c1` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay explainable risk dossier after snapshot diff merge | non |
| `network/service-exposure-foundation-20260922` | `47d8789a7167` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Test: validate local service exposure classification | non |
| `network/snapshot-diff-foundation-20260922` | `42b0fd261c7e` | 2026-09-21 | teetee971 | divergente +3/-1102 | histoires_disjointes | Test: validate deterministic network snapshot diffs | non |
| `network/snapshot-timeline-adapter-20260922` | `d6fc1bba7f59` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Test: validate snapshot to timeline mapping | non |
| `network/timeline-linear-dedupe-20260922` | `fbfd45db5e9a` | 2026-09-22 | teetee971 | divergente +4/-1097 | histoires_disjointes | Test exact ignore-case dedupe behavior | non |
| `network/topology-after-952-20260922` | `70e0346a6df6` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Replay topology evidence after Phone main move | non |
| `network/topology-after-984-20260922` | `0f72db579f87` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay topology evidence after snapshot merge | non |
| `network/topology-evidence-foundation-20260921` | `90bc86e31b7f` | 2026-09-21 | teetee971 | divergente +2/-1103 | histoires_disjointes | Test: enforce topology evidence provenance and privacy | non |
| `ops/hourly-autocontrol-20261022` | `b9f4952f581c` | 2026-10-22 | teetee971 | contient_main +1/-0 | histoires_disjointes | ci: schedule Sentinel autonomous engineering audit hourly | oui |
| `ops/watchdog-hourly-cost-control-20261022` | `a6704557c054` | 2026-10-22 | teetee971 | contient_main +1/-0 | histoires_disjointes | chore(ci): reduce watchdog polling to hourly cadence | oui |
| `perf/android-sms-thread-query` | `1b2f01f39909` | 2026-09-19 | teetee971 | divergente +1/-1246 | histoires_disjointes | perf(android): remove N+1 SMS thread lookups | non |
| `perf/android-sms-thread-query-v2` | `c60718b6c6db` | 2026-09-19 | teetee971 | divergente +1/-1245 | histoires_disjointes | perf(android): remove N+1 SMS thread queries on current main | non |
| `perf/android-sms-thread-query-v3` | `859404dd608d` | 2026-09-19 | teetee971 | divergente +1/-1241 | histoires_disjointes | perf(android): remove N+1 SMS thread provider queries | non |
| `perf/app-permission-off-main-a5538eb` | `5b4bae321023` | 2026-09-30 | teetee971 | divergente +2/-624 | histoires_disjointes | CI: lock installed-app analysis off main thread | non |
| `perf/call-screening-memory-snapshot-12f1e21` | `4ab91ee997be` | 2026-10-01 | teetee971 | divergente +5/-617 | histoires_disjointes | CI: require synchronous in-memory screening preload | non |
| `perf/local-analyzers-off-main-c7d6e7b` | `31a419a75418` | 2026-10-01 | teetee971 | divergente +2/-619 | histoires_disjointes | Performance: keep local message analysis off UI thread | non |
| `perf/message-analysis-off-main-2f27c60` | `d53568618787` | 2026-10-01 | teetee971 | divergente +3/-618 | histoires_disjointes | CI: prevent synchronous message-analysis regressions | non |
| `perf/message-analysis-off-main-343dc36` | `99753f1c82a1` | 2026-10-01 | teetee971 | divergente +4/-616 | histoires_disjointes | CI: preserve Communications guards while adding message off- | non |
| `perf/message-analysis-off-main-b6e7cab` | `949a3762caa8` | 2026-10-01 | teetee971 | divergente +3/-615 | histoires_disjointes | CI: lock message analysis off UI thread | non |
| `perf/osint-cache-off-main-a5538eb` | `2a72d2a4285d` | 2026-09-30 | teetee971 | divergente +1/-624 | histoires_disjointes | Performance: load OSINT cache off UI thread | non |
| `perf/system-doctor-off-main-343dc36` | `920be914f9ba` | 2026-10-01 | teetee971 | divergente +2/-616 | histoires_disjointes | CI: prevent System Doctor UI-thread scan regression | non |
| `perf/system-doctor-off-main-b6e7cab` | `41cd443509b9` | 2026-10-01 | teetee971 | divergente +2/-615 | histoires_disjointes | CI: lock System Doctor scan off UI thread | non |
| `perf/system-doctor-off-main-c7d6e7b` | `6ec89f786004` | 2026-10-01 | teetee971 | divergente +2/-618 | histoires_disjointes | Merge branch 'main' into perf/system-doctor-off-main-c7d6e7b | non |
| `phase-b` | `a04f0b7b537a` | 2025-12-13 | Copilot | divergente +2/-2316 | histoires_disjointes | Fix malformed GitHub Actions workflow YAML syntax (#132) | non |
| `phone-block-metadata-policy-tests` | `e1a7ba647810` | 2026-09-21 | teetee971 | divergente +1/-1117 | histoires_disjointes | Phone: strengthen block metadata policy tests | non |
| `phone-core-accessibility-state-truth-20260923` | `4317107bd83e` | 2026-09-23 | teetee971 | divergente +1/-878 | histoires_disjointes | Make Phone Core accessibility states truthful | non |
| `phone-core-activation-semantic-ui-20260922` | `2c700f105f99` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UX: use semantic activation status colors | non |
| `phone-core-activation-ux-v2-20260922` | `2d7aa4d346b4` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UX: make activation states semantic and accessibl | non |
| `phone-core-apk-candidate-verification-20260923` | `c5d5f4bcb44d` | 2026-09-23 | teetee971 | divergente +3/-939 | histoires_disjointes | Phone Core CI: include debug APK policy in governance tests | non |
| `phone-core-apk-candidate-verification-replay-20260923` | `690b33f82778` | 2026-09-23 | teetee971 | divergente +3/-900 | histoires_disjointes | Phone Core CI: include debug APK policy in governance tests | non |
| `phone-core-apk-install-smoke-20260923` | `9356bc1149c2` | 2026-09-23 | teetee971 | divergente +4/-900 | histoires_disjointes | Phone Core CI: fix emulator launch smoke command | non |
| `phone-core-apk-install-smoke-replay-20260923` | `e5281e68b5e5` | 2026-09-23 | teetee971 | divergente +5/-894 | histoires_disjointes | CI: fail fast and avoid broken Android 10 device profile | non |
| `phone-core-apk-install-smoke-replay2-20260923` | `b6b4dc0f4c8d` | 2026-09-23 | teetee971 | divergente +2/-890 | histoires_disjointes | Phone Core CI: restore APK install smoke policy test | non |
| `phone-core-call-decision-mapping` | `8599c2801878` | 2026-09-19 | teetee971 | divergente +2/-1208 | histoires_disjointes | Test privacy-bounded call decision journal mapping | non |
| `phone-core-call-screening-fail-open` | `761349bd0e87` | 2026-09-24 | teetee971 | divergente +1/-845 | histoires_disjointes | Phone Core: fail open on screening safety uncertainty | non |
| `phone-core-caller-ui-evidence-20260923` | `2ad07670fddc` | 2026-09-23 | teetee971 | divergente +1/-878 | histoires_disjointes | Record Caller ID physical evidence only when UI resumes | non |
| `phone-core-caller-ui-evidence-main-20260923` | `0797bd2439b1` | 2026-09-23 | teetee971 | divergente +1/-876 | histoires_disjointes | Record Caller ID physical evidence only when UI resumes | non |
| `phone-core-callerid-accessibility-20260923` | `956bc5c63e44` | 2026-09-23 | teetee971 | divergente +1/-1033 | histoires_disjointes | Phone Core UX: improve Caller ID accessibility semantics | non |
| `phone-core-cert-provenance-foundation` | `bca91b7d7274` | 2026-09-24 | teetee971 | divergente +2/-845 | histoires_disjointes | Phone Core: test certification provenance boundaries | non |
| `phone-core-cert-schema-v3-wifi` | `c5e05b1b8df7` | 2026-09-25 | teetee971 | contenue_dans_main +0/-816 | histoires_disjointes | Network: replay event-driven device registry on current main | non |
| `phone-core-cert-schema-v3-wifi-ccb748d` | `ccb748d92d90` | 2026-09-25 | teetee971 | contenue_dans_main +0/-819 | histoires_disjointes | Network: replay explainable local risk dossier on current ma | non |
| `phone-core-cert-v2-decouple-wifi` | `4b20252e1c95` | 2026-09-24 | teetee971 | divergente +6/-849 | histoires_disjointes | Fix schema v2 Phone Core validation expectations | non |
| `phone-core-certification-schema-v3-wifi` | `1809a7d35a98` | 2026-09-25 | teetee971 | contenue_dans_main +0/-814 | histoires_disjointes | Network: reject unspecified authorized LAN probe targets (#1 | non |
| `phone-core-certification-wifi-v3-5ceaeb1` | `5ceaeb1ffd5e` | 2026-09-25 | teetee971 | contenue_dans_main +0/-813 | histoires_disjointes | Wearables: add vendor-neutral capability truth foundation (# | non |
| `phone-core-certification-wifi-v3-8abb790` | `8abb790e21fa` | 2026-09-25 | teetee971 | contenue_dans_main +0/-809 | histoires_disjointes | Wearables: enforce exclusive handshake and session lifecycle | non |
| `phone-core-certification-wifi-v3-c16a1ec` | `c16a1ec08b92` | 2026-09-26 | dependabot[bot] | contenue_dans_main +0/-802 | histoires_disjointes | Bump the gradle-minor-patch group across 1 directory with 2  | non |
| `phone-core-certification-wifi-v3-c803cfc` | `c803cfcd4c3e` | 2026-09-25 | teetee971 | contenue_dans_main +0/-807 | histoires_disjointes | Wearables: define vendor-neutral adapter truth contract (#12 | non |
| `phone-core-contact-list` | `2e84d6dd8b0c` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Add permission-gated local contact listing | non |
| `phone-core-contact-list-v2` | `a42cca498a24` | 2026-09-19 | teetee971 | divergente +1/-1198 | histoires_disjointes | Add bounded local contacts source on current Phone Core main | non |
| `phone-core-contact-list-v3` | `380f47a8b8ea` | 2026-09-19 | teetee971 | divergente +1/-1196 | histoires_disjointes | Add bounded local contact list on hardened main | non |
| `phone-core-contact-list-v4` | `9ec4f436a1d5` | 2026-09-19 | teetee971 | divergente +1/-1195 | histoires_disjointes | Add bounded local contact list after SMS timeline integratio | non |
| `phone-core-contact-list-v5` | `d412e898968a` | 2026-09-19 | teetee971 | divergente +1/-1194 | histoires_disjointes | Add bounded local contact list after in-call hardening | non |
| `phone-core-contact-list-v6` | `b8d8907e847a` | 2026-09-19 | teetee971 | divergente +1/-1193 | histoires_disjointes | Add bounded local contacts after hidden-caller integration | non |
| `phone-core-contact-sms-action` | `595bcdcddde6` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Add Sentinel SMS action from dialer contact number | non |
| `phone-core-contacts-permission-request` | `58605b025738` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Add explicit contacts permission action to Sentinel dialer | non |
| `phone-core-dialer-contacts-ui` | `adf592739814` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Add contacts browser to Sentinel dialer | non |
| `phone-core-dialer-input-hardening` | `6a16ca7db695` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Harden Sentinel dialer number validation | non |
| `phone-core-dialer-responsive-v3-20260922` | `c7cfb3a013c5` | 2026-09-22 | teetee971 | contenue_dans_main +0/-1085 | histoires_disjointes | Phone Core: harden MMS quarantine path (#1035) | non |
| `phone-core-dialer-ux-v3-20260922` | `6ef41c918d83` | 2026-09-22 | teetee971 | divergente +2/-1085 | histoires_disjointes | Phone Core UX: surface call role, permission and launch fail | non |
| `phone-core-dtmf-cleanup` | `b9edbe2ba2fd` | 2026-09-24 | teetee971 | divergente +1/-845 | histoires_disjointes | Phone Core: make DTMF stop cleanup state-independent | non |
| `phone-core-emergency-sms-fail-closed-73c1220` | `73c1220e1baf` | 2026-09-25 | teetee971 | contenue_dans_main +0/-822 | histoires_disjointes | Phone Core: keep valid call lines when SIM labels fail (#118 | non |
| `phone-core-fsma-regulatory-feed-20260923` | `fc73e5916298` | 2026-09-23 | teetee971 | divergente +2/-979 | histoires_disjointes | Phone Core: test FSMA warning normalization and advisory sem | non |
| `phone-core-hidden-caller-screening` | `8b4c537eae72` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Handle hidden callers safely in screening UI | non |
| `phone-core-hidden-caller-screening-v2` | `0e4690bf4edc` | 2026-09-19 | teetee971 | divergente +1/-1198 | histoires_disjointes | Handle hidden callers on current Phone Core main | non |
| `phone-core-hidden-caller-screening-v3` | `4af2ddb3c4d4` | 2026-09-19 | teetee971 | divergente +1/-1196 | histoires_disjointes | Handle hidden callers safely on hardened main | non |
| `phone-core-hidden-caller-screening-v4` | `5dc7318e3ef5` | 2026-09-19 | teetee971 | divergente +2/-1180 | histoires_disjointes | Merge branch 'main' into phone-core-hidden-caller-screening- | non |
| `phone-core-history-presentation-boundary` | `20f193e37643` | 2026-09-19 | teetee971 | divergente +3/-1208 | histoires_disjointes | Test call history presentation privacy boundary | non |
| `phone-core-inbound-correlation-v2` | `69c99c7831a8` | 2026-09-19 | teetee971 | divergente +2/-1198 | histoires_disjointes | test(phone-core): align inbound correlation fixtures | non |
| `phone-core-incall-action-guards` | `b1de47fb80e1` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Guard in-call actions by telecom state | non |
| `phone-core-incall-action-guards-v2` | `0038a7ea29bd` | 2026-09-19 | teetee971 | divergente +1/-1198 | histoires_disjointes | Guard in-call actions on current Phone Core main | non |
| `phone-core-incall-action-guards-v3` | `7388fc484e01` | 2026-09-19 | teetee971 | divergente +1/-1197 | histoires_disjointes | Guard in-call actions after dialer integration | non |
| `phone-core-incall-action-guards-v4` | `262d9b25ec4e` | 2026-09-19 | teetee971 | divergente +1/-1196 | histoires_disjointes | Guard in-call actions on hardened main | non |
| `phone-core-incall-audio-20260923` | `46b282c3499e` | 2026-09-23 | teetee971 | divergente +4/-948 | histoires_disjointes | Phone Core UX: add truthful mute and audio-route controls | non |
| `phone-core-incall-conference-prep-20260923` | `9a0d11fbfa96` | 2026-09-23 | teetee971 | divergente +2/-904 | histoires_disjointes | Phone Core: test conference target presentation policy | non |
| `phone-core-incall-semantic-theme-20260922` | `4320558a1982` | 2026-09-22 | teetee971 | divergente +1/-1086 | histoires_disjointes | Phone Core UI: use semantic theme colors in call screen | non |
| `phone-core-incall-theme-contrast-v3-20260922` | `8c8f0cee4177` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UI: use semantic in-call contrast colors | non |
| `phone-core-incall-ux-v4-20260922` | `b992e80343c1` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UX: harden in-call states and small-screen layout | non |
| `phone-core-incoming-sms-hardening-20260922` | `b09ff3532d49` | 2026-09-22 | teetee971 | divergente +1/-1084 | histoires_disjointes | Phone Core SMS: bound and normalize incoming provider writes | non |
| `phone-core-journal-future-skew` | `c988099861ef` | 2026-09-19 | teetee971 | divergente +2/-1199 | histoires_disjointes | Test bounded Phone Core journal clock skew | non |
| `phone-core-journal-future-skew-v2` | `b56fcb30b8a5` | 2026-09-19 | teetee971 | divergente +2/-1198 | histoires_disjointes | Test bounded Phone Core clock skew | non |
| `phone-core-journal-future-skew-v3` | `520087dd33c1` | 2026-09-19 | teetee971 | divergente +3/-1180 | histoires_disjointes | Merge branch 'main' into phone-core-journal-future-skew-v3 | non |
| `phone-core-journal-future-skew-v4` | `43d6ff695512` | 2026-09-19 | teetee971 | divergente +3/-1180 | histoires_disjointes | Merge branch 'main' into phone-core-journal-future-skew-v4 | non |
| `phone-core-journal-future-skew-v5` | `06763806d660` | 2026-09-20 | teetee971 | divergente +4/-1170 | histoires_disjointes | Merge branch 'main' into phone-core-journal-future-skew-v5 | non |
| `phone-core-local-vs-global-state-truth-20260923` | `081b8f2799fe` | 2026-09-23 | teetee971 | contenue_dans_main +0/-916 | histoires_disjointes | Merge pull request #1092 from teetee971/phone-core-incall-st | non |
| `phone-core-missed-call-notification` | `ce79acbbcb00` | 2026-09-24 | teetee971 | divergente +2/-845 | histoires_disjointes | Phone Core: register Telecom missed-call receiver | non |
| `phone-core-multi-call-tracking` | `d763774d5d76` | 2026-09-24 | teetee971 | divergente +4/-845 | histoires_disjointes | Phone Core: keep direction synchronized with foreground call | non |
| `phone-core-notification-truth-ux-prep-20260923` | `6281e4b5ddc3` | 2026-09-23 | teetee971 | divergente +9/-916 | histoires_disjointes | Phone Core: correct notification evidence test counts | non |
| `phone-core-physical-evidence-20260923` | `0c50202c37dc` | 2026-09-23 | teetee971 | divergente +9/-1033 | histoires_disjointes | Phone Core: clarify current APK install timestamp state | non |
| `phone-core-protection-dashboard-ux-20260922` | `158844676f20` | 2026-09-22 | teetee971 | divergente +2/-1084 | histoires_disjointes | Phone Core UI: refresh protection state on resume | non |
| `phone-core-replay-1050-ux-20260922` | `ddbc25b72fc0` | 2026-09-22 | teetee971 | divergente +1/-1083 | histoires_disjointes | Phone Core UX: replay #1050 on current main | non |
| `phone-core-replay-1051-ux-20260922` | `90cf6183b119` | 2026-09-22 | teetee971 | divergente +1/-1083 | histoires_disjointes | Phone Core UX: replay #1051 on current main | non |
| `phone-core-replay-caller-report-v3-20260922` | `f0b773101a60` | 2026-09-22 | teetee971 | divergente +3/-1084 | histoires_disjointes | Fix Caller ID semantic color compilation | non |
| `phone-core-replay-incall-ux-v5-20260922` | `14eca9752ce6` | 2026-09-22 | teetee971 | divergente +1/-1084 | histoires_disjointes | Phone Core UX: replay responsive in-call states on latest ma | non |
| `phone-core-require-fresh-wifi-proof` | `f86f488cae87` | 2026-09-25 | teetee971 | contenue_dans_main +0/-821 | histoires_disjointes | Phone Core: replay fail-closed emergency SMS check on curren | non |
| `phone-core-restore-wifi-gate-180113` | `7c866edad2f4` | 2026-09-24 | teetee971 | divergente +1/-844 | histoires_disjointes | Phone Core: restore Wi-Fi software readiness gate | non |
| `phone-core-rtr-french-status-20260923` | `835f117bd9e8` | 2026-09-23 | teetee971 | divergente +8/-900 | histoires_disjointes | Phone Core UX: make contacts and history activation actionab | non |
| `phone-core-sms-canonical-sim-state-ae49869` | `ae498692f604` | 2026-09-25 | teetee971 | contenue_dans_main +0/-827 | histoires_disjointes | Phone Core: defend SMS thread ordering from future timestamp | non |
| `phone-core-sms-deliver-async-resilience` | `978a2f9be18d` | 2026-09-24 | teetee971 | divergente +2/-845 | histoires_disjointes | Phone Core: finish async SMS result when worker rejects | non |
| `phone-core-sms-delivery-feedback-20260922` | `7a70a09df37c` | 2026-09-22 | teetee971 | divergente +21/-1084 | histoires_disjointes | Phone Core tests: cover multipart SMS callback aggregation | non |
| `phone-core-sms-delivery-ui-20260923` | `83f738f7a8dd` | 2026-09-23 | teetee971 | divergente +4/-1027 | histoires_disjointes | Phone Core SMS UX: test durable provider status classificati | non |
| `phone-core-sms-delivery-ui-replay-20260923` | `163e7f882d62` | 2026-09-23 | teetee971 | divergente +4/-1022 | histoires_disjointes | Phone Core replay: SmsProviderMessageStateTest.kt | non |
| `phone-core-sms-destination-hardening` | `8ef9a58e36c6` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Harden SMS destination validation | non |
| `phone-core-sms-destination-hardening-v2` | `d614553c6a3d` | 2026-09-19 | teetee971 | divergente +1/-1198 | histoires_disjointes | Harden SMS destination on current Phone Core main | non |
| `phone-core-sms-emergency-check-73c122` | `bb4f4228084f` | 2026-09-25 | teetee971 | divergente +1/-822 | histoires_disjointes | Phone Core: replay emergency SMS fail-closed on current main | non |
| `phone-core-sms-emergency-check-73c1220` | `73c1220e1baf` | 2026-09-25 | teetee971 | contenue_dans_main +0/-822 | histoires_disjointes | Phone Core: keep valid call lines when SIM labels fail (#118 | non |
| `phone-core-sms-emergency-check-958a869` | `081d3ea7e12c` | 2026-09-25 | teetee971 | divergente +2/-823 | histoires_disjointes | Phone Core: explain blocked emergency-check SMS sends | non |
| `phone-core-sms-explicit-multisim-1809a7d` | `2dc5806cfedb` | 2026-09-25 | teetee971 | divergente +2/-814 | histoires_disjointes | Test explicit multi-SIM selection | non |
| `phone-core-sms-explicit-multisim-5ceaeb1` | `b5b3ca3f77ba` | 2026-09-25 | teetee971 | divergente +2/-813 | histoires_disjointes | Phone Core: test explicit multi-SIM selection | non |
| `phone-core-sms-explicit-multisim-73c1220` | `e5c4365e9537` | 2026-09-25 | teetee971 | divergente +2/-822 | histoires_disjointes | Test explicit multi-SIM SMS selection invariant | non |
| `phone-core-sms-explicit-multisim-8abb790` | `8abb790e21fa` | 2026-09-25 | teetee971 | contenue_dans_main +0/-809 | histoires_disjointes | Wearables: enforce exclusive handshake and session lifecycle | non |
| `phone-core-sms-explicit-multisim-a986b04` | `a986b04a1963` | 2026-09-25 | teetee971 | contenue_dans_main +0/-815 | histoires_disjointes | Network: replay bounded authorized LAN probe on current main | non |
| `phone-core-sms-explicit-multisim-c803cfc` | `7f42b7197d4b` | 2026-09-25 | teetee971 | divergente +2/-807 | histoires_disjointes | Test Phone Core explicit multi-SIM SMS selection | non |
| `phone-core-sms-explicit-multisim-f86f488` | `5b03c7de0cdd` | 2026-09-25 | teetee971 | divergente +2/-821 | histoires_disjointes | Phone Core: test explicit multi-SIM selection | non |
| `phone-core-sms-export-confirm-20260922` | `1a1078385c39` | 2026-09-22 | teetee971 | divergente +3/-1085 | histoires_disjointes | Fix SMS export confirmation state syntax | non |
| `phone-core-sms-french-risk-20260923` | `750ee95ea4d4` | 2026-09-23 | teetee971 | divergente +3/-912 | histoires_disjointes | Phone Core: test French SMS finding labels | non |
| `phone-core-sms-multisim-explicit-73c1220` | `73c1220e1baf` | 2026-09-25 | teetee971 | contenue_dans_main +0/-822 | histoires_disjointes | Phone Core: keep valid call lines when SIM labels fail (#118 | non |
| `phone-core-sms-outgoing-status-fix-20260923` | `6bb82ebfabef` | 2026-09-22 | teetee971 | divergente +8/-1083 | histoires_disjointes | Phone Core SMS: add SmsCallbackProgressTest.kt | non |
| `phone-core-sms-physical-truth-20260923` | `56da2122d3d3` | 2026-09-23 | teetee971 | divergente +4/-944 | histoires_disjointes | Phone Core UX: require successful complete SMS delivery proo | non |
| `phone-core-sms-physical-truth-replay-20260923` | `5e68d2b9a668` | 2026-09-23 | teetee971 | divergente +4/-939 | histoires_disjointes | Phone Core: replay strict SMS physical-evidence truth on cur | non |
| `phone-core-sms-role-sequence-20260923` | `90dcdba99f1e` | 2026-09-23 | teetee971 | divergente +3/-1033 | histoires_disjointes | Docs: align ROLE_SMS migration with measurable Phone Core se | non |
| `phone-core-sms-status-timeline` | `e29fe1105f35` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Record SMS telephony status in private timeline | non |
| `phone-core-sms-status-timeline-v2` | `c76ba4170a6c` | 2026-09-19 | teetee971 | divergente +1/-1198 | histoires_disjointes | Record outgoing SMS status on current Phone Core main | non |
| `phone-core-sms-status-timeline-v3` | `90fcbf4f9210` | 2026-09-19 | teetee971 | divergente +1/-1197 | histoires_disjointes | Record outgoing SMS status after dialer integration | non |
| `phone-core-timeline-adapter` | `f7e27dc2696c` | 2026-09-19 | teetee971 | divergente +2/-1208 | histoires_disjointes | Test call history timeline privacy boundary | non |
| `phone-core-timeline-correlation-direction` | `782e7265dcde` | 2026-09-19 | teetee971 | divergente +1/-1199 | histoires_disjointes | Limit Phone Core call-SMS correlation to inbound events | non |
| `phone-core-wifi-readiness-gate-6a186` | `6a186250356a` | 2026-09-24 | teetee971 | contenue_dans_main +0/-831 | histoires_disjointes | Network: replay explicit topology evidence on current main ( | non |
| `phone-core-wifi-scan-truth-20260923` | `e280f237ea08` | 2026-09-23 | teetee971 | divergente +4/-944 | histoires_disjointes | Phone Core UX: expose fresh versus cached Wi-Fi scan truth | non |
| `phone-core-wifi-scan-truth-replay-20260923` | `db4f284c8443` | 2026-09-23 | teetee971 | divergente +4/-939 | histoires_disjointes | Phone Core UX: replay Wi-Fi scan freshness status | non |
| `phone-core/caller-id-overseas-normalization-20260926` | `8d3eaa48c199` | 2026-09-26 | teetee971 | divergente +2/-795 | histoires_disjointes | Test overseas caller number normalization | non |
| `phone-core/caller-id-overseas-normalization-9fd7027` | `9fd70276fd48` | 2026-09-27 | teetee971 | contenue_dans_main +0/-696 | histoires_disjointes | Merge pull request #1264 from teetee971/site/d1-total-redesi | non |
| `phone-core/caller-id-overseas-replay-c7d21fa` | `c7d21fac2c96` | 2026-09-26 | teetee971 | contenue_dans_main +0/-784 | histoires_disjointes | Phone Core: keep SMS composer permissions minimal (#1239) | non |
| `phone-core/caller-report-confirmation-20260922` | `15753c5e4e3e` | 2026-09-22 | teetee971 | divergente +2/-1087 | histoires_disjointes | Fix Caller ID report confirmation build | non |
| `phone-core/certification-wifi-v3-2528b5f` | `2528b5f286fa` | 2026-09-26 | teetee971 | contenue_dans_main +0/-797 | histoires_disjointes | Android: replay truthful Protection mobile visual foundation | non |
| `phone-core/community-report-client-egress-guard-20260926` | `11032c76c901` | 2026-09-26 | teetee971 | divergente +3/-789 | histoires_disjointes | Test client-level fail-closed community report privacy guard | non |
| `phone-core/community-report-egress-revocation-6c54c22` | `6c54c22ac732` | 2026-09-27 | teetee971 | contenue_dans_main +0/-694 | histoires_disjointes | Security: reject static secrets packaged in Android APK (#12 | non |
| `phone-core/community-report-egress-revocation-9fd7027` | `9fd70276fd48` | 2026-09-27 | teetee971 | contenue_dans_main +0/-696 | histoires_disjointes | Merge pull request #1264 from teetee971/site/d1-total-redesi | non |
| `phone-core/community-report-egress-revocation-c541061` | `f44afa4bf5ae` | 2026-09-27 | teetee971 | divergente +3/-781 | histoires_disjointes | Test community-report revocation at egress | non |
| `phone-core/community-report-egress-revocation-eae3ec0` | `9ad752fb0be4` | 2026-09-27 | teetee971 | divergente +3/-780 | histoires_disjointes | Replay community-report egress revocation on current main | non |
| `phone-core/contact-provider-dial-format-20260930-f710` | `9b4021230f54` | 2026-09-30 | teetee971 | divergente +4/-640 | histoires_disjointes | test(phone): enforce formatted contact and history dialing | non |
| `phone-core/contacts-directory-ux-20260930` | `d4223ed29447` | 2026-09-30 | teetee971 | divergente +28/-642 | histoires_disjointes | test(phone-core): require complete virtualized contacts dire | non |
| `phone-core/device-test-activation-20260922` | `648cb7e08c92` | 2026-09-22 | teetee971 | divergente +9/-1096 | histoires_disjointes | Phone Core: keep optional call-log permission role-gated | non |
| `phone-core/dialer-semantic-theme-20260922` | `e6628a23a468` | 2026-09-22 | teetee971 | divergente +1/-1086 | histoires_disjointes | Phone Core UI: use semantic theme colors in dialer | non |
| `phone-core/exact-block-cold-start-20260930` | `8a721d18160f` | 2026-09-30 | teetee971 | divergente +3/-642 | histoires_disjointes | test(phone-core): forbid asynchronous exact-block key warmup | non |
| `phone-core/gate-wifi-readiness-0a00791` | `0a0079196aff` | 2026-09-29 | teetee971 | contenue_dans_main +0/-655 | histoires_disjointes | GeoIntel: add verifiable world-monitor foundation (#1334) | non |
| `phone-core/gate-wifi-readiness-0a00791-r2` | `0a0079196aff` | 2026-09-29 | teetee971 | contenue_dans_main +0/-655 | histoires_disjointes | GeoIntel: add verifiable world-monitor foundation (#1334) | non |
| `phone-core/gate-wifi-readiness-0c2e720` | `042c5c9ae1f2` | 2026-09-28 | teetee971 | divergente +1/-675 | histoires_disjointes | Phone Core: gate readiness on Wi-Fi scanner | non |
| `phone-core/gate-wifi-readiness-0c2e720-r2` | `22ae99076784` | 2026-09-29 | teetee971 | divergente +2/-675 | histoires_disjointes | test(phone-core): gate readiness on Wi-Fi scan | non |
| `phone-core/gate-wifi-readiness-10a6642` | `10a66428d18f` | 2026-09-27 | teetee971 | contenue_dans_main +0/-682 | histoires_disjointes | Phone Core: make first-run permission prompts sequential (#1 | non |
| `phone-core/gate-wifi-readiness-51537e6` | `51537e6e13e3` | 2026-09-30 | teetee971 | contenue_dans_main +0/-651 | histoires_disjointes | Phone Core: Android 16 truth and in-call redesign (#1342) | non |
| `phone-core/gate-wifi-readiness-5ad1aac` | `791899f4699d` | 2026-09-29 | teetee971 | divergente +1/-652 | histoires_disjointes | Phone Core: gate software readiness on Wi-Fi scan | non |
| `phone-core/gate-wifi-readiness-5ad1aac-r3` | `5ad1aac1a878` | 2026-09-30 | teetee971 | contenue_dans_main +0/-652 | histoires_disjointes | Harden required PR gate and stabilize live Lighthouse SLO (# | non |
| `phone-core/gate-wifi-readiness-610cac2` | `f8eca708f94d` | 2026-09-29 | teetee971 | divergente +2/-661 | histoires_disjointes | Phone Core: test Wi-Fi readiness as blocking prerequisite | non |
| `phone-core/gate-wifi-readiness-75f233c` | `01ed2128644e` | 2026-09-29 | teetee971 | divergente +1/-670 | histoires_disjointes | Phone Core: gate readiness on Wi-Fi scanner | non |
| `phone-core/gate-wifi-readiness-ab99fdb` | `2fae98f67014` | 2026-09-27 | teetee971 | divergente +1/-683 | histoires_disjointes | Phone Core: gate readiness on Wi-Fi scanner prerequisites | non |
| `phone-core/gate-wifi-readiness-d65054f` | `d65054fea019` | 2026-09-29 | teetee971 | contenue_dans_main +0/-671 | histoires_disjointes | VPN: replay fail-closed runtime activation on current main ( | non |
| `phone-core/gate-wifi-readiness-f856e19` | `f856e199987c` | 2026-09-29 | teetee971 | contenue_dans_main +0/-657 | histoires_disjointes | Fix Cloudflare canonical clean URLs (#1338) | non |
| `phone-core/history-pagination-20260930-a146` | `f5a8c4a5f6b9` | 2026-09-30 | teetee971 | divergente +6/-637 | histoires_disjointes | CI: keep Phone Core provider queries off the UI thread | non |
| `phone-core/incoming-call-sms-activation-20260922` | `1ac90a2b51b0` | 2026-09-22 | teetee971 | divergente +12/-1097 | histoires_disjointes | Phone Core: enforce dialer and SMS manifest requirements | non |
| `phone-core/mms-callback-binding-e127734` | `1c669648e5a9` | 2026-09-30 | teetee971 | divergente +1/-638 | histoires_disjointes | Phone Core: bind MMS callback token to download target | non |
| `phone-core/mms-callback-role-revalidation-20260930` | `568e051edeb6` | 2026-09-30 | teetee971 | divergente +2/-642 | histoires_disjointes | test(phone-core): require MMS callback role revalidation | non |
| `phone-core/mms-cold-start-integrated-20260930-91bb` | `ee70990f13e0` | 2026-09-30 | teetee971 | divergente +4/-641 | histoires_disjointes | fix(phone-core): integrate MMS callback role and cold-start  | non |
| `phone-core/mms-intake-hardening-20260922` | `3b528aa49d56` | 2026-09-22 | teetee971 | divergente +1/-1087 | histoires_disjointes | Phone Core: harden private MMS quarantine path | non |
| `phone-core/mms-preview-truth-fccebda` | `7385e353ef02` | 2026-09-30 | teetee971 | divergente +3/-629 | histoires_disjointes | Tests: build WebP extension-media MMS fixture explicitly | non |
| `phone-core/mms-receivers-off-main-20260930-f817` | `e8620febb21d` | 2026-09-30 | teetee971 | divergente +14/-633 | histoires_disjointes | CI: require MMS receivers to keep heavy work off BroadcastRe | non |
| `phone-core/mms-truthful-diagnostics-20260929` | `75e7bf92437b` | 2026-09-29 | teetee971 | divergente +2/-663 | histoires_disjointes | Phone Core: distinguish MMS cleanup failure | non |
| `phone-core/protection-visual-foundation-replay-20260926b` | `9291fe402090` | 2026-09-26 | teetee971 | divergente +1/-799 | histoires_disjointes | Android: replay truthful Protection mobile visual foundation | non |
| `phone-core/reopen-after-runtime-revocation-20260929` | `390294307963` | 2026-09-29 | teetee971 | divergente +4/-674 | histoires_disjointes | Test Phone Core setup reopening after revocation | non |
| `phone-core/reopen-after-runtime-revocation-replay-20260929` | `9225ae375485` | 2026-09-29 | teetee971 | divergente +3/-673 | histoires_disjointes | Test Phone Core setup reopening after revocation | non |
| `phone-core/replay-caller-report-confirmation-20260922` | `a3f57fab2b32` | 2026-09-22 | teetee971 | divergente +1/-1086 | histoires_disjointes | Phone Core UX: replay caller report confirmation on current  | non |
| `phone-core/replay-caller-report-confirmation-v2-20260922` | `c7cfb3a013c5` | 2026-09-22 | teetee971 | contenue_dans_main +0/-1085 | histoires_disjointes | Phone Core: harden MMS quarantine path (#1035) | non |
| `phone-core/replay-caller-report-v2-20260922` | `a9b0388e2b36` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UX: replay caller report confirmation | non |
| `phone-core/replay-dialer-theme-v2-20260922` | `3b6afa41df1f` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UI: replay semantic dialer theme on latest main | non |
| `phone-core/replay-incall-theme-v2-20260922` | `327af7fc3b4e` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UI: replay semantic in-call theme on latest main | non |
| `phone-core/replay-sms-theme-v2-20260922` | `44220a4a6bce` | 2026-09-22 | teetee971 | divergente +1/-1085 | histoires_disjointes | Phone Core UI: replay semantic SMS theme on latest main | non |
| `phone-core/reputation-egress-revocation-c541061` | `c954edd7029e` | 2026-09-27 | teetee971 | divergente +5/-781 | histoires_disjointes | Test reputation consent revocation at egress | non |
| `phone-core/respond-via-message-off-main-20260930-8789` | `5563a4b1ef46` | 2026-09-30 | teetee971 | divergente +3/-629 | histoires_disjointes | Merge branch 'main' into phone-core/respond-via-message-off- | non |
| `phone-core/runtime-revocation-main-20260929b` | `d65054fea019` | 2026-09-29 | teetee971 | contenue_dans_main +0/-671 | histoires_disjointes | VPN: replay fail-closed runtime activation on current main ( | non |
| `phone-core/runtime-revocation-reopen-20260927` | `0d7f119a457b` | 2026-09-27 | teetee971 | divergente +1/-696 | histoires_disjointes | Phone Core: reopen setup after runtime revocation | non |
| `phone-core/screening-post-response-off-main-20260930-8789` | `535db21810d9` | 2026-09-30 | teetee971 | divergente +4/-632 | histoires_disjointes | Tests: lock call-screening and local-log threading contracts | non |
| `phone-core/screening-post-response-off-main-replay-f8a9` | `f8a9d82117d1` | 2026-09-30 | teetee971 | contenue_dans_main +0/-631 | histoires_disjointes | Phone Core MMS: move receiver I/O and decode work off the ma | non |
| `phone-core/sms-accessibility-state-06b187a` | `933d76a73dca` | 2026-09-27 | teetee971 | divergente +1/-777 | histoires_disjointes | Phone Core: expose exact SMS accessibility state | non |
| `phone-core/sms-activation-state-20260927` | `3130e5065a47` | 2026-09-27 | teetee971 | contenue_dans_main +0/-691 | histoires_disjointes | Phone Core UI: align primary surfaces with Sentinel D.1 (#12 | non |
| `phone-core/sms-activation-truth-20260927` | `25b5eaa7739c` | 2026-09-27 | teetee971 | divergente +1/-691 | histoires_disjointes | Phone Core: expose truthful French SMS activation state | non |
| `phone-core/sms-callback-tombstone-pruning` | `1df73bce42e9` | 2026-09-24 | teetee971 | divergente +1/-847 | histoires_disjointes | Phone Core: preserve SMS callback tombstones under capacity  | non |
| `phone-core/sms-callback-tombstone-retention` | `971030102eb1` | 2026-09-24 | teetee971 | contenue_dans_main +0/-847 | histoires_disjointes | Merge pull request #1141: Phone Core legacy SMS activation g | non |
| `phone-core/sms-compose-subscription-truth` | `180113b7b964` | 2026-09-24 | teetee971 | contenue_dans_main +0/-844 | histoires_disjointes | Phone Core: certification v2 decouples Wi-Fi on current main | non |
| `phone-core/sms-explicit-multisim-2528b5f` | `2528b5f286fa` | 2026-09-26 | teetee971 | contenue_dans_main +0/-797 | histoires_disjointes | Android: replay truthful Protection mobile visual foundation | non |
| `phone-core/sms-explicit-multisim-b7a8150` | `b7a8150cbe86` | 2026-09-26 | teetee971 | contenue_dans_main +0/-787 | histoires_disjointes | Mesh: require explicit activation before control-plane egres | non |
| `phone-core/sms-explicit-multisim-d48fbe0` | `12c6f3a030f7` | 2026-09-26 | teetee971 | divergente +2/-795 | histoires_disjointes | Test explicit SMS SIM selection with Android default | non |
| `phone-core/sms-explicit-state-semantics-8ca2b0e` | `8ca2b0eff4db` | 2026-09-27 | teetee971 | contenue_dans_main +0/-779 | histoires_disjointes | Phone Core: recheck caller-reputation consent at network egr | non |
| `phone-core/sms-minimal-send-permissions-836c068` | `26b553f56447` | 2026-09-26 | teetee971 | divergente +4/-786 | histoires_disjointes | Phone Core: test minimal SMS composer permissions | non |
| `phone-core/sms-multipart-truthful-state-20260926` | `c3226c0b2866` | 2026-09-26 | teetee971 | divergente +7/-802 | histoires_disjointes | Test uncertain SMS submission policy | non |
| `phone-core/sms-multipart-truthful-state-replay-20260926` | `4b162625a9d9` | 2026-09-26 | teetee971 | contenue_dans_main +0/-801 | histoires_disjointes | CI: attest CodeQL merge revision (#1215) | non |
| `phone-core/sms-permission-policy-20260927` | `145fd457f64f` | 2026-09-27 | teetee971 | divergente +3/-683 | histoires_disjointes | Test Phone Core SMS least-privilege policy | non |
| `phone-core/sms-permission-policy-main-20260928` | `37f73c004018` | 2026-09-27 | teetee971 | contenue_dans_main +0/-679 | histoires_disjointes | Phone Core: add local Android activation diagnostics (#1283) | non |
| `phone-core/sms-refresh-on-resume` | `c6968fd67cc8` | 2026-09-21 | dependabot[bot] | contenue_dans_main +0/-1123 | histoires_disjointes | Bump org.json:json from 20250517 to 20260814 in /native-andr | non |
| `phone-core/sms-resume-refresh-908` | `17c5b5bc9636` | 2026-09-21 | teetee971 | contenue_dans_main +0/-1119 | histoires_disjointes | Phone: add private block metadata foundation (#913) | non |
| `phone-core/sms-role-fail-closed-2dd6033` | `2dd603387b7a` | 2026-09-30 | teetee971 | contenue_dans_main +0/-645 | histoires_disjointes | Android: complete contact directory and simplify home discov | non |
| `phone-core/sms-role-fail-closed-2de3e53` | `2de3e53fd798` | 2026-09-30 | teetee971 | contenue_dans_main +0/-648 | histoires_disjointes | Android UI: harden consistency and keep call controls reacha | non |
| `phone-core/sms-role-fail-closed-2de3e53-r2` | `2de3e53fd798` | 2026-09-30 | teetee971 | contenue_dans_main +0/-648 | histoires_disjointes | Android UI: harden consistency and keep call controls reacha | non |
| `phone-core/sms-role-fail-closed-3d41901` | `f05fefbbc2b6` | 2026-09-30 | teetee971 | divergente +1/-647 | histoires_disjointes | Phone Core: fail closed on SMS role lookup errors | non |
| `phone-core/sms-role-fail-closed-51537e6` | `a4dfe78bffb1` | 2026-09-30 | teetee971 | divergente +1/-651 | histoires_disjointes | Phone Core: fail closed on SMS role lookup errors | non |
| `phone-core/sms-role-fail-closed-5456fc4` | `5456fc412e4f` | 2026-09-30 | teetee971 | contenue_dans_main +0/-649 | histoires_disjointes | Android UI: close remaining visual consistency gaps (#1344) | non |
| `phone-core/sms-role-fail-closed-6444a89` | `63c7fac7f94e` | 2026-09-30 | teetee971 | divergente +1/-646 | histoires_disjointes | Phone Core: fail closed on SMS role lookup errors | non |
| `phone-core/sms-role-query-fail-closed-20260929` | `d9c1582b2d71` | 2026-09-29 | teetee971 | divergente +1/-657 | histoires_disjointes | Phone Core: fail closed when SMS role query fails | non |
| `phone-core/sms-role-query-fail-closed-20260930-r3` | `2de3e53fd798` | 2026-09-30 | teetee971 | contenue_dans_main +0/-648 | histoires_disjointes | Android UI: harden consistency and keep call controls reacha | non |
| `phone-core/sms-role-query-fail-closed-main-20260930` | `9fc1bf43795b` | 2026-09-29 | teetee971 | divergente +1/-652 | histoires_disjointes | Phone Core: fail closed when SMS role lookup fails | non |
| `phone-core/sms-sim-diagnostics-c7d21fa` | `a8cc0b300e4c` | 2026-09-27 | teetee971 | divergente +2/-784 | histoires_disjointes | Phone Core: test active SIM readiness diagnostics | non |
| `phone-core/sms-sim-refresh-20260927` | `6c54c22ac732` | 2026-09-27 | teetee971 | contenue_dans_main +0/-694 | histoires_disjointes | Security: reject static secrets packaged in Android APK (#12 | non |
| `phone-core/sms-state-accessibility-26a7c99` | `26a7c99ee89d` | 2026-09-27 | teetee971 | contenue_dans_main +0/-775 | histoires_disjointes | Android: add truthful network posture to System Doctor (#125 | non |
| `phone-core/sms-subscription-source-truth` | `971030102eb1` | 2026-09-24 | teetee971 | contenue_dans_main +0/-847 | histoires_disjointes | Merge pull request #1141: Phone Core legacy SMS activation g | non |
| `phone-core/sms-subscription-source-truth-20260924` | `9cf2061d70fb` | 2026-09-24 | teetee971 | contenue_dans_main +0/-837 | histoires_disjointes | Phone Core: wire certification provenance (#1170) | non |
| `phone-core/sms-subscription-truth-20260924` | `971030102eb1` | 2026-09-24 | teetee971 | contenue_dans_main +0/-847 | histoires_disjointes | Merge pull request #1141: Phone Core legacy SMS activation g | non |
| `phone-core/theme-semantic-surfaces-20260922` | `1bec7a73cdd0` | 2026-09-22 | teetee971 | divergente +1/-1086 | histoires_disjointes | Phone Core UI: use semantic theme surfaces in SMS | non |
| `phone-core/truth-integrated-20260930` | `7e5afbcad70e` | 2026-09-30 | teetee971 | divergente +11/-641 | histoires_disjointes | rebase(phone-core): replay integrated anti-regression gates | non |
| `phone-core/truth-integrated-backup-20260930` | `f120115e2599` | 2026-09-30 | teetee971 | divergente +26/-642 | histoires_disjointes | test(phone-core): enforce atomic durable evidence storage | non |
| `phone-core/wifi-cert-v5-54f6a78` | `54f6a7844fee` | 2026-10-01 | teetee971 | contenue_dans_main +0/-608 | histoires_disjointes | Finalization: align Android release documentation with execu | non |
| `phone-core/wifi-cert-v5-c7d6e7b` | `f06ab966bd35` | 2026-10-01 | teetee971 | divergente +2/-619 | histoires_disjointes | Tests: lock Wi-Fi into Phone Core certification v5 | non |
| `phone-core/wifi-certification-gate-e5c75ed` | `e5c75edb233b` | 2026-09-30 | teetee971 | contenue_dans_main +0/-622 | histoires_disjointes | Android: consolidate UX, performance and Phone Core contract | non |
| `phone-core/wifi-certification-v3-2528b5f` | `2528b5f286fa` | 2026-09-26 | teetee971 | contenue_dans_main +0/-797 | histoires_disjointes | Android: replay truthful Protection mobile visual foundation | non |
| `phone-core/wifi-certification-v3-836c068` | `836c068336ce` | 2026-09-26 | teetee971 | contenue_dans_main +0/-786 | histoires_disjointes | Mesh: enforce activation at WireGuard tunnel boundary (#1235 | non |
| `phone-core/wifi-certification-v3-b7a8150` | `b7a8150cbe86` | 2026-09-26 | teetee971 | contenue_dans_main +0/-787 | histoires_disjointes | Mesh: require explicit activation before control-plane egres | non |
| `phone-core/wifi-certification-v3-c7d21fa` | `c7d21fac2c96` | 2026-09-26 | teetee971 | contenue_dans_main +0/-784 | histoires_disjointes | Phone Core: keep SMS composer permissions minimal (#1239) | non |
| `phone-core/wifi-certification-v3-d2d9d82` | `d2d9d82cb8f6` | 2026-09-26 | teetee971 | contenue_dans_main +0/-792 | histoires_disjointes | Phone Core: keep alphanumeric OTP secrets local (#1228) | non |
| `phone-core/wifi-certification-v3-run11` | `c7d21fac2c96` | 2026-09-26 | teetee971 | contenue_dans_main +0/-784 | histoires_disjointes | Phone Core: keep SMS composer permissions minimal (#1239) | non |
| `phone-core/wifi-certification-v5` | `8e5f0c5163fb` | 2026-09-30 | teetee971 | contenue_dans_main +0/-621 | histoires_disjointes | Phone Core 1.0.5: activation truth, UX hardening and live vo | non |
| `phone-core/wifi-physical-evidence-a3ef84f` | `a3ef84fc23e5` | 2026-09-27 | teetee971 | contenue_dans_main +0/-782 | histoires_disjointes | Phone Core: make active-SIM readiness explicit (#1242) | non |
| `phone-core/wifi-scan-off-main-20260930-e862` | `7ac2561f5900` | 2026-09-30 | teetee971 | divergente +17/-633 | histoires_disjointes | CI: require Wi-Fi scan and trust evaluation off main thread | non |
| `phone-core/wifi-scan-off-main-20260930-f8a9` | `f8a9d82117d1` | 2026-09-30 | teetee971 | contenue_dans_main +0/-631 | histoires_disjointes | Phone Core MMS: move receiver I/O and decode work off the ma | non |
| `phone-sms-subscription-source-20260925` | `6a186250356a` | 2026-09-24 | teetee971 | contenue_dans_main +0/-831 | histoires_disjointes | Network: replay explicit topology evidence on current main ( | non |
| `phone-whatsapp-link-tests` | `34d3486fc403` | 2026-09-21 | teetee971 | divergente +2/-1120 | histoires_disjointes | Test WhatsApp click-to-chat input boundaries | non |
| `phone/block-metadata-policy-tests-current-main` | `3d57a270d349` | 2026-09-21 | teetee971 | divergente +1/-1109 | histoires_disjointes | Phone: strengthen block metadata policy tests | non |
| `phone/hold-resume-20260921` | `d7ecd5862deb` | 2026-09-21 | teetee971 | divergente +2/-1104 | histoires_disjointes | Phone: gate hold resume controls by Telecom state | non |
| `plan/verified-email-connected-services-20261104` | `3ea6a81a15ee` | 2026-11-04 | teetee971 | contient_main +1/-0 | histoires_disjointes | docs: define verified-email connected-services implementatio | oui |
| `principal` | `47064a8fae66` | 2025-10-31 | teetee971 | histoires_disjointes | histoires_disjointes | 🚀 Initial Commit FullDeploy v1 | non |
| `purge` | `f479683b4d91` | 2025-11-20 | sentinellebot | contenue_dans_main +0/-3006 | histoires_disjointes |  Purge complète du dépôt  reconstruction Sentinel | non |
| `qualification/google-play-readiness` | `8169dc50a333` | 2026-11-07 | teetee971 | contient_main +280/-0 | histoires_disjointes | Record executed Play qualification, preserve external blocke | oui |
| `qualification/phone-core-v1-20261029` | `da157f4c5593` | 2026-10-22 | teetee971 | contient_main +188/-0 | histoires_disjointes | fix(android): expose inventory page controls above results o | non |
| `qualification/play-android-20261104` | `fed548502c4c` | 2026-11-04 | teetee971 | contient_main +220/-0 | histoires_disjointes | test(voice-api): add reproducible local backend validation g | oui |
| `recovery/phone-core-emulator-phase1-20261005` | `aeffc1128ddb` | 2026-10-05 | teetee971 | contenue_dans_main +0/-451 | histoires_disjointes | test(phone-core): add incoming screening callback marker | non |
| `refactor/android-duplicate-hash-null-safe-main-20261006` | `1ef03996347f` | 2026-10-06 | teetee971 | contenue_dans_main +0/-301 | histoires_disjointes | fix(phone-core): qualify SEND_SMS denial correctly on Androi | non |
| `release/candidate-1.0.10-preflight-20261021` | `6a874b40a83d` | 2026-10-21 | teetee971 | contient_main +43/-0 | histoires_disjointes | test(security): add read-only redacted source secret and APK | non |
| `release/mobile-touch-targets` | `8bcab3e8e606` | 2026-09-05 | teetee971 | contenue_dans_main +0/-1527 | histoires_disjointes | A11y: harden shared mobile navigation (#257) | non |
| `release/require-qualified-main-20261003` | `747ce158d48b` | 2026-10-03 | teetee971 | divergente +1/-521 | histoires_disjointes | ci(release): require exact-main qualification before signing | non |
| `release/sentinel-d1-phase3-test-0242647-artifact` | `3685f5411b2b` | 2026-10-18 | Thierry Naud | contient_main +308/-0 | histoires_disjointes | chore(release): publish sentinel d1 phase3 test apk | non |
| `release/sentinel-d1-phase4-test-0944a1f-artifact` | `2c06804eefe5` | 2026-10-18 | Thierry Naud | contient_main +314/-0 | histoires_disjointes | chore(release): publish final phase4 test apk | non |
| `release/sentinel-d1-phase4-test-2008bae-artifact` | `1bf0f0387c9a` | 2026-10-18 | Thierry Naud | contient_main +313/-0 | histoires_disjointes | chore(release): publish phase4 test apk | non |
| `release/sentinel-d1-phase4-test-2a8eb3b-artifact` | `88727ea74fd9` | 2026-10-18 | Thierry Naud | contient_main +312/-0 | histoires_disjointes | chore(release): include phase4 apk binary | non |
| `release/sentinel-phone-core-v1-20261102` | `97a8d776559e` | 2026-11-02 | teetee971 | contient_main +194/-0 | histoires_disjointes | ui: remove APK installation actions from release Security La | non |
| `repair/arcep-numbering-1293` | `6c959d585b04` | 2026-09-28 | teetee971 | divergente +1/-675 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data | non |
| `repair/arcep-numbering-1293-exact` | `98875b865800` | 2026-09-28 | teetee971 | divergente +1/-675 | histoires_disjointes | Phone intelligence: refresh ARCEP numbering data | non |
| `repair/vulnerability-watch-1294` | `53a1e08dc114` | 2026-09-28 | teetee971 | divergente +1/-675 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `repair/vulnerability-watch-1294-exact` | `fa92a36361d7` | 2026-09-28 | teetee971 | divergente +1/-675 | histoires_disjointes | Threat intel: refresh vulnerability watch state | non |
| `replay-1142-main-9710301` | `971030102eb1` | 2026-09-24 | teetee971 | contenue_dans_main +0/-847 | histoires_disjointes | Merge pull request #1141: Phone Core legacy SMS activation g | non |
| `replay-1148-main-180113b` | `1a5b61c98974` | 2026-09-24 | teetee971 | divergente +1/-844 | histoires_disjointes | Replay #1148 on current main | non |
| `replay-1149-main-180113b` | `525ea3a360f0` | 2026-09-24 | teetee971 | divergente +1/-844 | histoires_disjointes | Replay #1149 on current main | non |
| `replay-1150-main-180113b` | `f4ed496b9813` | 2026-09-24 | teetee971 | divergente +1/-844 | histoires_disjointes | Replay #1150 on current main | non |
| `replay-1152-main-180113b` | `4dd6472fa4d2` | 2026-09-24 | teetee971 | divergente +1/-844 | histoires_disjointes | Replay Phone Core DTMF cleanup on current main | non |
| `replay-1156-main-16e3420` | `c06c19bf4568` | 2026-09-24 | teetee971 | divergente +1/-843 | histoires_disjointes | Replay #1156 on current main | non |
| `replay-1157-main-16e3420` | `0d1f5dc168fe` | 2026-09-24 | teetee971 | divergente +1/-843 | histoires_disjointes | Replay #1157 on current main | non |
| `replay-1158-main-16e3420` | `d7e5d95d08d3` | 2026-09-24 | teetee971 | divergente +1/-843 | histoires_disjointes | Replay #1158 on current main | non |
| `replay-1161-main-5031bb1` | `cd4666bcb9da` | 2026-09-24 | teetee971 | divergente +1/-842 | histoires_disjointes | Replay #1161 after call-screening merge | non |
| `replay-1162-main-5031bb1` | `d215f4283b93` | 2026-09-24 | teetee971 | divergente +1/-842 | histoires_disjointes | Replay #1162 after call-screening merge | non |
| `replay-1165-main-48e1c99` | `28d017ef12de` | 2026-09-24 | teetee971 | divergente +1/-841 | histoires_disjointes | Replay #1165 after SMS resilience merge | non |
| `replay/advanced-lab-boundary-main-20260922` | `48ba88c7be8c` | 2026-09-22 | teetee971 | divergente +2/-1096 | histoires_disjointes | Security: test authorized-lab boundary | non |
| `replay/advanced-lab-pricing-main-20260922` | `0aff14a60f46` | 2026-09-22 | teetee971 | divergente +3/-1096 | histoires_disjointes | Pricing: define Advanced Lab authorization terms | non |
| `replay/agp-9-4-1-main-20260922` | `55292fcf4fd8` | 2026-09-22 | teetee971 | divergente +1/-1096 | histoires_disjointes | Deps: bump Android Gradle Plugin to 9.4.1 | non |
| `replay/android-call-history-privacy-20260920` | `ed974a7577e4` | 2026-09-20 | teetee971 | contenue_dans_main +0/-1140 | histoires_disjointes | Add trusted sequence floor to VPN lease restore (#885) | non |
| `replay/cisa-kev-20260921-main-20260922` | `d34011ac6e8d` | 2026-09-22 | teetee971 | contenue_dans_main +0/-1096 | histoires_disjointes | Security: clarify authorized local discovery boundary (#996) | non |
| `replay/cisa-kev-refresh-20260920` | `60cc4ecd3b21` | 2026-09-20 | teetee971 | divergente +1/-1140 | histoires_disjointes | Refresh CISA KEV catalog snapshot | non |
| `replay/covert-device-watch-main-ac52ef4-20260922` | `0fff652ebb2c` | 2026-09-22 | teetee971 | divergente +8/-1088 | histoires_disjointes | Android: replay WifiRiskEvaluatorTest.kt | non |
| `replay/device-dna-flow-main-ac52ef4-20260922` | `5c9e8e8055af` | 2026-09-22 | teetee971 | divergente +6/-1088 | histoires_disjointes | Network: replay NetworkFlowAnalyzerTest.kt | non |
| `replay/device-registry-main-ac52ef4-20260922` | `706004c3cded` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Network: replay NetworkDeviceEventRegistryTest.kt | non |
| `replay/local-agent-main-ac52ef4-20260922` | `590e906c98f0` | 2026-09-22 | teetee971 | divergente +5/-1088 | histoires_disjointes | Network: replay LocalAgentProtocolTest.kt | non |
| `replay/local-discovery-manifest-wording-main-20260921` | `7efb896ab692` | 2026-09-21 | teetee971 | divergente +1/-1106 | histoires_disjointes | Security: clarify consent-bounded local discovery | non |
| `replay/local-discovery-wording-after-943-20260921` | `0f59b3c4d461` | 2026-09-21 | teetee971 | divergente +1/-1105 | histoires_disjointes | Security: clarify consent-bounded local discovery | non |
| `replay/network-alert-policy-main-ac52ef4-20260922` | `93ff8fc98b6a` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Network: replay NetworkAlertPolicyTest.kt | non |
| `replay/network-behavior-baseline-main-ac52ef4-20260922` | `66375a03022e` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Test local behavior baseline | non |
| `replay/network-correlation-main-ac52ef4-20260922` | `e52779938105` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Network: replay NetworkEvidenceCorrelationEngineTest.kt | non |
| `replay/network-risk-bounds-main-20260922` | `da0ba5075079` | 2026-09-22 | teetee971 | divergente +2/-1096 | histoires_disjointes | Network: test oversized risk-score fail-closed behavior | non |
| `replay/network-risk-dossier-main-ac52ef4-20260922` | `954ea24859c2` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Test explainable local risk dossier | non |
| `replay/network-snapshot-timeline-main-ac52ef4-20260922` | `3a876d56ea98` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Test snapshot timeline mapping | non |
| `replay/network-timeline-linear-main-20260922` | `a87c0b2bcae3` | 2026-09-22 | teetee971 | divergente +2/-1096 | histoires_disjointes | Network: test linear duplicate suppression semantics | non |
| `replay/network-topology-main-ac52ef4-20260922` | `dc52b080c63c` | 2026-09-22 | teetee971 | divergente +2/-1088 | histoires_disjointes | Network: replay NetworkTopologyEvidenceTest.kt | non |
| `replay/osint-cache-bounds-main-20260922` | `f5cb19a2f66c` | 2026-09-22 | teetee971 | divergente +2/-1096 | histoires_disjointes | Android: test bounded OSINT cache codec | non |
| `replay/phone-core-family-policy-after-754` | `cc956e3d3197` | 2026-09-19 | teetee971 | divergente +2/-1215 | histoires_disjointes | Add assisted Phone Core safety policy tests | non |
| `replay/pr1676-red-20261016` | `23a17582bab1` | 2026-10-16 | teetee971 | contenue_dans_main +0/-11 | histoires_disjointes | test(email): reproduce unfolded header length overflow | non |
| `save/2026-11-10-session` | `0b0fb1067cf4` | 2026-11-10 | teetee971 | histoires_disjointes | histoires_disjointes | Sauvegarde du 10 novembre 2026 : aperçu D.1 et talkie. | non |
| `security/authorized-lab-after-952-20260922` | `abecbc865c95` | 2026-09-21 | teetee971 | divergente +2/-1102 | histoires_disjointes | Replay paid authorized-lab policy after Phone main move | non |
| `security/authorized-lab-after-984-20260922` | `91d84fc449a4` | 2026-09-22 | teetee971 | divergente +2/-1097 | histoires_disjointes | Replay authorized-lab boundary after snapshot merge | non |
| `security/authorized-lab-policy-20260921` | `114e3216caad` | 2026-09-21 | teetee971 | divergente +4/-1103 | histoires_disjointes | Test: lock advanced lab modes behind paid entitlement | non |
| `security/codeql-kotlin` | `2d82b5f50e90` | 2026-09-06 | teetee971 | divergente +2/-1525 | histoires_disjointes | CodeQL: force uncached Kotlin compilation for extraction | non |
| `security/codeql-kotlin-v2` | `a4351886bd98` | 2026-09-06 | teetee971 | divergente +1/-1524 | histoires_disjointes | security: extend CodeQL to Android Kotlin | non |
| `security/covert-device-watch-20260921` | `5eb1c1fff3a8` | 2026-09-21 | teetee971 | divergente +9/-1103 | histoires_disjointes | UI: expose hidden-device detection channel status | non |
| `security/covert-device-watch-after-952-20260922` | `58be65235022` | 2026-09-21 | teetee971 | divergente +8/-1102 | histoires_disjointes | Replay covert-device watch after Phone main move | non |
| `security/covert-device-watch-after-984-20260922` | `d2fe87d0a82f` | 2026-09-22 | teetee971 | divergente +8/-1097 | histoires_disjointes | Replay covert-device watch after snapshot merge | non |
| `security/cra-executable-controls` | `88ffefac1f6d` | 2026-10-01 | teetee971 | divergente +6/-619 | histoires_disjointes | ci(cra): prove native release dependency inventory | non |
| `security/evidence-trust-layer` | `49ff66dc3bc1` | 2026-09-04 | teetee971 | divergente +34/-1562 | histoires_disjointes | chore: preserve deterministic evidence hashing | non |
| `security/local-discovery-scope-after-945-20260921` | `0b841bf42288` | 2026-09-21 | teetee971 | divergente +1/-1104 | histoires_disjointes | Security: clarify authorized local discovery scope | non |
| `security/local-discovery-scope-after-949-20260921` | `5b26cb2bd5fa` | 2026-09-21 | teetee971 | divergente +1/-1103 | histoires_disjointes | Security: clarify authorized local discovery scope | non |
| `security/local-discovery-scope-after-952-20260922` | `f1785ba39e5c` | 2026-09-21 | teetee971 | divergente +1/-1102 | histoires_disjointes | Replay authorized local discovery wording after Phone main m | non |
| `security/malwarebazaar-metadata-adapter` | `71693607149a` | 2026-09-06 | teetee971 | divergente +5/-1488 | histoires_disjointes | Inventory: classify MalwareBazaar adapter as dormant library | non |
| `security/owner-admin-access` | `0f8310e2402b` | 2026-09-06 | teetee971 | divergente +5/-1500 | histoires_disjointes | CI: classify owner access policy module | non |
| `security/owner-recovery` | `9b104c5fdd2b` | 2026-09-06 | teetee971 | contenue_dans_main +0/-1498 | histoires_disjointes | Security: add OWNER administrative access contract | non |
| `security/owner-recovery-v2` | `9b104c5fdd2b` | 2026-09-06 | teetee971 | contenue_dans_main +0/-1498 | histoires_disjointes | Security: add OWNER administrative access contract | non |
| `security/pass-151-action-allowlist` | `92ef194ce18a` | 2026-09-03 | teetee971 | divergente +32/-1629 | histoires_disjointes | test: cover structured execution adapter proof boundary | non |
| `security/pass-154-proof-freshness` | `b86fc88f2227` | 2026-09-03 | teetee971 | divergente +51/-1629 | histoires_disjointes | test: enforce execution precondition and postcondition separ | non |
| `security/pass-155-execution-binding` | `42cfd1108487` | 2026-09-03 | teetee971 | divergente +90/-1629 | histoires_disjointes | Security: use one final clock for execution proof validation | non |
| `security/zero-trust-orchestrator` | `3c7079a20ffa` | 2026-09-28 | teetee971 | divergente +3/-675 | histoires_disjointes | CI: add Zero Trust orchestrator | non |
| `security/zero-trust-orchestrator-75f233c` | `770932d05079` | 2026-09-29 | teetee971 | divergente +3/-670 | histoires_disjointes | security: add Zero Trust evidence orchestrator | non |
| `sentinel/autonomous-guard` | `c3b66a754f42` | 2026-09-04 | teetee971 | contenue_dans_main +0/-1562 | histoires_disjointes | Add plan-only remediation stage to autonomous maintenance | non |
| `sentinel/remediation-guard` | `c3b66a754f42` | 2026-09-04 | teetee971 | contenue_dans_main +0/-1562 | histoires_disjointes | Add plan-only remediation stage to autonomous maintenance | non |
| `site/advanced-lab-paid-option-20260921` | `00ddbb6c4f1e` | 2026-09-21 | teetee971 | divergente +3/-1103 | histoires_disjointes | Terms: define paid Advanced Lab entitlement boundary | non |
| `site/advanced-lab-pricing-after-952-20260922` | `a941016070a2` | 2026-09-21 | teetee971 | divergente +3/-1102 | histoires_disjointes | Replay Advanced Lab pricing after Phone main move | non |
| `site/advanced-lab-pricing-after-984-20260922` | `e1e9a9d0a6ee` | 2026-09-22 | teetee971 | divergente +3/-1097 | histoires_disjointes | Replay Advanced Lab pricing after snapshot merge | non |
| `site/android-status-after-dns` | `77cd0c849f7e` | 2026-09-18 | teetee971 | divergente +1/-1318 | histoires_disjointes | site: align anti-tracker status with merged gateway DNS | non |
| `site/android-status-after-dns-current` | `41efb3ec2747` | 2026-09-18 | teetee971 | divergente +1/-1317 | histoires_disjointes | rebase: android-preview.js | non |
| `site/android-status-after-dns-current2` | `8d09765e71a1` | 2026-09-18 | teetee971 | divergente +1/-1316 | histoires_disjointes | rebase: Refresh Android anti-tracker status on latest main | non |
| `sms-mms-boundary-adversarial` | `26e1db4e7727` | 2026-09-20 | teetee971 | divergente +1/-1129 | histoires_disjointes | Add adversarial MMS boundary tests | non |
| `sms-swipe-confirm-delete` | `0a0a7e6a2339` | 2026-09-21 | teetee971 | divergente +2/-1124 | histoires_disjointes | Fix SMS confirmation branch nesting | non |
| `social-intelligence/evidence-model-foundation` | `fc8175afdfe3` | 2026-09-28 | teetee971 | divergente +4/-677 | histoires_disjointes | Fix Social Intelligence CI for ESM repository | non |
| `spec/phone-core-security-intelligence` | `5860ba94b3e9` | 2026-10-12 | teetee971 | divergente +4/-293 | histoires_disjointes | docs(plan): add call security intelligence plan | non |
| `sync/pr1641-on-pr1678-20261017` | `72377be87696` | 2026-10-17 | teetee971 | contient_main +3/-0 | histoires_disjointes | test(ci): require bounded ADB evidence commands | non |
| `sync/pr1646-on-pr1678-20261017` | `95d73984e921` | 2026-10-17 | teetee971 | contient_main +4/-0 | histoires_disjointes | test(ci): register bounded adb reconnect contract | non |
| `sync/pr1675-main-20261016` | `fab42018202c` | 2026-10-16 | teetee971 | divergente +2/-18 | histoires_disjointes | test(network): preserve flow aggregation regression coverage | non |
| `sync/pr1675-main-after-1685-20261016` | `128ab841ac91` | 2026-10-16 | teetee971 | contenue_dans_main +0/-14 | histoires_disjointes | test(network): replay flow aggregation bounds regressions | non |
| `sync/pr1677-main-after-1676-20261016` | `65c315ee9b6a` | 2026-10-16 | teetee971 | contenue_dans_main +0/-3 | histoires_disjointes | test(community): require recipient country validation before | non |
| `sync/pr1678-main-after-1677-20261017` | `757cfe88976c` | 2026-10-16 | teetee971 | contient_main +1/-0 | histoires_disjointes | test(ci): require bounded ADB evidence recovery | non |
| `sync/pr1679-main-after-1677-20261017` | `edd199dd2a16` | 2026-10-17 | teetee971 | contient_main +1/-0 | histoires_disjointes | test(network): reject overlong behavior destination aliasing | non |
| `sync/pr1682-main-after-1677-20261017` | `2b855662ed92` | 2026-10-17 | teetee971 | contient_main +2/-0 | histoires_disjointes | test(ci): register bounded Build Native adb install contract | non |
| `teetee971-patch-1` | `c1a8b59927f4` | 2026-09-18 | teetee971 | divergente +2/-1304 | histoires_disjointes | Merge branch 'main' into teetee971-patch-1 | non |
| `teetee971-patch-2` | `8df997a53581` | 2026-09-18 | teetee971 | divergente +2/-1301 | histoires_disjointes | Merge branch 'main' into teetee971-patch-2 | non |
| `teetee971-patch-3` | `2e92f11b2fdf` | 2026-09-18 | teetee971 | divergente +4/-1296 | histoires_disjointes | Merge branch 'main' into teetee971-patch-3 | non |
| `test/android-call-rule-boundaries` | `a99b24995c6e` | 2026-09-19 | teetee971 | divergente +1/-1240 | histoires_disjointes | test(android): lock call screening safety boundaries | non |
| `test/android-call-rule-boundaries-v2` | `3d9780414a10` | 2026-09-19 | teetee971 | divergente +1/-1238 | histoires_disjointes | test(android): lock call screening safety boundaries | non |
| `test/android-community-report-privacy` | `044ed2e4d007` | 2026-09-19 | teetee971 | divergente +1/-1240 | histoires_disjointes | test(android): lock community report privacy contract | non |
| `test/android-community-report-privacy-v2` | `9feb313f1377` | 2026-09-19 | teetee971 | divergente +1/-1239 | histoires_disjointes | test(android): lock community report privacy contract | non |
| `test/android-otp-privacy-boundary` | `27fe44b81857` | 2026-09-19 | teetee971 | divergente +1/-1238 | histoires_disjointes | test(android): harden OTP privacy boundary | non |
| `test/android-otp-privacy-boundary-v2` | `8fbea333b58b` | 2026-09-19 | teetee971 | divergente +1/-1237 | histoires_disjointes | test(android): harden OTP privacy boundary | non |
| `test/android-sms-link-hardening` | `e3e700318a7a` | 2026-09-19 | teetee971 | divergente +1/-1245 | histoires_disjointes | test(android): harden SMS link analyzer edge cases | non |
| `test/mms-pdu-callback-fuzz-1455` | `ccdf3eb0530b` | 2026-10-03 | teetee971 | divergente +1/-512 | histoires_disjointes | test(phone-core): add deterministic MMS PDU and callback fuz | non |
| `test/mms-role-revocation-transaction-20261022` | `f6b5517558e1` | 2026-10-22 | teetee971 | contient_main +1/-0 | histoires_disjointes | test(android): cover MMS compensation when SMS role becomes  | oui |
| `test/remove-network-exposure-nonnull-warning-74ab6226` | `22307323bb58` | 2026-09-29 | teetee971 | divergente +1/-669 | histoires_disjointes | Test: remove redundant network exposure non-null assertions | non |
| `test/remove-network-exposure-nonnull-warning-75f233c` | `6e0e4c062d46` | 2026-09-29 | teetee971 | divergente +1/-670 | histoires_disjointes | test(android): remove redundant service hint non-null assert | non |
| `threat-intel-vulnerability-watch-current-main-20260923` | `fcf64d8ab55b` | 2026-09-23 | teetee971 | divergente +1/-997 | histoires_disjointes | Threat intel: replay vulnerability watch state on current ma | non |
| `threat-intel-vulnerability-watch-current-main-20260923b` | `a4fbd803127b` | 2026-09-23 | teetee971 | divergente +1/-894 | histoires_disjointes | Threat intel: replay vulnerability watch state on latest mai | non |
| `threat-intel/cisa-kev-refresh-after-984-20260922` | `af9f1eb8f1ba` | 2026-09-22 | teetee971 | divergente +1/-1097 | histoires_disjointes | Threat intel: refresh CISA KEV state | non |
| `threat-intel/cisa-kev-replay-20260926` | `87063cd6a160` | 2026-09-26 | teetee971 | divergente +1/-797 | histoires_disjointes | chore(threat-intel): replay latest CISA KEV snapshot | non |
| `tmp-do-not-use-89e64` | `89e64a0a54cb` | 2026-10-02 | teetee971 | divergente +7/-573 | histoires_disjointes | fix(phone-core): provide counter-free customer status on all | non |
| `tmp-noop` | `c558f4a3e741` | 2026-09-17 | teetee971 | divergente +11/-1354 | histoires_disjointes | Merge branch 'main' into tmp-noop | non |
| `tmp/rebase-adb-reconnect-red-20261013` | `6b3b8bc77404` | 2026-10-13 | teetee971 | divergente +3/-290 | histoires_disjointes | test(ci): replay bounded ADB reconnect RED on rebased parent | non |
| `tmp/rebase-bound-adb-reconnect-20261013` | `e99d48ee4a30` | 2026-10-13 | teetee971 | divergente +1/-290 | histoires_disjointes | fix(ci): replay bounded evidence recovery on merged main | non |
| `tmp/rebase-sms-submission-observable-20261013` | `59e1b95b8eb0` | 2026-10-12 | teetee971 | divergente +35/-291 | histoires_disjointes | test(sms): replay observable gate regression coverage | non |
| `tmp/rebase-sms-submission-observable-main-20261013` | `74e2617fc136` | 2026-10-13 | teetee971 | divergente +2/-290 | histoires_disjointes | test(sms): replay observable gate regression on merged main | non |
| `transfer/sentinel-ai-v3-20261116` | `723c6c6067ee` | 2026-11-16 | Sentinel Transfer | contient_main +359/-0 | histoires_disjointes | Transfer Sentinel AI V3 sources and tests | non |
| `transfer/sentinel-cycle10-validation-20261117` | `a0c82be42314` | 2026-11-17 | Thierry Naud | histoires_disjointes | histoires_disjointes | delivery: cycle10 bounded inference and pairing validation e | non |
| `transfer/sentinel-cycle9-validation-artifacts-20261117` | `8c171aa118e8` | 2026-11-17 | Thierry Naud | histoires_disjointes | histoires_disjointes | delivery: cycle9 S24 validation APKs and verified source bun | non |
| `transfer/sentinel-integral-p0-cycle3-20261116` | `caa43c1392b9` | 2026-11-16 | Thierry Naud | histoires_disjointes | divergente +6/-20 | chore: transfer verified local AI HTTP deadline fix [skip ci | non |
| `truth/phonecore-mms-label-2f27c60` | `6e685604719d` | 2026-10-01 | teetee971 | divergente +2/-618 | histoires_disjointes | CI: prevent overclaiming Phone Core MMS scope | non |
| `truth/phonecore-mms-label-343dc36` | `b714f342e345` | 2026-10-01 | teetee971 | divergente +2/-616 | histoires_disjointes | CI: prevent Phone Core MMS overclaim | non |
| `ui/android-capability-copy-current` | `9ee7e9abd50a` | 2026-09-18 | teetee971 | divergente +3/-1315 | histoires_disjointes | Fix HomeScreen button separation | non |
| `ui/android-capability-copy-current3` | `49b52b97c0c1` | 2026-09-18 | teetee971 | divergente +2/-1314 | histoires_disjointes | android: rebase capability copy (strings.xml) | non |
| `ui/android-capability-copy-current4` | `d9b994f6cf4f` | 2026-09-18 | teetee971 | divergente +4/-1275 | histoires_disjointes | Merge branch 'main' into ui/android-capability-copy-current4 | non |
| `ui/homepage-design-baseline` | `d35c5374a99c` | 2026-09-06 | teetee971 | divergente +1/-1522 | histoires_disjointes | ui: align homepage with shared design tokens | non |
| `ui/scope-shared-navigation-styles` | `1b8057da343d` | 2026-09-06 | teetee971 | divergente +2/-1524 | histoires_disjointes | test: keep shared navigation CSS scoped | non |
| `update/roadmap-2026-09-08` | `9f9d04503c8f` | 2026-09-07 | teetee971 | contenue_dans_main +0/-1414 | histoires_disjointes | chore(ci): remove obsolete MSDO placeholder (#358) | non |
| `ux/bottom-navigation-truth-94aa1ef` | `6905ed0de466` | 2026-09-30 | teetee971 | divergente +2/-626 | histoires_disjointes | UX: label communications destination | non |
| `ux/communications-bottom-nav-2f27c60` | `685eb4c8a135` | 2026-10-01 | teetee971 | divergente +3/-618 | histoires_disjointes | CI: reject legacy Calls/Messages bottom navigation | non |
| `ux/communications-priority-a5538eb` | `f08ec10d1550` | 2026-09-30 | teetee971 | divergente +1/-624 | histoires_disjointes | UX: prioritize usable communications actions | non |
| `ux/communications-priority-df11b2c` | `13568190f03a` | 2026-09-30 | teetee971 | divergente +1/-625 | histoires_disjointes | UX: prioritize usable communication actions | non |
| `ux/directory-alpha-sections-2026-10-02` | `a29ce4cf2644` | 2026-10-02 | teetee971 | divergente +9/-581 | histoires_disjointes | CI: guard grouped Unicode-safe contact sections | non |
| `ux/global-home-information-architecture-312743e` | `4287acb94f66` | 2026-09-30 | teetee971 | divergente +1/-627 | histoires_disjointes | UX: organize home tools by user intent | non |
| `ux/incall-calling-zone-2026-10-02` | `a4696bb63746` | 2026-10-02 | teetee971 | divergente +5/-580 | histoires_disjointes | test(phone): keep NANP resolution case focused | non |
| `ux/network-action-first-a5538eb` | `795c3c23e7e4` | 2026-09-30 | teetee971 | divergente +1/-624 | histoires_disjointes | UX: make network surveillance action-first | non |
| `ux/phone-core-activation-friendly-2026-10-02` | `abfa8c7d1eaf` | 2026-10-02 | teetee971 | divergente +5/-580 | histoires_disjointes | CI: guard simplified three-chip Phone Core activation | non |
| `ux/phone-core-state-chip-action-2026-10-02` | `2bcb37df1ab5` | 2026-10-02 | teetee971 | divergente +6/-580 | histoires_disjointes | CI: guard structural state-chip action semantics | non |
| `ux/phone-core-status-copy-2026-10-02` | `35cfea40c1c3` | 2026-10-02 | teetee971 | divergente +3/-580 | histoires_disjointes | Android resources: add Phone Core validation plurals | non |
| `ux/phone-directory-welcoming-2026-10-02` | `4fb5d76b59c7` | 2026-10-02 | teetee971 | divergente +6/-594 | histoires_disjointes | CI: make directory UX marker layout-agnostic | non |
| `ux/phone-directory-welcoming-replay-2026-10-02` | `add87dea6866` | 2026-10-02 | teetee971 | contenue_dans_main +0/-581 | histoires_disjointes | fix(intelligence): preserve graph truth state and auth regre | non |
| `ux/recents-cached-name-2026-10-02` | `679972527a76` | 2026-10-02 | teetee971 | divergente +7/-580 | histoires_disjointes | CI: guard bounded cached-name and masked-number rendering | non |
| `ux/remove-wifi-from-phonecore-2f27c60` | `83dacfeb4aa6` | 2026-10-01 | teetee971 | divergente +1/-618 | histoires_disjointes | UX: keep Wi-Fi setup out of Phone Core activation | non |
| `ux/remove-wifi-from-phonecore-343dc36` | `3a7a0b019f96` | 2026-10-01 | teetee971 | divergente +1/-616 | histoires_disjointes | UX: keep Wi-Fi setup outside Phone Core activation | non |
| `ux/remove-wifi-from-phonecore-b6e7cab` | `84fe9b75db4c` | 2026-10-01 | teetee971 | divergente +1/-615 | histoires_disjointes | UX: keep Wi-Fi outside Phone Core activation | non |
| `ux/settings-safe-links-a5538eb` | `9128b2877291` | 2026-09-30 | teetee971 | divergente +1/-624 | histoires_disjointes | UX: fail visibly when legal links cannot open | non |
| `ux/smarthome-safe-handoff-a5538eb` | `d9a796db0e94` | 2026-09-30 | teetee971 | divergente +1/-624 | histoires_disjointes | UX: make Smart Home settings handoff fail visibly | non |
| `ux/sms-swipe-delete-2026-10-02` | `af9081d94bcc` | 2026-10-02 | teetee971 | divergente +5/-581 | histoires_disjointes | CI: scope SMS delete guard to per-message action | non |
| `vpn/replay-runtime-activation-20260927` | `8ca2b0eff4db` | 2026-09-27 | teetee971 | contenue_dans_main +0/-779 | histoires_disjointes | Phone Core: recheck caller-reputation consent at network egr | non |
| `vpn/runtime-activation-gate-c541061` | `bb92d5de64e4` | 2026-09-27 | teetee971 | divergente +3/-781 | histoires_disjointes | Test VPN activation revocation before provisioning | non |
| `vpn/runtime-activation-gate-eae3ec0` | `7581266939d2` | 2026-09-27 | teetee971 | divergente +3/-780 | histoires_disjointes | Replay VPN activation gate on current main | non |
| `vpn/runtime-activation-replay-0c2e7203` | `8485717a8ad9` | 2026-09-28 | teetee971 | divergente +3/-675 | histoires_disjointes | VPN: replay fail-closed runtime activation guard | non |
| `vpn/runtime-activation-replay-10a66428` | `10a66428d18f` | 2026-09-27 | teetee971 | contenue_dans_main +0/-682 | histoires_disjointes | Phone Core: make first-run permission prompts sequential (#1 | non |
| `vpn/runtime-activation-replay-4acef4c8` | `4acef4c82645` | 2026-09-27 | teetee971 | contenue_dans_main +0/-688 | histoires_disjointes | Phone Core: report ungranted Android roles without guessing  | non |
| `vpn/runtime-activation-replay-8ca2b0e` | `d32d3d60b534` | 2026-09-27 | teetee971 | divergente +1/-779 | histoires_disjointes | VPN: require fail-closed runtime activation before provision | non |
| `vpn/runtime-activation-replay-ab99fdbc` | `ab99fdbccd4b` | 2026-09-27 | teetee971 | contenue_dans_main +0/-683 | histoires_disjointes | Phone Core: make setup role actionability truthful (#1278) | non |
| `vpn/runtime-activation-replay-ab99fdbc-v2` | `ab99fdbccd4b` | 2026-09-27 | teetee971 | contenue_dans_main +0/-683 | histoires_disjointes | Phone Core: make setup role actionability truthful (#1278) | non |
| `wearables/handshake-anti-replay` | `1c285397eacb` | 2026-09-25 | teetee971 | divergente +5/-803 | histoires_disjointes | Wearables: test single-use challenge anti-replay | non |
| `wearables/handshake-anti-replay-replay-20260926` | `c16a1ec08b92` | 2026-09-26 | dependabot[bot] | contenue_dans_main +0/-802 | histoires_disjointes | Bump the gradle-minor-patch group across 1 directory with 2  | non |
| `wearables/handshake-anti-replay-replay-2528b5f` | `3e1796cb31a4` | 2026-09-26 | teetee971 | divergente +3/-797 | histoires_disjointes | Wearables: test challenge transcript substitution | non |
| `wearables/handshake-anti-replay-replay-c16a1ec` | `1d51cbfbf6c6` | 2026-09-25 | teetee971 | divergente +1/-802 | histoires_disjointes | Wearables: replay anti-replay handshake on current main | non |
| `wearables/handshake-anti-replay-replay-main` | `c16a1ec08b92` | 2026-09-26 | dependabot[bot] | contenue_dans_main +0/-802 | histoires_disjointes | Bump the gradle-minor-patch group across 1 directory with 2  | non |
| `work/phone-core-v1-20261029` | `92d1c916ebbd` | 2026-10-29 | Thierry Naud | contient_main +192/-0 | histoires_disjointes | docs(phone-core): deliver verified artifacts manifest and S2 | oui |
