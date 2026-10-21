#!/data/data/com.termux/files/usr/bin/bash
# Evidence-only APK signature tamper test. Never signs, installs, patches or touches live apps.
set -euo pipefail
APK="${1:-$HOME/sentinel-security-lab-compile/native-android-app/app/build/outputs/apk/debug/app-debug.apk}"
[[ -s "$APK" ]] || { echo "BLOQUÉ : APK de test absent. Lancer d'abord assembleDebug dans la copie de qualification."; exit 2; }
LAB="$(mktemp -d "$HOME/sentinel-sigcheck.XXXXXXXX")"
trap 'rm -rf "$LAB"' EXIT
cp -- "$APK" "$LAB/reference.apk"
# Mutate a disposable copy outside the APK ZIP central directory; APK Signature v2/v3 covers full structure.
python - "$LAB/reference.apk" "$LAB/tampered.apk" <<'PY'
import pathlib, sys
data=pathlib.Path(sys.argv[1]).read_bytes()
if len(data)<4096: raise SystemExit("BLOQUÉ : APK trop petit")
# Inserting one extra byte after EOCD preserves original ZIP entries but invalidates APK signing block offsets.
pathlib.Path(sys.argv[2]).write_bytes(data+b"X")
PY
echo "=== Empreintes : uniquement copies temporaires ==="
sha256sum "$LAB/reference.apk" "$LAB/tampered.apk"
echo "=== Vérification cryptographique par Android SDK apksigner ==="
proot-distro login ubuntu -- bash -s -- "$LAB/reference.apk" "$LAB/tampered.apk" <<'BASH'
set -euo pipefail
APKSIGNER="/root/android-sdk/build-tools/36.0.0/apksigner"
[[ -x "$APKSIGNER" ]] || { echo "BLOQUÉ : apksigner indisponible"; exit 3; }
set +e
timeout 75s "$APKSIGNER" verify --verbose --min-sdk-version 24 "$1" > /tmp/sentinel-apk-reference-verify.log 2>&1
GOOD=$?
timeout 75s "$APKSIGNER" verify --verbose --min-sdk-version 24 "$2" > /tmp/sentinel-apk-tampered-verify.log 2>&1
BAD=$?
set -e
grep -E 'Verified using v[1234]|Number of signers' /tmp/sentinel-apk-reference-verify.log || true
echo "ORIGINAL_VERIFY_EXIT=$GOOD"
echo "ALTERED_VERIFY_EXIT=$BAD"
if [[ "$GOOD" -eq 0 && "$BAD" -ne 0 && "$BAD" -ne 124 ]]; then
  echo "VERDICT=SIGNATURE_ORIGINALE_VALIDEE_ET_COPIE_ALTEREE_REJETEE"
else
  echo "VERDICT=BLOQUE_OU_NON_CONCLUANT"
  exit 4
fi
BASH
echo "NOTE: test CLI externe ; aucun vérificateur cryptographique embarqué dans Sentinel à ce stade."
