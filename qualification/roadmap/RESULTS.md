# Finalisation vérifiée de la roadmap — 7 novembre 2026

**Livraison technique, pas publication Google Play.** La v22 du S24+ n'a pas été installée/remplacée, aucun rôle ou stockage du téléphone modifié. Aucune fusion main, release officielle ou CI payante déclenchée. Aucun test physique exécuté.

## Ce qui est terminé ici

- Doctor intégré dans la candidate isolée depuis le code revu `7f0db292`, sans importer la compression MMS JPEG défectueuse.
- Scan en flux : plus de `listFiles().toList()` matérialisant tous les fichiers avant la limite. Limite de visites, fermeture des dossiers, erreurs partielles explicites ; Android7 ne propose pas le scan borné, Android8+ utilise DirectoryStream.
- Nettoyage limité aux copies reconnues de Sentinel datant d'au moins24h, sélection/confirmation, revalidation âge/taille/date, progression et rapport par fichier. Conversations, bases, fichiers MMS et autres applications exclus.
- Laboratoire séparé : application `com.sentinel.quantum.labpreview`, tests `com.sentinel.quantum.labpreview.test` ciblant `.labpreview`, label `Sentinel Lab Preview`. Composants et permissions privilégiés Phone/SMS retirés. Assistant automatique désactivé seulement dans ce build ; bytecode de la méthode = `return-void`. Production conserve le flag `true` et le parcours normal.
- Registre canonique passé à18 capacités : Doctor déclaré implémenté/configuré, mais runtime/physique/signature/disponibilité publique restent faux. Quatre critères Doctor distincts des14 Phone Core. Vues générées et roadmap réconciliées, aucune offre payante activée.
- Procédure physique isolée et dossier opérateur/Play remis : [PHYSICAL_LAB_RUNBOOK.md](PHYSICAL_LAB_RUNBOOK.md), [OWNER_HANDOFF.md](OWNER_HANDOFF.md). Ces documents ne remplacent pas leurs preuves d'exécution.

## Source et environnement

Parent : `8169dc50a33376ed157513667c1686b194def765` (candidate Play précédente).

Code exécuté : `b3c420bbe29c7207853ce4896e3431dea6c6921b` ; arbre Android `f720a25df5cb375a726d5795644a291c7cec509d`. Les commits de preuves ultérieurs ne changent pas cet arbre. Branche `fix/roadmap-finalization`, revue isolée au-dessus de `qualification/google-play-readiness` ; pas main.

Linux cloud x86_64, JDK17.0.21+9, Gradle9.8.0, AGP9.4.1, Kotlin2.4.20, SDK37/Build Tools36.0.0, cible36/minimum24. Candidate principale 24/1.0.10 ; laboratoire 24/1.0.10-lab-preview. Le maximum versionCode déjà utilisé dans Play reste inconnu : 24 est provisoire.

Les intermédiaires générés de ce seul worktree ont été déplacés dans un répertoire temporaire `/dev/shm/sentinel-roadmap-build-*` et reliés depuis `app/build/intermediates` pour contourner la limite disque. Sources, SDK, autres worktrees et keystores n'ont pas été déplacés. Cette optimisation locale n'est pas nécessaire dans un environnement disposant de plus d'espace ; les artefacts finaux restent dans `/workspace`.

## Résultats finaux

