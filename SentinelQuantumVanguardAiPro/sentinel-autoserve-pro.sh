#!/data/data/com.termux/files/usr/bin/bash
while true; do
  echo "🟢 Démarrage Sentinel Light Server..."
  node sentinel-server.js
  echo "🔁 Redémarrage automatique après erreur..."
  sleep 3
done
