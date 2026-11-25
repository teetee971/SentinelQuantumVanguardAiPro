# Focus OSINT et threat intelligence

Lecture statique du commit `6de5a272e0302ad1eccc6c2215157b13f97d485c`. Aucun test, aucun flux réseau, aucun worker et aucune page n’ont été exécutés. « Réel » signifie que le code d’appel existe. Cela ne signifie pas que le flux répond, ni qu’un appareil a été qualifié.

Statut produit inchangé : **COMMERCIAL NO-GO**. Ce focus ne qualifie aucune capacité.

Lien de lecture : `https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/`

`origin/main` (`935344423b2af2a4a57c58123f2c5c178b46ec03`) et cette ligne n’ont **aucun ancêtre commun** (confirmé par l’API GitHub Compare, HTTP 404). Les blobs ci-dessous décrivent `6de5a272`. Quand le même objet Git existe sur `main`, c’est indiqué.

## Verdict

| Élément | Nature constatée | Raccordement | Profil `-PsentinelCommercialV1=true` | Catégories |
| --- | --- | --- | --- | --- |
| Flux Android CERT-FR | Client RSS/Atom réel (Rome + OkHttp), trois URL HTTPS figées. Pas de jeu de données factice dans ce chemin. | Écran du `NavGraph` complet et worker. Pas le graphe commercial. | Absent de la navigation. Worker annulé et `doWork()` sort tout de suite. | 2, 9 |
| `osint-intelligence/` | Bibliothèques sur événements déjà fournis. Pas de collecte, pas d’attribution. | Aucun import hors tests et workflow CI. | Dormant, hors APK. | 4 |
| `evidence-core.js` | Validateur fail-closed. `canAutonomouslyAttribute()` et `canAutonomouslySanction()` retournent `false`. | Tests et inventaire seulement. | Dormant. | 4 |
| DISARM-FR | Note de conception. Aucune taxonomie importée. | Aucun. | Absent. | 3 |
| FIMI / ingérence comme offre | Catalogue `NOT_FOR_SALE`, statut déclaré PLANNED/FOUNDATION. Page roadmap en prose. | Pas de moteur branché à l’app ni au site. | Absent du profil téléphone. | 3, 4 |
| Adaptateurs MalwareBazaar et VirusShare | Code HTTP borné, clé exigée, téléchargement d’échantillon interdit. | Pas de serveur ni d’écran. | Dormant. | 4, 9 |
| Page `public/threat-intelligence.html` | Script navigateur qui interroge des API publiques au clic. Le HTML dit ne pas inventer de liste de secours. | Site statique. Pas l’application Android. | Hors APK V1. | 1 pour le lien page→script, 9 pour les API. Non exécuté. |
| Veille CISA KEV | Fichier JSON versionné + workflow qui réécrit une branche d’automation. | Script, pas l’écran Android. | Hors profil V1. | 4, 9 |
| Backend OSINT dédié | Pas de route FastAPI ni Flask d’OSINT dans `backend/`. | Absent. | Absent. | 6 |

Catégorie 5 (simulation seule) : aucun de ces chemins n’est un simulateur. Le simulateur red team du site (`public/red-team-simulator.html`) est un autre module, hors de ce focus. Catégorie 10 : aucune. Rien n’a été exécuté, donc rien n’est déclaré réparable sans régression.

## 1. Application Android — flux CERT-FR

### Fichiers

