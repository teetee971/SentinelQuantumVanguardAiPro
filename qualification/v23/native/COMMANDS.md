# Commandes de qualification exécutées

Environnement et limites : REPORT.md. CWD `/workspace/sentinel-mms-jpeg-native/native-android-app`, sauf mention. Aucune commande d’installation ou d’émission MMS n’a été exécutée. Les tableaux distinguent les tests partagés, les assertions et les builds.

Préparation shell utilisée :

```bash
source /workspace/toolchains/env.sh
common=(--offline --no-daemon --no-build-cache --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process -I /dev/shm/sentinel-native/ram-build.gradle -PsentinelHostCmake=/dev/shm/sentinel-native/cmake-sdk/bin/cmake -PsentinelRobolectricSdkDir=/dev/shm/sentinel-native/sdks)
```

`ram-build.gradle` déplace les buildDirectory de chaque module vers `/dev/shm/sentinel-native/gradle-build/MODULE`, fixe java.io.tmpdir dans la RAM ; la bibliothèque JNI effective est fournie par le task buildHostValidator et son java.library.path. Les chemins sont ceux réellement employés dans ce cloud, pas des chemins supposés exister sur le téléphone.

## Régression d’origine, avant correction

CWD `/workspace/sentinel-mms-v23/native-android-app`, HEAD708b7a20 propre :

```bash
gradle --offline --no-daemon --max-workers=1 -I /dev/shm/sentinel-native/ram-build.gradle -PsentinelRobolectricSdkDir=/workspace/toolchains/robolectric-sdks :mms-compression-lab:testDebugUnitTest --tests '*MmsOutgoingJpegCompressorRobolectricTest.truncatedEntropyWithAnAppendedEndMarkerIsRejected'
```

Exit1 ;1test exécuté,1échec,0ignoré. Le premier essai ayant échoué dans l’infrastructure temporaire n’est pas présenté comme preuve du défaut ; ce passage possède son JUnit dédié.

## Annulation : rouge puis vert

```bash
gradle "${common[@]}" :app:testDebugUnitTest --tests '*MmsOutgoingSenderTest.interruption*'
```

Avant correctif : exit1,3tests/3échecs/0ignoré, archivés. Après correctif : passage ciblé `--tests '*MmsOutgoingSenderTest*'` réussi, puis tous les tests correspondants inclus dans la suite finale ci-dessous ; ne pas ajouter le passage ciblé aux1539tests de couverture.

## Assemblage Debug et tests ciblés finaux

```bash
gradle "${common[@]}" :app:testDebugUnitTest --tests '*MmsOutgoingSenderTest*' :app:assembleDebug :app:assembleDebugAndroidTest :mms-compression-lab:assembleDebug :mms-compression-lab:assembleDebugAndroidTest
```

Exit0, cancellation-build.log ; Kotlin et Java compilés,4APK assemblés. Le nombre de cas ciblés n’a pas été archivé séparément ; ils sont tous repris dans le décompte final. Aucun test instrumenté exécuté.

## Suite finale et Lint

```bash
gradle "${common[@]}" -PsentinelSignatureFixture=/workspace/qualification-evidence/v23-native-8c2c2b98/app.apk :app:testDebugUnitTest --rerun :mms-compression-lab:testDebugUnitTest --rerun :wearable-contract:test :wearable-security:test :app:lintDebug :mms-compression-lab:lintDebug
```

Exit0 ;1539app +71lab,0échec/0ignoré. Wearable2+26exécutés dans la campagne sur des modules inchangés, ce passage estUP-TO-DATE. Lint0erreur ; app223warnings/19hints, lab4warnings. Log cancellation-full-regression.log et JUnit complets.

## Bundle final isolé

```bash
gradle --offline --no-daemon --no-build-cache --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process -I /dev/shm/sentinel-native/ram-build.gradle -PsentinelHostCmake=/dev/shm/sentinel-native/cmake-sdk/bin/cmake :app:bundleReleaseUnsigned
```

Exit0,5m34s ; Kotlin release, R8 et LintVital compris. Non signé, non publiable. Le premier build combiné antérieur a disparu à R8 sous la limite8Gio avec oom_kill1 observé ; son log d’échec reste archivé. La compilation finale utilise un processus de compilation borné et un build de bundle séparé.

## Garde de signature réellement testée

```bash
# CWD racine du worktree
bash qualification/v23/native/check-signing-gate.sh /workspace/qualification-evidence/v23-native-49a38b4d/signing-gate --offline --no-daemon --no-build-cache --max-workers=1 -I /dev/shm/sentinel-native/ram-build.gradle -PsentinelHostCmake=/dev/shm/sentinel-native/cmake-sdk/bin/cmake
```

