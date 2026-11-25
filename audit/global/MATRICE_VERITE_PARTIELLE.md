# Matrice de vérité partielle

Commit lu : `6de5a272e0302ad1eccc6c2215157b13f97d485c`. Rien n’a été exécuté. Le statut V1 reste **COMMERCIAL NO-GO**.

Catégorie primaire = le qualificatif le plus juste pour le dépôt lu, en exigeant un appel effectif. Les autres catégories qui s’appliquent sont indiquées. Le détail OSINT est dans [FOCUS_OSINT_THREAT_INTEL.md](FOCUS_OSINT_THREAT_INTEL.md).

Déclaré = `config/product-capabilities.json` (champ `updated_at` : 2026-10-05), `docs/PRODUCT_CAPABILITY_STATUS.md` (généré depuis ce registre), `audit/v1-commercial/FEATURE_MATRIX.md` (profil `-PsentinelCommercialV1=true` seulement). Le registre d’octobre ne décrit pas le flag commercial de novembre : l’écart est signalé.

## Décomptes

### Primaire (une catégorie par ligne, total 16)

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 3 | 10 | 1 | 1 | 0 | 1 | 0 | 0 | 0 | 0 |

Les trois « 1 » sont le Phone Core (rôles Android non prouvés), l’analyse SMS raccordée au manifeste qui reste dans le profil V1, et le site statique dont les pages sont des fichiers. Aucun n’est un PASS.

### Toutes les catégories qui s’appliquent (une ligne peut compter deux fois)

| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 3 | 11 | 1 | 6 | 0 | 1 | 3 | 4 | 8 | 0 |

Catégorie 5 : 0 sur cette liste. Le simulateur `public/red-team-simulator.html` est hors liste ; il est noté en annexe. Catégorie 10 : 0, parce qu’aucune correction n’a été exécutée.

## Lignes

### 1. Ingérence étrangère / FIMI / DISARM-FR — primaire 3

Aussi 4. Preuves et écart : focus OSINT. Déclaré catalogue : `NOT_FOR_SALE`, PLANNED/FOUNDATION. Le registre `product-capabilities.json` n’a pas d’entrée FIMI. `FEATURE_MATRIX.md` ne la met pas dans le V1. Écart : le nom existe dans le catalogue et sur la roadmap ; le code d’import DISARM n’existe pas. `evidence-core.js` est une bibliothèque de validation, pas le produit FIMI.

### 2. Cybersécurité Android — primaire 2

