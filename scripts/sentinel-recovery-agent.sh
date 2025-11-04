#!/usr/bin/env bash
# ============================================================
# ♻️ Sentinel Quantum Recovery Agent v1.0
# Auto-récupération Termux + redémarrage des agents critiques
# ============================================================

CONFIG_FILE="$HOME/.config/sentinel/.env"
LOGS_DIR="$HOME/logs"
mkdir -p "$LOGS_DIR"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "⚠️ Fichier de configuration manquant : $CONFIG_FILE"
  exit 1
fi

source "$CONFIG_FILE"

# Redémarrage des services principaux
echo "🔁 Redémarrage des processus Sentinel..." >> "$LOGS_DIR/recovery.log"
pkill crond 2>/dev/null
crond
nohup "$HOME/scripts/sentinel-auto-resolve.sh" >/dev/null 2>&1 &
nohup "$HOME/scripts/sentinel-watchdog-v2.sh" >/dev/null 2>&1 &
echo "✅ Services relancés à $(date '+%d/%m/%Y %H:%M:%S')" >> "$LOGS_DIR/recovery.log"

# Envoi d'une confirmation Telegram
MESSAGE="✅ *Sentinel Quantum Recovery Agent* a relancé tous les services IA.  
🕒 $(date '+%d/%m/%Y %H:%M:%S')
- AutoResolve ✅  
- Watchdog ✅  
- Cron ✅"

curl -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
  -d "chat_id=$TELEGRAM_CHAT_ID" \
  -d "text=$MESSAGE" \
  -d "parse_mode=Markdown"
