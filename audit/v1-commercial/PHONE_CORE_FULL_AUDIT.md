# Audit Phone Core V1 — 18–19 novembre 2026

Verdict : **V1 COMMERCIAL NO-GO**.

Lecture seule. Aucun correctif de code. Aucune fusion. `main`, les cycles C9, C10, C11 et les branches des PR #1767, #1768 et #1769 n’ont pas été modifiés. Les tests JVM ne qualifient ni un appel, ni un SMS, ni un MMS. La machine cloud n’a pas d’appareil. Une observation Termux / ADB sans fil sur un Galaxy S24+ SM-S926B a été faite le 19 novembre 2026 (§6) : rôles et certificat seulement. Une affirmation d’opérateur « postes 100 % / appel SMS MMS » est traitée en §6.1 : elle ne remplace pas la matrice physique V1 ni le invariant `physicalDeviceValidated=false`. Le verdict reste NO-GO.

## 1. Têtes examinées

| Repère | SHA | Rôle |
| --- | --- | --- |
| Tête de départ, PR #1769 `cursor/v1-p0-privacy-catalog-c39e` | `6de5a272e0302ad1eccc6c2215157b13f97d485c` | Code audité. Empilée sur #1768. |
| PR #1767 `cursor/v1-call-screening-audit-536d` | `5a6f7014bdb1df8905a1cba3ca0afca8244fa6d2` | Correctifs P1 du filtrage, lus par `git show`, sans checkout de travail. |
| Référence `integration/sentinel-v1-commercial-20261118` | `113862500b9579e2ce476c190ad3195f676ffdda` | Base commune des deux PR. Ancêtre de fusion. |
| Build V1 cité | `25c4e2c9224abe8feaf07474a2d092ea657330f5` | « Remove nonessential diagnostic exports from the V1 APK ». Présent dans les deux historiques. |

Profil Gradle : `-PsentinelCommercialV1=true`. Il pose `BuildConfig.COMMERCIAL_PHONE_CORE_V1`, le manifeste `src/v1Commercial/AndroidManifest.xml`, le libellé « Sentinel Phone Core », et le suffixe debug `com.sentinel.quantum.v1validation`. Il ne retire pas les dépendances.

## 2. Divergence #1767 / #1769

Les fichiers Kotlin et XML modifiés depuis `113862500` ne se recoupent pas (`comm -12` vide). `git merge-tree` ne produit aucun marqueur de conflit. Une fusion textuelle appliquerait les deux lots. Elle n’a pas été faite.

#1769 contient les catalogues, la sauvegarde et le lien de confidentialité. #1767 contient le préchargement 250 ms, la région de ligne, la projection de la décision et la permission contacts AppOps. Chaque PR est donc incomplète pour un Phone Core commercial. Les correctifs de #1767 ne sont pas une régression de #1769 : ils n’ont jamais été sur cette tête. Le filtrage de `6de5a272` est identique à celui de `113862500`.

La porte d’émulation échoue de la même façon sur `113862500`, sur `5a6f7014` et sur `6de5a272` : le marqueur `if (emergency != false)` est absent du service depuis la référence. Le code appelle `screeningMustFailOpen(emergency)`.

## 3. Constats

Sévérité : P0 bloque une distribution commerciale. P1 casse une fonction promise du téléphone sur un chemin réaliste. P2 dégrade un cas borné ou un contrôle. P3 est une réserve de lisibilité ou de preuve.

