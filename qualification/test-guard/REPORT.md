> Historical guard-stage report (de4b281d / b7af566). Current continuation isolates the seven reputation cases; only the Activity case retains the destructive rule. See ISOLATION.md. Full continuation qualification pending.

# Revue des tests destructifs — arbre vérifié

Base réellement accessible : fafe9f5b1c57b21f276ca62189acab625a490ec9.
Code corrigé/exécuté : de4b281dc9ec4bb214ac9dece4aacea2a5295602.
Branche : fix/disposable-instrumentation-guard, worktree /workspace/sentinel-test-guard.

## Différence avec le retour reçu

Le worktree Termux ~/sentinel-phone-core-release-20261102 n'est pas accessible depuis cet hôte. L'utilisateur ne connaît pas le SHA de la version commentée. Sur la base accessible : aucun des deux @Ignore n'existe, preferencesName/isolatedPrefs sont absents, le test de reprise ne contient qu'un @Test. Ses @Before et @After effacent les préférences de production. La classe réputation possède sept tests qui modifient paramètres de consentement et stockage réels.

D1 « couverture supprimée par @Ignore » n'est donc pas reproduit sur cet arbre. Le défaut vérifié ici est une exécution destructive possible des huit tests actifs sur un appareil utilisé. Aucun défaut READY du store de production n'a été démontré par cette vérification. Aucun code de production ni API de préférences n'a été reconstruit à partir d'un autre diff inconnu.

## Correctif

Deux règles DestructiveTestRule, une dans chaque classe, enveloppent toute l'exécution JUnit avant @Before et @After. Une hypothèse refusée produit SKIP. Une garde en première ligne de la méthode aurait été trop tard sur cet arbre : @Before écrit déjà en production.

La politique reconnaît les marqueurs émulateur proposés : fingerprint generic/emulator, hardware goldfish/ranchu, produit sdk_gphone, ou argument instrumentation exactement sentinelAllowDestructive=true. Aucun Intent applicatif ne sert à cet opt-in. C'est une heuristique de qualification, pas une certification qu'un appareil ne contient aucune donnée : utiliser un émulateur réellement jetable. Aucune utilisation de cet opt-in ni installation sur le S24+ n'a été exécutée.

La politique et la règle générique sont partagées entre JVM et AndroidTest via src/sharedTest/java ; elles ne font pas partie des sources main. Le raccord Build/InstrumentationRegistry existe uniquement dans androidTest/DestructiveTestGuard.kt. Les deux remises à zéro des préférences de l'assistant assertent désormais le succès de commit() dans le test.

Six tests hôte exécutent la politique et de vrais cycles JUnit : refus Samsung, marqueurs émulateur, opt-in exact ; refus avant tous les hooks avec émission d'une hypothèse de SKIP ; hooks réels sur autorisation ; erreur de garde en échec sans écriture. Le cas refusé interne a1 assumption failure,0 ignore,0 failure et ne crée aucun fichier before/body/after. Le cas autorisé crée bien les trois fichiers. Ce résultat hôte n'est pas une exécution instrumentée S24+.

Les contrats purement logiques existaient déjà pour READY/révocation, rôle indisponible, ordre des permissions et absence de boucle. Un seul cas manquant a été ajouté à PhoneCoreSetupWizardStoreTest : READ_CONTACTS optionnel ne bloque pas les permissions essentielles. Aucun doublon de test de store isolé n'a été inventé.

## Vérifications de production demandées

