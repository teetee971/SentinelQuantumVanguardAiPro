#!/data/data/com.termux/files/usr/bin/bash
URL="https://sentinelquantumvanguardaipro.pages.dev"
LOGFILE="$HOME/logs/external_$(date '+%Y-%m-%d').log"
STATUS=$(curl -Is -o /dev/null -w "%{http_code}" "$URL" --max-time 5)
PING=$(ping -c 1 sentinelquantumvanguardaipro.pages.dev 2>/dev/null | awk -F'=' '/time=/{print $4}' | awk '{print $1}')

if [ -z "$PING" ]; then PING="timeout"; fi

echo "$(date '+%F %T') – HTTP:$STATUS – PING:$PING" >>"$LOGFILE"

ALERT=""
[ "$STATUS" != "200" ] && ALERT+="⚠️ HTTP $STATUS "
[ "$PING" = "timeout" ] && ALERT+="🚨 Pas de réponse réseau "

if [ -n "$ALERT" ]; then
  if [ -f ~/.config/sentinel/.env ]; then
    source ~/.config/sentinel/.env
    curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
         -d chat_id="${TELEGRAM_CHAT_ID}" -d text="🔍 External Check : $ALERT"
  fi
fi
