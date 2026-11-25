# Inventaire des modules

Commit lu : `6de5a272e0302ad1eccc6c2215157b13f97d485c`. Les lignes sont un comptage de sauts de ligne par extension connue (Kotlin, Java, JavaScript, Python, JSON, YAML, XML, HTML, CSS, Markdown, Gradle, SQL, Shell). Les binaires et les fichiers sans extension reconnue ne sont pas comptés. Aucun module n’a été compilé ni lancé.

Il n’y a pas de dossier racine `ai/` ni `integration/`. L’IA est dans `ai-governance/`, `ai-orchestrator/`, `backend/local-ai/` et `native-android-app/.../ai/`. Le mot integration désigne `config/integration-capabilities.json` et le paquet Kotlin `.../integration/`.

**Inclus dans le profil `-PsentinelCommercialV1=true`** signifie : la surface est atteinte par le graphe commercial, un composant du manifeste fusionné, ou un worker qui ne sort pas immédiatement. Le flag ne retire pas les sources Kotlin du jeu `main` : la plupart des classes restent compilées. « Compilé mais non exposé » n’est pas « inclus ».

Actif / dormant reprend `config/module-continuity-inventory.json` et `config/integration-capabilities.json` quand l’entrée existe. Sinon : déduit du raccordement lu, et marqué comme tel.

## Premier niveau

| Chemin | Langage principal | Fichiers | Lignes | Entrée lue | Raccordé | Registre | Profil V1 |
| --- | --- | ---: | ---: | --- | --- | --- | --- |
| `native-android-app/` | Kotlin | 870 | 91 899 | `settings.gradle` inclut `:app`, `:wear-app`, `:wearable-contract`, `:wearable-security` | Application Android | capacités téléphone et autres | Voir sous-modules |
| `public/` | JavaScript, HTML, CSS | 79 | 10 588 | `index.html`, pages `public/*.html` | Site statique | product-truth | Hors APK. Non inclus comme fonction téléphone. |
| `backend/` | Python | 29 | 6 770 | Voir sous-modules | API séparées | défense collective, voix | Non inclus |
| `osint-intelligence/` | JavaScript | 17 | 1 287 | Aucune entrée runtime. README. | Non raccordé à l’app ni au site | deux fichiers dormants ; outillage analyste désactivé | Non inclus |
| `security/` | JavaScript | 79 | 7 632 | Bibliothèques et tests | Partiel : pas d’écran unique | plusieurs `DORMANT_LIBRARY` | Non inclus |
| `core/` | Markdown, peu de JS | 9 | 847 | Pas d’entrée unique | Dossiers vides : `cyber-prediction`, `decision-support`, `geopolitics` | — | Non inclus |
| `config/` | JSON, JavaScript | 10 | 1 642 | Registres lus par des scripts de contrôle | Scripts et génération de statut | lui-même | Le flag Gradle n’est pas dans ce dossier. `feature-flags.js` est `DORMANT_LIBRARY`. |
| `decision-plane/` | JavaScript | 41 | 3 563 | Bibliothèques | Pas d’écran | SOC désactivé, plusieurs politiques dormantes | Non inclus |
| `network/` (`network/mesh`) | JavaScript | 47 | 10 638 | `network/mesh/server.js` | Pas appelé par l’UI Android | `mesh_runtime` verrouillé | Non inclus. JNI WireGuard exclu du packaging V1, ce n’est pas le Mesh. |
| `ops/` (`ops/vpn-gateway`) | JavaScript, Shell | 28 | 4 091 | Bibliothèques de passerelle | Pas de listener de production dans l’inventaire | VPN verrouillé, passerelle non disponible | Non inclus |
| `ai-governance/` | JavaScript | 28 | 1 624 | Bibliothèques | Pas d’entrypoint de production (inventaire) | orchestration dormante | Non inclus |
| `ai-orchestrator/` | JavaScript, JSON | 3 | 128 | Schémas | Pas d’appel Android trouvé | — | Non inclus |
| `security-digital-twin/` | JavaScript | 8 | 373 | Bibliothèque | Inventaire : dormant, pas d’entrée | — | Non inclus |
| `scripts/` | JavaScript | 141 | 17 757 | Scripts npm et CI | Outillage, pas l’application | plusieurs `CI_TOOLING` | Non inclus |
| `docs/` | Markdown | 93 | 10 025 | Documents | — | — | Les brouillons Play décrivent le profil. Ils ne sont pas l’application. |
| `audit/` | Markdown, JSON | 43 | 2 811 | Rapports de cycles | — | — | `audit/v1-commercial/FEATURE_MATRIX.md` décrit le profil. Ce dossier `audit/global/` est ajouté par la présente PR. |
| `.github/` | YAML | 49 | 5 764 | Workflows dans `.github/workflows/` | CI, non lancée | — | Non inclus |
| `data/` | JSON | 4 | 25 759 | Instantanés, dont `data/threat-intel/` | Lus par des scripts de veille, pas par l’écran CERT-FR | — | Non inclus |
| `preuves/` | XML JUnit | 337 | 3 673 | Artefacts déjà écrits | Pas une entrée | — | Non relancés |
| `deploy/` | YAML | 2 | 122 | `deploy/mesh` | Document de déploiement, non exercé | — | Non inclus |
| `compliance/`, `adversaries/`, `scenarios/`, `internal/`, `security-validation/` | Markdown ou JSON | petits | sous 700 chacun | Documents ou jeux | Pas des applications | — | Non inclus |
| `assets/` | — | 0 comptés | 0 | — | — | — | — |

