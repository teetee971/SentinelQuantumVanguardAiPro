# Audit filtrage d’appels · Sentinel Phone Core V1

**Statut : V1 COMMERCIAL NO-GO.** Les tests JVM et la compilation du profil `-PsentinelCommercialV1=true` ne qualifient pas une distribution. La signature de livraison, la conformité Google Play et les 14 scénarios physiques restent ouverts. Aucun résultat d’appel, de SMS ou de MMS réel n’est simulé ici.

Branche de travail : `cursor/v1-call-screening-audit-536d`, créée exactement depuis `113862500b9579e2ce476c190ad3195f676ffdda`. Le nom suggéré `audit/v1-call-screening-20261118` n’a pas été utilisé : la branche cloud suit le préfixe `cursor/` et le suffixe `-536d`. Aucune branche `main`, C9, C10 ou C11 n’a été modifiée. Aucun merge. Aucune action GitHub payante n’a été lancée.

## 1. Inventaire vérifié

Dépôt `teetee971/SentinelQuantumVanguardAiPro`. `origin/integration/sentinel-v1-commercial-20261118` pointe sur `113862500b9579e2ce476c190ad3195f676ffdda` (« Record Android runtime advisory coverage and its limits »).

| Commit | Relation vérifiée par `git merge-base --is-ancestor` | Sujet |
| --- | --- | --- |
| `4e444a59a7692320c9a8f77c48ce19b676726c3c` | Ancêtre de `25c4e2c…` et de `1138625…` | Base C11 |
| `25c4e2c9224abe8feaf07474a2d092ea657330f5` | Ancêtre de `1138625…` | Build code V1 |
| `113862500b9579e2ce476c190ad3195f676ffdda` | Pointe de la branche d’intégration | Référence de cet audit |

Commits entre C11 et la référence : `e609ca8d0`, `7f0de58d9`, `d16bcea74`, `25c4e2c92`, `8ea201987`, `113862500`.

Profil commercial : `native-android-app/app/build.gradle` lit `-PsentinelCommercialV1`. La valeur absente vaut false. `true` pose `BuildConfig.COMMERCIAL_PHONE_CORE_V1`, le manifeste `src/v1Commercial/AndroidManifest.xml`, le suffixe d’application `com.sentinel.quantum.v1validation`, `versionName` `1.0.9-v1-validation`, `versionCode` 10, ABI debug `arm64-v8a`, et retire des exports hors Phone Core. Les préfixes de réputation signés sont passés vides au moteur dans `SentinelCallScreeningService` lorsque ce drapeau est vrai. Qwen, VPN, Mesh et scanners radio ne sont pas réintroduits.

Sources du filtrage relues : `SentinelCallScreeningService.kt`, `CallRuleEngine.kt`, `CallBlocklistStore.kt`, `CallDirectoryCache.kt`, `CallScreeningPlatformPolicy.kt`, `CallScreeningResponseBoundary.kt`, `CallScreeningActivationPolicy.kt`, `UnknownCallerPolicy.kt`, `ScreeningProjection.kt`, `ScreeningHomeRegion.kt`, `CallerIdActivity.kt`, `CallFilterLogStore.kt`, `PhoneCoreFrenchLabels.kt`. Les tests existants de ces types ont été rejoués après correction.

`audit/v1-commercial/S24_PHYSICAL_ACCEPTANCE.md` est lu. Les scénarios A01–A14 y restent **NON EXÉCUTÉ**. Ce rapport ne les remplit pas.

## 2. Chemin de filtrage