| ID | Gravité | Fichier:ligne | Description | Impact | Preuve | Correctif proposé | Test à écrire |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | P0 | `CommercialPrivacyPolicyLink.kt:13-17` | `VERIFIED_V1_URL` est nul. `url()` ouvre `SHOWCASE_URL`, la politique de vitrine. | Le bouton des réglages V1 n’ouvre pas une politique Phone Core publiée. Play reste bloqué sur ce point. | Constante nulle, commentaire des lignes 6-9 : ne pas inventer d’URL. | Publier le texte V1, vérifier par GET HTTPS, puis seulement renseigner la constante. | Test qui échoue tant que l’URL est nulle, puis vérifie le corps distinct de la vitrine. |
| F-02 | P0 | `app/build.gradle:40-44` et `:122-127` | Aucun keystore release. `KEYSTORE_FILE` est absent de l’environnement. Cette VM n’a pas de `debug.keystore`. | Impossible de produire l’AAB Play. Une reconstruction debug créerait un certificat nouveau, incompatible avec l’installation de laboratoire. | Le dépôt ne contient aucun `.jks`. Digest labo archivé `0d5e03ce…` (`audit/cycle11/C11_LOCAL_EVIDENCE.log:426`). Sur le S24+ du 19 nov. 2026, l’APK installé porte `68915958…`, identique à `iacapture`, différent du labo (§6). | Keystore release hors git. Épingler le digest réellement installé sur le S24 de recette. | `apksigner verify --print-certs` sur l’AAB, digest différent du debug. Non exécuté ici. |
| F-03 | P0 | `SentinelApplication.kt:24-30` et `:48-50` ; `CallNumberFingerprinter.kt:32-47` ; `CallBlocklistStore.kt:436-445` | `onCreate` ne précharge rien. Le callback ne lit que le snapshot mémoire, vide au démarrage à froid. `cachedCandidates` n’ouvre pas le Keystore et ne génère pas de clé. | Après la mort du processus, le premier appel sonne même si un numéro est bloqué. Les règles ne s’appliquent qu’après une activité ou après le travail post-réponse. | Commentaire explicite lignes 24-30. #1767 ajoute `ScreeningProcessWarmup` (`CALLBACK_WAIT_BUDGET_MS = 250`) dans `SentinelApplication.kt:50` et `SentinelCallScreeningService.kt:22-24` et `:80` de `5a6f7014`. Ces fichiers sont absents de `6de5a272`. | Reprendre le préchargement de #1767, hors du callback, budget 250 ms, sans générer de clé. | Test : snapshot vide avant `await`, règles présentes après, aucun accès disque dans `awaitForScreening`. |
| F-04 | P1 | `CallRuleEngine.kt:194-202` | Un numéro de chiffres sans `+` ni `00` tombe dans `else -> digits`. `33612345678` ne devient pas `+33612345678`. | Un blocage ou un contact enregistré en E.164 rate l’appel présenté sans le signe plus. | Branche `else` ligne 201. #1767 ajoute `promoteUnprefixedFrenchNumber` aux lignes 314-322 de sa copie, longueurs exactes 33/590/594/596/262/508. | Porter cette promotion, limitée aux longueurs complètes et à une région française. | `33612345678` et `+33612345678` produisent la même identité. `447911123456` ne devient pas français. |
| F-05 | P1 | `ScreeningHomeRegion.kt:16-29` ; `CallDirectoryCache.kt:80` | Une seule `simCountryIso`. Défaut `"FR"` tant que `capture` n’a pas tourné. `capture` n’est appelé que pendant le rafraîchissement du répertoire. | Double SIM, ou SIM non française avant le premier rafraîchissement : le national est plié sur le plan français. Android ne donne pas la ligne dans `Call.Details`. | Lecture unique lignes 23-29. #1767 lit les abonnements dans `readSimRegions` (lignes 54-70) et, si deux parses divergent, laisse sonner (`ScreeningLineRegionPolicy.kt:19-33`), sauf si toutes les parses sont `BLOCK` (ligne 25). | Porter la capture hors callback. Si deux pays divergent, sonner et le dire. Ne pas inventer la ligne. | Deux ISO distincts, deux E.164 valides, actions différentes : `ALLOW` / `AMBIGUOUS_LINE`. |
| F-06 | P1 | `CallBlocklistStore.kt:173` ; `CallRuleEngine.kt:276-278` ; `CallDirectoryCache.kt:134` | L’enrôlement et `blockMatchInputs` appellent `normalizeNumber` sans région, donc avec le défaut `FR`. Le répertoire aussi. `evaluate` utilise `homeRegion`. | Le blocage exact reste cohérent avec l’enrôlement, les deux ignorant la région capturée. Un contact national étranger est stocké sous une identité française et n’est plus reconnu. #1767 ne change pas la ligne 133 de son `CallDirectoryCache`. | Appels sans second argument. | Passer la région capturée à l’enrôlement, à `blockMatchInputs` et au chargement du répertoire. | SIM `BE`, contact `0470123456`, répertoire contient `+32…` et non `+33…`. |
| F-07 | P1 | `CallDirectoryCache.kt:114-118` | La permission contacts est lue avec `ContextCompat.checkSelfPermission`, pas avec `PermissionChecker`. | Un AppOp retiré peut laisser le répertoire « prêt ». Si la requête renvoie un curseur vide sans exception, « silence hors répertoire » met tout le monde en sourdine. Non observé sur appareil. | Lignes 114-118. #1767 passe à `PermissionChecker` lignes 113-117. | Porter ce contrôle, et publier `ready = false` si l’AppOp n’est pas accordé. | AppOp refusé : vue non prête, aucune mise en sourdine. |
| F-08 | P1 | `CallScreeningPlatformPolicy.kt:21-24` ; `SentinelCallScreeningService.kt:102-120` et `:187-191` | Sous l’API 29, `SILENCE` devient `ALLOW` pour Android, mais le journal enregistre l’action du moteur. | Sur Android 7 à 9, l’historique dit « mis en sourdine » alors que l’appel sonne. `minSdk` est 24. | `sdkInt >= 29` sinon `ALLOW`. Le log ligne 190 écrit `decision.action`. #1767 projette avant de répondre (`projectScreeningDecision`, service lignes 112-113 de `5a6f7014`). | Journaliser l’effet réellement envoyé à Telecom. | API 28, action moteur `SILENCE` : réponse sans `setSilenceCall`, motif journalisé en autorisation. |
| F-09 | P1 | `scripts/check-phone-core-emulation-gate.js:179` | La porte exige le texte `if (emergency != false)` dans le service. | `node scripts/check-phone-core-emulation-gate.js` sort 1 sur les trois SHA. Le comportement fail-open existe pourtant. | Exécuté sur les trois arbres. Seule erreur : marqueur manquant. `CallScreeningPlatformPolicy.kt:32-33` : `emergencyClassification != false`. | Faire chercher `screeningMustFailOpen(emergency)`. | Le script passe, et `CallScreeningPolicyTest` couvre null, true et false. |
| F-10 | P1 | Manifeste fusionné release, permission ligne 56 ; journal de fusion `:1207-1210` | `MODIFY_AUDIO_SETTINGS` vient de `com.github.davidliu:audioswitch`, dépendance de LiveKit. | Le contrôle Python dit PASS et ne voit pas cette permission. Un composeur cellulaire n’en a pas besoin. LiveKit, Auth0 et WireGuard sont encore dans `app/build.gradle:158-161`. | Journal `manifest-merger-releaseUnsigned-report.txt`. Les activités Auth0, le service LiveKit et le `VpnService` WireGuard sont retirés par l’overlay, pas le classpath. | `implementation` conditionnelle hors V1. Étendre le script Python à cette permission et à la requête ci-dessous. | Le manifeste fusionné V1 ne contient plus `audioswitch` ni `auth0`. |
| F-11 | P2 | Manifeste fusionné `:20-26` ; journal `:977-986` | `<queries>` garde `VIEW` + `BROWSABLE` + `https`, ajouté par Auth0 `3.21.1-beta.0`. | Pas une activité exportée. Visibilité de paquets héritée d’un SDK retiré de l’interface. | Le composant Auth0 n’est pas dans les 27 composants. La requête, si. | Retirer Auth0 du graphe V1, ou `tools:node="remove"` sur cette requête. | Le script refuse cette requête dans le manifeste V1. |
| F-12 | P2 | `UnknownCallerPolicy.kt:21-29` | `decide` n’est appelé par aucun écran ni par le moteur. Seul `UnknownCallerPolicyTest` le touche. | Favoris, liste blanche et rappel de cette politique n’ont aucun effet. Le silence des inconnus réellement branché est `silenceOutsideDirectory` dans `CallRuleEngine`. | Recherche sur `*.kt` : deux fichiers. | Brancher la politique sur le moteur, ou la supprimer pour ne pas laisser croire qu’elle filtre. | Test d’intégration : mode activé, numéro hors répertoire, réponse `SILENCE` après `respondToCall`. |
| F-13 | P2 | `PhoneCoreFrenchLabels.kt:32-42` | `IN_DIRECTORY` et `USER_PREFIX_SILENCE` tombent sur « Motif technique non traduit ». | L’historique montre un code interne après un filtrage réussi. #1767 ajoute les libellés. | Branche `else` ligne 42. Le moteur émet ces motifs aux lignes 37 et 40 de `CallRuleEngine.kt`. | Ajouter les deux libellés français. | Les deux motifs ne contiennent pas « non traduit ». |
| F-14 | P2 | `SentinelSmsStatusReceiver.kt:219-243` ; `SmsCallbackProgressStore.kt:186` ; `SmsConversationStore.kt:426-427` | La réparation repart toutes les 60 s si l’écriture provider échoue. `markOutgoingSent` refuse sans `ROLE_SMS`. TTL 24 h. | Rôle SMS retiré : la boucle continue jusqu’à l’expiration, sans envoyer. L’envoi initial, lui, est bien revérifié. | Pas de lecture de rôle dans `queueProviderRepair`. | Sortir et clore la ligne si le rôle n’est plus tenu. | Rôle absent : une tentative, pas de nouveau `schedule`. |
| F-15 | P2 | `SmsActivationDiagnostics.kt:110-111` ; `SentinelSmsSender.kt:227-229` | L’écran de préparation utilise `PermissionChecker` seul. L’envoi exige aussi `ContextCompat`. | L’interface peut afficher « prêt » là où l’envoi refusera. | Deux définitions de `hasEffectivePermission`. | Une seule fonction, celle de l’expéditeur. | AppOp et permission runtime divergents : `canSend` faux, `sendTextMessage` non appelé. |
| F-16 | P2 | `SentinelCallNotificationHelper.kt:50-54`, `:77-86`, `:113-125` | Raccrocher existe : `ACTION_DISCONNECT`, `PendingIntent` `FLAG_UPDATE_CURRENT \| FLAG_IMMUTABLE`, identifiant dans l’URI et l’extra, receveur non exporté (`SentinelCallActionReceiver.kt:16-30`). | Sans `POST_NOTIFICATIONS` (API 33+), `showIncoming` retourne faux : pas de plein écran Sentinel. Une notification unique suit l’appel au premier plan. Une action périmée ne fait rien. | `return false` lignes 50-54. `setFullScreenIntent` ligne 120, entrant seulement. Aucun `FLAG_MUTABLE` dans `app/src/main`. | Si le rôle Téléphone est tenu et la notification refusée, ouvrir quand même l’activité d’appel. | Instrumentation : permission refusée, activité d’appel visible. Non exécutée. |
| F-17 | P2 | `CallScreeningResponseBoundaryIntegrationContractTest.kt:11-33` | Le test lit le source et compte des chaînes. | Il casse si l’on renomme, il ne prouve pas qu’un `respondToCall` part. | `File(...).readText()` ligne 12. | Test du service avec un faux `Call.Details`, ou assumer que ce fichier est un contrat de structure. | Un faux `respondToCall` qui jette laisse la plateforme sans seconde réponse : comportement à figer. |
| F-18 | P2 | `app/build.gradle:131-132` et `:158-161` ; `settings.gradle:18-21` | LiveKit 2.29.0, Auth0 3.21.1-beta.0, WireGuard tunnel, Play Services Wearable, `:wearable-security` et `:wearable-contract` sont toujours compilés. Les `.so` LiveKit et WireGuard sont exclus du packaging (`:102-110`). | Qwen, VPN, Mesh et scanners radio ne sont pas des modules réintroduits. Leur source est dans `:app`. Le graphe commercial (`CommercialPhoneCoreNavGraph.kt:24-36`) ne les affiche pas. Le manifeste retire Voice Studio, l’activité IA et le service Wear. R8 n’a pas été exécuté : la présence dans le DEX n’est pas mesurée. Lint debug cite encore `MeshNodeCredentialStore.kt` et `VpnWireGuardIdentityStore.kt`. | Dépendances non conditionnelles. Overlay `v1Commercial/AndroidManifest.xml:45-57`. | Couper le classpath V1. Revérifier le manifeste et un `dexdump` de l’APK release. | Inventaire `releaseRuntimeClasspath` sans `io.livekit`, `com.auth0`, `com.wireguard`, `audioswitch`. |
| F-19 | P2 | #1767 `ScreeningProcessWarmup.kt:36-49` et service `:80` | Présent seulement sur #1767. `awaitForScreening` rend `true` dès que les règles sont prêtes, même si les clés ou les régions ont dépassé le reste du budget. L’appelant ignore le booléen. | Keystore lent : ce coup-ci sonne, le chargement sert le suivant. Comportement décrit, mal signalé. `installed` vaut `setOf("FR")` tant que la capture n’a pas fini. | Lignes citées de `5a6f7014`. | Retourner faux si une latch utile a expiré, et journaliser un code sans numéro. | Budget 250 ms, clés qui dorment 1 s : snapshot de règles présent, candidats HMAC vides, région encore au défaut. |
| F-20 | P3 | `ArcepDirectoryClient.kt:70-74` et `:38-41` | L’interrupteur est relu avant le GET et après le corps. Un GET déjà parti n’est pas annulé. Le corps est jeté. | Quelques octets peuvent quitter l’appareil après le retrait du consentement. Ils ne sont pas stockés. | Commentaire lignes 70-73. Le composeur passe l’interrupteur (`SentinelDialerActivity.kt:843`). La fiche d’appel aussi (`CallerIdActivity.kt:185`). Le worker sort si l’interrupteur est faux. | Documenter ce compromis dans Data safety. Annuler l’appel OkHttp seulement si le produit l’exige. | Test déjà présent : interrupteur coupé pendant le transfert, corps non stocké. Relu, non rejoué dans cette session au-delà de la suite JVM. |
| F-21 | P3 | `IncomingMmsConversationStore.kt:482-496` | Le numéro de la ligne est lu pour ne pas fusionner le fil MMS avec soi-même. `SentinelDeviceIdentity` (`ANDROID_ID` haché) sert à Voice Studio et à l’écran réglages complet, hors graphe commercial. | Usage MMS local, échec en ensemble vide. Pas un envoi vers un serveur dans ce fichier. | `line1Number` ligne 495. Appels de `SentinelDeviceIdentity` : `SettingsScreen.kt:63`, `VoiceStudioActivity.kt:145`. | Laisser la lecture MMS. Ne pas monter l’identifiant appareil dans l’UI V1. | Aucun log du numéro de ligne. Grep des `Log.*` du module sécurité : pas d’interpolation de corps SMS trouvée. |
| F-22 | P3 | `audit/v1-commercial/validation/check_merged_manifest.py:35-93` | Le script vérifie paquet, sauvegarde, cleartext, NSC absent, permissions listées, exports connus, FileProvider, requête contact. | Il dit PASS (voir §5) tout en laissant passer F-10 et F-11. Il ne regarde pas `debuggable`, `FLAG_MUTABLE`, ni `MODIFY_AUDIO_SETTINGS`. | Comparaison du script et du manifeste réel. | Ajouter ces assertions. | Manifeste avec `audioswitch` : le script sort 1. |

