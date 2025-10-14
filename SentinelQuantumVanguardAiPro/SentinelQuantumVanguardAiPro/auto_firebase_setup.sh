#!/usr/bin/env bash
set -e

# 🔧 Config
PROJECT_ID="sentinel-vanguard-ai-pro"
PUBLIC_DIR="dist" # Mets "public" si c'est ton dossier de sortie

echo "📂 Création de firebase.json..."
cat > firebase.json <<EOL
{
  "hosting": {
    "site": "$PROJECT_ID",
    "public": "$PUBLIC_DIR",
    "ignore": [
      "firebase.json",
      "**/.*",
      "**/node_modules/**"
    ]
  }
}
EOL

echo "🛠 Mise à jour de .firebaserc..."
cat > .firebaserc <<EOL
{
  "projects": {
    "default": "$PROJECT_ID"
  },
  "targets": {
    "$PROJECT_ID": {
      "hosting": {
        "default": ["$PROJECT_ID"]
      }
    }
  }
}
EOL

echo "🔗 Application de la cible Hosting..."
firebase target:apply hosting default "$PROJECT_ID" || true

# 📦 Build si package.json existe
if [ -f package.json ]; then
    if npm run | grep -q 'build'; then
        echo "🏗 Lancement du build npm..."
        npm run build
    else
        echo "ℹ Aucun script build trouvé, skip."
    fi
else
    echo "ℹ Aucun package.json trouvé, skip build."
fi

# 🚀 Déploiement
echo "🚀 Déploiement Firebase Hosting..."
firebase deploy --only hosting

echo "✅ Terminé !"
