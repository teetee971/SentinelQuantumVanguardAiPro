#!/usr/bin/env bash
# ============================================================
# 🧠 Sentinel Quantum Watchdog v2.0
# Auto-réparation + compteur d’incidents + historique des logs
# ============================================================

LOG_FILE="$HOME/sentinel-auto-resolve.log"
CONFIG_FILE="$HOME/.config/sentinel/.env"
LOGS_DIR="$HOME/logs"
HISTORY_FILE="$LOGS_DIR/watchdog_history.log"
ERROR_PATTERN="fatal|error|conflict|denied|failed|cannot|refused"

mkdir -p "$LOGS_DIR"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "⚠️ Fichier de config non trouvé : $CONFIG_FILE"
  exit 1
fi

source "$CONFIG_FILE"

INCIDENT_COUNT=0

while true; do
  if [[ -f "$LOG_FILE" ]]; then
    ALERT=$(grep -iE "$ERROR_PATTERN" "$LOG_FILE" | tail -n 8)
    if [[ -n "$ALERT" ]]; then
      INCIDENT_COUNT=$((INCIDENT_COUNT+1))
      DATE_NOW=$(date '+%d/%m/%Y %H:%M:%S')
      echo "[$DATE_NOW] Incident #$INCIDENT_COUNT détecté" >> "$HISTORY_FILE"
      echo "$ALERT" >> "$HISTORY_FILE"
      echo "--------------------------------------" >> "$HISTORY_FILE"

      MESSAGE="🚨 *Sentinel Quantum Watchdog v2.0*  
Incident *#$INCIDENT_COUNT* détecté à $DATE_NOW  
\`\`\`
$ALERT
\`\`\`
🔁 Redémarrage automatique du processus AutoResolve."
      
      curl -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
        -d "chat_id=$TELEGRAM_CHAT_ID" \
        -d "text=$MESSAGE" \
        -d "parse_mode=Markdown"

      # Relance le processus AutoResolve pour corriger automatiquement
      bash "$HOME/run-auto-resolve.sh"

      # Réinitialise le log
      echo "" > "$LOG_FILE"
    fi
  fi
  sleep 300
done
