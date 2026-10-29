# Detailed Device Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer un inventaire Wi-Fi, Bluetooth classique/LE et LAN avec fiches détaillées, preuves d’identité, historique local et connecteur Orange facultatif réellement compatible.

**Architecture:** Adaptateurs Android et LAN annulables produisent des observations normalisées. Un agrégateur conserve sources/états distincts et alimente un store chiffré dédié et un ViewModel Compose. Les connecteurs box partagent le contrat de source mais n’accèdent jamais automatiquement aux credentials.

**Tech Stack:** Kotlin/Compose et APIs publiques Android existantes, AndroidKeyStore AES-GCM, coroutines existantes, tests JUnit/AndroidX existants ; pas de nouvelle dépendance prévue.

**Spec:** `docs/superpowers/specs/2026-10-29-device-inventory-design.md` — validée par l’utilisateur ; Orange confirmé, modèle exact demandé séparément.

## Global Constraints

- Base documentaire main `935344423b2af2a4a57c58123f2c5c178b46ec03`. Créer `feat/device-inventory-20261029` depuis le commit documentaire contenant ce plan ; ne pas fusionner #1699/#1739 ou changer main.
- Android app : minSdk24, compileSdk37, targetSdk36 ; conserver ces valeurs.
- Aucune dépendance payante, aucun service cloud d’identification, aucune modification Room/coffre/VPN/mesh/Phone Core/MMS.
- Préserver les contrôles de risque et la politique AuthorizedLanProbe : pas de scan TCP de sous-réseau/ports, pas de capture ni appairage automatique.
- Bluetooth classique 12 s puis BLE 8 s ; session totale 25 s, 256 observations/source ; visibilité n’est pas connexion.
- LAN 15 s, 256 observations/source, 16 résolutions simultanées, TXT 8 Kio/appareil ; BLE brut 1 Kio maximum en mémoire.
- SSDP optionnel : 3 s, 64 réponses, 4 descriptions simultanées, 64 Kio/description, délai 3 s ; pas de DTD/entités/redirection/URL externe.
- Box : 1 000 clients, réponse 512 Kio, timeout 5 s, refresh au plus une fois/10 s, fraîcheur 60 s ; modèle et interface à vérifier avant adaptateur réel.
- Historique : 30 jours, 1 000 appareils, 5 000 événements, fichier 2 Mio ; données et credentials chiffrés, séparés, noBackupFilesDir.
- Chaque champ affiche sa source et sa nature ; aucune marque/modèle/connexion déduite d’un nom ou d’une adresse aléatoire seuls.
- Logos embarqués avec provenance/licence ; pas de téléchargement d’image depuis les appareils.
- Pas d’installation ni désinstallation automatique : la signature doit correspondre à l’app existante avant une mise à jour conservant ses données.

## Review Focus

1. Un appareil multiradio et une IP réattribuée ne doivent pas fusionner deux identités : tests tâche 1.
2. Une permission révoquée au milieu d’une session ne doit pas laisser de callback ou inventer une disparition : tests tâches 2/3/7.
3. Une réponse réseau locale annonçant une URL externe ou un scope IPv6 erroné ne doit pas provoquer de requête hors réseau : tests tâche 3.
4. Un fichier plein/corrompu ou une clé absente ne doit pas effacer silencieusement l’historique précédent : tests tâche 5.
5. Une box expirée ou un scan tronqué ne doit pas afficher « aucun appareil connecté » : tests tâches 4/7.

---

## File Structure

Sous `native-android-app/app/src/main/java/com/sentinel/quantum/networkinventory/` :

- `InventoryModels.kt` : observations, champs sourcés, états, snapshots et limites.
- `InventoryReducer.kt` : rapprochement conservateur et historique normalisé.
- `InventorySession.kt` : propriété/epoch, fraîcheur, annulation et coordination.
- `WifiInventorySource.kt`, `BluetoothInventorySource.kt` : API Android et métadonnées.
- `LanScopePolicy.kt`, `LanDiscoverySource.kt`, `SsdpDescriptorParser.kt` : portée et découverte LAN.
- `RouterConnector.kt`, `RouterConnectorRegistry.kt`, `RouterCredentialStore.kt` : contrat, sélection, secrets.
- `OrangeRouterConnector.kt` : seulement après jalon modèle/protocole documenté.
- `InventoryStore.kt`, `InventoryExport.kt` : stockage chiffré borné et export expurgé.
- `DeviceIdentityResolver.kt`, `DeviceArtwork.kt` : dictionnaires locaux, preuves et icônes/logos.
- `InventoryViewModel.kt` : état Compose et sessions.

