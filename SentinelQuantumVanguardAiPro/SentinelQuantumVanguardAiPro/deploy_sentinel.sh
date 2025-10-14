#!/bin/bash

echo "🚀 [1/4] Sélection du projet Firebase réel..."
firebase use sentinel-vanguard-ai-pro

echo "🧱 [2/4] Build du projet Vite (React + Tailwind)..."
npm install
npm run build

echo "🔧 [3/4] Vérification du fichier firebase.json..."
cat firebase.json

echo "🌐 [4/4] Déploiement Firebase (hosting + functions)..."
firebase deploy --only "hosting,functions"

echo "✅ Déploiement terminé avec succès !"
