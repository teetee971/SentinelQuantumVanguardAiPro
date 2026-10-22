#!/data/data/com.termux/files/usr/bin/bash
# Sentinel V3 APK metadata audit. Read-only: no signing, installation, keystore access.
set -euo pipefail
APK="$HOME/sentinel-qa-integrated-v3/native-android-app/app/build/outputs/apk/debug/app-debug.apk"
[[ -s "$APK" ]] || { echo "BLOQUÉ : APK V3 absent"; exit 2; }
echo "=== AUDIT APK V3 ==="
sha256sum "$APK"
proot-distro login ubuntu -- bash -c '
 set -euo pipefail
 apk="$1"
 sdk="/root/android-sdk"
 tool="$(find "$sdk/build-tools" -maxdepth 2 -type f -name apksigner 2>/dev/null | sort -V | tail -n 1)"
 if [[ -z "$tool" ]]; then echo "BLOQUÉ : apksigner absent"; exit 2; fi
 echo "Outil: $tool"
 "$tool" verify --verbose --print-certs "$apk"
 aapt="$(find "$sdk/build-tools" -maxdepth 2 -type f -name aapt 2>/dev/null | sort -V | tail -n 1)"
 if [[ -n "$aapt" ]]; then
   "$aapt" dump badging "$apk" | grep -m1 "^package:"
 else
   echo "INFO : aapt non disponible pour lire le versionCode"
 fi
' _ "$APK"
echo "CERTIFICAT APK DEBUG UNIQUEMENT — ne prouve pas la signature production."
echo "Aucun keystore, mot de passe, rôle Android ou application installée consulté."