Sous `ui/screens/networkinventory/` : `InventoryScreen.kt`, `DeviceDetailSheet.kt`, `RouterSetupSheet.kt`.
Tests JVM sous `app/src/test/java/com/sentinel/quantum/networkinventory/` ; tests Android sous `app/src/androidTest/java/com/sentinel/quantum/networkinventory/`.

Fichiers existants à modifier : `security/WifiScanner.kt`, `security/BluetoothScanner.kt` (champs optionnels rétrocompatibles si nécessaires) ; `ui/screens/NetworkSurveillanceScreen.kt` (raccordement sans retrait du VPN/diagnostic existant) ; `app/src/main/AndroidManifest.xml` et `res/xml/inventory_file_paths.xml` pour FileProvider/permissions de découverte réellement nécessaires. Ressources nouvelles dans `res/drawable/`, `res/values/strings.xml` et `assets/device-identifiers/` avec manifeste de provenance. Pas de migration Room.

## Task 1: Modèle de preuves et réduction d’identité

**Files:** Create `networkinventory/InventoryModels.kt`, `InventoryReducer.kt`; Test `InventoryReducerTest.kt`.

**Interfaces:** Produces `InventoryField<T>(value:T, source:InventorySource, observedAtMs:Long, kind:EvidenceKind)` ; `InventoryObservation(source, sessionId, sourceKey, observedAtMs, fields, states)` ; `InventoryDevice(id, fields, states, firstSeenAtMs, lastSeenAtMs)` ; `InventorySnapshot(devices, sourceStates, truncated)` ; `InventoryReducer.reduce(previous:InventorySnapshot, observations:List<InventoryObservation>, nowMs:Long):InventorySnapshot`. Source IDs WIFI_SCAN/WIFI_CONNECTION/BT_CLASSIC/BT_LE/BT_BONDED/BT_PROFILE/LAN_MDNS/LAN_SSDP/ROUTER/USER. `EvidenceKind` OBSERVED/DECLARED/PROBABLE/USER_CONFIRMED ; états VISIBLE/BONDED/PROFILE_CONNECTED/LAN_OBSERVED/ROUTER_CONNECTED/HISTORICAL. Le reducer reçoit une sourceKey déjà pseudonymisée ; il ne dépend pas de Keystore. Les producteurs reçoivent une fonction `fingerprint:(String,String)->String` injectable, dont l’implémentation HMAC-SHA256 Keystore séparée est fournie tâche 5 par `InventoryIdentityKeys.fingerprint(source:String, raw:String):String`.

- [ ] Écrire les tests : `sameNameDoesNotMergeDistinctDevices`, `reassignedIpDoesNotCarryIdentity`, `randomBleAddressIsSessionScoped`, `bssidIsNotRouterClient`, `failedScanDoesNotMarkDisappeared`, `conflictingManufacturerAssertionsRemainVisible`, `sourceCapMarksTruncation`.
- [ ] Lancer `:app:testDebugUnitTest --tests '*InventoryReducerTest'`, constater RED.
- [ ] Implémenter les modèles immuables et rapprochement sourceKey stable dans la source seulement ; aucune fusion multisource automatique sans preuve d’identifiant commun explicitement qualifiée. Pas de rapprochement BLE privé entre sessions. Champs inconnus absents, pas de valeurs sentinelles trompeuses.
- [ ] Relancer les mêmes tests ; PASS et compte non nul. Ajouter tests bornes et dates invalides.
- [ ] Commit `feat(network): model sourced device inventory evidence`.

## Task 2: Sources Wi-Fi et Bluetooth complètes

**Files:** Create `WifiInventorySource.kt`, `BluetoothInventorySource.kt`, `InventorySession.kt`; Modify les scanners existants si besoin ; Test `AndroidInventoryMappingTest.kt`, `InventorySessionTest.kt`.

**Interfaces:** Consumes modèles tâche 1. Les constructeurs des sources reçoivent `fingerprint:(String,String)->String` (fake en test, HMAC tâche 5 lors du raccordement). Produces `InventorySourceAdapter.collect(session:InventorySession):Flow<InventorySourceEvent>` ; événements `Observation`, `Complete(truncated)`, `Unavailable(reason)`, `Failed(reason)`. `InventorySession(id, networkEpoch, startedElapsedMs, cancelled)` ; `InventorySessionController.start(sourceSet:Set<InventorySource>):Flow<InventorySourceEvent>` et `cancel():Unit`. Callback courant vérifié par ID + epoch ; délais sur elapsedRealtime, dates d’affichage sur currentTimeMillis.

