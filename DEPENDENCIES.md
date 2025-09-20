# 📦 Installation des Dépendances

Ce projet utilise une logique d'installation intelligente des dépendances npm :

## Fonctionnement

```bash
# Si package-lock.json existe
npm ci  # Installation déterministe basée sur le lockfile

# Si package-lock.json n'existe pas
npm install  # Installation classique avec génération du lockfile
```

## Scripts Disponibles

### Script Utilitaire
- `./install-deps.sh` - Script autonome pour installer les dépendances

### Scripts de Déploiement
- `./deploy_sentinel.sh` - Déploiement Firebase avec installation automatique
- `./auto_firebase_setup.sh` - Configuration et déploiement Firebase

### Workflows GitHub Actions
- `.github/workflows/build-and-deploy.yml` - Build et déploiement automatique
- `.github/workflows/firebase-hosting-merge.yml` - Déploiement Firebase (main)
- `.github/workflows/firebase-hosting-pull-request.yml` - Preview Firebase (PR)

## Avantages

✅ **Installation déterministe** avec `npm ci` quand un lockfile existe  
✅ **Fallback automatique** vers `npm install` si pas de lockfile  
✅ **Builds reproductibles** en CI/CD  
✅ **Installation plus rapide** avec `npm ci` (skip résolution des dépendances)  
✅ **Cohérence** entre l'environnement local et de production  

## Utilisation

```bash
# Installation automatique
./install-deps.sh

# Ou manuellement
if [ -f package-lock.json ]; then
  npm ci
else
  npm install
fi
```