#!/data/data/com.termux/files/usr/bin/bash
# ======================================================
# 🚀 Script méga réparation & build pour A KI PRI SA YÉ
# Compatible Termux (Node.js v22+) + Cloudflare Pages
# ======================================================

echo "🔄 Nettoyage des dépendances..."
rm -rf node_modules package-lock.json

echo "🧹 Suppression de lightningcss (incompatible ARM64)..."
npm uninstall lightningcss --force

echo "⬇️ Réinstallation des dépendances critiques..."
npm install esbuild@0.21.5 --save-dev --force
npm install postcss postcss-cli autoprefixer --save-dev

echo "📦 Réinstallation des dépendances globales..."
npm install

echo "⚡ Lancement du serveur de développement (Vite)..."
nohup npm run dev -- --host > vite.log 2>&1 &

echo "🌍 Vérifie les adresses locales :"
echo "   ➜ Local   : http://localhost:5175"
echo "   ➜ Network : http://$(ip addr show wlan0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1):5175"
echo ""
echo "🛠️ Pour suivre les logs en direct : tail -f vite.log"

# ======================================================
# 🔧 Partie déploiement Cloudflare Pages
# ======================================================

echo "📂 Construction du build pour Cloudflare Pages..."
npm run build

if [ -d "dist" ]; then
  echo "✅ Build terminé. Dossier 'dist/' prêt."
  echo "⚡ Tu peux maintenant pousser sur GitHub avec :"
  echo "    git add ."
  echo "    git commit -m 'Fix dépendances et build stable'"
  echo "    git push origin main"
else
  echo "❌ ERREUR : le dossier 'dist/' n'a pas été généré !"
fi

# ======================================================
# 🌀 Automatisation via Termux:Boot
# ======================================================
# Crée un fichier ~/.termux/boot/start-vite.sh avec :
#   #!/data/data/com.termux/files/usr/bin/bash
#   cd ~/akiprisaye-web
#   ./fix-deps.sh
#
# Ensuite : chmod +x ~/.termux/boot/start-vite.sh
# Cela lancera automatiquement le projet au démarrage.
# ======================================================

echo "🎉 Script terminé !"