- [ ] Tests RED : `classicThenLeNeverOverlap`, `classicStopsAt12Seconds`, `leStopsAt8Seconds`, `overallBluetoothStopsAt25Seconds`, `revokedPermissionClosesDiscovery`, `lateCallbackCannotUpdateNewSession`, `bondedDoesNotMeanConnected`, `missingAddressTypeStaysUnknown` ; assertions de cap 256 et BLE 1 Kio.
- [ ] Lancer les deux classes de tests filtrées, constater RED.
- [ ] Mapper Wi-Fi fréquence/canal/bande/largeur/sécurité/horodatage selon API. Lire état Wi-Fi actuel et profils Bluetooth publics accessibles. Pas de GATT/SDP supplémentaire ; libérer proxies de profils. Sérialiser discovery classique puis BLE et conserver les observations même sans nom. Réutiliser les policies de permissions et maintenir les anciens contrats des scanners.
- [ ] Tests PASS ; tests Android de permission/refus/annulation préparés, exécution seulement sur appareil disponible ; vérifier API24 et API36 via guards.
- [ ] Commit `feat(network): enrich Wi-Fi and Bluetooth observations`.

## Task 3: Découverte LAN liée au réseau

**Files:** Create `LanScopePolicy.kt`, `LanDiscoverySource.kt`, `SsdpDescriptorParser.kt`; Test `LanScopePolicyTest.kt`, `LanDiscoverySessionTest.kt`, `SsdpDescriptorParserTest.kt`.

**Interfaces:** `LanNetworkScope(networkHandle:Long, epoch:Long, prefixes:List<IpPrefix>, interfaceName:String)` ; `LanScopePolicy.permits(scope, address:InetAddress):Boolean` ; `DescriptorTargetPolicy.validate(scope, uri:String):ValidatedLocalTarget?`. `LanDiscoverySource` implémente `InventorySourceAdapter` avec événements tâche 2. Ports seulement annoncés, pas sondés.

- [ ] Tests RED : IP privée hors préfixe rejetée, loopback rejetée, host DNS non validé rejeté, URL externe/credentials/redirection rejetée, IPv6 link-local avec mauvais scope rejeté, changement de réseau annule toute requête, XML DTD/entité refusé ; payload au-delà des caps tronqué ou refusé explicitement.
- [ ] Lancer les trois classes ; RED.
- [ ] Implémenter DNS-SD sur réseau sélectionné ; si l’API ne garantit pas ce rattachement, utiliser mDNS lié au réseau ou désactiver cette source avec raison visible. Ajouter SSDP optionnel et parsage borné. Aucun ARP root, balayage IP, endpoint Internet ou usage de AuthorizedLanSocketProbe.
- [ ] Tests PASS ; tester fermeture sockets/multicast lock et limite 16 résolutions, 4 descriptions, délais et annulations.
- [ ] Commit `feat(network): discover bounded local network services`.

## Task 4: Box Orange, configuration et source réelle

**Files:** Create `RouterConnector.kt`, `RouterConnectorRegistry.kt`, `RouterCredentialStore.kt`, `ui/screens/networkinventory/RouterSetupSheet.kt`; Create `OrangeRouterConnector.kt` uniquement après compatibilité prouvée ; Tests `RouterConnectorPolicyTest.kt`, `OrangeRouterConnectorTest.kt` avec fixtures expurgées.

**Interfaces:** `RouterCompatibility(model:String?, supported:Boolean, reason:String)` ; `RouterConnector.compatibility(model:String):RouterCompatibility` ; `suspend authenticate(config:RouterEndpoint, credentials:RouterCredentials):RouterSessionResult` ; `suspend readClients(session:RouterSession):RouterInventoryResult` ; `disconnect():Unit`. Result contient source timestamp, clients, état Fresh/Expired/Unavailable/Unsupported/Truncated. `RouterCredentialStore` est une interface injectable définie ici : `save(connectorId:String, credentials:RouterCredentials):Boolean`, `load(connectorId:String):RouterCredentials?`, `delete(connectorId:String):Boolean`. Les tests utilisent un fake ; l’implémentation Android chiffrée vient tâche 5.

