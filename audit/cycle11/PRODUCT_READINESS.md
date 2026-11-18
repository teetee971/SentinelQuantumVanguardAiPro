# État de préparation produit C11

**NO-GO pour la distribution Android.** Revue du code C11 à `b717802b171c6324ce7c8559e92d7c36cc2fd04a`. La classification décrit ce que le dépôt permet d'établir. « Réelle en code » signifie qu'un chemin d'exécution est implémenté. La preuve de fonctionnement sur appareil et la disponibilité client ont leurs propres critères.

## Matrice des fonctions

| Fonction ou canal | État | Preuve et condition restante |
| --- | --- | --- |
| Recherche téléphonique Web et données d'attribution | Réelle en code | Parcours public, normalisation et sources de numérotation testées. Une attribution de plage n'identifie ni la personne ni l'opérateur actuel. Déploiement Web de ce SHA non mesuré. `index.html:47–89`, `public/phone-intelligence.test.js`, `public/phone-rtr.test.js`. |
| Navigation Compose et outils locaux | Réelle en code | Quatre destinations principales câblées, écrans d'e-mail/SMS manuels, permissions, journaux et diagnostics. Les contrats de source et les scénarios Compose existent. La lisibilité et les parcours sur le S24+ restent à relever. `MainActivity.kt:80–95,163–228`, `navigation/NavGraph.kt:19–102`, `AllStaticNavigationSurfacesInstrumentationTest.kt`. |
| Phone Core, contacts et historique | Partielle | Appels, filtrage et lecture des fournisseurs Android requièrent le rôle approprié, les autorisations et l'observation du résultat. Le registre impose 14 critères physiques distincts et une release signée. `config/product-capabilities.json:6–58`, `docs/PHONE_CORE_PHYSICAL_VALIDATION.md:31–61`. |
| Gestionnaire SMS/MMS et notifications | Partielle | Receivers système et composeur présents. Qualification de l'envoi, réception, livraison, sélection SIM et notifications à lier au matériel, à l'opérateur et au SHA. Une soumission à Android ne suffit pas à prouver le transport. `AndroidManifest.xml:140–191`, `docs/PHONE_CORE_PHYSICAL_VALIDATION.md:42–49`. |
| Wi-Fi, Bluetooth, proximité et LAN | Partielle | Démarrage et résultats conditionnés par les API Android, les autorisations radio et l'état système. Le marquage d'un réseau bloqué est interne au téléphone. Découverte physique et limites d'observabilité à vérifier. `AndroidManifest.xml:19–28`, `ui/screens/NetworkSurveillanceScreen.kt:907–923,1233–1246`. |
| Maison connectée | Partielle, catalogue et accès aux outils système | Boutons vers le scanner et les réglages Android. Le catalogue décrit des compatibilités et la zone topologie attend une preuve observable. Aucun pilotage de ces marques démontré. `ui/screens/SmartHomeScreen.kt:71–96,109–165`. |
| Sentinel VPN | Partielle, service verrouillé | L'écran et le client WireGuard existent. Passerelle, séquence de baux, tunnel, DNS, IPv4/IPv6 et reprise requièrent une qualification de bout en bout. `ui/screens/VpnScreen.kt:89–110`, `config/product-capabilities.json:60–76`, `docs/PRODUCT_CAPABILITY_STATUS.md:39–48`. |
| Wear et Mesh | Partielle, dépendances externes | Listener Data Layer et socle d'authentification Wear présents. Le registre indique la liaison d'identité Wear non vérifiée, le relais Mesh non déployé. Qualification de la connectivité et de l'appareil compagnon distincte des scénarios BLE locaux. `AndroidManifest.xml:62–74`, `wear-app/src/main/AndroidManifest.xml:9–22`, `config/integration-capabilities.json:18–19`. |
| Agent IA Android | Partielle, expérimentale | Entrée depuis Paramètres vers l'activité privée. Inférence locale avec service séparé, consentement, approbation et journal HMAC. Les preuves JVM et les APK debug historiques sont spécifiques à leur SHA. S24+ requis pour reprise/reboot, Keyguard et Qwen ARM64 réels. `ui/screens/SettingsScreen.kt:413–418`, `AndroidManifest.xml:88–91`, `audit/cycle11/REVOCATION_VALIDATION.md`. |
| Veille OSINT Android et GeoIntel | Partielle | Écrans publics/Android et flux USGS présents. Tests de provenance sur fixtures. Les autres sources GeoIntel restent prévues. Les preuves de runtime doivent être fraîches et liées au déploiement. `docs/PRODUCT_CAPABILITY_STATUS.md:61–76`, `public/geointel-source-usgs.test.js`. |
| Collective Defense, exposition et réputation distante | Partielle, réglages ou services requis | Appels API et gardes dédiés. Exposition verrouillée tant que l'identité autorisée est indisponible. Santé des backends, opt-in, modération et effets de bout en bout à valider. `ui/screens/DigitalExposureScreen.kt:123–151,192–207`, `config/integration-capabilities.json:8–9,20`, `docs/PRODUCT_CAPABILITY_STATUS.md:78–91`. |
| Studio voix / appels Sentinel / VoIP-PSTN | Partielle, aperçu local | Un chemin d'aperçu local existe. L'appel RTC transformé dépend de LiveKit et de la passerelle. Une fonction d'appel SIM transformé est absente du produit. `ui/screens/SettingsScreen.kt:420–427`, `docs/PLAY-DATA-SAFETY.md:16–29,32–37`. |
| Aperçu Android sur le site et simulateur pédagogique | Simulée | Vignettes et états de roadmap dans le site. Le simulateur présente explicitement des scénarios de simulation. `index.html:98–139`, `public/android-preview.js:12–93`, `public/red-team-simulator.html:20–42`. |
| Télécharger l'APK public / Play Store | Absente au sens livraison vérifiée | Le guide refuse un binaire public sans preuve. La compilation debug isolée et des règles de vérification d'AAB sont documentées. L'artefact de production, son certificat et l'installation correspondante manquent. `public/download-guide.html:19–29,47–49`, `RELEASE_STATUS.md:9–30`. |
| Client PC, checkout, essai et abonnements | Absente au sens livraison vérifiée | Le catalogue statique distingue niveaux commerciaux et gates fermés. Les pages déclarent l'essai désactivé et l'absence d'offre payante commercialisée. `public/pricing.html:27–32,54–74`, `public/product-status.html:17–28`. |

