# Sentinel Offline Mesh — rapport de régression Android phase 2

Date : 2026-10-24  
Branche : `feature/sentinel-offline-mesh-foundation-20261024`  
Base : `d7215a9eb683180c21252df223b17a214c9b9f34`

## Commandes exécutées

Dans `native-android-app` avec JDK 17 et le SDK Android local :

```text
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL`.

```text
git diff --check
```

Résultat : aucun diagnostic.

Le décompte des XML sous `app/build/test-results/testDebugUnitTest` est :

```text
tests=1389 failures=0 errors=0 skipped=0
Offline Mesh=72 failures=0 errors=0
```

Détail Offline Mesh : Crypto 9, SecureTransport 4, Transport 6, CCCD 3,
Frame 4, LinkGate 9, Outbox 8, PeerSessions 5, ProtocolIds 2, Reassembler 3,
SecurityBoundary 3, soit 72.

Artefact produit : `app/build/outputs/apk/debug/app-debug.apk`.

## Tests instrumentés

La compilation Kotlin des tests instrumentés a réussi via la commande ci-dessus.
`adb` n'est pas installé (`adb: command not found`) et aucun émulateur/appareil
n'est donc disponible dans cet environnement. Aucun test instrumenté n'a été
exécuté; `INSTRUMENTED_PASS` est donc non attribué.

## Analyse statique et API 28

La branche BLE conserve volontairement le code API 28 historique afin de ne pas
mélanger la correction APK. La correction dédiée est sur
`fix/selected-apk-inspector-api28-20261024`, commit `b4b6564a` : elle déplace la
lecture de `PackageInfo.signingInfo` dans une méthode annotée `@RequiresApi(28)`
appelée uniquement après le garde de version. Sur cette branche séparée,
`compileDebugKotlin` et `lintDebug` ont réussi.

## Limitations

Les essais radio réels, pertes de liaison, portée, notifications CCCD sur
matériel et mesures de performance restent à exécuter sur deux appareils.
Les avertissements Kotlin/Gradle préexistants ne sont pas traités par cette
correction Offline Mesh.