Points vérifiés sains sur `6de5a272` :

- Urgence : `screeningMustFailOpen` (`CallScreeningPlatformPolicy.kt:32-33`), appelé avant le moteur (`SentinelCallScreeningService.kt:65-69`). Null ou true : l’appel sonne.
- Rôle de filtrage retiré (API 29+) : réponse vide, pas de moteur (`SentinelCallScreeningService.kt:42-50`). Sous l’API 29, l’autorité suit le composeur par défaut (`CallScreeningActivationPolicy.kt:19-22`).
- Exception du moteur : réponse vide (`:95-100`).
- Le callback n’instancie pas `CallBlocklistStore` et ne requête pas les contacts.
- Marqueur log `CallScreeningService:onScreenCall` sans numéro (`:37`, `:246-247`). Le journal post-réponse écrit l’action et le motif, pas le MSISDN (`:187-191`).
- STIR/SHAKEN seulement à partir de l’API 30 (`:132-140`).
- HMAC : `prepareExistingKeys` ne génère pas de clé (`CallNumberFingerprinter.kt:50-69`). La génération reste sur `candidates()`, hors du callback.
- Rôle Téléphone absent : `placeCallIfReady` ne lance pas l’appel (`SentinelDialerActivity.kt:388-390`). Urgence : transfert au composeur système (`:375-386`).
- SMS : seconde lecture du rôle et des permissions juste avant `sendTextMessage` (`SentinelSmsSender.kt:151-182` et `:218-230`).
- Callback SMS tardif : `markOutgoingSent` / `markOutgoingFailed` exigent encore le rôle (`SmsConversationStore.kt:426-427` et `:442-443`).
- MMS : journal de phases sans destination ni texte (`MmsProviderJournal.kt:6-10`, `:74-89`). Un `READY` après mort du processus devient `SUBMISSION_UNKNOWN`, sans inventer un accusé.
- Contacts du composeur : génération comparée avant d’afficher (`SentinelDialerActivity.kt:831-837`). Le répertoire mémoire rejette un rafraîchissement périmé (`CallDirectoryCache.kt:79-96`).
- Journal d’appels système : rôle Téléphone et `READ_CALL_LOG` (`SystemCallLogReader.kt:133-143`, d’après la lecture du fichier pendant l’audit).
- Réputation distante forcée à vide dans le filtrage V1 (`SentinelCallScreeningService.kt:81-82`).
- Sauvegarde : `allowBackup=false`, règles legacy sur root, file, database, sharedpref, external (`backup_rules.xml:2-7`).
- `PendingIntent` d’appel, SMS et MMS : immuables. Aucun `FLAG_MUTABLE` dans `app/src/main`.
- Plein écran : `USE_FULL_SCREEN_INTENT` dans le manifeste fusionné, activité d’appel `exported=false`, `setFullScreenIntent` sur l’entrant.