1. **Réception et identité.** `onScreenCall` ignore le sens autre qu’entrant à partir d’Android 10, puis journalise un marqueur sans numéro, relit `ROLE_CALL_SCREENING`, classe l’urgence, puis évalue le moteur sur `handle.schemeSpecificPart`. Le cadre Android ne fournit au service que le sens, le statut de vérification, les horodatages et le handle. Les présentations restreinte, inconnue, indisponible ou publiphone ne sont pas livrées au service.
2. **Normalisation.** Plan français si la région domicile capturée est française. Forme internationale `+` ou `00` acceptée partout. Hors région française, le numéro national passe par libphonenumber déjà chargé, sinon l’appel reste sans correspondance.
3. **Listes.** Blocage exact par HMAC en cache, préfixes utilisateur, préfixes ARCEP si l’utilisateur les a activés, silence de préfixe, silence surtaxé, silence proche d’un contact, silence des inconnus seulement si le répertoire mémoire est prêt. Pas de liste blanche branchée sur le moteur. `UnknownCallerPolicy` (favori, liste blanche, rappel) n’est appelée par aucun écran ni par le service.
4. **Priorité.** Blocage exact, puis préfixe, puis contact du répertoire, puis silence de préfixe, surtaxé, réputation signée (vide en V1), proche du répertoire, inconnu, sinon autorisation. Un blocage explicite gagne sur le répertoire. Un contact gagne sur un silence.
5. **Délai et fail-open.** Urgence inconnue ou vraie : autorisation sans moteur. Exception du moteur : autorisation. Cache froid : autorisation. Le cadre impose une réponse entrante en 5 secondes ; passé ce délai il ignore la réponse. Le service n’a pas de second minuteur interne.
6. **Révocation du rôle.** Lecture avant l’urgence et de nouveau avant une réponse restrictive. Perte du rôle : `CallResponse` vide. Le travail après réponse se referme aussi si le rôle a disparu.
7. **Rappels tardifs.** Une frontière par appel, une seule réponse. Un échec de `respondToCall` n’est pas rejoué. Le travail optionnel ne part qu’après une réponse revenue sans exception.
8. **Cohérence décision / journal / UI.** Avant correction, l’action du moteur était enregistrée même lorsque Android ne pouvait pas couper la sonnerie, et deux motifs produits par le moteur n’avaient pas de libellé français. Voir P1-2 et P1-3.
9. **Double SIM et données manquantes.** Le handle de compte n’est pas dans le `Call.Details` de filtrage. La région domicile est un seul code SIM, pas la ligne qui reçoit l’appel. Numéro vide ou illisible : autorisation.
10. **Limites Android et permissions.** Le rôle de filtrage est requis à partir d’Android 10. `setSilenceCall` existe à partir de l’API 29. Sans `READ_CONTACTS`, Android n’envoie en général pas les numéros déjà dans les contacts, et le silence des inconnus reste inactif tant que le répertoire mémoire n’est pas prêt. L’oracle Phone Core des permissions est `PermissionChecker` (permission et AppOps). Le répertoire de filtrage utilisait seulement `checkSelfPermission`.

Le retour immédiat sur un appel sortant est conforme au cadre actuel : après `onScreenCall`, la plateforme autorise elle-même `DIRECTION_OUTGOING`, et `respondToCall` est spécifié pour l’entrant. Aucun correctif n’ajoute une réponse sur le sortant.

## 3. Défauts

