# Sentinel Offline Mesh — qualification phase 4

Date : 2026-10-24  
Branche : `feature/sentinel-offline-mesh-foundation-20261024`  
Base : `b35636b5c0863fa16449b65a6a2ab0a7139ee1ce`

## Correction anti-rollback

Le magasin de confiance utilise désormais une clé HMAC Android Keystore
différente pour chaque révision. L'enveloppe contient la révision de clé, la
charge sérialisée et sa MAC. La publication suit cette séquence : création de
la clé suivante, calcul de la MAC, suppression de l'ancienne clé, écriture
flushée dans un fichier temporaire, puis `android.system.Os.rename`, qui
remplace le fichier cible atomiquement sur le système de fichiers applicatif.

Une interruption après suppression de l'ancienne clé et avant le `rename`
rend l'ancien fichier illisible. Le magasin retourne `ERROR` ou lève une
`StorageException`; aucune approbation n'est reconstruite depuis cet état.
Une réinitialisation explicite via `resetStorage()` supprime le fichier et les
aliases de clés de confiance; toutes les relations doivent alors être
réapprouvées.

Cette protection ne prétend pas résister à un système Android compromis qui
restaurerait simultanément les fichiers privés et l'état complet du Keystore.

## Pannes injectées couvertes

Les tests unitaires couvrent : échec de création de clé, panne après suppression
de l'ancienne clé, échec de publication après cette suppression, restauration
d'une ancienne copie authentique après révocation, MAC/clé absente, corruption,
écritures concurrentes et persistance logique après recréation du magasin.

## Résultats exécutés

Commande complète :

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL`.

```text
tests=1403 failures=0 errors=0 skipped=0
Offline Mesh=86 failures=0 errors=0
```

Les artefacts APK debug et tests instrumentés ont été compilés. `git diff
--check` est propre.

## Tests instrumentés

`./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain` a été
réellement lancé avec ADB disponible via `/workspace/android-sdk/platform-tools`.
`adb devices -l` ne listait aucun appareil. La tâche a ensuite échoué avant
l'exécution des tests car Gradle ne pouvait pas résoudre
`com.android.tools.utp:gradle-work-action:32.4.1` sans accès réseau
(`Network is unreachable`). Aucun test instrumenté n'est donc déclaré réussi.

## Environnement Android

- JDK de validation : `/workspace/java17`.
- SDK : `/workspace/android-sdk`.
- ADB : présent, mais aucun appareil attaché.
- Émulateur/AVD : binaire `emulator` et AVD non disponibles.
- Qualification Keystore réelle, StrongBox et redémarrage de processus : non
  exécutés sur Android.

## Lint et branche APK

Sur la branche BLE, `:app:lintDebug` échoue avec 3 erreurs API 28 historiques
dans `SelectedApkInspector.kt`, 217 warnings et 19 hints. Le correctif isolé
`fix/selected-apk-inspector-api28-20261024` (`b4b6564a`) modifie uniquement ce
fichier et a précédemment passé `compileDebugKotlin` et `lintDebug`. Il n'est
pas fusionné dans la branche BLE.

## Qualification GATT et limites physiques

Les tests unitaires et simulations contrôlées couvrent sessions, CCCD,
fragmentation, ACK/retry, callbacks obsolètes, délais et révocation d'un canal
établi. Aucun échange radio, test de portée, perte Bluetooth ou reconnexion sur
deux appareils n'a été observé.

La procédure Galaxy S24+ reste dans
`docs/superpowers/reports/2026-10-24-sentinel-offline-mesh-s24-physical-procedure.md`.

## Verdict

- `CODE_COMPLETE` : attribué après compilation et régression réussies.
- `UNIT_TESTS_PASS` : attribué, 1 403/1 403 tests sans échec.
- `INSTRUMENTED_PASS` : non attribué.
- `KEYSTORE_VALIDATED` : non attribué, exécution Android indisponible.
- `CRYPTO_REVIEW_PASS` : non attribué, aucune revue indépendante.
- `PHYSICAL_BLE_PASS` : non attribué.
- `READY` : non attribué.

Verdict global : `LIMITED / NOT_READY`.