Dans les références Kotlin abrégées, le préfixe est `native-android-app/app/src/main/java/com/sentinel/quantum/`. Les références au manifeste correspondent à `native-android-app/app/src/main/AndroidManifest.xml` sauf mention Wear.

## Constats et priorités

- **P0 distribution — attestation et artefact de production manquants.** Le registre canonique laisse Phone Core indisponible. Les quatorze preuves physiques, les rôles autorisés sur l'appareil, la version identifiée et le dossier d'acceptation Play manquent à la preuve produit. La variante `releaseUnsigned` efface explicitement sa configuration de signature. La release exige les secrets de signature et le workflow versionné vérifie la provenance du tag, les deux signatures et les SHA-256. Le workflow n'a pas été lancé. La Debug et la matrice de validation de workflow prouvent des propriétés distinctes de l'installation d'une release. `config/product-capabilities.json:6–58`, `app/build.gradle:29–59,86–93`, `.github/workflows/android-release.yml:45–70,216–246`, `docs/PLAY-DATA-SAFETY.md:3–7`.
- **P0 qualification — l'ancienne recette C11 partage le paquet C9.** Une installation `-r` du paquet historique `com.sentinel.quantum.aivalidation` avec le même certificat réutilise ses données. La recette corrigée produit `com.sentinel.quantum.aivalidation.c11`. L'identité et la cible des deux APK sont vérifiées localement. Leur installation physique reste ouverte. [Procédure S24+](VALIDATION_S24_PLUS.md)
- **P1 conformité rédactionnelle — exception HTTP locale omise.** Le brouillon Play affirme une interdiction générale du trafic HTTP en clair. Le manifeste principal référence une configuration de sécurité dont la règle globale refuse le clair et dont l'exception est exactement `127.0.0.1`. Le chemin IA expérimental est présent dans les paramètres du code principal. Avant une soumission, le texte Play et l'inventaire de données doivent correspondre au manifeste fusionné, au transport loopback authentifié et à l'activation réellement livrée. `native-android-app/PLAY_STORE_LISTING.md:31–41`, `docs/PLAY-DATA-SAFETY.md:9–20`, `AndroidManifest.xml:40–51`, `res/xml/sentinel_ai_local_network_security.xml:2–6`, `ui/screens/SettingsScreen.kt:413–418`.
- **P1 preuves des frontières Android incomplètes.** Le contrôle statique accepte 25 permissions déclarées. Il ne mesure pas leurs états réels par API et OEM. Le manifeste source protège les services Telecom par `BIND_SCREENING_SERVICE` et `BIND_INCALL_SERVICE`, les receivers SMS/WAP par leurs permissions système et les callbacks d'émission par la non-exportation. L'entrée partage, le composeur et le dialer sont des surfaces publiques à vérifier sur le manifeste fusionné. Wear exporte les listeners Data Layer. La réception de transport et la confiance de session sont distinctes dans la composition. Il faut vérifier l'identité de l'émetteur et les échecs de liaison sur une installation réelle avant toute extension de la surface Wear. `AndroidManifest.xml:53–108,122–199`, `wear-app/src/main/AndroidManifest.xml:9–22`, `MainActivity.kt:319–328`.
- **P2 contrôle interactif sans action.** Après une réponse de Collective Defense, le statut est rendu par un `AssistChip` actif avec `onClick = {}`. Un élément de statut non interactif éviterait un contrôle sans effet et une annonce trompeuse par les technologies d'assistance. Le véritable bouton d'analyse se trouve au-dessus et reste distinct. `ui/screens/CollectiveDefenseScreen.kt:172–224`.
- **P2 cohérence du catalogue expérimental.** Le statut explicite de l'IA figure dans l'APK. Les 17 entrées du registre public et les 25 familles du registre d'intégration ne contiennent pas un statut propre pour ce chemin. Avant toute promotion de l'IA, lier sa qualification physique, ses dépendances et son périmètre au registre. `ui/screens/SettingsScreen.kt:413–418`, `config/product-capabilities.json`, `config/integration-capabilities.json`.

