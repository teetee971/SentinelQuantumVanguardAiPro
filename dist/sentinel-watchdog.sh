#!/bin/bash
# ===========================================
# 🧠 Sentinel Watchdog - Auto Relaunch & Monitor
# Redémarre le cycle automatiquement si échec
# ===========================================

LOG_FILE="$HOME/SentinelQuantumVanguardAiPro/dist/sentinel-watchdog.log"
SCRIPT="$HOME/SentinelQuantumVanguardAiPro/dist/start-sentinel.sh"
source ~/SentinelQuantumVanguardAiPro/dist/telegram.env

send_telegram() {
  message="$1"
  [ -n "$message" ] && curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
    -d chat_id="${TELEGRAM_CHAT_ID}" \
    -d text="$message" > /dev/null
}

while true; do
  echo "🔄 $(date) — Lancement du cycle Sentinel..." >> "$LOG_FILE"
  $SCRIPT
  STATUS=$?

  if [ $STATUS -ne 0 ]; then
    echo "❌ $(date) — Échec détecté, relance automatique dans 2 minutes." >> "$LOG_FILE"
    send_telegram "⚠️ Sentinel Watchdog : échec détecté. Relance automatique dans 2 minutes..."
    sleep 120
  else
    echo "✅ $(date) — Cycle terminé avec succès. Relance dans 30 minutes." >> "$LOG_FILE"
    sleep 1800
  fi
done
