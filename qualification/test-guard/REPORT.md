# Revue des tests — garde Activity et isolation réelle du stockage réputation

Code exécuté : `2add1102b76cd920f71e15887e7d25a088382f1b`.
Branche `fix/disposable-instrumentation-guard`, base v22 `fafe9f5b1c57b21f276ca62189acab625a490ec9`.
PR : https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1757

## Résultat de la continuation

Les sept cas de réputation utilisent maintenant des préférences isolées par test : paramètres, cache et secret HMAC. Le helper test-only IsolatedPreferencesContext renvoie lui-même pour applicationContext ; les appels imbriqués de SettingsStore et de l'adaptateur ne retrouvent donc pas les préférences par défaut. Seuls les trois magasins connus en MODE_PRIVATE sont acceptés, avec préfixe UUID ; nom inattendu refusé ; fermeture vérifiée et limitée aux alias ouverts, réutilisation interdite.

Les sept méthodes @Test originales ont été déplacées **sans modification de leur corps**, dans CommunicationReputationStorageCases. Elles sont héritées par AndroidJUnit4 et par un runner hôte Robolectric. CASE_MOVE_CHECK.json enregistre l'empreinte de ces corps. La garde globale de cette classe est retirée : ces tests ne dépendent plus d'un effacement des paramètres ou du cache de production. Ils ne lisent, n'initialisent ni ne restaurent les préférences réelles. Le test d'Activity conserve sa règle avant @Before, car lui efface l'historique de l'assistant et ouvre les écrans Android réels.

Les sources main, manifeste, permissions, mécanismes de stockage et API de production restent strictement inchangés. Aucun preferencesName ajouté au store et aucune option par Intent. Robolectric4.16.1 est uniquement testImplementation ; contrôle DEX confirmé : il n'est pas embarqué dans l'APK AndroidTest. Le wrapper de préférences n'est pas présenté comme une sandbox pour des API Android arbitraires : les chemins actuels SettingsStore/purge/HMAC ont été inspectés et testés.

## Preuves exécutées

Linux x86_64, JDK17.0.21+9, Gradle9.8.0, SDK37/build-tools36.0.0. Robolectric exécute les API Android35 sur l'hôte ; ce n'est pas Samsung/API37. Dépendances et JAR runtime pré-cachés, commandes finales --offline --no-daemon --max-workers=1, tests forcés --rerun.

| Vérification | Résultat réel |
|---|---|
| Isolation RED |5 tests,5 échecs avec ContextWrapper transférant les préférences|
| Ciblés isolation + stockage réel + garde + logique assistant |43 tests,0 échec/erreur/ignoré|
| Suite applicative complète |1446 tests,0 échec/erreur/ignoré|
| Politique Android / contrats produit / hygiène identifiants |7 +69 +6 tests réussis|
| assembleDebugAndroidTest |Réussi : cas et helpers compilés|
| lintDebug |0 erreur,220 avertissements/19 hints conservés|
| Signature v2 et alignement16KiB AndroidTest |Vérifiés|
| Classes DEX attendues / absence de Robolectric dans cet APK |Vérifiées|
| Tests instrumentés sur émulateur ou S24+ |NON EXÉCUTÉS|

Les cinq cas d'isolation vérifient applicationContext, les vrais setters de consentement/purge et l'adaptateur HMAC non modifiés, l'indépendance des contextes, les noms inattendus et la fermeture. Des sentinelles de « production » sont initialisées **uniquement dans la sandbox Robolectric hôte**, pour vérifier que ces cartes restent identiques avant/après. Aucun tel ensemencement n'est présent dans AndroidTest. Les sept cas métier réels tournent sur l'hôte avec ces mêmes sources partagées. La simulation JUnit précédente vérifie aussi qu'un refus de la garde émet une hypothèse de SKIP avant @Before/corps/@After ; aucun SKIP réellement observé sur S24+ n'est revendiqué.

Revue indépendante en lecture seule : aucun problème concret Critical/Important trouvé dans l'isolation, les purges imbriquées, le secret et le retrait de la garde réputation. Le reviewer n'a exécuté aucun test ni accédé à un appareil ; les résultats ci-dessus sont ceux exécutés par l'implémenteur.