Aussi 7. `SecurityAudit.performAudit()` lit les permissions demandées par **le paquet installé de Sentinel**, pas celles des autres applications ([SecurityAudit.kt L10–L28](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/security/SecurityAudit.kt#L10-L28)). L’écran est dans `NavGraph.kt` L32–L34 et est ouvert depuis `HomeScreen` et `PhoneSecurityScreen`. Il n’est pas dans `CommercialPhoneCoreNavGraph`. Déclaré : pas une ligne propre du registre d’octobre. La matrice V1 ne l’inclut pas dans les fonctions livrées. Écart : le nom « cybersécurité Android » est plus large que le contrôle du propre manifeste.

### 3. Audit des permissions — primaire 2

Aussi 8. `AppPermissionAnalyzerScreen` appelle `analyzeInstalledApps()` (`AppPermissionAnalyzer.kt` L39–L75, `getInstalledApplications`). Route du graphe complet (`NavGraph.kt` L62–L64), absente du graphe commercial. Aucune `QUERY_ALL_PACKAGES` trouvée dans les sources lues. Sur les Android récents, la liste visible sans ce privilège est incomplète. Ce comportement n’a pas été mesuré sur appareil. Déclaré : hors matrice V1.

### 4. Analyse e-mail / SMS — primaire 1

Aussi 2 et 8.

SMS : `SmsLinkAnalyzer` est appelé par `SentinelSmsDeliverReceiver` (vers la ligne 114), par `SmsComposeActivity` et par `SmsScannerScreen`. Le composeur et les receivers SMS/MMS restent dans le manifeste principal. Le manifeste V1 ne les retire pas. `CommunicationsHubScreen` L132–L139 lance le composeur et le dialer, et cet écran est dans le graphe commercial. C’est un appel effectif **dans le profil V1**. Le rôle SMS par défaut est exigé par Android : catégorie 8. Non exécuté.

E-mail : `EmailSecurityAnalyzer` analyse un message brut fourni, sans boîte mail ni DNS ([commentaire L8–L11](https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/6de5a272e0302ad1eccc6c2215157b13f97d485c/native-android-app/app/src/main/java/com/sentinel/quantum/security/EmailSecurityAnalyzer.kt#L8-L11)). Écran dans le graphe complet seulement. `MainActivity` ne traite l’intent de partage que si le flag commercial est faux (L117–L119). Catégorie 2 pour l’e-mail.

Déclaré V1 : SMS inclus, partage d’e-mail EXCLUDE (`FEATURE_MATRIX.md`). Cohérent avec le code lu. Le registre d’octobre ne sépare pas l’analyse de contenu du Phone Core.

### 5. Nettoyage système / System Doctor — primaire 2

Aussi 7. `SystemDoctorScreen` est dans le graphe complet (`NavGraph.kt` L35–L37), pas dans le graphe commercial. `SentinelSystemDoctor.scan()` empile des constats et publie `PHYSICAL_REQUIRED`. Il ne supprime pas. Le nettoyage passe par `SentinelDeviceUtilitiesOrchestrator`, limité au cache de l’application (`SentinelOwnedCleanupCollector` : `cacheDir` et `externalCacheDir` seulement). Ce n’est pas un nettoyage du système Android. Déclaré : `device_utilities` et `system_doctor_release_evidence` = `PHYSICAL_REQUIRED` dans `integration-capabilities.json`. `FEATURE_MATRIX.md` ne les met pas dans le V1. Écart de nom : « System Doctor » existe ; « nettoyage système » non.

### 6. Mises à jour automatiques — primaire 6

Aussi 4. Aucun `InAppUpdate`, `PackageInstaller` de mise à jour, ni téléchargement d’APK de l’application trouvé. `config/feature-flags.js` fixe `FEATURE_ANDROID_AUTO_UPDATE` à `false`. L’inventaire classe ce fichier `DORMANT_LIBRARY` : pas importé par le runtime. Le rafraîchissement du catalogue de numéros (`PublishedNumberCatalogScheduler`) est un autre mécanisme, opt-in, pas une mise à jour de l’application. Déclaré : le drapeau existe et il est faux. `FEATURE_MATRIX.md` ne promet pas d’auto-update.

### 7. OSINT / threat intelligence — primaire 2

Aussi 4 et 9. Voir le focus. Déclaré : `osint_android_feeds` = `IMPLEMENTED_NOT_VERIFIED`, `customer_available` false. `threat_intelligence` verrouillé « sources publiques partielles ». `osint_analyst_tooling` désactivé par politique. Matrice V1 : veille EXCLUDE. Écart : le registre dit implemented/configured pour les flux Android ; le profil commercial de novembre coupe l’écran et le worker. Les bibliothèques JS ne sont pas le flux CERT-FR.

### 8. Protection réseau Wi-Fi / Bluetooth — primaire 2

Aussi 8. `WifiScanner`, `BluetoothScanner` et `NetworkSurveillanceScreen` existent. La route est dans le graphe complet (`NavGraph.kt` L65–L67) et dans `HomeScreen` / `PhoneSecurityScreen`. Le graphe commercial ne l’a pas. Le manifeste V1 retire `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, la localisation et les features wifi / BLE (`src/v1Commercial/AndroidManifest.xml` L11–L22). `SentinelApplication` appelle `TrackerWatchScheduler.sync(context, false)` quand le flag commercial est vrai. Déclaré : `wifi_observation` et `bluetooth_observation` = `PHYSICAL_REQUIRED`. Matrice V1 : EXCLUDE, NOT RUN. Cohérent sur l’exclusion. Les scanners n’ont pas été lancés.

### 9. SOC / decision plane — primaire 2

Aussi 4. `integration-capabilities.json` : `soc_decision_plane` = `INTENTIONALLY_DISABLED`. `security/soc/passive-pipeline.js` et `decision-plane/policy/soc-automation-policy.js` sont des bibliothèques sans entrée de production (inventaire). Aucun écran Android « SOC » trouvé dans les graphes. Déclaré : désactivé par politique. Pas d’écart vers un SOC actif. Les tests JS existent et n’ont pas été lancés.

### 10. Défense collective — primaire 2

Aussi 9. `CollectiveDefenseScreen` construit `CollectiveDefenseClient` et est dans le graphe complet (`NavGraph.kt` L71–L73). `CollectiveDefenseWatchWorker` est programmé depuis `MainActivity` seulement hors profil commercial, et annulé dans `SentinelApplication` en profil commercial. Backend : `backend/wangiri-api/collective_intel.py` (FastAPI). Déclaré : `IMPLEMENTED_NOT_VERIFIED`, bloqueurs de déploiement et d’appareil. Santé du service non observée. Matrice V1 : enrichissement EXCLUDE.

### 11. Exposition numérique — primaire 2

Aussi 4 et 9. L’écran `DigitalExposureScreen` appelle `PwnedPasswordClient` (vérification k-anonyme, préfixe SHA-1). Il est dans le graphe complet, pas dans le graphe commercial. `DigitalExposureClient` (jeton Auth0, `egressGate` par défaut à false) n’est instancié nulle part ailleurs : le `grep` du constructeur ne trouve que la classe. Déclaré : `digital_exposure` = `LOCKED_EXTERNAL_DEPENDENCY` / `IDENTITY_BINDING_MISSING`. Écart : l’écran branché est le contrôle de mot de passe Have I Been Pwned, pas le client d’exposition authentifié. Service externe non appelé ici.

### 12. Agent IA / Qwen — primaire 2

Aussi 9. `SentinelAiActivity` est dans le manifeste principal (L90) et retirée par le manifeste V1 (`tools:node="remove"`). `SettingsScreen.kt` L416 lance l’activité. L’écran réglages commercial ne le fait pas. `SentinelAiLocalEngine` parle à un transport loopback et épingle un SHA-256 de modèle. `backend/local-ai/README.md` dit que les poids et le binaire llama.cpp ne sont pas dans le dépôt ni dans l’APK, et que le README considère le pont comme non qualifié Android. Les deux textes peuvent diverger sur « client présent » : le Kotlin existe ; les poids n’y sont pas. Déclaré matrice V1 : EXCLUDE, BLOCKED Qwen ARM64. Aucune inférence lancée.

### 13. VPN — primaire 2

Aussi 7 et 9. `VpnScreen` est dans le graphe complet. Le texte de l’écran dit qu’aucune action de connexion n’est affichée sans chaîne runtime vérifiable (`VpnScreen.kt` vers L130). Le service `GoBackend$VpnService` est retiré du manifeste V1. Les bibliothèques natives WireGuard sont exclues du packaging quand le flag est vrai (`app/build.gradle` L102–L110). Passerelle : bibliothèques dans `ops/vpn-gateway/`, inventaire dormant, `vpn_runtime` = `GATEWAY_NOT_AVAILABLE`. Déclaré registre : `IMPLEMENTED_NOT_CONFIGURED`, trois bloqueurs. Matrice V1 : EXCLUDE. Écart : le registre dit implemented ; l’écran lu n’offre pas le bouton de connexion, et le profil V1 retire le service.

### 14. Mesh — primaire 4

Aussi 9. `network/mesh/server.js` implémente un plan de contrôle HTTP. `MeshRuntimeCoordinator` n’est construit dans aucun autre fichier Kotlin (recherche du nom hors de sa définition et de `MeshControlPlaneClient`). `VpnScreen` mentionne « Arbitrage avec Private Mesh » dans un texte, sans appel. Déclaré : `mesh_runtime` = `LOCKED_EXTERNAL_DEPENDENCY` / `BACKEND_NOT_DEPLOYED`, et le registre d’octobre dit implemented mais non configuré. Écart : du code serveur et un client Kotlin existent ; l’application ne les appelle pas depuis un écran, un manifeste ou un worker lu. Matrice V1 : EXCLUDE.

### 15. Phone Core (dialer, SMS, MMS, filtrage) — primaire 1

Aussi 8 et 9. Manifeste principal : `SentinelCallScreeningService`, `SentinelInCallService`, `SentinelDialerActivity`, `SmsComposeActivity`, receivers SMS et MMS, `SentinelRespondViaMessageService` (lignes 54–193). Le manifeste V1 ne retire pas ces composants. Le graphe commercial inclut le hub qui les lance, le filtrage, les listes, l’historique et les journaux. C’est un raccordement réel du profil V1. Ce n’est pas une qualification : les rôles téléphone et SMS sont des privilèges Android (catégorie 8) ; le registre exige 14 critères physiques et une release signée, tous à `false` (catégorie 9). Déclaré : `IMPLEMENTED_NOT_VERIFIED`, `customer_available` false. Matrice V1 : inclus, S24+ BLOCKED, commercialisable NO-GO dans ce document de dépôt. Cet audit ne reprend pas ce NO-GO comme un nouveau test : il ne l’infirme pas non plus. Pas d’exécution ici. Les journaux cités par `audit/v1-commercial/TEST_EVIDENCE.md` n’ont pas été relancés.

### 16. Site web — primaire 1

Aussi 9. `index.html` et `public/*.html` sont le site (Vite, `vite.config.js`). Navigation `public/shared-navigation.js`. Plusieurs pages sont des textes ou des formulaires locaux. `threat-intelligence.js` appelle des API externes au clic. Déploiement Cloudflare / Render non observé. `espace-client.html` est cité par le registre comme preuve de `saas_identity` = `NOT_IMPLEMENTED`. Déclaré : le site n’est pas une ligne de la matrice Android V1. Écart possible de communication : les pages existent ; leur mise en production n’a pas été vérifiée, et certaines étiquettes (FIMI) n’ont pas de moteur derrière elles.

## Annexe hors liste

`public/red-team-simulator.html` charge `offensive-simulation-engine.js`. C’est une simulation de page. Catégorie 5. Non exécutée. Elle ne remplace aucune ligne ci-dessus.

`core/cyber-prediction`, `core/decision-support`, `core/geopolitics` : répertoires vides. Catégorie 6 pour ces noms de dossiers.

## Tests

Des tests existent (361 fichiers `*Test.kt` unitaires, 22 `androidTest`, 205 `*.test.js`, 19 tests Python trouvés par nom, au moment de la lecture du 2026-11-19). Ils n’ont pas été lancés. Les journaux `preuves/junit/*.xml` sont des artefacts versionnés ; leur campagne n’a pas été reproduite et ils ne sont pas un PASS de ce commit.

## Non vérifié

Même liste que le README et le focus : appareil, réseau, CI, histoires `main` ligne à ligne, clone local D1, branches des PR #1767 #1768 #1769 #1771. `fix/v1-phone-core-p1-20261119` existe au 2026-11-25 et n’a pas été extraite.
