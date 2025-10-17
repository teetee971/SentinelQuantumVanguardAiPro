chmod +x sentinel-autodeploy-pro.sh
./sentinel-autodeploy-pro.sh#!/bin/bash
# ==========================================================
# 🚀 Sentinel AutoDeploy PRO+  — Audit, Build & Self-Heal
# ==========================================================
# Auteur : IA Supervisor
# Projet : Sentinel Quantum Vanguard AI Pro (Fusion)
# ==========================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'
LOG_DIR=~/SentinelQuantumVanguardAiPro/logs
REPORT_DIR=~/SentinelQuantumVanguardAiPro/audits
SITE_URL="https://sentinel-fusion.pages.dev"

mkdir -p "$LOG_DIR"
mkdir -p "$REPORT_DIR"

LOG_FILE="$LOG_DIR/sentinel-latest.log"
REPORT_FILE="$REPORT_DIR/sentinel-report-$(date '+%Y%m%d-%H%M%S').html"

echo -e "${GREEN}🚀 Lancement Sentinel AutoDeploy PRO+...${NC}" | tee -a "$LOG_FILE"

cd ~/SentinelQuantumVanguardAiPro || exit

# === 1️⃣ Synchronisation Git ===
echo -e "${GREEN}🔁 Synchronisation GitHub...${NC}" | tee -a "$LOG_FILE"
git pull origin main --rebase >>"$LOG_FILE" 2>&1
git add .
git commit -m "🤖 AutoDeploy PRO+ $(date '+%Y-%m-%d %H:%M:%S')" >>"$LOG_FILE" 2>&1 || echo "Aucun changement."
git push origin main >>"$LOG_FILE" 2>&1 && echo -e "${GREEN}✅ Push GitHub réussi.${NC}" | tee -a "$LOG_FILE"

# === 2️⃣ Déclenchement Cloudflare ===
echo -e "${GREEN}🌐 Vérification du site en ligne...${NC}" | tee -a "$LOG_FILE"
if curl -Is "$SITE_URL" | grep -q "200 OK"; then
  echo -e "${GREEN}✅ Site en ligne.${NC}" | tee -a "$LOG_FILE"
else
  echo -e "${RED}⚠️ Site non accessible, tentative de redéploiement...${NC}" | tee -a "$LOG_FILE"
  echo "trigger redeploy" >> trigger.txt
  git add trigger.txt
  git commit -m "🚨 AutoRedeploy - Site injoignable"
  git push origin main
  termux-toast -b yellow "⚠️ AutoRedeploy : Site injoignable"
fi

# === 3️⃣ Audit Lighthouse (nécessite Node.js + npm) ===
echo -e "${YELLOW}🧠 Audit Lighthouse en cours...${NC}" | tee -a "$LOG_FILE"
if command -v npx >/dev/null 2>&1; then
  npx --yes lighthouse "$SITE_URL" \
    --quiet \
    --chrome-flags="--headless" \
    --output html \
    --output-path "$REPORT_FILE" >>"$LOG_FILE" 2>&1

  if [ -f "$REPORT_FILE" ]; then
    echo -e "${GREEN}📊 Rapport d’audit généré : ${REPORT_FILE}${NC}" | tee -a "$LOG_FILE"
    termux-notification --title "Sentinel Fusion" --content "📊 Rapport Lighthouse généré avec succès." --priority high
  else
    echo -e "${RED}❌ Échec de génération du rapport Lighthouse.${NC}" | tee -a "$LOG_FILE"
  fi
else
  echo -e "${RED}⚠️ npx non trouvé. Installe Node.js avant d’exécuter l’audit.${NC}" | tee -a "$LOG_FILE"
fi

# === 4️⃣ Vérifications secondaires ===
echo -e "${GREEN}🔍 Vérifications SSL / PWA...${NC}" | tee -a "$LOG_FILE"

if curl -Is "$SITE_URL" | grep -q "Strict-Transport-Security"; then
  echo -e "${GREEN}🔒 SSL actif.${NC}" | tee -a "$LOG_FILE"
else
  echo -e "${YELLOW}⚠️ SSL non détecté.${NC}" | tee -a "$LOG_FILE"
fi

if curl -s "$SITE_URL/manifest.webmanifest" | grep -q "name"; then
  echo -e "${GREEN}📱 PWA manifest valide.${NC}" | tee -a "$LOG_FILE"
else
  echo -e "${YELLOW}⚠️ Manifest PWA manquant.${NC}" | tee -a "$LOG_FILE"
fi

# === 5️⃣ Notifications finales ===
if grep -q "200 OK" <<<"$(curl -Is "$SITE_URL")"; then
  termux-toast -b green "✅ Sentinel Fusion déployé + audité avec succès"
  termux-notification --title "Sentinel AutoDeploy PRO+" --content "✅ Déploiement et audit réussis." --priority high
else
  termux-toast -b red "❌ Échec du déploiement Sentinel Fusion"
  termux-notification --title "Sentinel AutoDeploy PRO+" --content "❌ Erreur durant le déploiement." --priority high
fi

echo -e "${GREEN}🎯 Exécution terminée — voir les logs dans ${LOG_FILE}${NC}"
