#!/bin/bash
# ============================================
# Sentinel Quantum Vanguard AI Pro - Oracle VPN Node Setup
# ============================================

set -e
echo "🔄 Mise à jour du système..."
sudo apt update -y && sudo apt upgrade -y

echo "⚙️ Installation de WireGuard et outils..."
sudo apt install -y wireguard qrencode ufw

echo "🔐 Génération des clés..."
WG_PRIVATE_KEY=$(wg genkey)
WG_PUBLIC_KEY=$(echo $WG_PRIVATE_KEY | wg pubkey)

cat <<EOCONF | sudo tee /etc/wireguard/wg0.conf > /dev/null
[Interface]
PrivateKey = $WG_PRIVATE_KEY
Address = 10.8.0.1/24
ListenPort = 51820
SaveConfig = true

[Peer]
PublicKey = CLIENT_PUBLIC_KEY
AllowedIPs = 10.8.0.2/32
EOCONF

sudo ufw allow 51820/udp
sudo ufw --force enable
sudo systemctl enable wg-quick@wg0
sudo systemctl start wg-quick@wg0

echo "✅ Serveur VPN IA Sentinel prêt !"
echo "Clé publique du serveur : $WG_PUBLIC_KEY"
