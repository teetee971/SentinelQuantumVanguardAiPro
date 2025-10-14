#!/bin/bash
# ============================================
# 🔒 Sentinel Vanguard AI Pro – Secure Start Sequence
# Vérifie le token GitHub, la connexion réseau et lance la synchronisation complète
# avec notifications Telegram intégrées
# ============================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'
LOG_FILE="$HOME/SentinelQuantumVanguardAiPro/dist/sentinel-sync.log"

# Charger la config Telegram
source ~/SentinelQuantumVanguardAiPro/dist/telegram.env

# Fonction d’envoi Telegram
send_telegram() {
  message="$1"
  if [ -n "$message" ]; then
    curl -s -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
      -d chat_id="${TELEGRAM_CHAT_ID}" \
      -d text="$message" > /dev/null
  fi
}

echo -e "${GREEN}🚀 Lancement Sentinel Secure Start...${NC}"
date +"[%Y-%m-%d %H:%M:%S] 🔵 Initialisation Secure Start" >> "$LOG_FILE"
send_telegram "🚀 Démarrage Sentinel Vanguard AI : initialisation du cycle."

# === 1️⃣ Vérification du Token GitHub ===
echo -e "${YELLOW}🧠 Vérification du token GitHub...${NC}"
~/check-github-token.sh
if [ $? -ne 0 ]; then
  echo -e "${RED}❌ Token GitHub invalide ou manquant.${NC}"
  echo "🔴 Token GitHub invalide à $(date)" >> "$LOG_FILE"
  send_telegram "❌ Token GitHub invalide ou expiré — vérifie ton autorisation GitHub."
  exit 1
fi

echo -e "✅ Token valide détecté."
echo "✅ Token valide à $(date)" >> "$LOG_FILE"

# === 2️⃣ Vérification connexion Internet ===
ping -c 1 github.com > /dev/null 2>&1
if [ $? -ne 0 ]; then
  echo -e "${RED}⚠️ Aucun accès Internet. Nouvelle tentative dans 1 minute.${NC}"
  echo "🌐 Internet KO à $(date)" >> "$LOG_FILE"
  send_telegram "⚠️ Internet hors ligne — Sentinel attend le retour du réseau."
  sleep 60
  exit 1
fi

echo -e "${GREEN}🌍 Connexion réseau active.${NC}"
echo "🌍 Connexion OK à $(date)" >> "$LOG_FILE"

# === 3️⃣ Lancement AutoSync principal ===
echo -e "${GREEN}⚙️ Lancement du cycle complet AutoSync...${NC}"
send_telegram "⚙️ Début du cycle AutoSync Sentinel Vanguard..."
bash ~/SentinelQuantumVanguardAiPro/dist/auto-sync.sh
STATUS=$?

# === 4️⃣ Journalisation et notification ===
if [ $STATUS -eq 0 ]; then
  echo -e "${GREEN}✅ Cycle Sentinel terminé avec succès.${NC}"
  echo "✅ AutoSync terminé à $(date)" >> "$LOG_FILE"
  send_telegram "✅ Cycle Sentinel terminé avec succès à $(date)."
else
  echo -e "${RED}❌ Erreur durant le cycle Sentinel.${NC}"
  echo "❌ AutoSync erreur à $(date)" >> "$LOG_FILE"
  send_telegram "❌ Erreur détectée durant le cycle Sentinel à $(date) — vérifie les logs."
fi

exit 0
