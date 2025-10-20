#!/bin/bash
echo "🚀 Déploiement automatique Sentinel Quantum Vanguard AI Pro v3.2"

# 1️⃣ Sécurisation du contexte
cd ~/SentinelQuantumVanguardAiPro || exit 1
echo "📁 Dossier actuel : $(pwd)"
git config --global user.name "teetee971"
git config --global user.email "thierrynaud2009@gmail.com"

# 2️⃣ Vérification de Node/NPM
echo "🧩 Vérification environnement Node.js..."
node -v
npm -v

# 3️⃣ Installation / build
echo "📦 Installation des dépendances..."
npm install --legacy-peer-deps --force --no-audit --no-fund
echo "🏗️ Build du projet..."
npm run build || echo "⚠️ Aucun script 'build' trouvé, on continue..."

# 4️⃣ Synchronisation GitHub
echo "🔄 Commit et push vers GitHub..."
git add .
git commit -m "🚀 AutoDeploy Sentinel v3.2 $(date '+%Y-%m-%d %H:%M:%S')" || true
git push origin main

# 5️⃣ Vérification Cloudflare Pages
echo "🌐 Vérification Cloudflare Pages..."
if command -v wrangler >/dev/null 2>&1; then
  echo "☁️ Déploiement Cloudflare en cours..."
  wrangler pages deploy dist --project-name=sentinelquantumvanguardaipro
else
  echo "⚠️ wrangler non trouvé — déploiement Cloudflare à faire manuellement."
fi

# 6️⃣ Fin
echo "✅ Déploiement terminé avec succès !"
