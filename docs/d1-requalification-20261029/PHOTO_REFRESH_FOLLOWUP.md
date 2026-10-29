# Livraison courante D.1 — photos modifiées et lectures annulées

## Provenance

Sources APK : **70d5cecd2aacd67487bd98591cbbcb4172225617**.
Branche : `fix/d1-phone-messages-conformity-20261029` ; PR brouillon #1748, base `work/phone-core-v1-20261029`.
L’audit précédent porte sur `c3226314` ; son ZIP est une photographie historique et ne contient pas ce correctif. La nouvelle livraison est séparée : `/workspace/d1-delivery-photo-refresh/`. Les SHA et tailles exactes des deux nouveaux APK sont dans `ARTIFACTS.json`. Un commit documentaire ultérieur ne change pas leurs sources.

## Correction appliquée après audit

1. Le fournisseur Contacts est lu avec `CONTACT_LAST_UPDATED_TIMESTAMP`, transmis comme `photoRevision` optionnelle. Pas de stockage, migration, synchronisation ni permission ajoutés.
2. L’identité d’une photo comprend désormais contactId, URI, révision et génération de reprise d’Activity. La génération change à ON_RESUME et est réellement propagée à la liste et à la fiche, pour relire les octets même si l’URI et la révision ne changent pas après édition.
3. `rememberContactPhoto` détient le véritable état Compose utilisé par `D1ContactAvatar`. Il redémarre la lecture quand cette identité change et rejette immédiatement un résultat d’une autre identité, avant même le démarrage de la nouvelle lecture.
4. La frontière de lecture `loadContactPhoto` vérifie `ensureActive()` après le chargement et avant publication. Une ancienne lecture non coopérative ne peut plus remplacer la photo courante, même si elle finit après la nouvelle ou renvoie null.
5. Les limites de lecture (2MiB), décodage, fournisseur local, traitement hors thread UI et contrôle de permission sont préservées.

Une relecture indépendante a d’abord trouvé la course d’annulation ; une régression JVM l’a reproduite avant correction. Après correction, la seconde revue statique n’a trouvé aucun autre défaut important. Cette revue ne constitue pas un test sur appareil.

## Vérifications réellement exécutées

| Vérification | Résultat | Preuve relative à ce dossier |
|---|---|---|
| Première phase rouge : nouveaux contrats de révision non implémentés | Échec compilation, sortie1 ; aucun test exécuté | `evidence/photo-refresh/photo-revision-red.log` et `.exit` |
| Tests ciblés politique photo + compilation Kotlin des tests instrumentés | Réussis, sortie0, 55s ; 7 tests photo dans cette étape | `evidence/photo-refresh/photo-revision-targeted.log` et `.exit` |
| Régression avant correctif d’annulation | **1 test exécuté, 1 échec attendu**, sortie1 : le résultat courant était perdu | `evidence/photo-refresh/photo-cancellation-red.log`, `.exit`, `.xml` |
| Suite finale app complète + compileDebugKotlin + assembleDebug + assembleDebugAndroidTest | **Réussies, sortie0, 1min02s** | `evidence/photo-refresh/photo-refresh-full.log` et `.exit` |
| Résultats unitaires finaux | **1369 exécutés, 1369 réussis, 0 échec, 0 erreur, 0 ignoré**, 297 suites XML | `evidence/photo-refresh/photo-refresh-unit-summary.json` ; XML complets dans la livraison |
| Tests photos inclus dans la suite finale | Politique/identité7 + annulation1 = **8 réussis** | Classes ContactPhotoPolicyTest et ContactPhotoLoadTest |
| Signature des deux APK | apksigner réussi, sortie0, signature v2 ; même certificat debug que la livraison précédente | `evidence/photo-refresh/photo-refresh-*-signature.log` et `.exit` |
| Alignement des deux APK | zipalign -c -v 4 réussi, sorties0 | `evidence/photo-refresh/photo-refresh-*-alignment.log` et `.exit` |
| Intégration du code dans l’APK | Classes ContactPhotoIdentity, ContactPhotoLoadKt et ContactPhotoStateKt présentes dans le DEX | `evidence/photo-refresh/apk-photo-code.json` |
| Schéma Room local non suivi | Inchangé : a7c5b4e1926c4441629734c0703ba6174e6bcf3796394b51302846bac0dae0c4 | `evidence/photo-refresh/photo-refresh-room.sha256` |
| Manifeste et configuration build | Aucun changement par ce correctif ; applicationId com.sentinel.quantum, version1.0.9/code10, min24/target36/compile37 | `evidence/photo-refresh/photo-refresh-manifest-config.log`, `photo-refresh-apk-badging.log` |
| Appareils connectés | Aucun, commande ADB exécutée ; ce constat n’est pas un test de l’application | `evidence/photo-refresh/photo-refresh-adb-devices.log` |

