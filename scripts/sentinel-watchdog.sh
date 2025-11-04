#!/usr/bin/env bash
# ============================================================
# 🧠 Sentinel Quantum Watchdog v1.2
# Surveillance en temps réel du log AutoResolve IA
# ============================================================

LOG_FILE="$HOME/sentinel-auto-resolve.log"
CONFIG_FILE="$HOME/.config/sentinel/.env"
ERROR_PATTERN="fatal|error|conflict|denied|failed"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "⚠️ Fichier de config non trouvé : $CONFIG_FILE"
  exit 1
fi

# Chargement des secrets
source "$CONFIG_FILE"

# Boucle infinie : vérifie toutes les 5 minutes
while true; do
  if [[ -f "$LOG_FILE" ]]; then
    ALERT=$(grep -iE "$ERROR_PATTERN" "$LOG_FILE" | tail -n 5)
    if [[ -n "$ALERT" ]]; then
      echo "🚨 Anomalie détectée dans le log !"
      MESSAGE="⚠️ *Sentinel Quantum Watchdog* a détecté une erreur :\n\n\`\`\`\n$ALERT\n\`\`\`\n🕒 $(date '+%d/%m/%Y %H:%M:%S')"
      curl -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
        -d "chat_id=$TELEGRAM_CHAT_ID" \
        -d "text=$MESSAGE" \
        -d "parse_mode=Markdown"
      echo "" > "$LOG_FILE"
    fi
  fi
  sleep 300
done
