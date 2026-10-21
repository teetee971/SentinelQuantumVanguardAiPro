#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$HOME/sentinel-security-lab-androidtest"
git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "BLOQUE: copie Git absente"; exit 2; }
test -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" || { echo "BLOQUE: modifications locales"; exit 2; }
git -C "$ROOT" fetch --no-tags origin feature/d1-global-audit-20261020
git -C "$ROOT" checkout --detach "$(git -C "$ROOT" rev-parse FETCH_HEAD)"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
cd "$1/native-android-app"
export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
./gradlew :app:assembleLabPreview --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$ROOT"
APK="$ROOT/native-android-app/app/build/outputs/apk/labPreview/app-labPreview.apk"
test -s "$APK" || { echo "BLOQUE: APK absent"; exit 3; }
proot-distro login ubuntu -- bash -c '
set -euo pipefail
APK="$1"
SIGNER=/root/android-sdk/build-tools/36.0.0/apksigner
"$SIGNER" verify --verbose --print-certs "$APK" | tee /tmp/sentinel-lab-preview-cert.txt
grep -q "Signer #1 certificate SHA-256 digest: 68915958f2f8683afead51cc3a1e578d182df4b9d9b2505c48c261b1a17b3fe2" /tmp/sentinel-lab-preview-cert.txt || { echo "BLOQUE: certificat debug changé, mise à jour non autorisée"; exit 4; }
' _ "$APK"
MANIFEST_ROOT="$ROOT/native-android-app/app/build/intermediates/merged_manifests/labPreview"
MANIFEST="$(find "$MANIFEST_ROOT" -maxdepth 3 -type f -name AndroidManifest.xml -print -quit)"
test -n "$MANIFEST" && test -f "$MANIFEST" || { echo "BLOQUE: manifeste fusionné absent"; exit 5; }
echo "MANIFESTE=$MANIFEST"
grep -q 'package="com.sentinel.quantum.labpreview"' "$MANIFEST" || { echo "BLOQUE: mauvais identifiant"; exit 6; }
if grep -Eq 'SentinelInCallService|SentinelCallScreeningService|SentinelSmsDeliverReceiver|SentinelMmsDeliverReceiver|SentinelRespondViaMessageService|SentinelDialerActivity|SmsComposeActivity' "$MANIFEST"; then
  echo "BLOQUE: composants telephonie presents"; exit 7
fi
DEST="$HOME/storage/downloads/Sentinel-Lab-Preview-v6.apk"
test -d "$HOME/storage/downloads" || { echo "BLOQUE: dossier Telechargements indisponible"; exit 8; }
cp "$APK" "$DEST"
sha256sum "$DEST"
echo "FICHIER_PREVIEW=$DEST"
echo "VERDICT=PREVIEW_V6_ICONES_PREPARE_NON_INSTALLEE"
