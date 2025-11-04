#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BACKUP="$HOME/logs/autoarchive_$(date '+%Y-%m-%d').json"
find ~/logs -type f -name "*.log" -exec cat {} + >"$BACKUP"

cd ~/SentinelQuantumVanguardAiPro
git pull --rebase >/dev/null 2>&1 || true
cp "$BACKUP" ./logs/ || true
git add logs/ >/dev/null 2>&1
git commit -m "💾 AutoCommit $(date '+%F %T')" >/dev/null 2>&1 || true
git push origin main >/dev/null 2>&1 || true

if [ -f ~/.config/sentinel/.env ]; then
  source ~/.config/sentinel/.env
  curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
       -d chat_id="${TELEGRAM_CHAT_ID}" -d text="✅ Backup Git envoyé"
fi
