# Résultats exécutés — candidate Google Play 24 / 1.0.10

**Publication BLOQUÉE.** Validation technique locale réussie, pas d'accord Google ni de validation physique. Voir [gates externes](../../docs/GOOGLE_PLAY_QUALIFICATION_20261107.md).

## Provenance et périmètre

- Source Android : commit `3d5753ee3effaa713baadb07abecdf3d8f5aecc1`, tree `native-android-app` = `c7567f174acd8881bc5b84d1b9d1bbdb08d174ff`.
- Branche `qualification/google-play-readiness`, parent `6a90af97a22f862ad5524b557a34c97615fa5d96` (tests isolés), base applicative v22 `fafe9f5b1c57b21f276ca62189acab625a490ec9`.
- Environnement cloud Linux x86_64, JDK 17.0.21+9, Gradle 9.8.0, SDK 37, Build Tools 36.0.0, AGP 9.4.1, Kotlin 2.4.20. Configuration shell : `source /workspace/toolchains/env.sh`. Dépendances épinglées inchangées.
- Les commits ultérieurs de livraison ne changent pas l'arbre Android. AGP indique `NO_SUPPORTED_VCS_FOUND` dans son métadonné VCS de worktree : la liaison au SHA est externe dans ce dossier, pas une attestation signée intégrée au bundle.
- Aucune installation, envoi réel de MMS, modification de rôle, effacement de données, fusion main ou déclenchement de CI payante. Aucun keystore de production lu/modifié ; clé debug existante inchangée.

## Résultats finaux

| Contrôle | Résultat exécuté |
|---|---|
| Application JVM | 1 449 tests, 0 échec, 0 erreur, 0 ignoré |
| Wearable JVM | 26 tests, 0 échec, 0 erreur, 0 ignoré ; tâche FROM-CACHE, résultats XML présents |
| Nouveaux tests compte | 3, inclus dans les 1 449 ; validation URL, schémas, credentials, fragment vide, contrôles encodés |
| Cohérence produit Android | 69 tests, 0 échec/ignoré |
| Politique release APK/AAB | 10 tests, 0 échec/ignoré |
| Contrat produit public | 11 tests, 0 échec/ignoré |
| Gate Python existante | 2 tests, 0 échec/ignoré ; ne prétend pas valider Play |
| Compilation Kotlin / APK debug / AndroidTest | Réussite ; APK conservés et signés avec clé debug cloud |
| AAB releaseUnsigned | Construction réussie après correction des contraintes d'environnement ; non signé, interdit de dépôt |
| Lint release complet | 0 erreur, 220 warnings, 19 hints ; aucun warning supprimé pour obtenir ce résultat |
| Manifeste | Source 24 permissions ; fusionné 30 ; composants sensibles protégés, pas CAMERA/media-projection, sauvegarde désactivée, HTTP clair désactivé ; bundle non débogable, pas AD_ID |
| Natif 16 Ko | 14 ELF 64 bits, alignements et congruence PT_LOAD contrôlés ; mêmes octets natifs dans APK debug/AAB ; config bundle PAGE_ALIGNMENT_16K |
| APK | apksigner vérifie les deux APK ; zipalign `-P 16` vérifie les deux |
| Bundletool 1.18.3 | `validate`, `dump config`, `dump manifest` exécutés, code 0 |
| Signature AAB | jarsigner confirme `jar is unsigned` ; aucune signature d'upload prétendue |
| Supply chain CI | 4 actions non épinglées détectées puis épinglées aux commits des mêmes tags majeurs ; contrôle final réussi |
| Instrumentation / appareil / MMS / installation Play | **NON EXÉCUTÉS**, liste ADB vide, aucun émulateur/KVM disponible |

Le lint restant comprend notamment UseKtx (83), UnusedResources (62), InlinedApi (24), AutoboxingStateCreation (19). Les avertissements restent dans le XML complet. Ils n'ont pas été transformés en preuve de compatibilité runtime ; une montée de dépendances arbitraire n'a pas été effectuée.

Les XML JUnit finaux et leur archive restent à `/workspace/qualification-evidence/play-20261107/JUNIT_RESULTS.zip`. Résumé versionné : [TEST_SUMMARY.json](TEST_SUMMARY.json). Inventaire résolu : [release-dependencies.json](release-dependencies.json), 187 composants ; cet inventaire n'est pas à lui seul une analyse complète de collecte des SDK ni un SBOM de release signé.

