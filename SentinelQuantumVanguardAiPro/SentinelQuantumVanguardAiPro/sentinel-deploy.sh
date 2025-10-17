#!/bin/bash
# ==========================================================
# 🚀 Sentinel Deploy - AutoSync Cloudflare + GitHub
# ==========================================================

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'

cd ~/SentinelQuantumVanguardAiPro || exit
echo -e "${GREEN}🔄 Synchronisation avec GitHub...${NC}"

git pull origin main --rebase
git add .
git commit -m "🚀 AutoDeploy Sentinel Fusion $(date '+%Y-%m-%d %H:%M:%S')" || echo "Aucun changement à committer."
git push origin main && echo -e "${GREEN}✅ Déploiement Cloudflare déclenché avec succès.${NC}"

if [ $? -eq 0 ]; then
  termux-toast -b green "✅ Sentinel Fusion : déploiement réussi"
  termux-notification --title "Sentinel Fusion" --content "Cloudflare Pages : déploiement réussi" --priority high
else
  termux-toast -b red "❌ Sentinel Fusion : échec du push"
  term
