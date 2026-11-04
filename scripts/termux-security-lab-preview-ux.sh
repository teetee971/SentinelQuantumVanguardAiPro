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
test -s "$APK"
proot-distro login ubuntu -- bash -c '
set -euo pipefail
APK="$1"
TOOLS=/root/android-sdk/build-tools/36.0.0
"$TOOLS/apksigner" verify --verbose "$APK"
MANIFEST="$(find "$2/app/build/intermediates/merged_manifests/labPreview" -name AndroidManifest.xml -type f -print -quit)"
test -s "$MANIFEST"
grep -q "package=\"com.sentinel.quantum.labpreview\"" "$MANIFEST" || { echo "BLOQUE: mauvais identifiant"; exit 3; }
if grep -Eq "SentinelInCallService|SentinelCallScreeningService|SentinelSmsDeliverReceiver|SentinelMmsDeliverReceiver|SentinelRespondViaMessageService|SentinelDialerActivity|SmsComposeActivity" "$MANIFEST"; then
 echo "BLOQUE: composant telephonique trouve"; exit 4
fi
' _ "$APK" "$ROOT/native-android-app"
cp "$APK" "$HOME/storage/downloads/Sentinel-Lab-Preview-UX.apk"
sha256sum "$HOME/storage/downloads/Sentinel-Lab-Preview-UX.apk"
echo "VERDICT=APK_PREVIEW_UX_CONSTRUIT_SIGNE_ET_ISOLE_NON_INSTALLE"
