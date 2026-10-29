# Qualification après redémarrage complet — procédure de référence

La variante conversationnelle a réussi selon les captures utilisateur :
[compte rendu](GALAXY-REBOOT-RUN-20261029.md), compteur 281 → 283, verify/cleanup
réussis. Le contrôle supplémentaire fingerprint avant/après présent ci-dessous
n’était pas inclus dans cette variante et n’est pas déclaré exécuté.

Cette procédure seule ne constitue pas un résultat PASS. Exécuter dans Termux sur le Galaxy,
avec les APK déjà qualifiés du commit eea40d4 et la signature existante.
Aucune réinstallation, suppression globale, suppression de clé ou changement de
branche nécessaire. Les nouvelles données de test sont distinctes du run réussi.

## 1. Préparer l'interruption au point published

Définir ANDROID_SERIAL avec l'adresse et le port ACTUELS de débogage sans fil,
puis copier le bloc ci-dessous. Si le dossier vault-reboot-published existe,
la préparation s'arrête plutôt que d'écraser les preuves ; examiner son état.

```bash
(
set -euo pipefail
: "${ANDROID_SERIAL:?Définir ANDROID_SERIAL avec le point ADB actuel}"
cd "$HOME/sentinel-recording-vault-r3-codex"
test "$(git branch --show-current)" = fix/recording-vault-v2-migration-hardening-20261028
adb -s "$ANDROID_SERIAL" get-state
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')" = SM-S926B
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = 36
state="$HOME/vault-reboot-published"
mkdir "$state"
git rev-parse HEAD > "$state/commit.txt"
python3 -c 'import uuid; print(uuid.uuid4().hex)' > "$state/run.txt"
adb -s "$ANDROID_SERIAL" shell settings get global boot_count | tr -d '\r' > "$state/boot-before.txt"
grep -Eq '^[0-9]+$' "$state/boot-before.txt"
adb -s "$ANDROID_SERIAL" shell getprop ro.build.fingerprint | tr -d '\r' > "$state/fingerprint-before.txt"
run=$(cat "$state/run.txt")
pkg=com.sentinel.quantum.r1cryptotest
class=com.sentinel.quantum.security.RecordingVaultMigrationPersistenceInstrumentedTest
runner="$pkg.test/androidx.test.runner.AndroidJUnitRunner"
verifier="$PWD/native-android-app/recording-vault-r1-test/qualification/verify-instrumentation.py"
trap 'adb -s "$ANDROID_SERIAL" shell am force-stop "$pkg" >/dev/null 2>&1 || true' EXIT
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class "$class" -e vaultRun "$run" -e vaultPoint published -e vaultPhase setup "$runner" > "$state/setup.log"
python3 "$verifier" "$state/setup.log" --single "$class#migrationAcrossProcessDeath"
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class "$class" -e vaultRun "$run" -e vaultPoint published -e vaultPhase interrupt "$runner" > "$state/interrupted.log" 2>&1 &
stage_pid=$!
ready=0
for ((i=0; i<120; i++)); do
  adb -s "$ANDROID_SERIAL" shell logcat -d -s SentinelR3:I '*:S' > "$state/logcat-before.txt"
  if grep -F "MIGRATION_READY run=$run point=published " "$state/logcat-before.txt"; then ready=1; break; fi
  sleep 0.25
done
adb -s "$ANDROID_SERIAL" shell am force-stop "$pkg"
wait "$stage_pid" || true
test "$ready" = 1
printf 'PRET POUR REDEMARRAGE run=%s\n' "$run"
)
```

Uniquement après PRET POUR REDEMARRAGE, redémarrer normalement le téléphone et
le déverrouiller. Ne pas lancer cleanup avant récupération. Relever le nouveau
port de débogage sans fil ; l'association peut être conservée.

## 2. Reconnecter et vérifier le même enregistrement

Mettre ANDROID_SERIAL à jour dans Termux, puis lancer le bloc suivant. Il refuse
de qualifier la reprise si le compteur ne prouve pas un nouveau démarrage, si
le firmware diffère ou si les assertions de récupération ne réussissent pas.

```bash
(
set -euo pipefail
: "${ANDROID_SERIAL:?Définir ANDROID_SERIAL avec le nouveau point ADB}"
cd "$HOME/sentinel-recording-vault-r3-codex"
state="$HOME/vault-reboot-published"
adb connect "$ANDROID_SERIAL"
adb -s "$ANDROID_SERIAL" get-state
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.product.model | tr -d '\r')" = SM-S926B
test "$(adb -s "$ANDROID_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')" = 36
before=$(cat "$state/boot-before.txt")
adb -s "$ANDROID_SERIAL" shell settings get global boot_count | tr -d '\r' > "$state/boot-after.txt"
after=$(cat "$state/boot-after.txt")
[[ $before =~ ^[0-9]+$ && $after =~ ^[0-9]+$ ]]
test "$after" -gt "$before"
adb -s "$ANDROID_SERIAL" shell getprop ro.build.fingerprint | tr -d '\r' > "$state/fingerprint-after.txt"
cmp -s "$state/fingerprint-before.txt" "$state/fingerprint-after.txt"
run=$(cat "$state/run.txt")
[[ $run =~ ^[a-f0-9]{32}$ ]]
class=com.sentinel.quantum.security.RecordingVaultMigrationPersistenceInstrumentedTest
runner=com.sentinel.quantum.r1cryptotest.test/androidx.test.runner.AndroidJUnitRunner
verifier="$PWD/native-android-app/recording-vault-r1-test/qualification/verify-instrumentation.py"
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class "$class" -e vaultRun "$run" -e vaultPoint published -e vaultPhase verify "$runner" > "$state/verify.log"
python3 "$verifier" "$state/verify.log" --single "$class#migrationAcrossProcessDeath"
adb -s "$ANDROID_SERIAL" shell am instrument -w -r -e class "$class" -e vaultRun "$run" -e vaultPoint published -e vaultPhase cleanup "$runner" > "$state/cleanup.log"
python3 "$verifier" "$state/cleanup.log" --single "$class#migrationAcrossProcessDeath"
printf 'REBOOT_VERIFIED run=%s boot_before=%s boot_after=%s\n' "$run" "$before" "$after" | tee "$state/result.txt"
)
```

verify contrôle le déchiffrement, l'empreinte V1 conservée, les identités des
processus et l'idempotence du V2. Le cleanup suivant ne supprime que les fixtures
de ce nouveau run. Si verify échoue, set -e empêche cleanup et conserve les preuves.
Archiver le dossier vault-reboot-published et transmettre ses journaux.

Le succès observé de la variante est une preuve de reprise après redémarrage
normal, pas de résistance à une coupure électrique ou à un stockage hostile.
