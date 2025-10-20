#!/bin/bash
# ============================================================
# 🚀 Sentinel Quantum Vanguard AI Pro — AutoDeploy v3.4.1 Stable
# Correction Termux/npm + rotation logs + redéploiement propre
# ============================================================

set -e
export NODE_OPTIONS=--max_old_space_size=4096

LOG_DIR="logs"
mkdir -p "$LOG_DIR"
LOG_FILE="${LOG_DIR}/deploy_$(date +'%Y-%m-%d_%H-%M-%S').log"

echo "🧠 [$(date +'%H:%M:%S')] Lancement du déploiement Sentinel..." | tee -a "$LOG_FILE"

# 1️⃣ Nettoyage
echo "🧹 Nettoyage cache & modules..." | tee -a "$LOG_FILE"
rm -rf node_modules package-lock.json dist 2>/dev/null || true
rm -rf /data/data/com.termux/files/home/.npm/_cacache 2>/dev/null || true
npm cache clean --force >> "$LOG_FILE" 2>&1 || true

# 2️⃣ Sync Git
echo "🔄 Synchronisation Git..." | tee -a "$LOG_FILE"
git fetch origin main >> "$LOG_FILE" 2>&1
git rebase --abort 2>/dev/null || true
git merge --abort 2>/dev/null || true
git pull origin main --rebase >> "$LOG_FILE" 2>&1 || echo "ℹ️ Rien à mettre à jour" | tee -a "$LOG_FILE"

# 3️⃣ Install
echo "⚙️ Installation des dépendances..." | tee -a "$LOG_FILE"
npm install --legacy-peer-deps >> "$LOG_FILE" 2>&1 || npm rebuild >> "$LOG_FILE" 2>&1

# 4️⃣ Build (Termux-safe)
echo "🏗️ Compilation du projet (vite build)..." | tee -a "$LOG_FILE"
TMP_NPM="/data/data/com.termux/files/home/.npm/_cacache"
rm -rf "$TMP_NPM" 2>/dev/null || true
npm rebuild >> "$LOG_FILE" 2>&1 || true
npm run build --if-present >> "$LOG_FILE" 2>&1 || (
  echo "⚠️ Premier build échoué — reconstruction forcée..." | tee -a "$LOG_FILE"
  rm -rf dist && npm ci --legacy-peer-deps >> "$LOG_FILE" 2>&1 && npm run build --if-present >> "$LOG_FILE" 2>&1
)
echo "✅ Build réussi" | tee -a "$LOG_FILE"

# 5️⃣ Commit & Push
echo "💾 Commit & Push GitHub..." | tee -a "$LOG_FILE"
git add . >> "$LOG_FILE" 2>&1
git commit -m "🚀 AutoDeploy v3.4.1 $(date '+%Y-%m-%d %H:%M')" >> "$LOG_FILE" 2>&1 || echo "ℹ️ Aucun changement" | tee -a "$LOG_FILE"
git push origin main --force >> "$LOG_FILE" 2>&1

# 6️⃣ Notification Telegram (optionnel)
if [[ -n "$BOT_TOKEN" && -n "$CHAT_ID" ]]; then
  MSG="✅ Déploiement Sentinel v3.4.1 réussi 🌐 $(date '+%d/%m %H:%M')%0Ahttps://sentinelquantumvanguardaipro.pages.dev"
  curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
    -d "chat_id=${CHAT_ID}" -d "text=${MSG}" >> "$LOG_FILE" 2>&1
fi

echo "🏁 Déploiement terminé — log enregistré : $LOG_FILE"
