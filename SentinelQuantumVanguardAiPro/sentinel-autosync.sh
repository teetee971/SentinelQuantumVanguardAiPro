#!/bin/bash
# ============================================================
# 🔁 Sentinel AutoSync - GitHub Continuous Deployment Script
# ============================================================
# Version sécurisée (sans token en clair)
# Auteur : Sentinel Quantum Vanguard AI Pro
# ============================================================

# Couleur pour lisibilité
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}🚀 Démarrage Sentinel AutoSync...${NC}"
cd ~/SentinelQuantumVanguardAiPro || { echo "❌ Dossier introuvable"; exit 1; }

# Vérifie l’état Git
echo -e "${GREEN}🔍 Vérification de l'état du dépôt...${NC}"
git status

# Nettoyage des caches Git
echo -e "${GREEN}🧹 Nettoyage des anciens credentials...${NC}"
git config --global --unset credential.helper 2>/dev/null
git config --system --unset credential.helper 2>/dev/null
git config --local --unset credential.helper 2>/dev/null

# Synchronisation avec la branche main
echo -e "${GREEN}📦 Ajout et commit automatique...${NC}"
git add .
git commit -m "⚙️ AutoSync Sentinel Quantum Vanguard AI Pro $(date '+%Y-%m-%d %H:%M:%S')" || echo "Aucune modification détectée."

# Force le bon remote (avec variable sécurisée)
git remote remove origin 2>/dev/null
git remote add origin "https://${SENTINEL_GITHUB_TOKEN}@github.com/teetee971/SentinelQuantumVanguardAiPro.git"

# Vérifie la connexion
echo -e "${GREEN}🔗 Vérification du lien distant...${NC}"
git remote -v

# Pull avant push (évite les conflits)
echo -e "${GREEN}🔄 Synchronisation avec GitHub...${NC}"
git pull origin main --rebase || echo "Aucun changement distant."

# Push vers GitHub
echo -e "${GREEN}🚀 Envoi des mises à jour vers GitHub...${NC}"
git push origin main && echo -e "${GREEN}✅ Synchronisation complète avec succès !${NC}"