- [ ] Confirmer modèle Livebox Orange et version, consulter interface officielle/documentée en lecture seule. Rédiger `docs/device-inventory/orange-compatibility.md` avec source et version. Ne pas inventer d’URL privée ni demander de credentials dans le chat.
- [ ] Tests RED du contrat générique : incompatibilité visible, auth expirée ≠ zéro client, leases anciennes ≠ connected, réseau changé révoque session, réponse 512 Kio capée, timeout 5 s, fréquence 10 s, expiration 60 s, max 1 000 clients. Tests de secrets absents des logs/exports.
- [ ] Lancer `'*RouterConnectorPolicyTest'`, constater RED.
- [ ] Implémenter contrat/configuration et rejet des protocoles non vérifiés. Pour modèle compatible, ajouter adaptateur Orange en lecture seule et fixtures expurgées ; TLS normal sans trust-all, refus d’envoi automatique de credentials si seulement HTTP. Si HTTP/certificat local non validable : jalon documenté en attente d’une solution spécifiquement approuvée, socle téléphone reste fonctionnel ; ne pas prétendre avoir livré le connecteur réel.
- [ ] Tests PASS + essai sur box réelle compatible ; comparaison de l’inventaire avec son interface et preuve sans secrets. Sans modèle/accès réel, tâche partielle explicitement notée, jamais clôturée comme connecteur qualifié.
- [ ] Commit de la partie effectivement livrée `feat(network): add optional verified router inventory` ; qualifier séparément adaptateur Orange si disponible.

## Task 5: Historique chiffré et export

**Files:** Create `InventoryStore.kt`, `InventoryIdentityKeys.kt`, `InventoryExport.kt`, `AndroidRouterCredentialStore.kt` (implémentation de l’interface tâche 4), `res/xml/inventory_file_paths.xml`; Modify manifest FileProvider ; Tests `InventoryStoreTest.kt`, `InventoryExportTest.kt`, `InventoryStorageInstrumentedTest.kt`.

**Interfaces:** `InventoryStore.load():InventoryLoadResult`, `save(snapshot:InventorySnapshot):InventorySaveResult`, `clear():InventorySaveResult` ; erreurs explicites Unavailable/Corrupt/TooLarge. `InventoryExport.encode(snapshot, mode:ExportMode):ByteArray`, modes REDACTED/COMPLETE ; `InventoryIdentityKeys` tâche 1. Stockage historique et secrets sous fichiers/aliases différents ; fichier d’inventaire schema v1.

- [ ] Tests RED : retention30Days, cap1000Devices, cap5000Events, maximum2MiB, redactedExportOmitsNamesMacIp, completeExportNeverContainsCredentials, failedWriteKeepsPreviousFile, missingKeyIsVisible, corruptFileDoesNotResetSilently, clearDoesNotTouchOtherStores.
- [ ] Lancer filtres correspondants ; RED.
- [ ] Implémenter chiffrement AES-GCM avec IV généré par provider et clé dédiée, AAD schema, stockage noBackupFilesDir, fichier temporaire puis remplacement atomique et traitement explicite des erreurs. Pas d’import RecordingVaultStore. Réaliser les clés pseudonymes HMAC sans log de MAC. Ancien état conservé jusqu’à publication du nouveau ; rollback visible en cas d’échec.
- [ ] Tests JVM avec backend crypto/FS injectable puis tests Android Keystore réellement exécutés lorsqu’appareil disponible ; FileProvider non exporté avec permissions temporaires pour le seul partage choisi.
- [ ] Commit `feat(network): persist bounded encrypted inventory history`.

## Task 6: Fabricants, catégories et logos sourcés

**Files:** Create `DeviceIdentityResolver.kt`, `DeviceArtwork.kt`, `assets/device-identifiers/provenance.json`, dictionnaires OUI/Company IDs sélectionnés ; ressources de logos autorisées ; Test `DeviceIdentityResolverTest.kt`.

**Interfaces:** `DeviceIdentityResolver.resolve(device:InventoryDevice):DeviceIdentification` ; identifications marque, fournisseur composant et type distincts, avec preuves et état. `DeviceArtwork.forIdentification(id):ArtworkReference` retourne ressource locale BrandLogo ou CategoryIcon.

- [ ] Tests RED : chipsetCompanyDoesNotBecomeProductBrand, locallyAdministeredMacDoesNotGetOuiVendor, nameAloneCannotConfirmModel, conflictUsesGenericIcon, confirmedBrandWithLicensedAssetShowsLogo, missingAssetUsesCategoryIcon, malformedManufacturerDataIsBounded.
- [ ] Lancer `'*DeviceIdentityResolverTest'` ; RED.
- [ ] Importer une sélection utile de données à provenance/licence vérifiée (révision, hash, source, licence dans provenance.json), pas une base dite exhaustive. Aucun logo généré ou URL issue d’une annonce réseau. Les identifiants fabricant BLE ne deviennent pas une marque sans preuve. Si licence de logo indisponible : icône générique, pas de copie non autorisée.
- [ ] Tests PASS ; vérifier tout asset par manifeste et l’absence de réseau dans le resolver.
- [ ] Commit `feat(network): show sourced device identity and artwork`.

