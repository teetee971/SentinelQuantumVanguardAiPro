#!/usr/bin/env bash
set -euo pipefail

SENTINEL_ID="sentinel-vanguard-ai-pro"
AKI_ID="a-ki-pri-sa-ye"

require() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "❌ '$1' introuvable. Installe-le puis réessaie." >&2
    exit 1
  }
}

echo "🔎 Vérifications…"
require firebase
if [ -f package.json ]; then
  require npm
fi

echo "🧹 Mise en place de .firebaserc"
cat > .firebaserc <<JSON
{
  "projects": {
    "sentinel": "${SENTINEL_ID}",
    "aki": "${AKI_ID}"
  },
  "targets": {
    "${SENTINEL_ID}": {
      "hosting": {
        "default": ["${SENTINEL_ID}"]
      }
    },
    "${AKI_ID}": {
      "hosting": {
        "default": ["${AKI_ID}"]
      }
    }
  }
}
JSON

echo "🔗 Application des cibles hosting"
firebase use sentinel >/dev/null
firebase target:apply hosting default "${SENTINEL_ID}" || true
firebase use aki >/dev/null
firebase target:apply hosting default "${AKI_ID}" || true

echo
echo "=============================="
echo "   Choisis le projet à déployer"
echo "=============================="
echo "1) Sentinel  (${SENTINEL_ID})"
echo "2) A KI PRI SA YÉ  (${AKI_ID})"
echo "3) Quitter"
read -rp "Ton choix [1-3]: " CH

case "$CH" in
  1) ALIAS="sentinel" ;;
  2) ALIAS="aki" ;;
  *) echo "👋 Bye"; exit 0 ;;
esac

firebase use "$ALIAS"

if [ -f package.json ]; then
  echo "🏗️ Build npm (si script 'build' existe)…"
  if npm run | grep -qE ' build '; then
    npm run build
  else
    echo "ℹ️ Aucun script 'build', on saute."
  fi
else
  echo "ℹ️ Pas de package.json, on saute le build."
fi

echo "📦 Déploiement vers Firebase Hosting…"
firebase deploy --only hosting

echo "✅ Terminé !"
