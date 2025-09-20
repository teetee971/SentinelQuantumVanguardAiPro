#!/bin/bash

# 📦 Script d'installation des dépendances npm
# Utilise npm ci si package-lock.json existe, sinon npm install

install_npm_dependencies() {
    if [ ! -f package.json ]; then
        echo "ℹ Aucun package.json trouvé, skip installation des dépendances."
        return 0
    fi

    echo "📦 Installation des dépendances npm..."
    
    if [ -f package-lock.json ]; then
        echo "🔒 Utilisation de npm ci (lockfile détecté)"
        npm ci
    else
        echo "📦 Utilisation de npm install (pas de lockfile)"
        npm install
    fi
    
    echo "✅ Dépendances installées avec succès"
}

# Si le script est exécuté directement (pas sourcé), lancer l'installation
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    install_npm_dependencies
fi