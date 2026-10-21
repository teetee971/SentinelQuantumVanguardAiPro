#!/data/data/com.termux/files/usr/bin/bash
# Candidate 1.0.10 release-mode verification. NEVER signs, installs, or publishes.
set -euo pipefail
ROOT="$HOME/sentinel-qa-release-110"
APP="$ROOT/native-android-app"
[[ -f "$APP/app/build.gradle" ]] || { echo "BLOQUÉ: candidat absent"; exit 2; }
[[ "$(git -C "$ROOT" rev-parse HEAD)" == "5d7d1c99cdbcc51da2a3a508d4acb76c93f47dec" ]] || { echo "BLOQUÉ: snapshot inconnu"; exit 2; }
[[ -z "$(git -C "$ROOT" status --porcelain --untracked-files=no)" ]] || { echo "BLOQUÉ: sources modifiées"; exit 2; }
grep -Eq '^[[:space:]]*versionCode 11$' "$APP/app/build.gradle" || exit 2
grep -Eq '^[[:space:]]*versionName "1.0.10"$' "$APP/app/build.gradle" || exit 2
echo "QUALIFICATION RELEASE UNSIGNED : aucune clé privée sollicitée."
proot-distro login ubuntu -- bash -c '
  set -euo pipefail
  unset KEYSTORE_FILE KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD
  cd "$1"
  export ANDROID_HOME=/root/android-sdk ANDROID_SDK_ROOT=/root/android-sdk
  export JAVA_TOOL_OPTIONS="-Dorg.sqlite.lib.path=/root/sentinel-sqlite-linux -Dorg.sqlite.lib.name=libsqlitejdbc.so"
  ./gradlew :app:assembleReleaseUnsigned --no-daemon --console=plain -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/home/sentinel-aapt2-amd64/gradle-wrapper/aapt2
' _ "$APP"
APK="$(find "$APP/app/build/outputs/apk/releaseUnsigned" -maxdepth 1 -type f -name '*.apk' -print -quit 2>/dev/null || true)"
[[ -n "$APK" && -s "$APK" ]] || { echo "BLOQUÉ: APK unsigned manquant"; exit 3; }
echo "APK_RELEASE_UNSIGNED=$APK"
sha256sum "$APK"
echo "VERDICT=RELEASE_UNSIGNED_COMPILEE — signature de production, instrumentation et installation NON VALIDÉES"
