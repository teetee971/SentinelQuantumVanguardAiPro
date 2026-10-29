# Sentinel D.1 — candidat à requalifier sur Galaxy S24+

> Rapport historique du premier candidat (sources 78653c57, 1366 tests). Pour la livraison actuelle et ses 1369 tests, lire [PHOTO_REFRESH_FOLLOWUP.md](PHOTO_REFRESH_FOLLOWUP.md). Le manifeste ARTIFACTS.json décrit désormais le nouveau candidat.

## Livraison et provenance

Branche `fix/d1-phone-messages-conformity-20261029`, base `92d1c916ebbdf393166fcf2fd904894ebee15661`.
Sources des APK : **78653c57108ad97b3a5cf16af0549da1accc4c88**. Un commit documentaire ultérieur peut porter les preuves sans modifier ces sources.

Application `com.sentinel.quantum`, version `1.0.9` / code `10`, minSdk24, targetSdk36, compileSdk37 ; JDK17, Gradle9.8.0. Les APK sont des builds **debug**, pas des versions de publication Google Play. Les configurations manifeste/build ne sont pas modifiées par ces correctifs.

`ARTIFACTS.json` donne chemins absolus, tailles et SHA-256. Livraison locale : `/workspace/d1-delivery/`. Les APK ne sont pas ajoutés à Git. Le paquet de livraison contient les rapports, journaux et les 296 XML JUnit de la dernière suite.

## Correctifs réalisés

- Téléphone : en-tête D.1 illustré réellement intégré ; saisie du numéro pleine largeur, actions flexibles, commandes d’appel existantes préservées.
- Contacts : avatar du carnet local borné et chargé hors thread UI ; fiche avec coordonnées, appel/message, édition déléguée à Contacts Android et partage explicite. Aucun nouveau stockage ni permission WRITE_CONTACTS. Les résultats photo sont associés au contact **et** à l’URI pour empêcher un ancien portrait de s’afficher après filtrage/réutilisation d’une ligne.
- Messages : en-tête compact intégré, état vide explicite et panneau de risque fondé sur l’analyse locale réelle. Aucun faux score d’identité, âge de domaine ou pays ajouté.
- Navigation/Bluetooth : commande de scan placée dans une barre réservée du Scaffold pour ne plus flotter sur les commandes du contenu.
- OSINT : trois URL canoniques directes remplacent les URL redirigées que la politique réseau refusait. La politique de refus des redirections est conservée.
- Voice : présentation claire des erreurs HTTP401/403/503 ; diagnostic read-only séparant processus vivant et service réellement prêt. Aucune activation fictive ni modification de secrets.

`evidence/source-integration.log` relève les callsites des composants partagés dans les véritables Activities ; `final-apk-resources.log` confirme les deux ressources dans l’APK. Cela prouve l’intégration aux sources et au paquet, pas leur rendu correct à l’exécution.

## Résultats exécutés