| Fichier | Rôle | Preuve |
| --- | --- | --- |
| `native-android-app/app/src/main/java/com/sentinel/quantum/data/OsintFeedItem.kt` | Enum fermée de trois sources | [L18–L22](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/data/OsintFeedItem.kt#L18-L22) |
| `.../data/OsintRepository.kt` | GET HTTPS, plafond 5 Mio et 500 entrées, redirections refusées | [L18–L125](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/data/OsintRepository.kt#L18-L125) |
| `.../ui/screens/OsintFeedScreen.kt` | À l’ouverture : cache local puis `fetchAllFeedsResult()` | [L48–L142](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/OsintFeedScreen.kt#L48-L142) |
| `.../navigation/NavGraph.kt` | Route `osint_feed` et détail | [L29–L31](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/navigation/NavGraph.kt#L29-L31) et [L93–L101](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/navigation/NavGraph.kt#L93-L101) |
| `.../navigation/CommercialPhoneCoreNavGraph.kt` | Ne déclare pas `OsintFeed` | [L24–L36](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/navigation/CommercialPhoneCoreNavGraph.kt#L24-L36) |
| `.../ui/screens/HomeScreen.kt` | Le graphe complet navigue vers le flux | autour de la ligne 611 |
| `.../background/OsintRefreshWorker.kt` | Worker périodique. Sortie immédiate si le flag commercial est vrai | [L21–L22](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/background/OsintRefreshWorker.kt#L21-L22) |
| `.../background/WorkScheduler.kt` | Planifie le worker. Intervalle 0 = annulation | [L24–L57](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/background/WorkScheduler.kt#L24-L57) |
| `.../data/SettingsStore.kt` | Défaut `INTERVAL_NEVER` (0). Valeurs admises : 0, 4, 12, 24 | [L202–L236](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/data/SettingsStore.kt#L202-L236) |
| `.../MainActivity.kt` | `WorkScheduler.sync` n’est appelé que si le flag commercial est faux | [L117–L124](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/MainActivity.kt#L117-L124) |
| `.../SentinelApplication.kt` | En profil commercial, annule le worker OSINT au premier passage au premier plan | [L58–L67](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/SentinelApplication.kt#L58-L67) |
| `.../ui/screens/SettingsScreen.kt` | Réglage d’intervalle et de notification, réservé à l’écran réglages complet | [L451–L511](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/ui/screens/SettingsScreen.kt#L451-L511) |

Sources écrites dans l’enum, et seulement celles-là :

- CERT-FR Alertes — `https://www.cert.ssi.gouv.fr/alerte/feed/`
- CERT-FR Avis — `https://www.cert.ssi.gouv.fr/avis/feed/`
- CERT-FR CTI — `https://www.cert.ssi.gouv.fr/cti/feed/`

Le dépôt refuse une URL qui ne commence pas par `https://`. User-Agent : `SentinelQuantumVanguardAiPro-OSINT/1.0`. Pas de clé API dans ce client. Une réponse partielle ne remplace pas un cache complet.

Ce n’est pas une plateforme OSINT : pas de graphe, pas de DISARM, pas de MalwareBazaar, pas d’appel vers `osint-intelligence/`.

### État V1

Trois barrières indépendantes : la route n’est pas dans le graphe commercial, `MainActivity` ne programme pas le worker, `OsintRefreshWorker.doWork()` retourne `Result.success()` sans réseau si `BuildConfig.COMMERCIAL_PHONE_CORE_V1` est vrai. Le code reste compilé avec les sources `main` : le profil remplace le manifeste et la navigation, il n’efface pas ce fichier.

Même hors V1, le worker ne part pas tant que l’utilisateur n’a pas choisi 4, 12 ou 24 heures. L’écran, lui, appelle le réseau dès qu’il est composé.

### `main`

`OsintRepository.kt` et `OsintFeedItem.kt` ont le même blob sur `main` et sur `6de5a272`. `OsintRefreshWorker.kt` diffère : `main` n’a pas le `return` commercial (deux lignes). `CommercialPhoneCoreNavGraph.kt` et `config/integration-capabilities.json` sont absents de `main`. Sur `main`, l’écran et le worker existent donc sans cette coupure de profil.

## 2. Dossier `osint-intelligence/`

Bibliothèques JavaScript. L’inventaire `config/module-continuity-inventory.json` marque deux fichiers dormants. Les autres ne sont importés que par leurs tests, par `campaign-analysis.js`, ou par un workflow. Aucun `backend/` et aucune activité Android ne les importe. Recherche des imports effectuée sur le dépôt.

| Chemin | Ce que le code fait | Entrée de production | Inventaire |
| --- | --- | --- | --- |
| `public-data/source-registry.js` | Liste fermée data.gouv.fr, Annuaire des entreprises, PCI cadastre. `automatic_operational_action: false`. Hôtes HTTPS autorisés. | Aucune. Tests et workflow `intel-public-data-validation.yml`. | Non listé comme runtime. |
| `public-data/recent-atom-watch.js` | Parse un Atom fourni. Ne télécharge pas lui-même dans le module lu. | Tests. Workflow `correlated-vulnerability-watch-validation.yml`. | — |
| `foreign-influence/registry-intelligence.js` | Normalise une observation de registre. Niveau `UNVERIFIED` tant qu’un connecteur n’a pas authentifié la source. Pas d’attribution. | Tests seulement. | `DORMANT_LIBRARY` |
| `coordination/coordination-engine.js` | Score de similarité sur des features déjà présentes (texte, temps, URL, hashtags). Le commentaire dit que ce n’est pas un classificateur d’identité. | Importé par le moteur de corrélation et le smoke test. | — |
| `narratives/narrative-engine.js` | Similarité textuelle déterministe. | Importé par la corrélation et le smoke test. | — |
| `graph/propagation-graph.js` | Graphe à partir d’observations déjà normalisées. | Importé par `campaign-analysis.js`. | — |
| `correlation/campaign-correlation-engine.js` | Regroupe des événements fournis. | Importé par `campaign-analysis.js` et ses tests. | — |
| `orchestration/campaign-analysis.js` | Plafond 750 événements. `side_effect_performed: false`. Pas d’attribution ni de blocage. | Aucune entrée hors tests. | `INTENTIONALLY_DORMANT` |
| `schemas/*.schema.json` | Contrats JSON. | Non branchés à un serveur lu. | — |
| `README.md` | Décrit l’architecture voulue et dit qu’aucun scraper de contournement n’est fourni. | Documentation. | — |

`osint-engine-smoke.test.js` appelle les fonctions sur des objets en mémoire puis écrit `osint-engine-smoke: OK`. Ce n’est pas une collecte.

Sources nommées dans le registre public, non appelées depuis l’application :

- `https://www.data.gouv.fr/api/1/datasets/recent.atom`
- `https://recherche-entreprises.api.gouv.fr`
- `https://cadastre.data.gouv.fr/...`

Aucune clé n’est lue dans ce registre. Le README dit que la collecte temps réel reste à brancher. Cette phrase est cohérente avec l’absence d’entrypoint.

## 3. `security/foreign-interference/evidence-core.js`

[Fichier](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/security/foreign-interference/evidence-core.js). Blob identique sur `main`.

Fonctions exportées : `hashEvidenceContent`, `validateEvidenceRecord`, `validateRelation`, `validateHumanAttributionGate`, `buildInterferenceAssessment`, `canAutonomouslyAttribute`, `canAutonomouslySanction`.

Une évaluation de niveau `ATTRIBUTION` est refusée sans un objet de porte humaine (`HUMAN_ATTRIBUTION_GATE_REQUIRED`, lignes 93–112 et 161–164). Le résultat porte `autonomous_enforcement_allowed: false` (ligne 185). Les deux fonctions d’autonomie retournent `false` (lignes 190–196).

L’inventaire dit : bibliothèque validée par des tests, sans entrée de collecte ni d’exécution, attribution et sanction autonomes interdites. Le seul import trouvé est `evidence-core.test.js`, plus le script npm `test:foreign-interference`. Ce n’est pas un moteur FIMI raccordé.

## 4. DISARM-FR et FIMI

`docs/DISARM-FR-INTEGRATION.md` (blob identique sur `main`) décrit un usage futur : identifiant, version, licence CC BY 4.0, lien observation→technique. Il dit que le dépôt VIGINUM-FR/DISARM-FR « complète le futur module ». Aucun fichier de taxonomie, aucun `techniqueId` peuplé, aucun import DISARM dans le code JavaScript ou Kotlin. `scripts/check-social-intelligence-boundaries.js` vérifie que le document de politique contient des phrases obligatoires. C’est un contrôle de document, pas un chargeur de taxonomie.

`docs/SOCIAL-INTELLIGENCE-EVIDENCE-MODEL.md` fixe le chaînage Source → Observation → Evidence → Relationship → DISARM mapping → Hypothesis → Human review → Assessment, et interdit les champs acteur, pays, commanditaire, État d’origine, intention.

`docs/DIGITAL_INTERFERENCE_DEFENSE.md` se présente comme une orientation et dit ne pas prouver qu’une fonction opérationnelle existe.

`config/commercial-catalog.json`, entrée `foreign_interference_defense` : offre `NOT_FOR_SALE`, note « PLANNED/FOUNDATION », pas de vente avant ingestion, licence, provenance, rétention. `public/roadmap.html` affiche une carte « Ingérence / FIMI » au statut planned. `public/product-truth.js` associe `threat-intelligence.html` au module `foreign_interference_defense` : c’est une étiquette de page, pas un moteur d’ingérence derrière cette page.

`config/product-capabilities.json` (mis à jour le 2026-10-05) ne contient pas d’identifiant FIMI. L’identifiant le plus proche est `osint_android_feeds` : `implemented: true`, `customer_available: false`, `runtime_verified: false`. Le statut généré `docs/PRODUCT_CAPABILITY_STATUS.md` dit `IMPLEMENTED_NOT_VERIFIED`. Cet état déclaré décrit les flux Android, pas DISARM.

`audit/v1-commercial/FEATURE_MATRIX.md` place « Veille, réputation distante, WhatsApp, partage d’e-mail et autre enrichissement » en **EXCLUDE** pour le profil commercial.

## 5. `security/threat-intelligence/`

Vingt fichiers JavaScript, chacun avec un test voisin pour les modules listés dans `package.json` (`test:threat-intel` et scripts de veille). Nature : bibliothèques et scripts. Pas un service écoutant un port dans `backend/`.

| Module | Constat | Secret | Raccord Android / site |
| --- | --- | --- | --- |
| `source-registry.js` | Registre. MalwareBazaar, VirusShare, AV-Atlas, cartes Kaspersky, Check Point, Radware, NETSCOUT. `automated_ingestion_enabled: false` et `attribution_capability: false` sur ces entrées. | Aucun. | Non importé par l’app. |
| `malwarebazaar-adapter.js` | POST métadonnées vers `https://mb-api.abuse.ch/api/v1/`. `MALWAREBAZAAR_AUTH_KEY_REQUIRED` si la clé injectée est vide. | Clé abuse.ch, non présente dans le dépôt. | Non. |
| `virusshare-adapter.js` | `https://virusshare.com/apiv2`. `VIRUSSHARE_API_KEY_REQUIRED`. `canVirusShareAdapterDownloadSamples()` retourne `false`. | Clé VirusShare, non présente. | Non. |
| `query-gate.js` | Cache mémoire, pas de scheduler. | — | Non. Inventaire `DORMANT_LIBRARY`. |
| `daily-brief.js` | Consolide des observations fournies. Accepte un résumé LLM optionnel et le rejette s’il est invalide. Pas de collecteur RSS. | — | Non. `DORMANT_LIBRARY`. |
| `normalization.js` | Forme d’observation. | — | Utilisé par les adaptateurs. |
| `vulnerability-source-registry.js` | CISA KEV, OSV (`https://api.osv.dev/v1`), NVD (`https://services.nvd.nist.gov/rest/json/cves/2.0`). Seul KEV a `automated_ingestion_enabled: true` dans ce registre. | NVD peut exiger une clé selon le quota NIST ; aucune clé n’est lue dans ce fichier. | Le site a son propre client, voir §6. |
| `vulnerability-correlation.js`, `vulnerability-history.js`, `vulnerability-watch-ledger.js` | Corrélation et journal à partir de données passées au script. | — | `scripts/collect-vulnerability-watch.js` et `scripts/update-vulnerability-watch-ledger.js`. |

Inventaire : MalwareBazaar et VirusShare sont `DORMANT_LIBRARY`. `config/integration-capabilities.json` (absent de `main`) : `threat_intelligence` = `LOCKED_EXTERNAL_DEPENDENCY` / `PARTIAL_PUBLIC_SOURCES_ONLY` ; `osint_analyst_tooling` = `INTENTIONALLY_DISABLED`.

## 6. Site

`public/threat-intelligence.html` charge `threat-intelligence.js` (ligne 77). Le script fige :

- GitHub Advisories `https://api.github.com/advisories?per_page=10`
- NVD `https://services.nvd.nist.gov/rest/json/cves/2.0?resultsPerPage=10`
- CISA KEV, le JSON public
- OSV `https://api.osv.dev/v1/query`
- fichier local `data/vulnerability-watch.json`

Le HTML affiche d’abord « NON VÉRIFIÉ » et dit qu’une source en échec ne doit pas être remplacée par une liste inventée. Cet audit n’a pas ouvert la page ni appelé ces URL. Le raccordement constaté est l’inclusion du script dans la page. GitHub Advisories et NVD peuvent refuser un navigateur sans jeton : ce comportement n’a pas été observé ici.

Cette page ne charge pas `evidence-core.js`, ni DISARM, ni les flux CERT-FR.

`public/investigations.html` et `investigation-maigret.js` sont un carnet local distinct. Ils ne sont pas le flux CERT-FR. Leur exécution n’a pas été faite.

## 7. Fichiers de données

- `data/threat-intel/cisa-kev.json`
- `data/threat-intel/vulnerability-watch-ledger.json`
- `data/threat-intel/vulnerability-watch-snapshot.json`

Ce sont des instantanés versionnés. Leur fraîcheur par rapport au jour de lecture (2026-11-19) n’a pas été contrôlée. Le workflow `.github/workflows/cisa-kev-refresh.yml` est prévu pour lancer `node scripts/update-cisa-kev.js` et pousser `automation/cisa-kev-refresh`. Il n’a pas été déclenché. De nombreuses branches `automation/cisa-kev-refresh-*` et `automation/vulnerability-watch-*` existent ; elles n’ont pas été extraites.

## 8. Backend

`backend/local-ai`, `backend/voice-api`, `backend/wangiri-api` ne contiennent pas le client CERT-FR ni `osint-intelligence`. La défense collective (`collective_intel.py`) est un autre domaine (indicateurs de réputation), traitée dans la matrice générale, pas comme OSINT CERT-FR.

## 9. Tests présents, non lancés

| Commande ou fichier | Périmètre |
| --- | --- |
| `npm run test:foreign-interference` | `evidence-core.test.js` |
| `npm run test:threat-intel` | normalisation, registres, MalwareBazaar, VirusShare, query-gate, daily-brief, script CISA |
| `.github/workflows/osint-validation.yml` | `registry-intelligence.test.js`, `campaign-analysis.test.js`, smoke |
| `.github/workflows/intel-public-data-validation.yml` | registres et `public/threat-intelligence.js` (contrôle de syntaxe) |
| `.github/workflows/correlated-vulnerability-watch-validation.yml` | corrélation, historique, recent-atom-watch |
| `.github/workflows/cisa-kev-refresh.yml` | test threat-intel puis écriture du JSON |
| Tests Kotlin du paquet `data` / background | non énumérés un par un au-delà de la présence du module ; aucun lancement Gradle |

Aucun de ces tests n’a été exécuté dans cette phase. Aucun résultat PASS n’est repris d’un journal ancien comme s’il s’appliquait à ce commit.

## 10. Branches pertinentes

| Référence | Rapport avec ce code |
| --- | --- |
| `6de5a272` (lecture) | Contient le flag commercial dans le worker. 12 commits devant `integration/sentinel-v1-commercial-20261118`. Histoire disjointe de `main`. |
| `integration/sentinel-v1-commercial-20261118` (`113862500`) | Mêmes blobs OSINT que `6de5a272` pour les fichiers comparés, y compris le `return` commercial du worker. |
| `main` (`935344423`) | Même `OsintRepository` et même `evidence-core.js`. Worker sans le `return` V1. Pas de graphe commercial. |
| `cursor/v1-p0-privacy-catalog-c39e`, `cursor/v1-play-compliance-20261118-c39e`, `cursor/v1-call-screening-audit-536d`, `cursor/v1-phone-core-full-audit-20261118` | Descendent de la ligne integration. Non extraites. Le diff integration→`6de5a272` ne touche pas les fichiers OSINT listés. |
| Branches `automation/cisa-kev-refresh-*` et `automation/vulnerability-watch-*` | Pointes d’automation. Contenu non lu. |
| `integration/d1-visual-phonecore-20261029` | Absente des branches distantes de ce clone. |
| `fix/v1-phone-core-p1-20261119` | Absente du remote après `git fetch`. |

## 11. Ce qui n’a pas été vérifié

- Réponse réelle des flux CERT-FR, data.gouv.fr, NVD, KEV, OSV, GitHub Advisories, MalwareBazaar, VirusShare.
- Quota, blocage navigateur, ou contenu RSS mal formé.
- Fraîcheur des trois JSON de `data/threat-intel/`.
- Exécution des workflows et des tests.
- Comportement WorkManager sur un appareil, y compris avec l’intervalle par défaut à 0.
- Diff fichier à fichier de chaque branche d’automation.
- Clone local de l’utilisateur sur `integration/d1-visual-phonecore-20261029`, et les fichiers non suivis signalés : `app/schemas/`, `ui/design/SentinelEvidenceGraph.kt`, `SentinelHolographicScene.kt`, `SentinelQuantumCore.kt`, `SentinelReferencePanels.kt`, `SentinelVoiceVisualizer.kt`. Ils ne sont pas dans ce clone. Ils ne sont pas classés absents pour autant : ils sont **non examinés**.
- Aucune clé n’a été cherchée hors du dépôt (variables d’environnement de production, coffres).
