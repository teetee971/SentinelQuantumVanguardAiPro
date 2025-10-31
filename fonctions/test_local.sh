#!/data/data/com.termux/files/usr/bin/bash
echo "🚀 Lancement des tests Firebase Functions en local..."
cd "$(dirname "$0")"
npm install
firebase emulators:start --only functions
