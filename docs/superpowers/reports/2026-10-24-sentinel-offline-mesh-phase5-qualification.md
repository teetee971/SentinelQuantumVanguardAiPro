# Sentinel Offline Mesh — phase 5 qualification

Date : 2026-10-24  
Branche : `feature/sentinel-offline-mesh-foundation-20261024`  
Référence : `bd07a67a5c4a3acdee1d8993f6876148430d9222`

## Environnement

- JDK 17 : `/workspace/java17/bin/java`, version `17.0.21`.
- Gradle wrapper : `9.8.0`.
- Android SDK : `/workspace/android-sdk`, plateforme Android 37.
- ADB : `1.0.41`, disponible sous `/workspace/android-sdk/platform-tools/adb`.
- `adb devices -l` : aucun appareil attaché.
- Binaire `emulator` : absent.
- AVD : aucun détecté.
- Virtualisation : aucun flag `vmx`/`svm` exploitable et `/dev/kvm` absent.

Le cache Gradle contient `gradle-work-action-api:32.4.1`, mais pas
`com.android.tools.utp:gradle-work-action:32.4.1`. Aucun contournement ni faux
artefact n'a été créé. Dans cet environnement sans réseau, la dépendance ne
peut pas être résolue.

## Validation Android

Commande forcée fraîche :

```text
JAVA_HOME=/workspace/java17 \
PATH=/workspace/java17/bin:/workspace/android-sdk/platform-tools:$PATH \
ANDROID_SDK_ROOT=/workspace/android-sdk \
ANDROID_HOME=/workspace/android-sdk \
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  :app:compileDebugAndroidTestKotlin --rerun-tasks --no-daemon --console=plain
```

Résultat : `BUILD SUCCESSFUL`.

```text
tests=1403 failures=0 errors=0 skipped=0
Offline Mesh=86 failures=0 errors=0
```

APK debug et APK de tests instrumentés compilés. Empreintes produites dans
cette exécution :

```text
app-debug.apk              643c75df0fa5f5decae9dd473e6dd8d8cb96cd5be6e980702b4bf6f85d94938b
app-debug-androidTest.apk  adcb6d6c27813c963a0b72406670555d8dc12b911524ec74b53eb5fded0cf855
```

## Exécution instrumentée

La commande suivante a été lancée réellement :

```text
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

Elle a compilé et empaqueté les APK, puis a échoué avant exécution des tests
avec :

```text
Could not resolve com.android.tools.utp:gradle-work-action:32.4.1
Network is unreachable
```

En parallèle, ADB ne signalait aucun appareil et aucun AVD/émulateur n'est
disponible. Aucun test instrumenté Keystore ou GATT n'est déclaré réussi.

Procédure reproductible dans un environnement compatible :

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
adb start-server
adb devices -l
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

Un appareil doit être `device`, pas `unauthorized` ou `offline`; le SDK doit
contenir l'émulateur et l'accès Maven Google doit permettre de récupérer les
artefacts UTP.

## Keystore et GATT

Les tests Android Keystore ajoutés couvrent génération P-256, signature,
révocation, rotation, corruption, restauration anti-rollback et nettoyage.
Ils sont compilés mais non exécutés sur Android. Les tests JVM couvrent les
mêmes transitions avec pannes injectées et totalisent la régression Offline
Mesh de 86 tests.

Les scénarios GATT automatisables (CCCD, écritures, notifications,
fragmentation, réassemblage, ACK/retry, délais, sessions obsolètes et
révocation) sont des simulations contrôlées ou tests unitaires. Aucun échange
radio réel, test de portée, perte Bluetooth ou reconnexion physique n'a été
effectué.

## Lint et régression API 28

Sur la branche BLE, `:app:lintDebug` reste bloqué par les 3 erreurs API 28 de
`SelectedApkInspector.kt`, avec 217 warnings et 19 hints.

La branche séparée `fix/selected-apk-inspector-api28-20261024`, commit
`b4b6564a77130aa7e5c85fd6f9453dd7713c054e`, contient uniquement
`SelectedApkInspector.kt`. Validation fraîche sur cette branche :

```text
./gradlew :app:compileDebugKotlin :app:lintDebug --no-daemon --console=plain
BUILD SUCCESSFUL
```

Elle n'est pas fusionnée dans la branche BLE.

## Revue de code

Revue complète de la modification phase 4 :

- stockage versionné et MAC liée à la révision vérifiés ;
- suppression de l'ancienne clé avant publication vérifiée ;
- interruption avant publication vérifiée par tests injectés ;
- lecture invalide, clé absente et corruption verrouillent l'accès ;
- `resetStorage()` impose une nouvelle approbation ;
- aucun secret ou clé privée ajouté au dépôt ;
- aucune modification Phone Core/SMS/MMS/VPN ;
- aucune correction supplémentaire reproduite pendant la qualification phase 5.

Limite maintenue : un système Android compromis qui restaure simultanément les
fichiers privés et l'état complet du Keystore dépasse les garanties du magasin.

## Qualification Galaxy S24+

La procédure dédiée est dans
`docs/superpowers/reports/2026-10-24-sentinel-offline-mesh-s24-physical-procedure.md`.
Elle couvre installation, permissions BLE, approbation, échanges chiffrés,
ACK/retry, coupure, reconnexion, révocation, rotation et conservation des
journaux. Aucune configuration ADB sans fil ni résultat Galaxy S24+ n'a été
inventé.

## Verdict

- `CODE_COMPLETE` : oui.
- `UNIT_TESTS_PASS` : oui, 1 403/1 403.
- `INSTRUMENTED_PASS` : non, blocage UTP/appareil.
- `KEYSTORE_VALIDATED` : non, tests Android non exécutés.
- `CRYPTO_REVIEW_PASS` : non, aucune revue indépendante.
- `PHYSICAL_BLE_PASS` : non, aucun appareil réel.
- `READY` : non.

Verdict : `LIMITED / NOT_READY`.
