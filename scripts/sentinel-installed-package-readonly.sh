#!/data/data/com.termux/files/usr/bin/bash
# Android 11+ limits package visibility for ordinary apps including Termux.
# Non-destructive diagnostic only; cannot certify installed app signing identity.
set -uo pipefail
PKG="com.sentinel.quantum"
echo "=== SENTINEL — APPLICATION INSTALLEE (LECTURE SEULE) ==="
echo "Cible: $PKG"
echo "Aucune installation / désinstallation / ADB / extraction de données."
if ! command -v dumpsys >/dev/null 2>&1; then
  echo "BLOQUE: dumpsys non disponible"
  exit 2
fi
OUT="$(timeout 12s dumpsys package "$PKG" 2>&1)"
RC=$?
if [[ "$RC" -ne 0 ]]; then
  echo "NON_VERIFIABLE: dumpsys refusé ou indisponible (code $RC)"
  echo "Sur Android, Termux ne possède généralement pas les autorisations pour lire le certificat installé."
  exit 0
fi
LINES="$(printf '%s\n' "$OUT" | grep -E -m 6 'versionCode=|versionName=|Package \[com\.sentinel\.quantum\]|signatures=' || true)"
if [[ -z "$LINES" ]]; then
  echo "NON_VERIFIABLE: aucune métadonnée fiable retournée."
else
  printf '%s\n' "$LINES"
  echo "ATTENTION: les informations exposées à Termux peuvent être partielles; ceci ne prouve PAS l'égalité des certificats."
fi
echo "STATUT_CERTIFICAT_INSTALLE=NON_VERIFIE"
echo "VERSION_CANDIDATE=1.0.10 (versionCode 11), compilation DEBUG uniquement"
echo "Ne pas installer le candidat sans preuve de signature et validation fonctionnelle."
