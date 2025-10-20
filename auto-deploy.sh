#!/bin/bash
echo "🚀 Déploiement Sentinel Quantum Vanguard AI Pro - Version Termux complète"

# === ⚙️ CONFIGURATION ===
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
PROJECT_NAME="sentinelquantumvanguardaipro"

notify() {
  local message="$1"
  curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
       -d "chat_id=${CHAT_ID}" \
       --data-urlencode "text=${message}" >/dev/null
}

cd ~/SentinelQuantumVanguardAiPro || {
  notify "❌ Dossier Sentinel introuvable sur Termux"
  exit 1
}

# === 1️⃣ Vérif Node/NPM ===
echo "🧩 Vérification environnement Node.js"
node -v && npm -v || {
  notify "⚠️ Node.js ou npm manquant sur Termux."
  exit 1
}

# === 2️⃣ Installation dépendances ===
echo "📦 Installation des dépendances..."
npm install --legacy-peer-deps --force --no-audit --no-fund
if [ $? -ne 0 ]; then
  notify "⚠️ Erreur pendant npm install"
  exit 1
fi

# === 3️⃣ Build (optionnel) ===
echo "🏗️ Build du projet..."
npm run build 2>/dev/null || echo "⚠️ Aucun script build"

# === 4️⃣ Push GitHub ===
echo "🔄 Commit et push vers GitHub..."
git add .
git commit -m "🚀 AutoDeploy $(date '+%Y-%m-%d %H:%M:%S')" || true
git push origin main
if [ $? -eq 0 ]; then
  notify "✅ [Sentinel] Code poussé sur GitHub avec succès"
else
  notify "⚠️ [Sentinel] Échec du push GitHub"
fi

# === 5️⃣ Déploiement Cloudflare Pages ===
if command -v wrangler >/dev/null 2>&1; then
  echo "☁️ Déploiement Cloudflare Pages..."
  wrangler pages deploy dist --project-name="$PROJECT_NAME" || {
    notify "⚠️ [Sentinel] Erreur pendant le déploiement Cloudflare"
    exit 1
  }
  notify "✅ [Sentinel Quantum Vanguard AI Pro] Déployé avec succès sur Cloudflare Pages 🌐"
else
  echo "⚠️ wrangler non trouvé"
  notify "⚠️ [Sentinel] wrangler absent, déploiement manuel requis"
fi

# === 6️⃣ Fin ===
echo "✅ Déploiement terminé avec succès"
notify "✅ [Sentinel] Déploiement complet terminé sur Termux 🚀"