## 4. Correctifs V1 déjà dans l’arbre

| Correctif | Statut sur `6de5a272` |
| --- | --- |
| Catalogues ARCEP/RTR seulement si l’interrupteur est vrai ; corps ignoré s’il retombe à faux | Préservé. `ArcepDirectoryClient.kt:38-41` et `:56-58`, équivalent RTR, composeur, fiche d’appel, worker. |
| Ne pas inventer l’URL de politique | Préservé, et toujours bloquant (F-01). |
| Exclusion des sauvegardes legacy et modernes | Préservé. `backup_rules.xml`, `data_extraction_rules.xml`, `allowBackup=false` dans le manifeste fusionné. |
| Overlay V1 : plus de NSC cleartext, plus de caméra, micro, localisation, Bluetooth, Wi-Fi, Voice Studio, IA, Wear, activités Auth0, service LiveKit, `VpnService` | Préservé dans le manifeste fusionné release. |
| Préchargement 250 ms, région multi-SIM, projection silence, AppOps contacts, promotion E.164, libellés | Absents. Ils vivent sur #1767. Pas une régression : jamais intégrés. |
| Filtrage d’urgence, rôle relu, HMAC sans Keystore sur le callback, garde SMS avant envoi, journal MMS | Préservés, identiques à `113862500` pour les fichiers que #1769 n’a pas touchés. |