| Contrôle | Résultat |
|---|---|
| Application JVM complète | **1 471 tests, 0 échec, 0 erreur, 0 ignoré** |
| Dont tests ciblés Doctor | **22**, inclus dans1471 : session, rapport, sécurité et lecture en flux |
| Wearable JVM | 26/0/0/0 ; résultats réutilisés, tâche finale UP-TO-DATE, XML présents |
| Node : capacités, contrat public, release, laboratoire | 44 tests, 0 échec/ignoré |
| Cohérence produit Android | 69 tests, 0 échec/ignoré |
| Gate Python existante | 2 tests, 0 échec/ignoré ; readiness Play reste false |
| Construction principale | APK debug et AAB releaseUnsigned construits, Kotlin/R8 réussis |
| Laboratoire | APK et AndroidTest construits ; signatures et cibles d'instrumentation vérifiées |
| Lint releaseUnsigned complet | 0 erreur, **221 warnings, 19 hints** |
| Lint labPreview complet | 0 erreur, **221 warnings, 19 hints**, y compris analyse AndroidTest |
| Manifeste principal fusionné | Contrat Phone Core validé ;30 permissions ; source inchangée24 permissions |
| Bundletool1.18.3 | validate/config/manifest réussis ; package/version/cible et absence AD_ID contrôlés |
| Natif/packaging |14 ELF64 bits vérifiés, PT_LOAD alignés/congruents16Ko, octets APK/AAB identiques ; config bundle PAGE_ALIGNMENT_16K |
| Signatures APK | Trois APK vérifiés, certificat debug cloud existant ; zipalign -P16 réussi |
| Signature AAB | **Non signé**, confirmé jarsigner ; interdit de dépôt Play |
| Registre/catalogue/isolation/secrets/actions | Contrôles réussis ;18 capacités, aucune nouvelle disponibilité client/vente |
| AndroidTest sur appareil, UI réelle, opérateur, distribution Play | **NON EXÉCUTÉS** ; ADB cloud sans appareil |

Les deux tests UI du nettoyeur sont compilés, pas déclarés PASS sur téléphone. Les tests JVM ne prouvent pas les14 critères Phone Core, un MMS livré, une UI qualifiée ni l'acceptation Google. Les warnings ne sont pas supprimés : notamment les améliorations KTX, ressources inutilisées et compatibilité API. L'inventaire résolu contient187 composants, sans changement de dépendances production ; il ne vaut pas audit de collecte/licences/vulnérabilités ni SBOM de release signé.

## Commandes principales exactes

Depuis `/workspace/sentinel-play-ready`, après `source /workspace/toolchains/env.sh` :

```bash
# Rouge : tests importés avant le code ; compilation refusée sur classes absentes.
gradle -p native-android-app :app:testDebugUnitTest --tests '*SentinelCleanerSessionTest' --tests '*SentinelCleanupReportTest' --tests '*SentinelOwnedCleanupSafetyTest' --offline --no-daemon --max-workers=1 --console=plain
# Même commande après intégration :19 tests réussis, puis22 après les correctifs supplémentaires.

gradle -p native-android-app :app:testDebugUnitTest --tests '*SentinelOwnedCleanupSafetyTest.scannerNeverMaterializesAnEntireDirectoryListing' --offline --no-daemon --max-workers=1 --console=plain
# Rouge observé avant correction de lecture en flux.

gradle -p native-android-app help -PsentinelInstrumentationBuildType=release --dry-run --offline --no-daemon --max-workers=1 --console=plain
# Avant la whitelist : accepté à tort ; après : refus attendu avant tâches.

gradle -p native-android-app :app:testDebugUnitTest :wearable-security:test :app:assembleDebug :app:bundleReleaseUnsigned :app:lintReleaseUnsigned :app:exportReleaseDependencyInventory -PsentinelSignatureFixture=/workspace/qualification-evidence/test-guard-2add1102/androidTest.apk -PsentinelRobolectricSdkDir=/workspace/toolchains/robolectric-sdks --offline --no-daemon --max-workers=1 --console=plain
# BUILD SUCCESSFUL, source final b3c420bb.

gradle -p native-android-app :app:assembleLabPreview :app:assembleLabPreviewAndroidTest :app:lintLabPreview -PsentinelInstrumentationBuildType=labPreview --offline --no-daemon --max-workers=1 --console=plain
# Première tentative :6 erreurs hardware ; après correction :réussite.
# Relance finale après protection de l'assistant :réussite.

node --test scripts/check-product-capabilities.test.js scripts/generate-public-product-truth.test.js scripts/android-release-policy.test.js scripts/android-aab-artifact-policy.test.js scripts/public-product-contract.test.js scripts/android-lab-preview-policy.test.js
npm run test:android-product-truth
node scripts/check-product-capabilities.js
node scripts/render-product-capabilities.js
node scripts/generate-public-product-truth.js
node scripts/generate-public-product-truth.js --check
node scripts/check-commercial-catalog.js
node scripts/check-android-manifest.js
node scripts/check-sentinel-isolation.js
node scripts/check-github-actions-pinning.js
node scripts/check-credential-hygiene.js
python3 -m unittest tests.test_play_release_evidence
```

