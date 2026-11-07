# Nettoyeur Android Sentinel — qualification hôte réussie

PR brouillon : https://github.com/teetee971/SentinelQuantumVanguardAiPro/pull/1756

Code effectivement exécuté et construit : `7f0db2921b0fb4405e18c421468668f1782787f7`.
Arbre Android : `3538eb0eb353ac6f50bb0432b62d265c015ec773`.
Branche `fix/android-cleaner-doctor`, worktree `/workspace/sentinel-cleaner`, base v22 exacte `fafe9f5b1c57b21f276ca62189acab625a490ec9`.

## Fonctionnement livré

Accueil → **Nettoyeur Sentinel**, ou Doctor → **Ouvrir le nettoyeur**. Scanner, examiner les copies proposées, sélectionner explicitement, confirmer la suppression définitive, consulter le rapport vérifié. Aucune sélection initiale et aucune suppression pendant le scan. Les actions de scan/nettoyage passent par Dispatchers.IO. Une session appartenant au ViewModel de l'activité conserve le travail et les résultats pendant rotation/navigation ; résultats publiés après chaque fichier, doubles soumissions refusées.

Seuls les fichiers immédiats des répertoires cache Sentinel `sentinel_log_export` et `sentinel_sms_export`, ainsi que les copies racine `sentinel_apk_inspection_*.apk`, âgés d'au moins24h, sont proposés. Pas de parcours récursif général. Le scan examine au plus2000 entrées (maximum configurable5000) et signale les limites/erreurs de couverture. L'énumération File.listFiles peut toutefois matérialiser la liste du répertoire avant cette limite ; ne pas la présenter comme une limite stricte d'allocation mémoire.

L'exécuteur revérifie indépendamment l'emplacement autorisé, l'absence de lien symbolique, l'âge et l'instantané taille/date avant suppression. Les MMS actifs/en récupération, conversations, bases, journaux originaux, fichiers récents/inconnus et données privées des autres applications sont exclus. Les copies modifiées sont refusées, les échecs restent visibles. Les fichiers déjà absents valent0 octet supprimé. Le rapport distingue suppressions, absences et résultats non résolus ; il mesure la taille logique des copies, pas un gain disque physique. Partage du texte uniquement sur action explicite, sans chemins absolus privés.

Liens vers diagnostic système, analyse des applications et stockage Android pour le reste de l'appareil. Aucune prétention d'antivirus complet, de suppression automatique de logiciels malveillants, de réparation du registre Windows ou de nettoyage des autres applications.

## Référence fournie

ZHPCleaner.zip : SHA-256 `1e7994f82239711c1d61dd3851ed0d8080cf7e2f64180f65c7453d5789b58eef`,2946984 octets. Une seule entrée régulière `ZHPCleaner.exe`,3418824 octets, PE x86(machine0x14c). Inspection statique en mémoire, aucun exécutable lancé, intégré ou copié dans le dépôt. L'archive n'a pas de source ni documentation des écrans ; nous ne revendiquons pas une reproduction fidèle de son UI ou moteur. L'authenticité Authenticode n'a pas été vérifiée. Inspiration limitée au parcours demandé, implémentation Android propre.

## Validation exécutée

Linux x86_64, JDK17.0.21+9, Gradle9.8.0, SDK37/build-tools36.0.0. Aucune nouvelle dépendance ni permission. Réutilisation des caches de compilation existants, qualification finale hors ligne :

```bash
source /workspace/toolchains/env.sh
bash qualification/cleaner/reproduce.sh 7f0db2921b0fb4405e18c421468668f1782787f7 /workspace/qualification-evidence/cleaner-7f0db292
```

Code retour0. Les commandes exactes figurent dans COMMANDS.txt, codes retour et décomptes dans VALIDATION_LEDGER.json, journaux/JUnit dans EXECUTION_PROOFS.zip. --offline --no-daemon --max-workers=1 ; tests forcés avec --rerun. Ce ne sont pas des résultats physiques Android.