## 5. Commandes exécutées

Répertoire `native-android-app`. SDK installé localement pour cette session : platform `android-37.0`, Build-Tools 37.0.0 puis 36.0.0 exigé par AGP. JDK 21 est le lanceur. Le projet demande un toolchain 17.

Première exécution de la commande demandée, avant l’installation du JDK 17 :

`bash ./gradlew --no-daemon --max-workers=2 :app:testDebugUnitTest :app:lintDebug :app:compileReleaseUnsignedKotlin -PsentinelCommercialV1=true`

Résultat : **BUILD FAILED in 29s**, code 1. Cause : aucun JDK 17. Aucun test n’a démarré. Ce n’est pas un PASS.

Seconde exécution, même commande, après `openjdk-17-jdk-headless` :

**BUILD SUCCESSFUL in 3m 40s**, code 0. 51 tâches actionnables, 48 exécutées, 3 depuis le cache.

| Tâche | Chiffre |
| --- | --- |
| `:app:testDebugUnitTest` | 350 suites, 1 677 tests, 0 échec, 0 erreur, 0 ignoré |
| `:app:lintDebug` | 0 erreur, 245 avertissements, 19 indications, 264 issues, 17 identifiants distincts. Les plus fréquents : UseKtx 92, UnusedResources 57, InlinedApi 25 |
| `:app:compileReleaseUnsignedKotlin` | Succès. Classes sous `app/build/intermediates/built_in_kotlinc/releaseUnsigned`. R8 non lancé. |