| ID | Gravité | État | Déclencheur | Effet | Lieu |
| --- | --- | --- | --- | --- | --- |
| P1-1 | P1 | Corrigé | Numéro ou préfixe français / outre-mer en chiffres d’indicatif, sans `+` ni `00` (`33612345678`, `590690123456`, `508410974`, préfixe `33612` ou `590`), région domicile française | Le blocage, le préfixe et le répertoire visaient une autre chaîne. Un contact enregistré sans `+` pouvait être traité comme inconnu et mis en sourdine. Hors région française, la chaîne n’est pas réécrite. | `CallRuleEngine.kt` normalisation, vers les lignes 194–201 et 246–259 |
| P1-2 | P1 | Corrigé | Décision `IN_DIRECTORY` ou `USER_PREFIX_SILENCE` | La fiche appelant et l’historique affichaient « Motif technique non traduit » pour une décision réelle du moteur. | `PhoneCoreFrenchLabels.kt` |
| P1-3 | P1 | Corrigé | Silence demandé sur API 24–28, où `setSilenceCall` n’existe pas | Android laisse sonner (`CallResponse` sans restriction) alors que le journal, la fiche et l’historique disaient « mis en sourdine ». Sur l’API 34 du S24+, silence reste silence. | `SentinelCallScreeningService.kt` vers la ligne 102, `CallScreeningPlatformPolicy.kt` |
| P1-4 | P1 | Corrigé | AppOps `READ_CONTACTS` refusé alors que la permission manifeste paraît encore accordée | Le répertoire mémoire restait prêt. Le silence des inconnus continuait. Les faits Phone Core, eux, utilisent `PermissionChecker`. | `CallDirectoryCache.kt` vers la ligne 114, `LocalContactLookup.kt`, écrans de listes |
| P1-5 | P1 | Corrigé sous budget de 250 ms | Processus froid : le premier `onScreenCall` lisait un cache vide | `Application.onCreate` ne fait que lancer `ScreeningProcessWarmup` sur un fil d’arrière-plan (règles, clés, régions, parseur). Le callback attend au plus 250 ms, sans ouvrir le disque ni le Keystore. Si le disque dépasse ce budget, cet appel reste fail-open et le chargement continue pour l’appel suivant. Le répertoire contacts reste hors de ce budget. | `ScreeningProcessWarmup.kt`, `SentinelApplication.onCreate`, `SentinelCallScreeningService` |
| P1-6 | P1 | Limité, non clos | Deux SIM de pays différents. Le `Call.Details` de filtrage ne porte pas la ligne | Les pays SIM lisibles sont capturés hors callback. Une seule identité parsable est utilisée, donc un `0612345678` bloqué en France l’est encore si l’autre SIM ne parse pas ces chiffres. Deux numéros valides qui divergent, ou un contact sur l’un des parses, laissent sonner (`AMBIGUOUS_LINE` ou `IN_DIRECTORY`). Sans `READ_PHONE_STATE`, une seule région reste connue. | `ScreeningHomeRegion.kt`, `ScreeningLineRegionPolicy.kt` |
| P1-7 | P1 | Corrigé sous le même budget | Clés HMAC absentes au premier rappel | Le fil de préchargement appelle `prepareFingerprintKeys` après l’instantané, sans générer de clé. Le callback n’attend les clés que s’il existe des règles exactes, dans le temps qui reste sur 250 ms. Un Keystore plus lent laisse le blocage exact en fail-open pour cet appel seulement. | `ScreeningProcessWarmup.kt`, `CallNumberFingerprinter.prepareExistingKeys` |
| P2-1 | P2 | Documenté, non branché | Lecture de `UnknownCallerPolicy` comme si elle filtrait | Favori, liste blanche et rappel de cette politique ne modifient pas l’appel. `PhoneFavoriteStore` interdit aux favoris de changer le filtrage. La brancher contredirait ce contrat. | `UnknownCallerPolicy.kt` |
| P2-2 | P2 | Ouvert | Consommateur futur de `ScreeningProjection` | Le service V1 n’utilise pas cette projection. Il force les préfixes signés à vide. La projection peut encore les copier. Elle n’applique rien aujourd’hui. | `ScreeningProjection.kt` |
| P2-3 | P2 | Ouvert | Révocation du rôle entre le second contrôle et `respondToCall` | Une réponse restrictive déjà calculée peut encore être remise. Android reste maître du dernier effet. | `SentinelCallScreeningService.kt` |
| P2-4 | P2 | Ouvert | Binder rôle ou urgence plus long que 5 s | Android ignore la réponse tardive et laisse sonner. Le journal peut encore décrire la décision calculée si `respondToCall` est revenu après le délai du cadre. Le chemin normal est en mémoire. | Service de filtrage |
| P2-5 | P2 | Ouvert | `DIRECTION_UNKNOWN` | Le cadre n’auto-autorise que le sortant. Sentinel ne filtre pas ce sens et n’appelle pas `respondToCall` (ignoré hors entrant). À observer sur le S24+, scénarios A04 et A05. | Ligne 24 du service |
| P2-6 | P2 | Ouvert | Plus de 100 préfixes manuels + ARCEP | `effectiveBlockedPrefixes` tronque. Le catalogue ARCEP actuel compte 22 entrées. L’activation refuse déjà un dépassement au moment du choix. | `CallBlocklistStore.Snapshot` |
| P2-7 | P2 | Préexistant | `scripts/check-phone-core-emulation-gate.js` | Le script exige le littéral `if (emergency != false)`. Le service appelle `screeningMustFailOpen`, équivalent (`null` et `true` ouvrent, `false` continue). Écart déjà présent sur `113862500`. Résultat local de ce script : échec sur ce seul marqueur. | Script de porte, pas le moteur |

Aucun P0 de contournement permanent du blocage n’a été établi sur le code de l’API 34. P1-6 reste limité : Android ne donne pas la ligne au service de filtrage. P1-5 et P1-7 sont couverts quand le préchargement tient dans 250 ms. Au-delà, l’appel concerné sonne et le chargement sert l’appel suivant. Le silence des inconnus attend toujours le répertoire contacts, chargé après la réponse.

## 4. Correctifs