Commandes exactes : COMMANDS.txt. Ledger : VALIDATION_LEDGER.json. Journaux/JUnit des étapes : EXECUTION_PROOFS.zip. La fixture du test de signature est l'APK v22 déjà construit, explicitement choisi ; elle vérifie l'apksig, pas un nouveau build de production ou une livraison sur appareil.

## Blocage Android précis

Aucun appareil ADB connecté, aucun émulateur ou system image installé, aucun /dev/kvm disponible dans cet environnement. Les tests instrumentés n'ont donc pas été lancés. Aucun APK installé, aucune utilisation de sentinelAllowDestructive=true, aucun changement de rôle ou de données du téléphone. La v22 stable reste intacte.

Sur un environnement Android de test autorisé et compatible avec les signatures, les sept cas isolés peuvent être ciblés avec :

```bash
adb -s TEST_DEVICE shell am instrument -w -r -e class com.sentinel.quantum.security.CommunicationReputationStorageInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Le test PhoneCoreSetupResumeInstrumentationTest reste réservé à un émulateur réellement jetable ou à l'opt-in instrumentation explicitement décidé par l'utilisateur. Ne pas installer le package expérimental sur le S24+ stable ni lancer toute la suite instrumentée : ce travail ne qualifie pas les autres tests sensibles du dépôt. Aucun résultat physique n'est déduit de la compilation.

## Conclusions de la revue initiale, liées à l'arbre vérifié

Le retour reçu faisait référence à deux @Ignore, isolatedPrefs et preferencesName absents de la base accessible. L'utilisateur ne connaît pas le SHA correspondant ; le worktree Termux n'est pas accessible ici. Nous avons corrigé les tests réellement présents, sans reconstruire une autre version de production.

- Aucun appel de production observé d'isCompleted/shouldOpenSetup comme preuve de READY ; les appelants vérifiés relisent les faits Android.
- corePermissionsReady utilise CALL_PHONE et READ_PHONE_STATE ; pas READ_CONTACTS ni POST_NOTIFICATIONS. Les contrats logiques préexistants sont conservés ; seul le cas READ_CONTACTS manquant a été ajouté.
- La même cible refusée possède les actions Réessayer cette étape et Paramètres Android.
- Correction factuelle : un cycle de vie inconnu peut retomber sur l'ancien bit completed=true et donc COMPLETED, pas toujours NOT_STARTED. Les garde-fous runtime restent nécessaires ; aucun faux READY démontré ni migration modifiée.
- commit() ignoré en production : confirmé en lecture, échec disque non injecté, aucune correction spéculative. markDeferred non verrouillé : seul appelant observé dans le callback MainActivity, concurrence non démontrée.
- README main au SHA935344423b2af2a4a57c58123f2c5c178b46ec03 : développement actif ; pas les deux annonces documentaires alléguées. README inchangé.

Les trois sorties demandées restent conservées comme snapshots de la base : 01-production-usage.txt, 02-resume-test-base.txt, 03-reputation-test-base.txt. Historique de la première phase garde : https://github.com/teetee971/SentinelQuantumVanguardAiPro/blob/b7af566975482f9b67065713c0c3cfe744892096/qualification/test-guard/REPORT.md

## Préservation et artefact

Pas de nouvelle compilation APK principale, de merge main, de release ou de changement de clé. Les worktrees initial, nettoyeur et MMS v23 restent propres et à leurs SHA précédents. Leur ancien APK ne reçoit pas automatiquement ce correctif ; intégration/reconstruction nécessaires. L'APK AndroidTest courant est dans /workspace/qualification-evidence/test-guard-2add1102/androidTest.apk ; empreinte exacte dans VALIDATION_LEDGER.json. Il cible le package principal et ne constitue pas un laboratoire autonome autorisé à installer sur le S24+.

La continuation fournit aussi un diagnostic Termux en lecture seule (DIAGNOSTIC.md), vérifié par neuf tests hôte. Les ajouts restent hors sources Android ; cet arbre reste identique au code exécuté 2add1102. Validation hôte réussie ; validation Android instrumentée toujours NON EXÉCUTÉE.


## Installed legacy APK identified; test-only preparation

The user diagnostic now confirms exact legacy/unguarded test APK db7215b5…, with tests_executed=false. TEST_ONLY_DELIVERY.md documents a test-only replacement ZIP, local signing gates and eight executed host checks. The kit includes no main APK and never installs/runs tests. Main exact hash and local signing remain pending on the device. Android sources are unchanged.