Le Kotlin release ne produit pas le manifeste fusionné. Commande supplémentaire, hors de la ligne demandée, nécessaire au contrôle :

`bash ./gradlew --no-daemon --max-workers=2 :app:processReleaseUnsignedManifest -PsentinelCommercialV1=true`

**BUILD SUCCESSFUL in 7s**, code 0.

`python3 audit/v1-commercial/validation/check_merged_manifest.py` sur le manifeste `processReleaseUnsignedMainManifest` :

`PASS merged V1 manifest: com.sentinel.quantum, 18 permissions, 27 components`

Même script sur le manifeste debug :

`PASS merged V1 manifest: com.sentinel.quantum.v1validation, 18 permissions, 27 components`

Release : `versionCode` 10, `versionName` 1.0.9, `minSdk` 24, `targetSdk` 36, `debuggable` absent, `allowBackup=false`, `usesCleartextTraffic=false`, pas de `networkSecurityConfig`, libellé « Sentinel Phone Core ». Debug : `debuggable=true`, `versionName` `1.0.9-v1-validation`. Onze composants exportés, tous attendus ou bibliothèques protégées (`SystemJobService`, `ProfileInstallReceiver`). Seize composants privés, dont FileProvider.

Porte d’émulation, sur l’arbre de travail, sur `113862500` et sur `5a6f7014` :

`PHONE CORE EMULATION SHADOW GATE: BLOCKED`

`- CallScreeningService truth missing marker: if (emergency != false)`

Code de sortie 1. Aucun émulateur n’a été démarré.

## 6. Observation physique S24+ (Termux / ADB sans fil) — 19 novembre 2026

Environnement : Termux sur le S24+ lui-même, ADB sans fil `192.168.1.16:39173`, répertoire `~/sentinel-cycle11-s24-validation`. Pas de second téléphone. Pas d’émulateur sur l’appareil. Rebuild Gradle sur l’appareil : échec KSP sous PRoot (`libm.so: invalid ELF header`). `apksigner` Termux : abort `ProtoLogNative`. Digest certificat lu par parsing Python du bloc APK Signature Scheme v2/v3.

| Élément | Valeur observée |
| --- | --- |
| Modèle | `SM-S926B` (`getprop ro.product.model`) |
| API / build | Android 17, API 37 (dumpsys / Developer Options) |
| Paquet V1 | `com.sentinel.quantum.v1validation` |
| Version | `versionName` `1.0.9-v1-validation`, `versionCode` 10 |
| Digest SHA-256 cert. installé V1 | `68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2` |
| Digest `com.sentinel.quantum.iacapture` | Identique à V1 (`same_as_v1 True`) |
| Digest labo archivé C11 `0d5e03ce…` | Différent (`same_as_labo False`) — confirme F-02 |
| `ROLE_DIALER` après réglages utilisateur | `com.sentinel.quantum.v1validation` (avant : `iacapture`) |
| `ROLE_SMS` | `com.sentinel.quantum.v1validation` |
| `ROLE_CALL_SCREENING` | `com.sentinel.quantum.v1validation` |
| Après `am force-stop` + `pidof` | `process_absent_ok` ; les trois rôles restent sur `v1validation` |