Les commandes d'inspection ont utilisé les fichiers de sortie avant archivage :

```bash
python3 scripts/verify-phone-core-v1-release.py native-android-app/app/build/intermediates/merged_manifests/releaseUnsigned/processReleaseUnsignedManifest/AndroidManifest.xml
# Pour chacun des trois APK :
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs APK
$ANDROID_HOME/build-tools/36.0.0/zipalign -c -P 16 -v 4 APK
$JAVA_HOME/bin/jarsigner -verify native-android-app/app/build/outputs/bundle/releaseUnsigned/app-releaseUnsigned.aab
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging native-android-app/app/build/outputs/apk/labPreview/app-labPreview.apk
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump xmltree native-android-app/app/build/outputs/apk/androidTest/labPreview/app-labPreview-androidTest.apk --file AndroidManifest.xml
apkanalyzer dex code --class com.sentinel.quantum.BuildConfig native-android-app/app/build/outputs/apk/labPreview/app-labPreview.apk
apkanalyzer dex code --class com.sentinel.quantum.MainActivity --method 'maybeOpenPhoneCoreFirstRunSetup()V' native-android-app/app/build/outputs/apk/labPreview/app-labPreview.apk
adb devices -l
```

Bundletool a été exécuté avec `java -cp` sur les JAR Maven déjà résolus et `com.android.tools.build.bundletool.BundleToolMain` : commandes exactes/arguments dans `/workspace/qualification-evidence/roadmap-finalization/bundletool-commands.json`. Contrôle ELF exécuté :

```bash
python3 qualification/google-play/verify-bundle-native.py native-android-app/app/build/outputs/bundle/releaseUnsigned/app-releaseUnsigned.aab native-android-app/app/build/outputs/apk/debug/app-debug.apk /workspace/qualification-evidence/roadmap-finalization/bundle-config.log /workspace/qualification-evidence/roadmap-finalization/bundle-manifest.log /workspace/qualification-evidence/roadmap-finalization/native-16k.json
```

Les sorties brutes restent dans le dossier externe ; copies versionnées sous `logs/` avec seuls les espaces finaux normalisés. JUnit complet : archive externe référencée et hashée dans [JUNIT_ARCHIVE.json](JUNIT_ARCHIVE.json). Résumés : [TEST_SUMMARY.json](TEST_SUMMARY.json), inventaire et rapports lint XML dans ce dossier.

## Anomalies rencontrées et traitées

1. Manque d'intégration : nouvelles classes absentes, démontré par la compilation rouge des tests. Import sélectif, préservation des ressources du compte et des tests isolés.
2. Mémoire du scan : limite2000 appliquée après matérialisation du dossier. Test rouge puis lecture DirectoryStream, arrêt/fermeture et trois cas supplémentaires ; échecs I/O/plateforme non disponible explicitement partiels. Android7 ne prétend pas supporter ce nouveau scan.
3. Instrumentation : nouveau paramètre devait refuser release et dérivés. Whitelist stricte debug/labPreview ; debug reste compatible CI et n'est **pas** une identité isolée. Les instructions téléphoniques utilisent uniquement labPreview explicite.
4. Dépendances de preview : `initWith(debug)` n'hérite pas des configurations de dépendances app. Hôte Compose de test ajouté uniquement à labPreview.
5. Lint preview :6 erreurs hardware, corrigées par une déclaration téléphonie optionnelle, sans ignorer de règle ni réintroduire les permissions retirées.
6. Assistant de preview : malgré le retrait des composants, MainActivity proposait encore automatiquement la configuration Phone/SMS. Flag false uniquement en preview, retour avant lecture/écriture du wizard ; tests de contrat rouge/vert et bytecode final contrôlés. Aucun READY artificiel.
7. Snapshot public : génération nécessaire après ajout de la capacité ; générateur exécuté et check réussi, aucune vente activée.
8. Archivage : un ancien APK debug de la candidate3d5753ee, conservé par lien matériel, a été réécrit par Gradle. Incident cloud sans impact téléphone. Artefact reconstruit depuis son commit original dans un worktree séparé, **SHA-256 identique** à l'initial (`1316a4f0…0273`), archive restaurée. AAB et test APK précédents vérifiés inchangés ; preuve [OLD_ARTIFACT_RECOVERY.json](OLD_ARTIFACT_RECOVERY.json). Désormais artefacts déplacés après validation vers leur archive, sans lien aux futures sorties Gradle.

