# Résultats de qualification réellement exécutés

Source Android compilée : **846ed793b1234e1d87ec21a218e50dba6c52d43d**. Journaux bruts conservés dans `logs/`. `COMMAND_RESULTS.json` donne l’argv, le code de sortie et le fichier journal de chaque commande du qualificateur. Les validations antérieures de d03dfb07 ont été relues et leurs empreintes vérifiées avant travaux ; elles ne sont pas présentées comme des essais physiques.

| Suite / vérification | Exécutés | Réussis | Échecs/erreurs | Ignorés | Preuve |
|---|---:|---:|---:|---:|---|
| App Kotlin/JUnit complète | **1 356** | **1 356** | **0 / 0** | **0** | UNIT_TEST_SUMMARY.json, JUNIT_RESULTS.zip, logs/gradle-build.log ; exit 0 |
| Phone Core JVM ciblée | 68 | 68 | 0 | 0 | logs/phone-core-jvm.log ; exit 0 |
| Vérité produit Android Node | 69 | 69 | 0 | 0 | logs/android-product-truth.log ; exit 0 |
| Politiques release + AAB Node | 10 | 10 | 0 | 0 | logs/release-policy.log ; exit 0 |
| Provenance des entrées de build Python | 6 | 6 | 0 | 0 | logs/provenance-tests.log ; exit 0 |
| Filtrage des journaux Python | 4 | 4 | 0 | 0 | logs/redaction-tests.log ; exit 0 |
| Instrumentation Android | 0 | — | — | — | **NON EXÉCUTÉE** : pas de runtime Android connecté ni émulateur/KVM disponible |
| Scénarios S24+ S01–S14 | 0 | — | — | — | **NON TESTÉS** : aucun appareil physique |

Les **68 tests JVM ciblés sont un sous-ensemble de la suite app** : ne pas additionner ces nombres comme des tests uniques. Les 69 et 10 tests Node sont des contrôles hôte de contrats, pas des appels Android. Les dix tests Python valident les outils de provenance/filtrage, pas le téléphone.

La suite complète utilise comme fixture cryptographique un APK debug signé conservé de la livraison d03dfb07 ; elle teste son acceptation et le rejet d’une copie corrompue. La signature du **nouvel APK** est ensuite vérifiée séparément par apksigner. Aucun test de signature n’est ignoré.

## Build et artefacts

- `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `bundleReleaseUnsigned` : **BUILD SUCCESSFUL en 6 min 38 s**, code 0.
- `apksigner verify --verbose --print-certs` : code 0 pour APK app et test APK ; certificats Android Debug, signature v2 vérifiée.
- `zipalign -c -P 16 -v 4` : code 0 pour les deux APK.
- `aapt2 dump badging` : code 0, applicationId/version/min24/target36 vérifiés dans l’artefact.
- `bundletool validate` : code 0 ; configuration PAGE_ALIGNMENT_16K. Les 14 bibliothèques ELF 64 bits sont alignées au moins sur 16 Kio et identiques entre APK et AAB (`logs/native-16k.json`). Aucun test sur système à pages 16 Kio n’est déclaré exécuté.
- `jarsigner -verify` : code 0 avec résultat explicite **jar is unsigned** attendu ; ce n’est pas une validation de signature release.
- Manifeste source : code 0, 24 permissions effectives ; fusionné release : code 0, **30** permissions, composants exportés listés, caméra/projection et service de capture absents.
- Contrôle de provenance après qualification : code 0, toutes les entrées Android correspondent au commit compilé ; seuls les outils et documents évoluent ensuite (`logs/source-provenance.log`). Schéma Room conservé et empreinte identique à celle de la livraison précédente.

## Résultats négatifs conservés

- NotificationPermissionRaceGuard et contrôle de provenance : essais rouges attendus avant implémentation ; compilateur/import non résolu, codes non nuls conservés dans les fichiers `.exit` correspondants. Ce ne sont pas des échecs du résultat final.
- Manifeste initial : le nouveau contrôle échouait avant retrait des deux permissions inutiles (`release-manifest-before.exit=1`), puis réussit sur le manifeste reconstruit.
- Collecteur physique : code **2**, état **NON EXÉCUTÉE**, aucun appareil unique autorisé (`logs/physical-preflight.*`). Ne pas transformer ce code en test FAIL d’un téléphone absent ni en PASS.
- Stockage GitHub : upload code **1**, HTTP 401. Le brouillon existe mais ne contient aucun artefact ; voir `ARTIFACT_STORAGE.md`. Ce blocage d’authentification n’affecte pas les binaires locaux.

## Contrôles non exécutés et raisons

- Installation, lancement sans crash, attribuer/reprendre le rôle, appels opérateur/audio/DTMF/double appel, notifications réellement vues et révocations sur Android : **NON EXÉCUTÉS**, appareil absent.
- Tests instrumentés, y compris contrat manifeste installé et notification synthétique : **NON EXÉCUTÉS**, aucun runtime ; APK compilé seulement.
- AAB signé release et validation d’une clé propriétaire : **NON EXÉCUTÉS**, clé d’upload non disponible/autorisée ici.
- Upload Play/test interne/fermé/production et validation par Google : **NON EXÉCUTÉS**, compte non connecté, informations propriétaire manquantes et aucune autorisation de publication.

Aucun journal de test physique ni résultat PASS de scénario S24+ n’est fabriqué.