## Commandes exactes

Sauf mention contraire, exécution depuis `/workspace/sentinel-play-ready/native-android-app` après `source /workspace/toolchains/env.sh`. Chaque sortie complète est dans `logs/` (espaces de fin de ligne normalisés pour Git ; sorties brutes intactes dans le dossier externe de preuves).

```bash
# Rouge attendu : classe absente, avant implémentation (compilation des tests refusée).
gradle :app:testDebugUnitTest --tests '*AccountLifecyclePolicyTest' --offline --no-daemon --max-workers=1 --console=plain
# Même commande après implémentation : réussite, 3 tests.

# Première validation complète : tests et APK réussis, tâche compose mapping refusée hors ligne.
gradle :app:testDebugUnitTest :wearable-security:test :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleReleaseUnsigned :app:lintReleaseUnsigned :app:exportReleaseDependencyInventory -PsentinelSignatureFixture=/workspace/qualification-evidence/test-guard-2add1102/androidTest.apk -PsentinelRobolectricSdkDir=/workspace/toolchains/robolectric-sdks --offline --no-daemon --max-workers=1 --console=plain

# Récupération de compose-group-mapping:2.4.20 : build avancé, puis manque d'espace.
gradle :app:bundleReleaseUnsigned :app:lintReleaseUnsigned :app:exportReleaseDependencyInventory --no-daemon --max-workers=1 --console=plain
# Après libération d'espace : même validation en mode offline, réussie.
gradle :app:bundleReleaseUnsigned :app:lintReleaseUnsigned :app:exportReleaseDependencyInventory --offline --no-daemon --max-workers=1 --console=plain

# Relance finale complète JVM : réussite après libération de l'espace temporaire.
gradle :app:testDebugUnitTest :wearable-security:test -PsentinelSignatureFixture=/workspace/qualification-evidence/test-guard-2add1102/androidTest.apk -PsentinelRobolectricSdkDir=/workspace/toolchains/robolectric-sdks --offline --no-daemon --max-workers=1 --console=plain
# Reconstruction finale APK : réussite, hashes identiques à la première construction.
gradle :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=1 --console=plain

# Régression découverte en revue, sans lecture d'une clé et sans exécution de tâches :
gradle :app:bundleRelease --dry-run '-PsentinelAccountDeletionUrl=https://example.org/delete#' --offline --no-daemon --max-workers=1 --console=plain
# Avant : passe à tort le contrôle URL, échoue sur signature manquante.
# Après : refus attendu sur URL de suppression invalide, avant signature.
```

Depuis `/workspace/sentinel-play-ready` :

```bash
node --test scripts/android-release-policy.test.js scripts/android-aab-artifact-policy.test.js
npm run test:android-product-truth
node --test scripts/public-product-contract.test.js
python3 -m unittest tests.test_play_release_evidence
node scripts/check-android-manifest.js
node scripts/check-github-actions-pinning.js
node scripts/check-credential-hygiene.js
node scripts/check-sentinel-isolation.js
python3 scripts/verify-phone-core-v1-release.py native-android-app/app/build/intermediates/merged_manifests/releaseUnsigned/processReleaseUnsignedManifest/AndroidManifest.xml
python3 scripts/play_release_evidence.py --repo /workspace/sentinel-play-ready
# Dernière commande : exit 2 attendu, release_ready=false, jamais une approbation.

$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs native-android-app/app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ANDROID_HOME/build-tools/36.0.0/zipalign -c -P 16 -v 4 native-android-app/app/build/outputs/apk/debug/app-debug.apk
$ANDROID_HOME/build-tools/36.0.0/zipalign -c -P 16 -v 4 native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ANDROID_HOME/build-tools/36.0.0/aapt2 dump badging native-android-app/app/build/outputs/apk/debug/app-debug.apk
$JAVA_HOME/bin/jarsigner -verify native-android-app/app/build/outputs/bundle/releaseUnsigned/app-releaseUnsigned.aab
adb devices -l
```

Bundletool utilise les JAR déjà résolus dans le cache (classpath enregistré dans le dossier externe), pas un téléchargement non vérifié de binaire autonome :

