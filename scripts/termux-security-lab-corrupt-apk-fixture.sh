#!/data/data/com.termux/files/usr/bin/bash
# Creates an intentionally invalid, non-installable APK fixture for the SAF picker.
set -euo pipefail
DEST="$HOME/storage/downloads/Sentinel-Test-APK-Corrompu.apk"
test -d "$HOME/storage/downloads" || { echo "BLOQUE: autoriser l'accès au stockage avec termux-setup-storage"; exit 2; }
python - "$DEST" <<'PY'
import sys,zipfile
p=sys.argv[1]
with zipfile.ZipFile(p,"w",compression=zipfile.ZIP_STORED) as f:
    f.writestr("AndroidManifest.xml",b"invalid-android-binary-manifest")
    f.writestr("classes.dex",b"not-a-real-dex")
# A deliberately malformed ZIP header: should be refused by structural screening.
with open(p,"r+b") as f:
    f.write(b"NOT_A_VALID_APK!")
print("FICHIER_TEST="+p)
PY
echo "VERDICT=APK_FACTICE_INVALIDE_CREE_NON_INSTALLABLE"
echo "IMPORTANT: selectionner ce fichier uniquement dans Security Lab Pro, ne pas l'installer."