- Identité E.164 pour les longueurs complètes `33` (11 chiffres, avec repli outre-mer), `590` / `594` / `596` / `262` (12) et `508` (9), région française seulement. Les préfixes des mêmes indicatifs, à partir de 3 chiffres, rejoignent la forme `+`. `33` et `1` seuls restent refusés. `0612345678` en Belgique reste non normalisé.
- `projectScreeningDecision` aligne l’action et le motif enregistrés sur l’effet Android. Sous l’API 29, un silence devient `ALLOW` / `SILENCE_UNSUPPORTED`. L’API 34 conserve le silence.
- Libellés français pour `IN_DIRECTORY`, `USER_PREFIX_SILENCE` et `SILENCE_UNSUPPORTED`.
- Répertoire de filtrage, recherche de contact locale et interrupteurs de contacts des écrans de listes passent par `PermissionChecker`. Un refus AppOps ferme le répertoire avant la décision. Les blocages explicites ne dépendent pas de ce répertoire.
- Commentaire sur `UnknownCallerPolicy` : ce n’est pas l’autorité de filtrage.
- Préchargement borné des règles et des clés HMAC, hors callback. Budget 250 ms. `onCreate` ne fait que `ScreeningProcessWarmup.schedule(this)`.
- Politique multi-région : une identité unique est appliquée ; deux parses valides en conflit laissent sonner. La ligne réelle reste inconnue du cadre.
- Libellé `AMBIGUOUS_LINE`.

Fichiers du premier lot : `CallRuleEngine.kt`, `CallScreeningPlatformPolicy.kt`, `SentinelCallScreeningService.kt`, `PhoneCoreFrenchLabels.kt`, `CallDirectoryCache.kt`, `LocalContactLookup.kt`, `CallBlockingScreen.kt`, `PhoneProtectionListsScreen.kt`, `UnknownCallerPolicy.kt`. Fichiers du second lot : `ScreeningProcessWarmup.kt`, `ScreeningLineRegionPolicy.kt`, `ScreeningHomeRegion.kt`, `SentinelApplication.kt`, `scripts/android-startup-contracts.js`, et les tests `ScreeningColdStartWarmupTest`, `ScreeningLineRegionPolicyTest`.

Les gardes suivantes sont inchangées : raccrochage depuis la notification, actions de notification périmées, gardes de rôle SMS/MMS, journal MMS durable, recontrôle de permission avant envoi, frontière de réponse unique, invalidation du répertoire par génération.

## 5. Diff et commits

Base du diff : `113862500b9579e2ce476c190ad3195f676ffdda`.

| Commit | Contenu |
| --- | --- |
| `34e8848041ccb389853c81e6fdb60326ad52a652` | P1-1 à P1-4, tests et première version du rapport |
| `bac9e1aa78a45e3aec482f174138efcce6b40325` | SHA du commit précédent inscrit dans le rapport |
| `9cd8c188065a7fa3c3c86920a47e648d2fe34685` | P1-5 et P1-7 sous budget de 250 ms, mitigation limitée P1-6, rapport |

`git diff --stat 113862500… 34e884804…` : 13 fichiers, +328 / −20. Aucun module exclu du profil V1 n’est réactivé.

## 6. Commandes réellement exécutées

Environnement cloud : JDK 21 déjà présent ; JDK 17 installé pour la toolchain Gradle 17 des modules wearable ; Android SDK local `platforms;android-37.0`, Build-Tools 37.0.0 et 36.0.0. Pas d’émulateur. Pas d’appareil. `local.properties` reste ignoré par Git.

