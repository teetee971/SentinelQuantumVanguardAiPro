# Qualification migration V1 → V2 — 2026-10-29

**Validation Android / Galaxy S24+ : NON EXÉCUTÉE par Codex.** Les résultats
historiques de 24 tests ne qualifient pas ce changement. Dependabot :
**NON VÉRIFIÉ (HTTP 403)**, aucune conclusion d'absence ou de résolution.

## Périmètre et fichiers

Conception : `docs/superpowers/specs/2026-10-29-recording-vault-migration-design.md`.
Code : RecordingVaultStore.kt, RecordingVaultCrypto.kt et VaultIo.kt dans ce module.
Le C JNI et le binaire ARM64 existants ne changent pas. L'application principale,
Room, ses dépendances et les autres modules ne changent pas.

`migrate(id)` est explicite : le module n'énumère ni ne migre automatiquement
l'ensemble des données Android. Les lectures V1 pré-migration restent compatibles.
La migration conserve la source .rv et publie .v2.rv ; delete(id) est la seule
suppression explicite de données d'enregistrement. Un état incohérent après
engagement remonte une erreur, jamais une lecture de secours V1.

## Matrice des preuves

| Exigence | Tests hôte | Tests Android préparés |
| --- | --- | --- |
| Lecture V1 et authenticité | fixture V1 réelle, corruption/troncature | legacyReadBeforeMigration, corruptOrTruncatedSourceIsNeverConverted |
| Migration et nouvel IV | migration keeps V1… | migrationPreservesSourceAndUsesFreshIV |
| Vérification après migration | read et AES-GCM réels | migratedPayloadIsAuthenticated |
| Idempotence | vérification des octets finaux et du marqueur | repeatedMigrationDoesNotRewrite |
| Préparation interrompue | EIO avant/après chaque frontière, ENOSPC partiel | partialPreparationPreservesSource |
| Publication interrompue | EIO avant/après rename et fsync | interruptionAfterIntentRecovers, publicationSyncFailureCannotReportSuccess |
| Mort de processus/reprise | JVM distinctes, halt sans cleanup, 3 frontières | migrationAcrossProcessDeath : prepared/required/published, force-stop externe |
| Corruption ou troncature | source, final, marqueur | corruptOrTruncatedSourceIsNeverConverted |
| Substitution id/directory | contenu V2 et marqueurs | substitutedV2IdentifierIsRejected, substitutedV2DirectoryIsRejected |
| Rétrogradation V1 | V1 authentique après exigence V2 refusé | authenticV1DowngradeIsRejected |
| Concurrence/non-écrasement | 8 processus save, 4 processus migrate, collision réelle JNI | concurrentMigrationAndSaveCannotOverwrite + suite existante |
| Préservation sur échec | source comparée octet pour octet à chaque faute | missingCandidateAfterIntentPreservesRecoverableV1 |
| Nouveau save interrompu | candidat complet récupéré ; partiel conservé/refusé | interruptedNewSaveCanRecoverPending |

Les EIO/ENOSPC hôte sont injectés ; ils ne constituent pas une mesure d'un disque
Android réellement plein. Les morts JVM n'émulent pas une coupure d'alimentation.
Le protocole physique suspend la VRAIE migration, puis tue son processus avec ADB.

## Commandes hôte

Depuis la racine du dépôt, avec un JDK possédant ses headers JNI et kotlinc :

```bash
export JAVA_HOME=/chemin/vers/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
export KOTLINC=/chemin/vers/kotlinc/bin/kotlinc
bash native-android-app/recording-vault-r1-test/qualification/run-host-tests.sh
PYTHONDONTWRITEBYTECODE=1 python3 native-android-app/recording-vault-r1-test/qualification/test-device-runner.py
PYTHONDONTWRITEBYTECODE=1 python3 native-android-app/recording-vault-r1-test/qualification/test-instrumentation-verifier.py
```

Le lanceur construit le vrai JNI pour Linux hôte dans un dossier temporaire.
AndroidKeyStore reste simulé ; aucun résultat de ces tests n'est un PASS Android.
Les sorties réellement obtenues sont archivées dans `evidence-migration/`.

## Build Termux + Ubuntu proot ARM64 — NON EXÉCUTÉ

Réutiliser l'environnement ARM64 qui avait compilé et signé les APK précédents,
son SDK et ses caches. Ne pas remplacer la clé de signature existante.
Dans Termux, entrer dans Ubuntu en rendant accessible le dépôt à jour :

```bash
repo="$HOME/SentinelQuantumVanguardAiPro"
test -d "$repo/.git"
proot-distro login ubuntu --bind "$repo:/root/vault"
```

Dans Ubuntu, définir les chemins de l'installation EXISTANTE ci-dessous. Les
valeurs par défaut sont des emplacements proposés ; les tests de présence
échouent explicitement si les outils ou caches manquent. Ne pas masquer cet
échec par une désinstallation Android ou un changement de dépendances.

