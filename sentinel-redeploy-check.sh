#!/bin/bash
# ============================================================
# 🧠 Sentinel Redeploy Check + IA Health Bar
# ============================================================

SITE_URL="https://sentinel-fusion.pages.dev/audit-report.html"
PROJECT_DIR=~/SentinelQuantumVanguardAiPro
LOG_FILE="$PROJECT_DIR/sentinel-redeploy.log"
STATS_FILE="$PROJECT_DIR/health-stats.txt"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "\n${YELLOW}🔍 Vérification automatique Sentinel Fusion...${NC}" | tee -a "$LOG_FILE"

# Initialise les compteurs si besoin
if [ ! -f "$STATS_FILE" ]; then
  echo "OK=0" > "$STATS_FILE"
  echo "FAIL=0" >> "$STATS_FILE"
fi

OK=$(grep "OK=" "$STATS_FILE" | cut -d'=' -f2)
FAIL=$(grep "FAIL=" "$STATS_FILE" | cut -d'=' -f2)

# Vérifie le statut du site
if curl -Is "$SITE_URL" | grep -q "200 OK"; then
  echo -e "${GREEN}✅ Site Sentinel Fusion opérationnel.${NC}" | tee -a "$LOG_FILE"
  termux-toast -b green "✅ Sentinel Fusion opérationnel"
  OK=$((OK + 1))
else
  echo -e "${RED}❌ Site injoignable — redéploiement automatique...${NC}" | tee -a "$LOG_FILE"
  termux-toast -b red "❌ Site injoignable — redeploy..."
  FAIL=$((FAIL + 1))

  cd "$PROJECT_DIR" || exit
  echo "trigger redeploy $(date '+%Y-%m-%d %H:%M:%S')" >> trigger.txt
  git add trigger.txt
  git commit -m "🚨 AutoRedeploy: Site injoignable, redéploiement automatique"
  git push origin main

  termux-notification --title "Sentinel Fusion" \
    --content "🚨 Redéploiement automatique lancé (site injoignable)" \
    --priority high
fi

# Sauvegarde les compteurs
echo "OK=$OK" > "$STATS_FILE"
echo "FAIL=$FAIL" >> "$STATS_FILE"

# Calcule le score de fiabilité
TOTAL=$((OK + FAIL))
if [ "$TOTAL" -eq 0 ]; then TOTAL=1; fi
PERCENT=$((100 * OK / TOTAL))

# Affiche la jauge IA
if [ "$PERCENT" -ge 90 ]; then
  COLOR=$GREEN; STATUS="🟢 Excellent"
elif [ "$PERCENT" -ge 70 ]; then
  COLOR=$YELLOW; STATUS="🟡 Moyen"
else
  COLOR=$RED; STATUS="🔴 Faible"
fi

echo -e "${COLOR}📊 Fiabilité IA : ${PERCENT}% ($STATUS)${NC}" | tee -a "$LOG_FILE"

# Notification visuelle
termux-notification --title "Sentinel Health Monitor" \
  --content "📊 Fiabilité IA : ${PERCENT}% ($STATUS)" \
  --priority low

echo "---------------------------------------------" >> "$LOG_FILE"