## Android

| Module | Lignes Kotlin approximatives | Entrée | Raccordement | V1 |
| --- | ---: | --- | --- | --- |
| `:app` | 86 985 | `MainActivity`, manifeste | Deux graphes. Le flag choisit `CommercialPhoneCoreNavGraph` ou `NavGraph`. | Surface téléphone, filtrage, journaux, recherche, réglages commerciaux. |
| `:wear-app` | 1 477 | `wear-app/src/main/AndroidManifest.xml` | Module Gradle séparé. Le service Wear de `:app` est retiré du manifeste V1. | Non inclus dans la surface V1 de `:app`. Le module peut encore être construit à part : ce build n’a pas été fait. |
| `:wearable-contract` | 365 | Bibliothèque | Dépendance de `:app` | Compilé avec `:app`. Pas une surface. |
| `:wearable-security` | 1 021 | Bibliothèque | Dépendance de `:app` | Idem. |
| `app/.../security` | 31 753 | Nombreuses classes | Télécom, SMS, VPN, Wi-Fi, doctor, e-mail | Seuls les composants encore dans le manifeste fusionné et le graphe commercial sont exposés. |
| `app/.../ui` | 11 381 | Écrans Compose | Voir les deux graphes | Écrans commerciaux seulement. |
| `app/.../ai` | 1 760 | `SentinelAiActivity` | Réglages complets et manifeste principal | Activité retirée. |
| `app/.../voice` | 1 080 | `VoiceStudioActivity` | Manifeste principal | Activité retirée. Dépendance LiveKit encore déclarée ; .so LiveKit exclus du packaging V1. |
| `app/.../background` | 764 | Workers | OSINT, défense collective, catalogue, traceurs | OSINT, défense collective et traceurs annulés ou court-circuités. Catalogue de numéros encore synchronisé si le réglage utilisateur l’autorise. |
| `app/.../data` | 932 | `OsintRepository`, réglages | Écran OSINT et worker | OSINT non exposé. |
| `app/.../intelligence` | 1 553 | Adaptateurs d’indices SMS | Appelés depuis le chemin SMS | Le chemin SMS du manifeste reste. Non exécuté. |
| `app/.../integration` | 218 | Identifiants de capacité | Publication d’état local | Présent dans le processus. Pas une fonction utilisateur. |
| `app/.../smarthome` | 100 | `SmartHomeScreen` | Graphe complet | Non inclus. |
| `app/.../auth` | 156 | Auth0 | Activités Auth0 retirées du manifeste V1 | Non inclus. |
| `app/.../navigation` | 179 | Deux graphes | Voir ci-dessus | Graphe commercial : accueil, hub, protection commerciale, réglages commerciaux, recherche, filtrage, listes, historique, journaux. |

Point d’entrée processus : `SentinelApplication` (pas de travail télécom dans `onCreate`). Lanceur : `MainActivity`.

Workers lus : `OsintRefreshWorker`, `CollectiveDefenseWatchWorker`, `PublishedNumberCatalogWorker`, `TrackerWatchWorker`, `MmsDownloadCleanupWorker`, `MmsDownloadRecoveryWorker`, `MmsSendCleanupWorker`. Les trois derniers servent le chemin MMS, qui reste déclaré dans le manifeste.

Il n’y a pas de product flavor Gradle nommé. Le profil est la propriété `sentinelCommercialV1` (`app/build.gradle` L7–L12), défaut false. Elle remplace le manifeste, le libellé, les ABI debug, et exclut des `.so`.

## Backend

| Chemin | Lignes | Entrée | Raccord | V1 |
| --- | ---: | --- | --- | --- |
| `backend/wangiri-api` | 4 378 Python | `collective_intel.py` (FastAPI) | Écran et worker Android hors V1. `render.yaml` existe. Santé non lue. | Non inclus |
| `backend/voice-api` | 1 700 Python | API voix | Studio voix retiré du manifeste V1 | Non inclus |
| `backend/local-ai` | 640 Python | `service.py` décrit par le README, loopback | Activité Android hors V1. Poids non versionnés. | Non inclus |

## Langages demandés

TypeScript : aucun fichier `.ts` compté à la racine des modules parcourus. Java : peu (runner JVM sous `scripts/ai-validation/`, pas une application). Le volume utile est Kotlin, JavaScript, Python, JSON, YAML, XML.

## Modules abandonnés ou vides

- `core/cyber-prediction`, `core/decision-support`, `core/geopolitics` : aucun fichier.
- `config/feature-flags.js` : défauts conservateurs, non importé par le runtime (inventaire).
- Bibliothèques listées `DORMANT_LIBRARY` ou `INTENTIONALLY_DORMANT` dans `config/module-continuity-inventory.json` (ingérence, OSINT analyste, SOC, threat intel, VPN, modération). La liste complète est ce JSON, non recopiée ligne à ligne ici.
- `DigitalExposureClient` et `MeshRuntimeCoordinator` : classes présentes, aucun site d’appel trouvé hors de leur fichier.

## Non vérifié

Build Gradle des quatre modules, contenu du manifeste fusionné produit par aapt, et tout fichier seulement présent sur le clone local de l’utilisateur.