| Contrôle | Exécution | Échecs | Ignorés |
|---|---:|---:|---:|
| Application, fixture APK signé construit |1446 tests|0|0|
| wearable-contract |2 tests|0|0|
| wearable-security |26 tests|0|0|
| Politique Android |7 tests|0|0|
| Contrats produit |69 tests|0|0|
| Hygiène identifiants |6 tests|0|0|
| Kotlin application |Réussi|—|—|
| Lint |0 erreur,220 avertissements,19 hints|—|—|
| APK debug + AndroidTest |Assemblés|—|—|
| Signatures v2, métadonnées, alignement16KiB |Réussis sur les deux APK|—|—|
| Deux nouveaux tests d'interface |Compilés uniquement ; NON EXÉCUTÉS|—|—|
| Tests physiques S24+/émulateur |NON EXÉCUTÉS, aucun appareil ADB|—|—|

Le premier passage sans fixture avait1 test de signature ignoré ; le passage final fournit l'APK réellement construit et n'ignore aucun test. Ne pas additionner les deux suites répétées.

19 nouveaux tests hôte couvrent notamment MMS protégés, fichiers récents/inconnus, symlinks, changement taille/date, âge, limite et erreurs de scan, rapports sans faux volumes, sélection obligatoire, progression avant suppression suivante, interruption, exception partielle et double soumission. Revue indépendante : deux problèmes Important reproduits(16 tests/4 échecs), corrigés puis revérifiés ; aucun problème Important restant signalé dans la relecture ciblée. La première tentative Lint a été interrompue volontairement après ces constats ; seule la qualification finale ci-dessus vaut résultat.

## Artefacts et préservation

APKs expérimentaux complets du package principal, **pas un laboratoire autonome**. Métadonnées héritées de v22 : versionCode22/versionName1.0.9, minSdk24,targetSdk36,compileSdk37. Aucun changement de version de release ni fusion de la v23 MMS bloquée. Ne pas installer ces APK sur le S24+ stable sans autorisation distincte. Aucun APK installé par Codex.

Répertoire de référence `/workspace/qualification-evidence/cleaner-7f0db292/` :

- `app.apk` : 97234166 octets ; SHA-256 `9d154746f470e98edfc72aeec6804306c38372b76cf8b5c2f5f3fec545f5bb71`.
- `test.apk` : 1137862 octets ; SHA-256 `95429ff0e49872d6d43727ca7399908883abf0b7bd501b29169fa86987de03f5`.

Sorties Gradle conservées : `native-android-app/app/build/outputs/apk/debug/app-debug.apk` et `native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

Signature debug cloud existante, certificat SHA-256 `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`, différente du signataire S24+. Keystore, manifeste/permissions, schémas et workflows inchangés. Worktrees initial et v23 propres/préservés (ISOLATION_CHECK.json). Aucun rôle changé, MMS émis, donnée applicative réinitialisée, secret modifié, fusion main ou release publiée.

## Validation Android restante

Utiliser un émulateur/appareil de test vierge, jamais installer ce package expérimental sur le S24+ stable sans accord. Installer app.apk puis test.apk sur ce seul environnement isolé et lancer :

```bash
adb -s TEST_DEVICE shell am instrument -w -r -e class com.sentinel.quantum.ui.SystemCleanerInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Ces deux cas ne confirment aucune suppression. Pour le parcours complet sur cet environnement vierge : préparer des exports temporaires factices ancien/récent et un faux PDU MMS ; vérifier que seul l'ancien apparaît ; annuler la confirmation et contrôler que rien ne change ; confirmer ensuite et vérifier suppression du seul fichier choisi, rapport/volume, conservation du récent et du PDU. Vérifier rotation et navigation pendant un lot et retour au rapport, ainsi que petits écrans/grandes polices. Ce parcours manuel n'a pas été exécuté ici.

Le S24+ revient à son état normal simplement en restant sur sa v22 déjà installée : rien n'y a été changé. Ne pas effacer ses données ni installer un downgrade.

## Limites explicites

Pas de quarantaine/restauration : suppression définitive de copies choisies, annoncée avant confirmation. Rapport en mémoire de la session d'activité, conservé lors des rotations/navigation mais pas après mort du processus ; partage explicite pour conserver une copie. Contrôle de fraîcheur taille/date, pas de hash intégral de contenu. Les limites d'Android empêchent le nettoyage silencieux des données des autres applications. Interface et comportement sur API37/Samsung à qualifier physiquement. Les avertissements Lint sont conservés, pas masqués.

Le commit de livraison ajoute seulement les preuves/documentation ; son arbre Android est identique à celui du code exécuté ci-dessus. Liste complète des fichiers modifiés : FILES_CHANGED.txt.