Commandes de clôture (sorties collées par l’opérateur) :

```text
adb -s 192.168.1.16:39173 shell am force-stop com.sentinel.quantum.v1validation
adb -s 192.168.1.16:39173 shell pidof com.sentinel.quantum.v1validation || echo process_absent_ok
→ process_absent_ok
adb … get-role-holders … DIALER        → com.sentinel.quantum.v1validation
adb … get-role-holders … SMS           → com.sentinel.quantum.v1validation
adb … get-role-holders … CALL_SCREENING → com.sentinel.quantum.v1validation
```

Ce que cette observation **prouve** : le binaire installé est bien le paquet de recette V1 ; l’utilisateur peut lui donner les trois rôles ; les rôles survivent à la mort du processus ; le certificat installé n’est pas celui du labo C11 archivé.

Ce que cette observation **ne prouve pas** : `onScreenCall`, fail-open à froid sur un vrai appel, silence / blocage, composeur entrant, SMS / MMS opérateur, retrait de rôle en cours de session, double SIM, écran verrouillé. Sans seconde ligne, ces points restent NON EXÉCUTÉS. Le verdict commercial ne bouge pas.

### 6.1 Affirmation « tous les tests postes à 100 %, appel / SMS / MMS »

Affirmation de l’opérateur (19 nov. 2026) : les tests postes, y compris appel, SMS et MMS, seraient déjà à 100 %.

Ce que le dépôt et le code permettent de **rattacher** à cette phrase :

| Source | Contenu | Portée |
| --- | --- | --- |
| `PhoneCorePhysicalValidationTest` (`preuves/junit/…PhoneCorePhysicalValidationTest.xml`) | 17 tests, 0 échec (horodatage 2026-11-16) | JVM uniquement. Les événements CALL/SMS/MMS sont **fabriqués** dans le test (`almostCompleteEvents()`), pas lus sur un opérateur. |
| `PhoneCorePhysicalValidation.Evidence.physicalDeviceValidated` | toujours `false` (`PhoneCorePhysicalValidation.kt:33`) | Même si les 14 critères locaux sont complets (`fullyValidated`), le modèle **refuse** de se promouvoir en validation matérielle / opérateur. |
| `audit/v1-commercial/S24_PHYSICAL_ACCEPTANCE.md` | A01–A14 en **NON EXÉCUTÉ** ; en-tête **BLOCKED** | Matrice physique V1 officielle du dépôt. |
| `FEATURE_MATRIX.md` / `TEST_EVIDENCE.md` | Qualifié S24+ = **BLOCKED** pour téléphone, SMS, MMS | Candidat `25c4e2c…` ; installation S24+ absente de ces preuves. |
| `audit/cycle11/VALIDATION_S24_PLUS.md` | Signale des scénarios Android du propriétaire **non accessibles** ici ; **non attribués** aux APK de la page ; certificat C11 `0d5e03ce…` | Ne couvre pas `v1validation` / cert `68915958…` observé le 19 nov. |
| Session Termux du 19 nov. | Rôles + cert seulement | Pas d’appel / SMS / MMS observés dans cette session. |

Conclusion de l’audit : un « 100 % » JVM (fixtures ou suites unitaires) **n’est pas** un PASS physique A04–A11. Sans pack de preuve lié au binaire exact (`1.0.9-v1-validation`, cert `68915958…`) — matrice A01–A14 remplie, horodatages, logcat filtré sans PII — l’audit **ne bascule pas** ces lignes en PASS. Le verdict reste **V1 COMMERCIAL NO-GO** (F-01, F-02, F-03 et classpath hors téléphone suffisent déjà).

## 7. Non exécuté

- Appel entrant ou sortant réel, SMS, MMS, accusé opérateur, double SIM réelle, retrait de rôle pendant un appel / un envoi — **non prouvés** pour le binaire V1 installé le 19 nov. (voir §6.1).
- Tout émulateur, `connectedDebugAndroidTest`, flux `scripts/phone-core-emulator-flow.sh`.
- `assembleRelease`, `bundleRelease`, R8, contenu DEX, `apksigner` (hôte et Termux).
- Signature release. `KEYSTORE_FILE` non défini.
- Annulation réelle d’un GET catalogue sur le réseau.
- Les 1 677 tests ne portent pas sur Telecom. Les dire PASS qualifie la JVM, pas le téléphone. Idem pour les 17 tests `PhoneCorePhysicalValidationTest`.
- #1767 n’a pas été recompilée dans cette session. Son code a été lu. Ses anciens comptages (1 676 tests) ne sont pas repris comme résultat d’aujourd’hui.

## 8. Plan de correction