| Commande | Issue |
| --- | --- |
| `git merge-base --is-ancestor` sur les trois SHA | Exit 0 pour les trois ancêtres. La branche d’intégration est exactement `113862500…`. |
| `sdkmanager platforms;android-37` | Échec : le paquet publié est `platforms;android-37.0`. |
| `sdkmanager platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools` | Exit 0. |
| Tests rouges ciblés, profil V1, avant correctif | Échec attendu. `frenchCountryCodeDigitsWithoutPlusMatchTheSameE164Identity` (`ComparisonFailure`), `directoryAndSilenceReasonsStayInFrench` (`ComparisonFailure`), `screeningLogUsesTheProjectedDecision…` (`AssertionError`), puis `screeningDirectoryDropsWhenAppOpsDeniesContacts` (`AssertionError`). |
| 11 classes ciblées après correctif, profil V1 | **PASS.** 87 tests, 0 échec, 0 erreur, 0 ignoré. |
| `:app:testDebugUnitTest :wearable-contract:test :wearable-security:test` avec `-PsentinelCommercialV1=true` | **PASS.** Application 1 669 tests / 347 suites. Contrat wearable 6. Sécurité wearable 37. 0 échec, 0 erreur, 0 ignoré. |
| `node --test security/phone-intelligence/private-phone-record-boundaries.test.js` | **PASS.** 8 tests. |
| `:app:assembleDebug :app:assembleDebugAndroidTest` avec `-PsentinelCommercialV1=true` | **PASS de compilation.** APK non installé, tests instrumentés non lancés. |
| `node scripts/check-phone-core-emulation-gate.js` | **ÉCHEC** sur le marqueur préexistant `if (emergency != false)`. |
| Tests P1-5/P1-6/P1-7 avant le code | **FAIL de compilation.** `ScreeningProcessWarmup` et `ScreeningLineRegionPolicy` absents (`UNRESOLVED_REFERENCE`). |
| `ScreeningColdStartWarmupTest`, `ScreeningLineRegionPolicyTest` et classes de filtrage déjà vertes, profil V1 | **PASS.** 3 + 4 tests nouveaux, 0 échec. Régressions ciblées vertes (moteur, politique, libellés, identité internationale, démarrage froid, frontières). |
| `node --test scripts/android-startup-contracts.test.js security/phone-intelligence/call-screening-pre-response-io.test.js` depuis la racine | **PASS.** 15 tests. Le callback ne contient ni disque ni Keystore. `onCreate` n’accepte que `ScreeningProcessWarmup.schedule(this)`. |
| `:app:testDebugUnitTest :wearable-contract:test :wearable-security:test :app:assembleDebug :app:assembleDebugAndroidTest` profil V1 | **PASS.** Application 1 676 tests / 349 suites, 0 échec. Wearable 6 et 37, tâches à jour (sources inchangées, déjà exécutées dans cette session). `assembleDebug` reconstruit. `assembleDebugAndroidTest` à jour et réussi. Aucun APK installé. |
| Installation, rôle, appel, SMS, MMS, redémarrage | **NON EXÉCUTÉ.** |

APK debug produit dans cette VM, non installé :

| Artefact | SHA-256 du fichier | Certificat debug SHA-256 |
| --- | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | `b3546ed33870df833d2d8e3731fab526450fcba2ba0737c9c7923c9253aa4b84` | `29cbb726858cc7cd4a2b1b382d1c304da772bd80824d8d4d0c1c7f52acc4e3f0` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `c088e0d653d531c0abe1f4960c40886ea22968d085f795d646448756db535e5e` | même certificat |

`aapt2 dump badging` : paquet `com.sentinel.quantum.v1validation`, `versionCode` 10, `versionName` `1.0.9-v1-validation`, label `Sentinel V1 - Recette`, `native-code: arm64-v8a`, instrumentation `com.sentinel.quantum.v1validation.test`.

Ce certificat n’est pas celui rapporté sur le S24+ (`68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2`) ni celui de l’archive de recette (`0d5e03ce9117817a0cfe55f2db1368f949218508abe34edf31a013cddde67275`). Les empreintes de fichiers diffèrent aussi de l’archive (`8c34c8a1…` / `baa59521…`) et de la paire locale citée (`6c775880…` / `c207ffbc…`).

## 7. Tableau des essais

| Essai | Résultat |
| --- | --- |
| Reproduction P1-1 à P1-4 avant correctif | FAIL, motif du premier passage |
| Reproduction P1-5 et P1-6 avant le préchargement | FAIL de compilation, symboles absents |
| Classes ciblées après P1-1 à P1-4 | PASS · 87 |
| `ScreeningColdStartWarmupTest` et `ScreeningLineRegionPolicyTest` après correctif | PASS · 3 et 4 |
| Unités application, profil V1, après P1-5/P1-6/P1-7 | PASS · 1 676 |
| `:wearable-contract:test` | PASS · 6, à jour |
| `:wearable-security:test` | PASS · 37, à jour |
| Contrats Node démarrage et absence d’E/S avant réponse | PASS · 15 |
| Contrats Node répertoire / contacts / journal | PASS · 8 |
| Assemblage debug + androidTest, profil V1 | PASS de build seulement |
| Porte `check-phone-core-emulation-gate.js` | FAIL · marqueur préexistant |
| Tests instrumentés, émulateur, S24+ | BLOCKED · aucun appareil |
| A01–A14 | NON EXÉCUTÉ |
| Signature de livraison, Play, rôles réels | BLOCKED |

