#!/data/data/com.termux/files/usr/bin/bash
# Install/inspect/remove a local hourly Termux cron task. No builds or APK changes.
set -euo pipefail
ACTION="${1:-status}"
BASE="$HOME/sentinel-autocontrol"
SCRIPT="$HOME/sentinel-v3.sh"
MARK="# SENTINEL_AUTOCONTROL_V3_HOURLY"
CRON="13 * * * * /data/data/com.termux/files/usr/bin/bash $SCRIPT >> $BASE/hourly.log 2>&1 $MARK"
case "$ACTION" in
 status)
   if ! command -v crontab >/dev/null; then echo "NON INSTALLÉ : cronie absent"; exit 0; fi
   if crontab -l 2>/dev/null | grep -Fq "$MARK"; then echo "PLANIFICATION PRÉSENTE (non garantie si Android suspend Termux)"; else echo "PLANIFICATION ABSENTE"; fi
   if pgrep -x crond >/dev/null 2>&1; then echo "crond actif"; else echo "crond arrêté"; fi
   ;;
 enable)
   [[ -f "$SCRIPT" ]] || { echo "BLOQUÉ : installer sentinel-v3.sh avant"; exit 2; }
   bash -n "$SCRIPT"
   command -v crontab >/dev/null || { echo "BLOQUÉ : installer cronie avec pkg install cronie"; exit 2; }
   mkdir -p "$BASE"
   EXISTING="$(crontab -l 2>/dev/null || true)"
   printf '%s\n' "$EXISTING" | grep -Fv "$MARK" | { cat; printf '%s\n' "$CRON"; } | crontab -
   if ! pgrep -x crond >/dev/null 2>&1; then crond; fi
   echo "PLANIFICATION ACTIVÉE : minute 13 de chaque heure, uniquement si Termux/crond fonctionne"
   ;;
 disable)
   command -v crontab >/dev/null || { echo "PLANIFICATION ABSENTE"; exit 0; }
   EXISTING="$(crontab -l 2>/dev/null || true)"
   printf '%s\n' "$EXISTING" | grep -Fv "$MARK" | crontab -
   echo "PLANIFICATION SENTINEL RETIRÉE. Les autres tâches cron sont conservées."
   ;;
 *) echo "Usage: bash $0 status|enable|disable"; exit 2 ;;
esac