## Task 7: Interface et raccordement aux parcours existants

**Files:** Create `InventoryViewModel.kt`, `InventoryScreen.kt`, `DeviceDetailSheet.kt`; Modify `ui/screens/NetworkSurveillanceScreen.kt`, strings et navigation si nécessaire ; Tests `InventoryPresentationTest.kt`, `InventoryScreenInstrumentedTest.kt`.

**Interfaces:** `InventoryUiState(snapshot, selectedSource, scanningSources, errors, selectedDeviceId)` ; `InventoryViewModel.startScan(sources)`, `cancelScan()`, `selectDevice(id)`, `annotate(id, annotation)`, `export(mode)`, `forgetHistory()`. Consomme services tâches 1–6 et conserve l’état sans relancer des scans lors d’une simple recomposition.

- [ ] Tests RED : sourceUnavailableIsNotEmptyList, truncatedRouterListShowsLimit, unknownDeviceUsesCategoryIcon, userAnnotationNotTechnicalTrust, disposedScreenCancelsSession, largeFontLeavesActionAndLastCardVisible.
- [ ] Lancer présentation JVM ; RED. Ajouter test Compose des trois onglets et détail sans mocks de statut connecté arbitraire.
- [ ] Implémenter trois onglets, cartes courtes et fiche complète (Identité/Connexion/Services/Historique/Indices). Réserver une barre d’action au scan pour supprimer le recouvrement. Garder les contrôles réseau/VPN existants et raisons de risque, sans toucher leur logique.
- [ ] Tests PASS, accès TalkBack, grande police, rotation et annulation ; aucun scan/appairage ni changement de réseau automatique.
- [ ] Commit `feat(network): connect detailed device inventory screens`.

## Task 8: Qualification exacte de branche et livraison

**Files:** Create `docs/device-inventory/QUALIFICATION.md` et preuves expurgées ; aucun binaire/secret dans Git.

- [ ] Exécuter baseline puis changements avec commandes Gradle officielles : `:app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`, sans exclusion de tests. Consigner sorties, commit et compte XML ; NO-SOURCE/cache distincts des exécutions.
- [ ] Exécuter les tests instrumentés ciblés sur appareil compatible disponible ; pas de faux PASS depuis simple compilation/découverte. Relever API, modèle et empreintes des APK sans identifiers personnels.
- [ ] Sur S24+ : permissions refusées/révoquées, Wi-Fi/Bluetooth classique/LE, LAN avec équipement connu, changement de réseau pendant scan, historique après relance, export expurgé, gros texte/bouton non masquant. Aucun SMS/appel ni effacement de données utilisateur nécessaire.
- [ ] Identifier la signature de l’app installée ; n’installer avec `adb install -r` que si signature compatible et APK exact choisi. Ne jamais désinstaller pour contourner une incompatibilité.
- [ ] Pour box Orange, relever modèle/version et résultat de comparaison réel ; sinon réserve explicite. Ne pas qualifier comme « tous appareils » si la box ou des sources sont inaccessibles.
- [ ] Revue complète de la branche, diff --check et comparaison des fichiers sensibles non concernés (Phone Core/MMS/coffre/mesh) ; aucune fusion automatique.
- [ ] Commit des preuves et PR brouillon avec résultats/réserves ; pas de promotion ni publication sans instruction utilisateur.

## Self-review du plan

- Couverture : Wi-Fi, Bluetooth classique/LE/associés, LAN, box, identité/logos, stockage/export, UI et qualification ont chacun une tâche.
- Dépendances : 1 → 2/3/4/6 ; 5 fournit persistance/keys/secrets ; 7 raccorde les tâches ; 8 qualifie. Exécuter 1, 2, 3, 4, 5, 6, 7, 8 ; les interfaces et fonctions injectables permettent les tests des adaptateurs avant leur raccordement au stockage Android en tâche 7.
- Deux interfaces tâche 4/5 peuvent être déclarées avant implémentation ; aucun test de source seul ne remplace les essais du connecteur réel.
- Questions externes non bloquantes pour le socle : modèle exact Livebox et interface d’accès compatible. Tâche 4 ne peut être clôturée comme adaptateur réel sans cette preuve.
- Méthode proposée : exécution dans cette session par l’agent principal, tâches séquentielles et revue indépendante finale ; cohérence des modèles et moindre surcharge que des agents distincts à chaque petite tâche.
