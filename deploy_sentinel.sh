#!/bin/bash

echo "🚀 [1/4] Sélection du projet Firebase réel..."
firebase use sentinel-vanguard-ai-pro

echo "🧱 [2/4] Build du projet Vite (React + Tailwind)..."
echo "📦 Installation des dépendances..."
if [ -f package-lock.json ]; then
  echo "🔒 Utilisation de npm ci (lockfile détecté)"
  npm ci
else
  echo "📦 Utilisation de npm install (pas de lockfile)"
  npm install
fi
npm run build

echo "🔧 [3/4] Vérification du fichier firebase.json..."
cat firebase.json

echo "🌐 [4/4] Déploiement Firebase (hosting + functions)..."
firebase deploy --only "hosting,functions"

echo "✅ Déploiement terminé avec succès !"
