#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd ~/SentinelQuantumVanguardAiPro

LOGFILE="$HOME/logs/autosync_$(date '+%Y-%m-%d_%H-%M').log"
echo "[🔄 $(date '+%F %T')] Début autosync..." > "$LOGFILE"

git pull --rebase >>"$LOGFILE" 2>&1 || echo "pull échoué" >>"$LOGFILE"
git add . >>"$LOGFILE" 2>&1
git commit -m "🔁 AutoSync $(date '+%F %T')" >>"$LOGFILE" 2>&1 || echo "aucun changement" >>"$LOGFILE"
git push origin main >>"$LOGFILE" 2>&1 || echo "push échoué" >>"$LOGFILE"

if grep -q "erreur" "$LOGFILE"; then
  MSG="⚠️ AutoSync terminé avec erreurs."
else
  MSG="✅ AutoSync réussi."
fi

if [ -f ~/.config/sentinel/.env ]; then
  source ~/.config/sentinel/.env
  curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
       -d chat_id="${TELEGRAM_CHAT_ID}" -d text="$MSG"
fi
