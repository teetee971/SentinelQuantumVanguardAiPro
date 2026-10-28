# Galaxy S24+ Android 16 — protocole reproductible

**État au 2026-10-28 : procédure préparée et compilée, pas exécutée sur appareil.**
Ne jamais remplacer cette mention par PASS sans les journaux d'une exécution réelle.

## Préparer les APK sur une machine Linux avec JDK et SDK Android

Utiliser la branche de la mission et conserver le commit exact testé. Depuis `native-android-app/recording-vault-r1-test` :

```bash
git rev-parse HEAD
bash ../gradlew -p qualification/android-build \
  :recording-vault-r1-test:assembleDebug \
  :recording-vault-r1-test:assembleDebugAndroidTest \
  :recording-vault-r1-test:lintDebug --console=plain
sha256sum build/outputs/apk/debug/recording-vault-r1-test-debug.apk \
  build/outputs/apk/androidTest/debug/recording-vault-r1-test-debug-androidTest.apk
```

Ce build ne configure pas `:app` et ne produit pas `app/schemas/`. SDK requis : plateforme `platforms;android-37.0` et build-tools `36.0.0` avec AGP 9.4.1. Les versions du dépôt sont conservées ; voir `COMMANDS.md` pour l'environnement utilisé ici.

**Conserver la signature de l'APK R1/R2 déjà installé.** Construire sur la machine qui possède sa clé debug d'origine (ou via la configuration de signature existante). Une clé debug créée dans ce cloud peut être différente. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` est un blocage, jamais une invitation à désinstaller. Ne pas partager la clé privée ; ne pas désinstaller et ne pas lancer `pm clear` pour contourner ce blocage : cela détruirait les données et la continuité AndroidKeyStore à tester.

## Termux et ADB sans fil

Sur le Galaxy, activer Options développeur → Débogage sans fil. Ouvrir « Associer l'appareil avec un code ». Dans Termux, en écran partagé si nécessaire :

```bash
pkg install android-tools python coreutils bash
adb pair ADRESSE_IP:PORT_ASSOCIATION
# Entrer le code affiché sur le téléphone ; ne pas le mettre dans les journaux.
adb connect ADRESSE_IP:PORT_CONNEXION
adb devices -l
export ANDROID_SERIAL='ADRESSE_IP:PORT_CONNEXION'
adb -s "$ANDROID_SERIAL" shell getprop ro.product.model
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.release
adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk
```

Le port d'association et le port de connexion sont distincts et peuvent changer. API attendue pour Android 16 : 36. Vérifier réellement le modèle S24+ et le fingerprint, ne pas se fier au nom du dossier ou à un émulateur. Ce protocole ne nécessite ni root ni accès de Termux aux fichiers privés de l'application.

Copier le module/protocole et les deux APK construits dans `$HOME` de Termux (stockage privé, pas d'exécution sur le stockage partagé). La compilation Android complète sous Termux/ARM64 n'est pas validée ici ; utiliser les APK de la machine de build. Si les APK sont ailleurs :

```bash
export APP_APK="$HOME/r3/recording-vault-r1-test-debug.apk"
export TEST_APK="$HOME/r3/recording-vault-r1-test-debug-androidTest.apk"
cd "$HOME/r3/recording-vault-r1-test"
set -o pipefail
bash qualification/run-device.sh "$HOME/r3-evidence-$(date -u +%Y%m%dT%H%M%SZ)"
```

Le script journalise les empreintes, installe systématiquement avec `adb install -r -t` (préserve données/clé si signature compatible), puis exécute la suite et les phases. Seul le package `com.sentinel.quantum.r1cryptotest` est forcé à s'arrêter. Il ne touche pas à l'application principale.

## Critères d'acceptation des journaux

- `regression.log` : `OK (21 tests)`, aucun échec/ignoré/hypothèse abandonnée. Les 16 tests R1/R2 doivent être présents, plus les 5 nouveaux. Le test qui accepte une substitution valide décrit une faiblesse attendue, pas une propriété sécurisée.
- `write.log`, `verify.log`, `cleanup.log` : chacun `OK (1 test)`. `logcat.log` contient WRITE, INTERRUPT_READY, VERIFIED et les identités différentes des processus. L'arrêt forcé est accompagné d'un `pidof` vide dans `protocol.log`.
- `interrupted.log` : interruption attendue après readiness ; **ne pas compter comme test réussi**. Le scénario dépose un temporaire partiel puis le tue ; ce n'est pas une preuve de crash au milieu de la vraie fonction Store.save.
- Le défi chiffré d'avant arrêt et le premier enregistrement sont déchiffrés après arrêt ; le hash du fichier reste identique. Un alias inchangé seul ne suffit pas.
- `SentinelR1KeyAudit` rapporte le niveau réellement observé : SOFTWARE/TEE/STRONGBOX. Ne pas transformer automatiquement un simple retour OK en garantie de stockage matériel.
- Archiver `protocol.log`, toutes les sorties, `logcat.log`, commit, empreintes APK et modèle/build. Aucun enregistrement d'appel n'est utilisé : seulement des fixtures synthétiques.

En cas d'échec, les fichiers sont conservés pour diagnostic. Relever le `RUN` affiché. Pour supprimer uniquement les données de cette exécution après analyse :

```bash
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e class com.sentinel.quantum.security.RecordingVaultPersistenceInstrumentedTest \
  -e vaultRun HEXADECIMAL_32_CARACTERES_DU_RUN \
  -e vaultPhase cleanup \
  com.sentinel.quantum.r1cryptotest.test/androidx.test.runner.AndroidJUnitRunner
```

Ne pas supprimer `.recording-vault-r3.lock`, le répertoire complet, ni l'alias KeyStore.

## Campagnes complémentaires non exécutées

- Première génération concurrente : sur **un profil/appareil de test vierge uniquement**, installer les deux APK puis lancer seulement `RecordingVaultProcessConcurrencyInstrumentedTest` avant tout autre test. Ne pas effacer l'alias d'un profil contenant les validations existantes. La suite normale a déjà créé la clé avant la course.
- Redémarrage complet : exécuter les phases write et staged-write/force-stop avec le même run, redémarrer le téléphone avant verify, se reconnecter au nouveau port ADB et exécuter verify puis cleanup. Conserver boot ID et fingerprint ; tester séparément avant/après déverrouillage si les règles du système le permettent. Ce n'est pas un test de coupure électrique brutale.
- Interruption du vrai Store.save à ses différentes étapes, disque plein, suppression de temporaires liés après publication, croissance/troncature concurrente, révocation/invalidation de clé et budgets mémoire/latence : campagnes supplémentaires requises, avec fixtures exclusivement dédiées.

La liste complète des blocages est dans `SECURITY-AND-REGRESSION.md`. Aucun résultat de ce protocole ne prouve l'enregistrement des deux côtés d'un appel.
