#!/bin/bash
# =============================================
# Sentinel VPN Nodes Auto-Updater
# =============================================

JSON_FILE="vpn_nodes.json"
DATE=$(date +%Y-%m-%dT%H:%M:%S)

echo "🔍 Vérification du fichier $JSON_FILE..."
if [ ! -f "$JSON_FILE" ]; then
  echo "❌ Fichier manquant, création..."
  echo '{"nodes":[]}' > "$JSON_FILE"
fi

echo "➕ Ajout d’un nouveau nœud Sentinel..."
read -p "Nom du provider : " provider
read -p "Adresse IP : " ip
read -p "Port : " port
read -p "Clé publique : " pubkey
read -p "Pays : " country
read -p "Région : " region

tmp=$(mktemp)
jq --arg provider "$provider" \
   --arg ip "$ip" \
   --arg port "$port" \
   --arg pubkey "$pubkey" \
   --arg country "$country" \
   --arg region "$region" \
   --arg date "$DATE" \
'.nodes += [{"provider":$provider,"ip":$ip,"port":($port|tonumber),"public_key":$pubkey,"status":"online","country":$country,"region":$region,"updated_at":$date}]' \
"$JSON_FILE" > "$tmp" && mv "$tmp" "$JSON_FILE"

echo "✅ Nœud ajouté avec succès à $JSON_FILE"
