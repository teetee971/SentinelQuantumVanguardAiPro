# Commandes et sorties de qualification

Les fichiers `logs/` sont les sorties brutes capturées (stdout + stderr), sans remplacement des erreurs par PASS. Les codes retour ci-dessous proviennent des exécutions. Tous les chemins relatifs de commande supposent le répertoire `native-android-app/recording-vault-r1-test`, sauf précision.

## JVM indépendante d'Android

Environnement : Kotlin CLI 2.2.21 téléchargé dans `/workspace/tooling/kotlinc`, OpenJDK runtime 21.0.12.1. Le script affiche les versions et compile les **vrais** `RecordingVaultStore.kt` et `RecordingVaultCrypto.kt` avec les adaptateurs hôte isolés.

```bash
set -o pipefail
KOTLINC=/workspace/tooling/kotlinc/bin/kotlinc qualification/run-host-tests.sh \
  2>&1 | tee qualification/logs/host-final.log
```

Code 0, 12 scénarios ; ce ne sont pas des tests Android.
Rejeu sur le code d'origine (sans toucher aux sources courantes) :

```bash
mkdir -p /workspace/tooling/vault-baseline
for file in RecordingVaultCrypto.kt RecordingVaultStore.kt; do
  git show "7b4b84363feac5c691dd9861bd044be7ca34acfc:native-android-app/recording-vault-r1-test/src/main/java/com/sentinel/quantum/security/$file" \
    > "/workspace/tooling/vault-baseline/$file"
done
set -o pipefail
VAULT_SOURCE_DIR=/workspace/tooling/vault-baseline \
  KOTLINC=/workspace/tooling/kotlinc/bin/kotlinc qualification/run-host-tests.sh \
  2>&1 | tee qualification/logs/host-baseline-expanded.log
```

Code 1 attendu : 8 scénarios réussissent, 4 échouent. Deux démontrent les défauts de publication et création de clé en lecture ; deux exigent la nouvelle coordination/verrou sans lien. `host-baseline.log` est la première reproduction historique, avant ajout des scénarios du verrou et de caractérisation : 7 réussis, 2 échecs. `host-corrected.log` est la première vérification après correctif : 9 réussis. Les tests ordinaires de course ont réussi aussi sur la base : ne pas les présenter comme une reproduction déterministe d'écrasement multiprocessus.

```bash
set -o pipefail
python3 qualification/test-device-runner.py \
  2>&1 | tee qualification/logs/device-runner-host-tests.log
bash -n qualification/run-device.sh qualification/run-host-tests.sh
git diff --check
```

Codes 0 avant staging. Après staging, `git diff --cached --check` signale uniquement des espaces/lignes vides dans les sorties brutes Gradle/ADB ; ils sont volontairement conservés. Le contrôle excluant `qualification/logs/**` réussit, voir `logs/scope.txt`. Le premier exécute 4 tests négatifs du script avec ADB simulé ; les deux autres sont des contrôles statiques, pas une instrumentation.

## Compilation Android isolée

Le runtime initial ne contenait ni SDK, ni ADB, ni `javac`. Outils installés uniquement sous `/workspace/tooling/`, sans changement de versions Gradle du dépôt. JDK de compilation : Temurin 21.0.13 ; SDK `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`. Le proxy et le truststore sont ceux de l'environnement, sans désactivation de TLS.

Commande finale réellement exécutée :

```bash
set -o pipefail
ANDROID_HOME=/workspace/tooling/android-sdk \
JAVA_HOME=/workspace/tooling/jdk21 \
JAVA_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ../gradlew -p qualification/android-build \
  -Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 \
  -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 \
  -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts \
  :recording-vault-r1-test:testDebugUnitTest \
  :recording-vault-r1-test:assembleDebug \
  :recording-vault-r1-test:assembleDebugAndroidTest \
  :recording-vault-r1-test:lintDebug --console=plain \
  2>&1 | tee qualification/logs/android-final.log
```

Code 0 ; `testDebugUnitTest NO-SOURCE`. Copier ensuite le rapport lint texte dans `logs/lint-final.txt` et calculer :

```bash
sha256sum build/outputs/apk/debug/recording-vault-r1-test-debug.apk \
  build/outputs/apk/androidTest/debug/recording-vault-r1-test-debug-androidTest.apk \
  | tee qualification/logs/apk-sha256.txt
```

Sur une machine sans ce proxy, ne pas reprendre ses paramètres : configurer normalement JDK/SDK. L'environnement isolé `qualification/android-build` utilise le module existant et le même AGP, sans configurer l'application principale.

### Tentatives antérieures conservées

| Journal | Code | Résultat |
|---|---:|---|
| `gradle-baseline.log` | 1 | wrapper sans proxy Java : Connection refused |
| `gradle-proxy.log` | 1 | wrapper obtenu ; build parent impossible avec checkout sparse sans wearable-contract |
| `android-isolated-baseline.log` | 1 | configuration isolée ; SDK/licences manquants |
| `android-build.log` | 1 | runtime sans compilateur Java |
| `android-build-jdk.log` | 1 | JDK externe sans truststore du proxy |
| `android-build-trust.log` | 1 | APK compilés ; lint 40 erreurs NewApi au minSdk24 |
| `lint-before.txt` | — | détail des 40 erreurs et 2 avertissements |

Les deux premiers lancements étaient depuis `native-android-app`, avec `bash ./gradlew` et les trois tâches `:recording-vault-r1-test:testDebugUnitTest :recording-vault-r1-test:assembleDebug :recording-vault-r1-test:assembleDebugAndroidTest --console=plain`. Le deuxième ajoutait `JAVA_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080'`. Les essais isolés utilisent la commande finale ci-dessus, avec installation progressive SDK/JDK/truststore ; `android-isolated-baseline.log` n'incluait pas lint.

## Instrumentation connectée

```bash
/workspace/tooling/android-sdk/platform-tools/adb devices -l \
  2>&1 | tee qualification/logs/adb-devices.log
```

Code 0 mais liste vide : **aucun test réussi ne peut être déduit**.
La commande Gradle finale a aussi été lancée avec la tâche unique `:recording-vault-r1-test:connectedDebugAndroidTest` à la place des quatre tâches, journal `logs/android-connected.log` : code 1, `DeviceException: No connected devices!`. Aucun test Android exécuté. Ce journal précède l'ajout d'un message diagnostique RACE dans le test ; les sources finales ont ensuite été recompilées et les empreintes actualisées, sans appareil supplémentaire.

Le protocole physique `run-device.sh` n'a pas été lancé sur appareil. Voir `GALAXY-S24-VALIDATION.md` pour les commandes Termux, l'association, la conservation de signature et les critères d'acceptation.