- isCompleted() et shouldOpenSetup() : définitions présentes, aucun appel de production observé par la recherche demandée. MainActivity relit PhoneCoreRuntimeFacts et utilise softwarePrerequisitesReady/shouldAutoOpenSetup ; markCompleted est conditionné aux faits.
- corePermissionsReady : CALL_PHONE et READ_PHONE_STATE seulement. READ_CONTACTS et POST_NOTIFICATIONS n'y figurent pas. Les notifications ont leur propre étape. Sur cette base firstMissingPermission ignore READ_CONTACTS, pas arbitrairement POST_NOTIFICATIONS.
- Même cible refusée : PhoneCoreActivationActivity propose Réessayer cette étape via clearAttempted/epoch et les paramètres Android. L'absence d'action explicite n'est pas reproduite.
- Correction factuelle : lifecycleState inconnu retombe sur le bit historique completed ; s'il vaut true, le résultat est COMPLETED, sinon NOT_STARTED. Ce n'est donc pas toujours NOT_STARTED. Les faits Android restent néanmoins nécessaires pour READY et pour garder l'assistant fermé. Aucun changement de migration sans contrat et preuve supplémentaires.
- commit() ignoré en production : confirmé par lecture, mais aucun échec disque n'a été injecté. Pas de modification de production sur cette hypothèse.
- markDeferred : lecture/écriture non verrouillée confirmée ; seul appelant observé, callback de résultat de MainActivity. Aucune concurrence démontrée.
- README de main lu au SHA935344423b2af2a4a57c58123f2c5c178b46ec03 : annonce développement actif ; ni Production Ready ni enregistrement d'appels non inclus. Écart documentaire allégué non reproduit, README inchangé. La présence d'un fixture recordings ne prouve pas à elle seule une fonctionnalité de production.

Les trois sorties demandées sont conservées : 01-production-usage.txt, 02-resume-test-base.txt (fichier entier, pas seulement les extraits), 03-reputation-test-base.txt. Elles sont liées à la base indiquée ci-dessus, pas à un worktree Termux supposé synchronisé.

## Exécution réelle

Linux x86_64, JDK17.0.21+9, Gradle9.8.0, SDK37/build-tools36.0.0. --offline --no-daemon --max-workers=1. Commandes dans COMMANDS.txt et preuves dans EXECUTION_PROOFS.zip.

| Vérification | Résultat |
|---|---|
| Tests de garde RED |6 tests,4 échecs reproduits avant correction|
| Ciblés garde + assistant |31 tests,0 échec/erreur/ignoré|
| Suite unitaire complète avec fixture APK signé v22 explicite |1434 tests,0 échec/erreur/ignoré|
| assembleDebugAndroidTest |Réussi : existants et ajouts compilés|
| lintDebug |Réussi :0 erreur,220 avertissements/19 hints conservés|
| Signature APK AndroidTest et alignement16KiB |Vérifiés|
| Tests instrumentés sur émulateur |NON EXÉCUTÉS|
| Tests instrumentés sur S24+ / SKIP Android réellement observé |NON EXÉCUTÉS|

Le résultat de SKIP est prouvé dans le cycle JUnit hôte contrôlé ; on ne le présente pas comme un SKIP exécuté sur le S24+. Les anciens résultats historiques ne remplacent pas cette qualification.

APK AndroidTest effectivement construit : native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk, SHA256 e52afebb41f9430cb3ac93c333f77512411893783c129237045a9a1ebefd83a9. Aucun APK de production nouvellement assemblé ni installé. La fixture de signature du test hôte est l'APK v22 déjà construit sous /workspace/qualification-evidence/fafe9f5b1c57b21f276ca62189acab625a490ec9/app-debug.apk ; elle sert à l'apksig, pas à une preuve de code ou d'appareil actuel.

## Procédure restante et préservation

Sur émulateur jetable uniquement, après installation autorisée des APK correspondants, exécuter les deux classes ciblées avec AndroidJUnitRunner. Vérifier tous les statuts, pas uniquement le code final. Sur appareil personnel, l'absence d'opt-in doit produire des assumptions SKIP avant les hooks ; cette observation physique reste à faire. Ne pas utiliser l'opt-in sur le S24+ stable et ne pas lancer une suite instrumentée complète : ce correctif ne qualifie pas les autres tests potentiellement sensibles.

Aucune installation/émission MMS/révocation de rôle/écriture de préférences de production sur un appareil, aucune modification main, secret ou keystore. Branches nettoyeur et MMS v23 préservées ; leurs anciens APK ne reçoivent pas rétroactivement ce correctif isolé. La garde doit être intégrée et les APK reconstruits avant de qualifier une autre branche.

Le commit final ajoute uniquement les preuves ; son arbre Android est identique à celui du code exécuté. Ces conclusions ne valident pas les fichiers non identifiés du retour initial.