## 8. Build et installation Termux sur le S24+

Depuis le dépôt, JDK 17 et un SDK dont la plateforme résout `compileSdk 37` (`platforms;android-37.0` avec les outils testés ici) :

```sh
cd native-android-app
bash gradlew -PsentinelCommercialV1=true :app:assembleDebug :app:assembleDebugAndroidTest
```

Les APK sortent dans `app/build/outputs/apk/debug/` et `app/build/outputs/apk/androidTest/debug/`. Si `aapt2` ou `apksigner` manquent dans Termux, faire le prévol sur un hôte qui a les Platform-Tools et Build-Tools, comme déjà écrit dans `S24_PHYSICAL_ACCEPTANCE.md`.

Avant toute copie vers le téléphone, relever le certificat de **cette** construction. Le debug Android de la machine qui compile n’est pas une identité de livraison. Deux machines produisent deux certificats. Conserver les APK hors du dépôt Git.

```sh
S24_SERIAL=   # adb devices -l, modèle SM-S926*
pkg=com.sentinel.quantum.v1validation
adb -s "$S24_SERIAL" shell getprop ro.product.model
```

## 9. Mise à jour qui conserve les données

À faire avant `adb install`, sur l’utilisateur Android courant, et à arrêter au premier écart.

1. Noter le titulaire de Téléphone, SMS et filtrage, le `versionCode`, le `versionName`, l’identifiant de paquet et le SHA-256 du certificat **déjà installé**. Tirer `pm path` puis le `base.apk` et lire le certificat avec `apksigner verify --print-certs`. Confirmer que les données du paquet existent encore (`adb shell dumpsys package <pkg>` et, sans les extraire, la présence du répertoire privé). Ne pas effacer ce répertoire.
2. Lire le même certificat, le `versionCode` et le paquet sur l’APK candidat. Les deux APK (application et instrumentation) doivent porter le même certificat. Le `versionCode` candidat doit être supérieur ou égal à celui installé pour un `adb install -r`.
3. Certificats identiques et paquet identique : `adb install --user <id> -r <apk-app>` puis `adb install --user <id> -r -t <apk-test>`. Relire le certificat installé et le `versionCode`. Les données du paquet restent.
4. Certificats différents : **arrêter**. Android refuse la mise à jour. Une désinstallation est le seul moyen d’installer l’autre signature, et elle détruit les données de l’application (règles, historique de filtrage, messages locaux). Ce parcours est hors protocole. Ne pas désinstaller pour « faire passer » un APK de cette VM : son certificat `29cbb726…` ne correspond ni au S24+ cité (`68915958…`) ni à l’archive (`0d5e03ce…`).
5. Après une mise à jour de même signature, rouvrir l’application et vérifier que les règles locales sont encore là avant tout scénario d’appel. A01–A14 restent à exécuter sur l’appareil ; ce document ne les coche pas.

## 10. Blocages de commercialisation

- Signature de release (`KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) absente. L’AAB signé Play n’existe pas.
- Politique Play pour le rôle de filtrage d’appels, le rôle SMS par défaut, et les permissions sensibles (`READ_CONTACTS`, journal d’appels, téléphone, SMS, MMS). Fiche Data safety et déclaration de gestionnaire d’appels / SMS non vérifiées dans la console.
- A01 à A14 non exécutés : installation et navigation, refus puis octroi des rôles, appels entrants et sortants, raccrochage par notification, filtrage effectif, contacts et historique, SMS et MMS opérateur, révocation pendant les opérations, reprise après fermeture et redémarrage, restitution des titulaires d’origine.
- P1-6 reste limité sur appareil : la ligne n’est pas dans le callback. A05 et A07 doivent observer un numéro national sur chaque SIM, y compris quand les deux parses divergent.
- P1-5 et P1-7 restent à chronométrer sur le S24+ après redémarrage (A12, A07). Le budget de 250 ms n’a pas été mesuré sur cet appareil. Un disque ou un Keystore plus lent laisse sonner le premier appel.
- Le certificat debug de recette doit être unique et connu avant la campagne. Trois empreintes circulent déjà (archive, téléphone cité, VM de cet audit).

**Décision : NO-GO.**