La phase rouge et la suite finale sont distinctes : l’échec avant correction n’est pas un échec restant de la dernière suite. Les 1369 tests ne sont pas une somme de plusieurs exécutions. Les contrats Node19 et backend23 du rapport initial ne sont pas relancés : leurs fichiers sont inchangés ; aucun nouveau résultat distant Voice n’est revendiqué.

Commandes Gradle : lanceur de session `/workspace/d1-evidence/run-gradle.py`, même JDK17/Gradle9.8.0/SDK que le rapport initial, fixture APK de signature existante. Tâches finales :

```text
:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
-PsentinelSignatureFixture=/workspace/phone-core-v1-delivery/Sentinel-PhoneCore-V1-debug.apk
--no-daemon --console=plain --max-workers=2
```

## NON EXÉCUTÉ / NON TESTÉ

- **Les 3 nouveaux tests Compose sont compilés, NON EXÉCUTÉS** : aucun runtime Android utilisable. Ils couvrent changement de révision à URI identique, génération de reprise sans révision et réponse tardive de l’ancien contact. La lecture du fournisseur est différée dans le test ; le véritable producteur d’état de l’avatar est utilisé. Ils ne montent pas la fiche complète et ne prouvent pas le comportement du fournisseur Samsung.
- **Galaxy S24+ : NON TESTÉ** pour ce candidat, y compris édition de photo, navigation, permissions/rôles, appels simples/simultanés, notifications et SMS/MMS. Voir `PHYSICAL_TESTS_AND_CAPTURE.md` pour commandes exactes et conservation des données.
- **Comparaison visuelle D.1 : NON EXÉCUTÉE.** Références officielles reçues dans la conversation, indexées dans `VISUAL_REFERENCES.md`. Aucune image générée ne remplace une capture du candidat.
- **Voice PTT** : dernier constat conservé live200/ready503 ; pas de nouvelle sonde ni réparation de configuration dans cette itération. Appel SIM transformé toujours non pris en charge.
- **AAB/release/Google Play : NON EXÉCUTÉS pour ce candidat debug.** Aucune publication ni dépense. Les limites fonctionnelles avancées et les exigences Play restent celles de `KNOWN_ISSUES.md`.

## Installation de qualification, après examen

```bash
sha256sum -c SHA256SUMS.txt
adb install -r Sentinel-D1-debug-70d5cecd.apk
adb install -r Sentinel-D1-androidTest-70d5cecd.apk
adb shell am instrument -w -e class com.sentinel.quantum.ui.design.ContactPhotoStateInstrumentationTest com.sentinel.quantum.test/androidx.test.runner.AndroidJUnitRunner
```

Utiliser un profil de test autorisé. Ne pas désinstaller ni effacer les données pour contourner une incompatibilité de certificat. La procédure physique comporte la modification d’une photo et le retour dans Sentinel, le filtrage rapide entre contacts et la révocation/réattribution de READ_CONTACTS. Le téléphone Samsung reste sélectionnable dans Paramètres → Applications → Applications par défaut → Téléphone, hors appel.