1. Ne pas merger vers `main`. Fusionner textuellement #1767 dans la pile #1769, puis rejouer les trois tâches Gradle et les deux manifestes. Le résultat JVM ne change pas le NO-GO.
2. Publier la politique V1 et ne renseigner `VERIFIED_V1_URL` qu’après un GET qui renvoie ce texte.
3. Déposer le keystore release dans le canal secret. Épingler le digest installé sur ce S24 (`68915958…`) avant toute réinstallation ; ne plus traiter `0d5e03ce…` comme le seul certificat de recette sans inventaire.
4. Retirer LiveKit, Auth0, WireGuard, Wear et audioswitch du classpath V1. Étendre `check_merged_manifest.py` à `MODIFY_AUDIO_SETTINGS` et à la requête `BROWSABLE`.
5. Aligner la porte d’émulation sur `screeningMustFailOpen(emergency)`.
6. Région capturée pour l’enrôlement, le répertoire et `blockMatchInputs`. Promotion E.164 limitée aux numéros français complets.
7. Brancher ou supprimer `UnknownCallerPolicy`. Traduire `IN_DIRECTORY` et `USER_PREFIX_SILENCE`.
8. Arrêter la réparation SMS quand le rôle disparaît.
9. Ensuite seulement : protocole S24+ restant (appel, SMS, MMS, retrait de rôle, double SIM, écran verrouillé, fail-open à froid). Les rôles sont déjà posés ; l’absence de seconde ligne bloque encore ces preuves. Sans elles, le statut reste NO-GO.

## 9. Pistes réalistes

Ces pistes découlent du code lu. Aucune n’est implémentée par cet audit.

1. **Décision avant la sonnerie, et le dire quand elle n’a pas eu lieu.** Le budget de 250 ms de #1767 est la bonne forme. L’écran diagnostic affiche « règles prêtes en N ms » ou « ce coup-ci a sonné, les règles n’étaient pas en mémoire ». Un bloqueur qui avoue son fail-open est plus crédible qu’un score.

2. **Un seul numéro, montré au moment du blocage.** Les tables outre-mer existent déjà (`CallRuleEngine`, décision ARCEP 2025-2215 dans le commentaire). Quand la personne bloque `0690…`, l’écran montre `+590…` et prévient que `336…` sans signe plus sera reconnu. Le marché Guadeloupe et métropole est exactement le cas où `06` et `0690` ne sont pas le même plan.

3. **Double SIM : demander la ligne, ne pas la deviner.** `Call.Details` ne porte pas la ligne. Si les deux SIM ne donnent pas la même identité, l’appel sonne et une action propose « ce préfixe, c’est cette ligne ». La politique de #1767 est le noyau. Le produit manquant est la mémoire de ce choix, locale, sans serveur.

4. **Inconnus en silence, avec les exceptions déjà écrites.** `UnknownCallerPolicy` connaît urgence, favori, contact, liste blanche et rappel. La brancher sur le moteur, après le fail-open d’urgence, donne la fonction que les composeurs opérateurs vendent, sans réputation distante. La réputation signée est déjà vidée en V1. La laisser vide.

5. **ARCEP nomme la tranche, pas la personne.** Le client le dit déjà. L’interface doit l’écrire en une phrase : « tranche publiée, pas l’identité après portabilité ». C’est le contraire d’un annuaire inversé, et c’est tenable devant Play.

6. **Écran verrouillé : type et territoire, numéro complet après déverrouillage.** Aujourd’hui le `CallStyle` est `VISIBILITY_PUBLIC` avec le numéro (`SentinelCallNotificationHelper.kt:64-66` et `:113`). Afficher « mobile Guadeloupe », « surtaxé » ou « international », et les derniers chiffres. Le plan de numérotation sait déjà étiqueter.

7. **L’appel reste répondable si la notification est refusée.** Le rôle Téléphone appartient à l’application. `showIncoming` qui retourne faux laisse l’utilisateur sans surface Sentinel. Ouvrir `SentinelInCallActivity` dans ce cas.

8. **Wangiri local, sur l’empreinte, sans numéro stocké.** La chronologie privée et le HMAC existent. Compter les sonneries courtes répétées sur l’empreinte, sur l’appareil, et proposer le silence du préfixe. Ne pas rallumer le worker de réputation réseau, qui est déjà un no-op en V1.

9. **Le téléphone V1 ne contient que le téléphone.** Retirer le classpath LiveKit, Auth0, WireGuard et Wear fait disparaître `MODIFY_AUDIO_SETTINGS` et la requête Auth0. C’est la décision produit la plus utile : un APK dont la fiche Data safety peut être relue dans le manifeste.

10. **Un certificat de laboratoire, un certificat de release.** Le digest `0d5e03ce…` est le seul archivé. Le cycle 6 dit qu’un autre poste en fabrique un autre. Sans cette hygiène, le S24 refuse la mise à jour et personne ne sait plus quel binaire a été tenu en main.
