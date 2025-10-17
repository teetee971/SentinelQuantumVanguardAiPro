#!/bin/bash
# ============================================================
# Sentinel Quantum Vanguard AI Pro - Vérification post-déploiement
# ============================================================

SITE_URL="https://sentinelquantumvanguardaipro.pages.dev"
LOG_FILE="deployment-report.txt"
BOT_TOKEN="7630324617:AAGYNTiOIhHFDyT83Qt_DezDUxEzEJf-K9E"
CHAT_ID="5707814118"

echo "🛰️ Vérification du site Sentinel Quantum Vanguard AI Pro..."
echo "----------------------------------------------" | tee -a $LOG_FILE
echo "📅 Date : $(date '+%Y-%m-%d %H:%M:%S')" | tee -a $LOG_FILE

START_TIME=$(date +%s)

# Vérifie la disponibilité du site
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

echo "🌍 URL : $SITE_URL" | tee -a $LOG_FILE
echo "📡 Statut HTTP : $HTTP_STATUS" | tee -a $LOG_FILE
echo "⏱️ Temps de réponse : ${RESPONSE_TIME}s" | tee -a $LOG_FILE
echo "🧭 Durée de la vérification : ${DURATION}s" | tee -a $LOG_FILE
echo "💬 Résultat : $STATUS_MSG" | tee -a $LOG_FILE
echo "----------------------------------------------" | tee -a $LOG_FILE

# Notification Telegram
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

echo "✅ Vérification et notification terminées."
