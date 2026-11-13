#!/data/data/com.termux/files/usr/bin/bash
# Read-only environment preflight; no downloads, SDK installs, ADB pairing or app changes.
set -euo pipefail
ROOT="${1:-$HOME/sentinel-qa-integrated-v2}"
echo "=== SENTINEL ÉMULATEUR — PRÉCONDITIONS ==="
echo "Dossier: $ROOT"
if [[ ! -d "$ROOT/.git" ]]; then echo "BLOCKED: dépôt intégré absent"; exit 2; fi
echo "Commit intégré: $(git -C "$ROOT" rev-parse HEAD)"
for cmd in proot-distro adb emulator; do
  if command -v "$cmd" >/dev/null 2>&1; then
    echo "$cmd: disponible ($(command -v "$cmd"))"
  else
    echo "$cmd: absent de Termux"
  fi
done
echo "Architecture Termux: $(uname -m)"
echo "Accélération /dev/kvm: $(if [[ -r /dev/kvm && -w /dev/kvm ]]; then echo disponible; else echo non accessible; fi)"
if command -v adb >/dev/null 2>&1; then
  echo "ADB: appareils connus (aucun pairage effectué)"
  adb devices -l || true
fi
if command -v proot-distro >/dev/null 2>&1; then
  echo "Ubuntu/proot — inventaire sans lancer d'émulateur"
  proot-distro login ubuntu -- bash -c '
    printf "Arch: "; uname -m
    for app in /root/android-sdk/emulator/emulator /root/android-sdk/platform-tools/adb; do
      if [[ -x "$app" ]]; then echo "$app: disponible"; else echo "$app: absent"; fi
    done
    if [[ -x /root/android-sdk/emulator/emulator ]]; then
      /root/android-sdk/emulator/emulator -list-avds 2>&1 || true
    fi
  ' || echo "Ubuntu/proot: inventaire impossible"
fi
echo "Test d'instrumentation compilé (vérification locale):"
TEST_APK="$ROOT/native-android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [[ -s "$TEST_APK" ]]; then
  echo "Présent: $TEST_APK"
  sha256sum "$TEST_APK"
else
  echo "Absent: $TEST_APK"
fi
echo "PRÉFLIGHT TERMINÉ — aucune exécution androidTest, aucun émulateur qualifié par cette commande."