Reconstruction de récupération réellement exécutée :

```bash
gradle -p /workspace/sentinel-play-recovery/native-android-app :app:assembleDebug --offline --no-daemon --max-workers=1 --console=plain
```

## Artefacts effectivement construits

Répertoire final : **/workspace/qualification-evidence/roadmap-finalization/**. Les sorties ont été déplacées après validation ; les chemins originaux Gradle sont enregistrés dans [ARTIFACTS.json](ARTIFACTS.json), mais les téléchargements/hashes utilisent les archives finales. Aucun APK/AAB nouveau n'est committé.

| Artefact | SHA-256 |
|---|---|
| Sentinel-Doctor-24-DEBUG-DO-NOT-INSTALL.apk | `946ce4a15b2e3e95f25233f3569aada4b834c9d830abc1ed101eaed64c6fccd2` |
| Sentinel-Doctor-24-LABPREVIEW.apk | `8912cdaf2c027759b2e011546ec538c4034d30a5c13004f99881e0b2fc77f2b1` |
| Sentinel-Doctor-24-LABPREVIEW-androidTest.apk | `77be9cace1657e6d9b425bf95ec72c7d98fde7977e1597ef9dfe41d60419d09f` |
| Sentinel-Doctor-24-UNSIGNED-NOT-FOR-UPLOAD.aab | `caa92f2a8852df812248077a2882b3e7815f4a1a20728ffcebd4e4555cdfbf3a` |

Certificat debug cloud existant : `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`. Pas le signataire de la v22 S24+ ni une clé d'upload Play. Le package principal ne doit pas être installé sur la v22 sans accord distinct ; le laboratoire lui-même n'a pas été installé ici.

## Ce qui reste ouvert

- **Logiciel JPEG MMS** : le défaut de troncature récupérée silencieusement par le décodeur Android reste non corrigé sur sa branche. Test en échec conservé, pas de fusion ni d'annonce de compression automatique dans cette candidate. Un contrôle des seuls marqueurs n'est pas une solution stricte.
- **Physique** : quatre critères Doctor, ergonomie/rotation et14 critères Phone Core sur l'artefact futur exact. Aucun ancien MMS livré ou test v22 n'est réattribué ici.
- **Google Play / propriétaire** : version maximale Console, clé/certificat d'upload, service opérationnel de suppression de compte/données, identité/contact/rétentions et page publique, déclarations et accès de revue, distribution interne/fermée et acceptation Google. AAB non signé interdit de dépôt.
- **Autres axes** : VPN et appels transformés nécessitent infrastructures réelles ; réputation communautaire, sources GeoIntel supplémentaires et internationalisation complète restent conditionnelles selon le registre, sans promotion artificielle.

La finalisation locale ne ferme pas ces jalons. Les risques résiduels comprennent les warnings lint, absence d'exécution UI/physique/OEM, rapport Doctor conservé en mémoire seulement et suppression définitive sans restauration. La v22 reste en place : aucun rollback ni effacement nécessaire. Un downgrade ultérieur peut être refusé par Android ; ne jamais désinstaller/vider les données pour le contourner.