Résultat sémantique exit0 : deux graphes réels en --dry-run, secrets explicitement absents dans les sous-processus. Unsigned exit0 ; signed exit1 avec le motif de signature attendu. Aucune signature de production. Cette garde est inchangée dans8c2c2b98 ; les preuves sont recopiées dans le dossier final/signing-gate.

## Décodeur amont et JNI hôte

```bash
/dev/shm/sentinel-native/cmake-wheel/cmake/data/bin/cmake -S /dev/shm/sentinel-native/libjpeg-turbo-3.2.1 -B /dev/shm/sentinel-native/host -DENABLE_SHARED=OFF -DWITH_SIMD=OFF -DWITH_TURBOJPEG=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_BUILD_TYPE=Release
/dev/shm/sentinel-native/cmake-wheel/cmake/data/bin/cmake --build /dev/shm/sentinel-native/host -j 4
/dev/shm/sentinel-native/cmake-wheel/cmake/data/bin/ctest --test-dir /dev/shm/sentinel-native/host --output-on-failure -j 2
python3 qualification/v23/native/generate-fixtures.py /dev/shm/sentinel-native/host/cjpeg-static /dev/shm/sentinel-native/fixtures
javac -d /dev/shm/sentinel-native/host-test native-android-app/mms-jpeg-validator/src/main/java/com/sentinel/quantum/mmsjpeg/StrictJpegValidator.java qualification/v23/native/StrictJpegHostTest.java
java -Djava.library.path=/dev/shm/sentinel-native/gradle-build/mms-jpeg-validator/host-validator -cp /dev/shm/sentinel-native/host-test StrictJpegHostTest /dev/shm/sentinel-native/fixtures
```

CMake4.4.4 pour les tests amont :212tests/0échec/0ignoré. JNI final :13assertions,0échec. Le module, son pont et le code amont n’ont pas changé pendant le correctif tardif d’annulation. Le task Gradle buildHostValidator utilise ensuite CMake3.22.1 ; la validation JPEG n’est pas mockée dans les tests de codec.

## Artefacts, signature, ELF et bundle

```bash
# CWD racine
python3 qualification/v23/native/verify-artifacts.py /dev/shm/sentinel-native/gradle-build /workspace/qualification-evidence/v23-native-8c2c2b98 --bundletool /dev/shm/sentinel-native/bundletool-all-1.18.3.jar
java -jar /dev/shm/sentinel-native/bundletool-all-1.18.3.jar dump config --bundle=/workspace/qualification-evidence/v23-native-8c2c2b98/app-releaseUnsigned.aab
```

Exit0. Les commandes apksigner verify, zipalign -c -P16, aapt2 dump badging, readelf et bundletool validate, avec leurs arguments exacts et codes, figurent dans ARTIFACT_COMMANDS.json. Un contrôle complémentaire de tous les ELF APK/AAB est conservé dans ALL_NATIVE_ELF.json :16bibliothèques64bits par paquet, toutes conformes16K ;32bits non confondus avec les plateformes16K. BundleConfig annonce PAGE_ALIGNMENT_16K. Permissions comparées à l’ancien badging7ef494b3 :30identiques,0ajout.

Bundletool1.18.3 provient du dépôt officielGoogle ; SHA-256 vérifié `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`. Le premier téléchargement a reçu504 ; le même asset officiel via ?download=1 a été vérifié avant exécution. NDKr28c et CMake3.22.1 ont été contrôlés contre les sommes de la liste SDK officielle. Le tar source libjpeg possède son SHA-256 dans PROVENANCE.md.

## Régressions Node et absence d’appareil

```bash
node --test scripts/android-release-policy.test.js
npm run test:android-product-truth
npm run test:credential-hygiene
adb devices -l
git diff --check
bash -n qualification/v23/native/reproduce.sh qualification/v23/native/check-signing-gate.sh
```

Node :7/69/6réussis,0échec/0ignoré. ADB : liste vide, aucune installation ni instrumentation. Git et syntaxe shell : exit0. Le keystore cloud existant a été comparé à son état initial : inchangé ; seuls le booléen et le certificat public sont conservés.

## Rejouer après livraison

Les scripts/preuves sont ajoutés après8c2c2b98 sans changement Android. Pour une nouvelle qualification, utiliser un nouveau dossier de preuves et le SHA réellement checkouté :

```bash
expected=$(git rev-parse HEAD)
bash qualification/v23/native/reproduce.sh "$expected" "$HOME/sentinel-v23-check-$expected"
```

Configurer préalablement JAVA_HOME/ANDROID_HOME, les versions NDK/CMake, le cache Gradle et ROBOLECTRIC_SDK_DIR(API24/27/28/35). SENTINEL_HOST_CMAKE et SENTINEL_BUILD_ROOT permettent les chemins locaux. Les SHA des binaires ci-joints décrivent les binaires effectivement construits ici ; aucune reproductibilité binaire entre machines, clés ou marqueurs de commit différents n’est revendiquée.