| Vérification | Résultat réel | Preuve |
|---|---|---|
| Build après 5a3309f1 | Réussi, sortie0, 1min37s ; 1364 tests unitaires, 0 échec/erreur/ignoré | `evidence/build-official-references.log` et `.exit` |
| Build après correction photo 78653c57 : compileDebugKotlin, suite unitaire complète, assembleDebug, assembleDebugAndroidTest | Réussi, sortie0, 1min09s | `evidence/build-final.log` et `.exit` |
| Suite unitaire finale | **1366 exécutés, 1366 réussis, 0 échec, 0 erreur, 0 ignoré**, 296 fichiers XML | `evidence/final-unit-summary.json` ; XML complets dans l’archive |
| Tests critiques de cette série | Endpoints OSINT2, présentation Voice3, politique photo5 inclus dans la suite complète | Classes OsintSourceEndpointTest, VoiceSessionFailurePresentationTest, ContactPhotoPolicyTest |
| Phase rouge photo | Compilation de test échouée (sortie1), ContactPhotoSnapshot pas encore implémenté ; aucun test exécuté à cette étape | `evidence/avatar-regression-red.log` ; ne pas compter comme 2 tests FAIL à l’exécution |
| Contrats Node | 19 réussis, 0 échec, sortie0 ; exécutés avant la correction photo, fichiers Node inchangés depuis | `evidence/node-contracts.log` et `.exit` |
| Backend Voice local | 23 réussis, sortie0 ; doubles Redis, pas un service distant | `evidence/voice-backend-tests.log` et `.exit` |
| APK debug + androidTest : apksigner | Vérification réussie pour chacun, sortie0, signature v2 | `evidence/final-*-signature.log` et `.exit` |
| APK debug + androidTest : zipalign -c -v 4 | Réussi pour chacun, sortie0 | `evidence/final-*-alignment.log` et `.exit` |
| Configuration/permissions du manifeste fusionné | Inventaire exécuté ; aucune nouvelle permission ou modification de manifeste/build par cette PR | `evidence/manifest-review.json`, `manifest-build-config-diff.log`, `final-apk-badging.log` |
| Schéma Room non suivi existant | Présent et inchangé, SHA a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4 | `evidence/final-room-schemas.sha256` |
| Instrumentation / captures | **NON EXÉCUTÉES**, démarrage émulateur incomplet | `evidence/runtime-results.json`, `runtime-driver.log`/`.exit` (2) |
| Galaxy S24+ du nouveau candidat | **NON TESTÉ**, aucun appareil physique accessible | `PHYSICAL_TESTS_AND_CAPTURE.md` |
| AAB release / bundletool | **NON EXÉCUTÉS pour ce candidat D.1** : livraison debug demandée, pas de qualification Play revendiquée | Aucun AAB D.1 annoncé |

Les 1366 tests sont le total de la dernière suite Android, pas une somme incluant plusieurs exécutions. Les 19 contrats Node et 23 tests backend sont des suites distinctes. La revue statique indépendante du correctif photo n’a trouvé aucun autre défaut important ; elle n’a exécuté ni Gradle ni test physique.

## Limite de l’émulateur et segmentation signalée

Le runtime AOSP Android17/API37 x86_64 sans `/dev/kvm`, en émulation logicielle, n’a pas atteint `sys.boot_completed=1` dans la fenêtre d’attente bornée. ADB online et présence progressive des services ne démontrent pas une UI prête. Les tentatives d’installation de la base ont échoué avant toute capture du candidat. Le collecteur runtime est sorti avec le code2.

La demande mentionne une erreur de segmentation : **aucun SIGSEGV, signal11 ou message de segmentation n’est retrouvé dans les journaux conservés de cette reprise**. L’arrêt explicitement demandé par `adb emu kill` a répondu OK, sortie0. On conserve donc le signalement utilisateur sans inventer un code139 ni lui attribuer une cause. La seule observation confirmée ici est un démarrage incomplet. Le fichier local `boot-state.png` de 62octets est incomplet et exclu des preuves visuelles. Aucun nouvel essai d’émulateur ne bloque la livraison des validations disponibles.

## Reproduction de la compilation

Avec les outils locaux conservés dans cet environnement :

```bash
export JAVA_HOME=/workspace/tooling/jdk17
export ANDROID_HOME=/workspace/tooling/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"
/workspace/tooling/gradle-9.8.0/bin/gradle -p native-android-app \
  :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest \
  -PsentinelSignatureFixture=/workspace/phone-core-v1-delivery/Sentinel-PhoneCore-V1-debug.apk \
  --no-daemon --console=plain --max-workers=2
```

La fixture de signature est l’ancien APK livré, pas une clé privée. Le lanceur exact de session `evidence/run-gradle.py` ajoute les options proxy/truststore de l’environnement sans enregistrer de secret. Sur une autre machine, adapter SDK/JDK/fixture disponibles et installer les plateformes/outils nécessaires ; ne pas supposer que les chemins `/workspace` y existent.

## Ce qui reste indispensable

Voir `VISUAL_REFERENCES.md`, `CONFORMITY_MATRIX.md`, `KNOWN_ISSUES.md` et `PHYSICAL_TESTS_AND_CAPTURE.md`. D.1 n’est pas déclaré terminé : aucune capture réelle du nouveau candidat ni comparaison côte à côte validée, aucun appel/SMS/MMS physique qualifié. Les tests unitaires ne prouvent ni absence de crash au lancement ni fidélité aux maquettes.
