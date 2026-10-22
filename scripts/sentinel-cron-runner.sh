#!/data/data/com.termux/files/usr/bin/bash
# La preuve CRON_TRIGGER est émise seulement par la tâche programmée.
set -uo pipefail
export PATH="/data/data/com.termux/files/usr/bin:/system/bin"
export HOME="/data/data/com.termux/files/home"
LOG="$HOME/sentinel-autocontrol/hourly.log"
mkdir -p "$HOME/sentinel-autocontrol"
{
  echo "CRON_TRIGGER $(date -Iseconds)"
  if bash "$HOME/sentinel-v3.sh"; then
    echo "CRON_EXIT=0 $(date -Iseconds)"
  else
    rc=$?
    echo "CRON_EXIT=$rc $(date -Iseconds)"
    exit "$rc"
  fi
} >> "$LOG" 2>&1
