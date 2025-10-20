#!/data/data/com.termux/files/usr/bin/bash
cd /sdcard/SentinelQuantumVanguardAiPro || exit
echo "🚀 Nettoyage..."
rm -rf node_modules package-lock.json
npm cache clean --force
export PATH=$HOME/.npm-global/bin:$PATH
echo "⚙️ Installation dépendances..."
npm install --no-bin-links --legacy-peer-deps --unsafe-perm --force
npm install vite serve --save-dev --no-bin-links --unsafe-perm --force
echo "🧱 Construction du build..."
npx vite build
echo "🌐 Lancement du serveur local sur port 3000..."
npx serve dist --listen 3000
