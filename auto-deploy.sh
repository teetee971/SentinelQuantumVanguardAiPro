#!/bin/bash
# ===========================================================
# 🚀 Sentinel Quantum Vanguard AI Pro — AutoDeploy Script v3.2
# Automatisation complète : build + commit + push + synchro GitHub
# ===========================================================

echo "🔄 Lancement du déploiement automatique Sentinel..."

# 1️⃣ Mise à jour du repo avant build
git fetch origin main
git pull --rebase origin main || echo "⚠️ Aucun rebase nécessaire"

# 2️⃣ Build local (Vite)
echo "⚙️ Compilation du projet (vite build)..."
npm install && npm run build || { echo "❌ Erreur de build"; exit 1; }

# 3️⃣ Commit automatique
echo "🧠 Commit automatique..."
git add .
git commit -m "🚀 AutoPush: Sentinel Quantum Vanguard v3.2 — Build & Sync" || echo "ℹ️ Aucun changement à valider"

# 4️⃣ Push vers GitHub avec ton token
echo "📤 Envoi vers GitHub..."
git push origin main --force || { echo "❌ Erreur lors du push"; exit 1; }

# 5️⃣ Confirmation
echo "✅ Déploiement terminé avec succès !"
echo "🌐 Vérifie Cloudflare Pages : https://sentinelquantumvanguardaipro.pages.dev"
