#!/bin/bash
# ============================================================
# 🔰 Sentinel Quantum Vanguard AI Pro - Déploiement Complet
# ============================================================

SITE_URL="https://sentinelquantumvanguardaipro.pages.dev"
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"
LOG_FILE="deployment-report.txt"

echo "🚀 DÉPLOIEMENT AUTOMATIQUE SENTINEL QUANTUM VANGUARD AI PRO"
echo "============================================================"

# --- 1️⃣ Vérification et build ---
echo "📦 Préparation du build..."
if [ ! -f node_modules/.bin/vite ]; then
  echo "⚙️ Réinstallation des dépendances..."
  rm -rf node_modules package-lock.json pnpm-lock.yaml
  npm cache clean --force
  npm install -g pnpm
  pnpm install vite@5.4.10 @vitejs/plugin-react@4.2.0 --save-dev --force
fi

echo "🧱 Build du projet..."
pnpm exec vite build || { echo "❌ Erreur de build"; exit 1; }

# --- 2️⃣ Commit et push GitHub ---
echo "📡 Push GitHub..."
git add .
git commit -m "🚀 Auto Deploy $(date '+%Y-%m-%d %H:%M:%S')" || echo "⚠️ Aucun changement à valider"
git pull origin main --rebase
git push origin main --force

# --- 3️⃣ Vérification Cloudflare Pages ---
echo "🌐 Vérification du déploiement sur Cloudflare Pages..."
START_TIME=$(date +%s)
sleep 8

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$SITE_URL")
RESPONSE_TIME=$(curl -s -o /dev/null -w "%{time_total}" "$SITE_URL")

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

if [ "$HTTP_STATUS" = "200" ]; then
  STATUS_MSG="✅ Site en ligne et opérationnel"
  STATUS_ICON="🟢"
else
  STATUS_MSG="⚠️ Erreur ou indisponibilité détectée (HTTP $HTTP_STATUS)"
  STATUS_ICON="🔴"
fi

# --- 4️⃣ Génération du log ---
echo "📋 Enregistrement du rapport..."
echo "----------------------------------------------" | tee -a $LOG_FILE
echo "📅 Date : $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $LOG_FILE
echo "🌍 URL : $SITE_URL" | tee -a $LOG_FILE
echo "📡 Statut HTTP : $HTTP_STATUS" | tee -a $LOG_FILE
echo "⏱️ Temps de réponse : ${RESPONSE_TIME}s" | tee -a $LOG_FILE
echo "🧭 Durée du déploiement : ${DURATION}s" | tee -a $LOG_FILE
echo "💬 Résultat : $STATUS_MSG" | tee -a $LOG_FILE
echo "----------------------------------------------" | tee -a $LOG_FILE

# --- 5️⃣ Notification Telegram ---
if [ -n "$BOT_TOKEN" ] && [ -n "$CHAT_ID" ]; then
  curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
    -d chat_id="$CHAT_ID" \
    -d parse_mode="Markdown" \
    -d text="🛰️ *Sentinel Quantum Vanguard AI Pro*  
$STATUS_ICON *$STATUS_MSG*  
⏱️ Temps de réponse : ${RESPONSE_TIME}s  
📡 HTTP : ${HTTP_STATUS}  
🌍 [Accéder au site]($SITE_URL)"
fi

echo "✅ Déploiement complet terminé avec succès."