```bash
SENTINEL_BUNDLE_CP=$(python3 -c 'from pathlib import Path; print(":".join(map(str,Path("/workspace/toolchains/gradle-home/caches/modules-2/files-2.1").rglob("*.jar"))))')
SENTINEL_BUNDLE=/workspace/sentinel-play-ready/native-android-app/app/build/outputs/bundle/releaseUnsigned/app-releaseUnsigned.aab
java -cp "$SENTINEL_BUNDLE_CP" com.android.tools.build.bundletool.BundleToolMain validate --bundle="$SENTINEL_BUNDLE"
java -cp "$SENTINEL_BUNDLE_CP" com.android.tools.build.bundletool.BundleToolMain dump config --bundle="$SENTINEL_BUNDLE" > /workspace/qualification-evidence/play-20261107/bundle-config.log
java -cp "$SENTINEL_BUNDLE_CP" com.android.tools.build.bundletool.BundleToolMain dump manifest --bundle="$SENTINEL_BUNDLE" --module=base > /workspace/qualification-evidence/play-20261107/bundle-manifest.log
python3 qualification/google-play/verify-bundle-native.py "$SENTINEL_BUNDLE" native-android-app/app/build/outputs/apk/debug/app-debug.apk /workspace/qualification-evidence/play-20261107/bundle-config.log /workspace/qualification-evidence/play-20261107/bundle-manifest.log /workspace/qualification-evidence/play-20261107/native-16k.json
```

## Échecs conservés et correction

- Avant implémentation, tests compte non compilables car classe inexistante ; après implémentation, 3 tests passent.
- Ancien test release exigeait encore 1.0.9 : mis à jour intentionnellement vers la candidate 1.0.10/code 24, sans retirer le contrôle monotone.
- Première fiche réécrite : gate refusant les disclosures techniques manquantes, puis ajout des contrats de permissions explicites hors copie commerciale ; documentation de baseline alignée ; 69 tests repassent.
- Revue : URL avec fragment ou user-info vide passait le contrôle Groovy. Comparaison explicite à null et cas JVM ajoutés ; garde Gradle rouge/verte exécutée.
- Hors ligne : dépendance Compose mapping absente. Récupérée sans modifier les versions ni désactiver le mapping ; construction finale offline réussie.
- Saturation disque : création de métadonnées bundle et sonde JDK refusée, puis 35 tests échouent faute de fichiers temporaires. Caches reproductibles et intermédiaires de cette candidate nettoyés, artefacts conservés, relance complète 1 449/0 réussie. Aucun code de production modifié pour masquer ces échecs.
- Une tentative de relance Gradle depuis la racine plutôt que le module échoue ; relance depuis `native-android-app` réussie (`release-final-retry-native.log`).
- Les anciennes sorties en échec restent présentes. Les résultats finaux sont `release-final-retry-native.log`, `unit-final-retry.log`, `apk-final.log`.

## Artefacts

Dossier exact : `/workspace/qualification-evidence/play-20261107/`. Les APK/AAB ne sont pas committés. [ARTIFACTS.json](ARTIFACTS.json) contient taille, SHA-256 et provenance ; [SHA256SUMS.txt](SHA256SUMS.txt) est vérifiable dans ce dossier.

| Fichier | SHA-256 |
|---|---|
| Sentinel-24-DEBUG-DO-NOT-INSTALL.apk | `1316a4f0414d1b99ae48682cf972658e940d86f74fd78e38b4b79d3930ef0273` |
| Sentinel-24-androidTest-NOT-RUN.apk | `baa4f3406e46344e876dfe0463f368ae43d17e7863062756efaa1627b79fd96a` |
| Sentinel-24-UNSIGNED-NOT-FOR-UPLOAD.aab | `188cffa618f16cd8949c6ce8923c189300c200b873eeb28fe1b434a7d69e3e3b` |

Signataire debug cloud : `fb90f8db77aff2e470ecbd9162b7308b550fdffdf9cd7c9c9daaa37c3812d7b6`. Il n'est pas présenté comme signataire de la v22 du S24+ ni comme certificat d'upload Play.

## Clôture

Les critères techniques ci-dessus sont vérifiables. Les gates externes restent ouverts et interdisent de déclarer l'APK « OK Google Play ». Ni le compte développeur, ni ses formulaires, ni la clé d'upload, ni le service de suppression n'ont été inventés. La v22 reste préservée ; aucun downgrade/retour arrière n'est nécessaire.