## Écarts sélectionnés avec main

Comparaison des blobs C11 avec l'arbre GitHub exact `935344423b2af2a4a57c58123f2c5c178b46ec03`, lecture seule. `index.html`, l'aperçu Android, les pages de téléchargement et de tarifs, le registre public canonique, les brouillons Play, le graphe Compose et le workflow de release ont des blobs identiques dans l'échantillon. C11 apporte le registre d'intégration absent de main, l'activité IA et l'exception loopback au manifeste principal, le bouton IA et le rafraîchissement du catalogue dans Paramètres. La roadmap passe de « certificat v5 » à « v6 » avec 14 critères dans les deux arbres. Le statut public C11 fournit un tableau de fallback statique. Aucun de ces écarts ne vaut attestation d'installation ou de déploiement.

## Contrôles exécutés

Contrôles locaux sur C11 `b717802`, Node 24.22.1. Aucun workflow lancé.

| Commande | Statut |
| --- | --- |
| `npm run test:android-manifest` | PASS · 25 permissions contrôlées dans le manifeste source |
| `npm run test:android-product-truth` | PASS · 69 tests |
| `npm run test:integration-product-truth` | PASS · 16 tests |
| `npm run test:public-product-contract` | PASS · 11 tests |
| `npm run test:public-claims` | PASS · contrôle statique des promesses |
| `node scripts/check-product-capabilities.js` | PASS · 17 capacités |
| `node --test scripts/check-product-capabilities.test.js scripts/generate-public-product-truth.test.js` | PASS · 20 tests |
| `node --test scripts/android-release-policy.test.js scripts/android-aab-artifact-policy.test.js scripts/android-debug-artifact-policy.test.js scripts/android-install-smoke-policy.test.js scripts/verify-android-release-evidence.test.js scripts/check-legal-readiness.test.js` | PASS · 35 tests de politique sur fixtures |
| `npm run test:frontend-accessibility` | PASS · 9 tests de contrat |
| `npm run test:static-links` | PASS · 2 tests, 47 HTML, 397 JS et 16 CSS inspectés |
| `node --test public/phone-intelligence.test.js public/phone-rtr.test.js public/geointel-core.test.js public/geointel-source-usgs.test.js` | PASS · 61 tests |
| `npm run test:client-security && npm run test:isolation` | PASS · 74 fichiers côté client, 1 743 fichiers dans le périmètre statique |
| Lecture des pages publiques | PASS · HTTP 200 sur l'accueil, la roadmap, Android, le guide et les tarifs. Le SHA du déploiement reste indéterminé. |
| Parcours Web interactif de bout en bout et revue Play Console | NOT RUN |
| Installation physique et autorisations Android par famille de rôle/radio | BLOCKED · S24+ absent de l'environnement d'audit |

Le bilan Node est de **223 tests réussis** sur les suites comptées, sans échec ni test ignoré. Les assertions de politique de livraison portent sur les règles et leurs fixtures. Les preuves historiques de compilation et d'APK debug sont rattachées à leurs SHA et empreintes dans `audit/cycle11/REVOCATION_VALIDATION.log`.

## Site public observé

Le 18 novembre 2026, l'accueil public affiche la recherche téléphonique Web comme disponible et Android comme en validation sur appareils réels. Le guide annonce l'absence d'APK public et un client PC planifié. La lecture publique confirme les libellés de statut. La comparaison HTML n'a pas associé le déploiement au SHA C11. [Accueil](https://sentinelquantumvanguardaipro.pages.dev/) · [Roadmap](https://sentinelquantumvanguardaipro.pages.dev/public/roadmap) · [Guide de distribution](https://sentinelquantumvanguardaipro.pages.dev/public/download-guide)
