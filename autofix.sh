#!/data/data/com.termux/files/usr/bin/bash
echo "🚀 AutoFix SentinelQuantumVanguardAiPro"

# 1️⃣ Nettoyage complet
echo "🧹 Suppression des caches et fichiers corrompus..."
rm -rf /sdcard/npm-cache /storage/emulated/0/npm-cache ~/.npmrc ~/SentinelQuantumVanguardAiPro/.npmrc
rm -rf node_modules package-lock.json

# 2️⃣ Configuration propre
echo "⚙️ Configuration propre de npm..."
npm config set prefix $HOME/.npm-global
npm config set cache $HOME/.npm-cache
npm config set tmp $HOME/.npm-tmp
export PATH=$HOME/.npm-global/bin:$PATH

# 3️⃣ Vérification
echo "📂 Nouveau cache npm : $(npm config get cache)"

# 4️⃣ Installation propre
echo "📦 Installation de vite et serve..."
npm cache clean --force
npm install vite serve --save-dev --no-bin-links --legacy-peer-deps --unsafe-perm --force

# 5️⃣ Build + serveur local
echo "🏗️ Construction du build..."
npx vite build

echo "🌐 Lancement du serveur local sur le port 3000..."
npx serve dist --listen 3000