```bash
set -euo pipefail
cd /root/vault
test "$(git branch --show-current)" = fix/recording-vault-v2-migration-hardening-20261028
git status --short
git rev-parse HEAD
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export GRADLE_BIN="${GRADLE_BIN:-$HOME/gradle-9.8.0/bin/gradle}"
# Exiger le aapt2 ARM64 déjà fonctionnel dans cet environnement proot.
: "${AAPT2_ARM64:?Définir le chemin absolu du aapt2 ARM64 déjà qualifié}"
test -x "$JAVA_HOME/bin/java"
test -x "$GRADLE_BIN"
test -x "$AAPT2_ARM64"
test -d "$ANDROID_HOME/platforms/android-37.0" || test -d "$ANDROID_HOME/platforms/android-37"
java -version
"$GRADLE_BIN" --version
"$AAPT2_ARM64" version
mkdir -p /root/vault-evidence
"$GRADLE_BIN" --offline --no-daemon --console=plain \
  -p native-android-app/recording-vault-r1-test/qualification/android-build \
  -Pandroid.aapt2FromMavenOverride="$AAPT2_ARM64" \
  :recording-vault-r1-test:assembleDebug \
  :recording-vault-r1-test:assembleDebugAndroidTest \
  :recording-vault-r1-test:lintDebug \
  2>&1 | tee /root/vault-evidence/build.txt
sha256sum \
  native-android-app/recording-vault-r1-test/build/outputs/apk/debug/recording-vault-r1-test-debug.apk \
  native-android-app/recording-vault-r1-test/build/outputs/apk/androidTest/debug/recording-vault-r1-test-debug-androidTest.apk \
  | tee /root/vault-evidence/apk-sha256.txt
exit
```

Le build isolé évite la configuration de :app et les autres modules. Si AGP,
Kotlin embarqué, SDK ou dépendances de tests ne sont pas en cache, le build
hors ligne est BLOQUÉ. Compatibilité de cet ensemble AGP/JDK17/ARM64 à confirmer
par ce build, pas à déduire de la compilation JVM.

## Galaxy SM-S926B, Android 16/API36 — NON EXÉCUTÉ

Dans Termux, reprendre l'association ADB sans fil existante (les ports de pairing
et de connexion sont différents). Ne journaliser aucun code d'association.

```bash
pkg install android-tools python coreutils bash
adb devices -l
export ANDROID_SERIAL='IP_DU_GALAXY:PORT_DE_CONNEXION'
adb -s "$ANDROID_SERIAL" get-state
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')" = SM-S926B
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = 36
adb -s "$ANDROID_SERIAL" shell getprop ro.build.fingerprint
cd "$HOME/SentinelQuantumVanguardAiPro"
module="$PWD/native-android-app/recording-vault-r1-test"
export APP_APK="$module/build/outputs/apk/debug/recording-vault-r1-test-debug.apk"
export TEST_APK="$module/build/outputs/apk/androidTest/debug/recording-vault-r1-test-debug-androidTest.apk"
evidence="$HOME/vault-device-$(date -u +%Y%m%dT%H%M%SZ)"
set -o pipefail
bash "$module/qualification/run-device.sh" "$evidence"
git rev-parse HEAD | tee "$evidence/commit.txt"
```

Le protocole installe avec `-r -t` et n'efface ni package, ni données, ni alias
KeyStore, ni verrou. Un échec de signature est BLOQUANT : jamais `pm clear` ni
uninstall. La suite découvre les identités attendues (`log=true`, non exécuté),
puis exige toutes les mêmes identités terminées avec succès, un résumé cohérent
et le code terminal -1. Aucun nombre de tests figé. Les phases explicitement
tuées ne sont jamais comptées comme réussies. Les trois points migration
prepared/required/published doivent chacun atteindre MIGRATION_READY, être
force-stoppés, puis vérifiés par un autre processus, avant cleanup de SES fixtures.

La précédente phase stageInterruptedWrite conserve sa caractérisation de
résidu artificiel ; elle n'est pas une preuve d'interruption de save. Les nouveaux
points sont dans les opérations réelles de migrate. Un protocole incomplet,
un skip, une hypothèse abandonnée ou un état incohérent bloque la qualification.

Pour une campagne redémarrage, utiliser un nouveau RUN puis les phases setup,
interrupt (dans un terminal séparé), force-stop et readiness comme dans le script ;
redémarrer avant verify, se reconnecter et vérifier le même RUN/point, puis cleanup.
Une coupure électrique brutale n'est pas couverte. Ne pas remplir le stockage
personnel du Galaxy : la campagne ENOSPC réelle demande un environnement de test
isolé et une méthode bornée approuvée séparément.

## Risques résiduels / revue

- Les V1 n'authentifient pas leur attribution historique : substitution préalable
  indétectable. Conservation V1 volontaire, consommation disque accrue.
- Le marqueur local ne résiste pas à une restauration cohérente de TOUT le
  stockage. Aucun anti-rollback absolu revendiqué ; aucune promesse RCS/READ.
- Un attaquant du même UID peut contourner le verrou et provoquer des courses de
  chemins ; NOFOLLOW_LINKS et JNI ne rendent pas ce modèle d'attaque impossible.
- Après marqueur, perte/corruption du candidat et du final : refus explicite ;
  source conservée pour récupération manuelle, aucun repli automatique.
- fsync de répertoire, AndroidKeyStore, mémoire, latence et stockage physique
  Galaxy restent NON EXÉCUTÉS. Aucun succès physique déduit des tests JVM.
- Revue indépendante effectuée : récupération du nouveau save pré-marqueur,
  fsync avant enrôlement, code terminal d'instrumentation, chemins Android
  canoniques et corruption de tag déterministe corrigés.
- Les six alertes Dependabot restent NON VÉRIFIÉES, HTTP403 ; aucun autre module
  ni aucune dépendance modifiés.
