#!/bin/bash

echo "🚀 Déploiement automatique Sentinel Quantum Vanguard AI Pro"
echo "📦 Build en cours..."

# 🧱 Étape 1 : build via Vite (avec npx pour compatibilité Termux)
npx vite build || { echo "❌ Erreur de build"; exit 1; }

echo "🧠 Commit et push GitHub..."
git add .
git commit -m "🚀 Auto Deploy $(date '+%Y-%m-%d %H:%M:%S')" || echo "⚠️ Rien à valider, passage au push"
git pull origin main --rebase
git push origin main --force

echo "🌐 Vérifie le déploiement sur Cloudflare Pages :"
echo "👉 https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions"
echo "🛰️ Site final : https://sentinelquantumvanguardaipro.pages.dev"

# ✅ Notification Telegram (optionnelle)
# Pour activer, ajoute ton BOT_TOKEN et ton CHAT_ID ci-dessous :
BOT_TOKEN="TON_BOT_TOKEN_ICI"
CHAT_ID="TON_CHAT_ID_ICI"

if [ "$BOT_TOKEN" != "TON_BOT_TOKEN_ICI" ]; then
  curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
    -d chat_id="$CHAT_ID" \
    -d text="✅ Sentinel Quantum Vanguard AI Pro déployé avec succès 🚀%0A🌍 https://sentinelquantumvanguardaipro.pages.dev"
fi

echo "✅ Déploiement terminé."
