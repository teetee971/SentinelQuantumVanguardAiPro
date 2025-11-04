#!/usr/bin/env bash
# ============================================================
# 🌐 Sentinel Network Guardian v1.0
# Surveillance connexion réseau + relance automatique des services IA
# ============================================================

CONFIG_FILE="$HOME/.config/sentinel/.env"
LOGS_DIR="$HOME/logs"
CHECK_INTERVAL=120  # secondes entre chaque vérification

mkdir -p "$LOGS_DIR"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "⚠️ Fichier .env manquant : $CONFIG_FILE"
  exit 1
fi

source "$CONFIG_FILE"

log_file="$LOGS_DIR/network_guardian.log"

check_connectivity() {
  ping -c1 8.8.8.8 >/dev/null 2>&1 && return 0 || return 1
}

check_service() {
  curl -Is --max-time 10 "$1" | head -n 1 | grep -q "200" && return 0 || return 1
}

send_alert() {
  local message="$1"
  curl -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
    -d "chat_id=$TELEGRAM_CHAT_ID" \
    -d "text=$message" \
    -d "parse_mode=Markdown" >/dev/null 2>&1
}

while true; do
  DATE_NOW=$(date '+%d/%m/%Y %H:%M:%S')

  if ! check_connectivity; then
    echo "[$DATE_NOW] ❌ Réseau inactif, tentative de reconnexion..." >> "$log_file"
    send_alert "⚠️ *Sentinel Network Guardian* : perte de connexion détectée à $DATE_NOW. Tentative de reconnexion..."
    termux-wifi-enable true 2>/dev/null || termux-telephony-call 911 >/dev/null 2>&1
    sleep 10
  else
    if ! check_service "https://github.com" || ! check_service "https://cloudflare.com"; then
      echo "[$DATE_NOW] ⚠️ Services externes inaccessibles (GitHub/Cloudflare)" >> "$log_file"
      send_alert "⚠️ *Sentinel Network Guardian* : GitHub ou Cloudflare inaccessibles à $DATE_NOW."
      pkill -f sentinel-auto-resolve.sh
      pkill -f sentinel-watchdog
      nohup "$HOME/scripts/sentinel-auto-resolve.sh" >/dev/null 2>&1 &
      nohup "$HOME/scripts/sentinel-watchdog-v2.sh" >/dev/null 2>&1 &
      send_alert "🔁 *Sentinel Network Guardian* a relancé les services IA."
    else
      echo "[$DATE_NOW] ✅ Réseau et services OK" >> "$log_file"
    fi
  fi

  sleep "$CHECK_INTERVAL"
done
