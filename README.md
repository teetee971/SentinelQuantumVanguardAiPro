# 🛡️ Sentinel Quantum Vanguard AI Pro

**Système de Surveillance et de Gestion VPN Avancé avec Intelligence Artificielle**

[![Déploiement](https://img.shields.io/badge/Cloudflare-Pages-orange?logo=cloudflare)](https://sentinelquantumvanguardaipro.pages.dev)
[![Framework](https://img.shields.io/badge/React-18-blue?logo=react)](https://react.dev)
[![Build](https://img.shields.io/badge/Vite-5-646CFF?logo=vite)](https://vitejs.dev)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

## 📖 Vue d'Ensemble

Sentinel Quantum Vanguard AI Pro est une plateforme complète de cybersécurité et de gestion VPN qui combine:
- 🔐 Gestion avancée de nœuds VPN WireGuard
- 📊 Monitoring en temps réel des systèmes
- 🗺️ Visualisation géographique des serveurs
- 🤖 Intelligence artificielle pour la détection des menaces
- 📈 Tableaux de bord analytiques interactifs
- 📞 **Module fusionné : Sécurité téléphonique + Assistant vocal IA**

## 🚀 Accès Rapide

- **Application Live**: [sentinelquantumvanguardaipro.pages.dev](https://sentinelquantumvanguardaipro.pages.dev)
- **État du Déploiement**: [DEPLOYMENT_STATUS.md](DEPLOYMENT_STATUS.md)
- **Guide de Déploiement**: [DEPLOYMENT.md](DEPLOYMENT.md)

## 🏗️ Architecture

```
SentinelQuantumVanguardAiPro/
├── frontend/              # Application React + Vite
│   ├── src/
│   │   ├── pages/        # Pages principales
│   │   │   ├── Diagnostic.jsx       # Dashboard diagnostic système
│   │   │   └── admin/
│   │   │       └── vpn-console.jsx  # Console VPN management
│   │   ├── components/   # Composants réutilisables
│   │   │   ├── VpnMap.jsx           # Carte interactive Leaflet
│   │   │   ├── VpnLogs.jsx          # Logs de connexion
│   │   │   └── VpnControlPanel.jsx  # Panneau de contrôle
│   │   ├── App.jsx       # Page d'accueil
│   │   └── firebaseConfig.js        # Configuration Firebase
│   └── dist/             # Build de production
├── backend/              # Backend (à déployer)
│   ├── routes/
│   │   └── vpn.ts        # API VPN (AdonisJS)
│   ├── vpn_nodes.json    # Données des nœuds VPN
│   └── update_vpn_nodes.sh
├── functions/            # Cloudflare Pages Functions
│   └── vpnList.js        # API endpoint /vpnList
├── oracle-vpn-node/      # Configuration Oracle Cloud VPN
│   └── setup-oracle-node.sh
├── .github/workflows/    # CI/CD
│   ├── deploy.yml        # Déploiement automatique
│   └── sentinel-audit-matrix.yml  # Rapports quotidiens
└── public/              # Assets statiques
```

## ✨ Fonctionnalités

### 🎯 Actuellement Déployées

#### 1. Page d'Accueil (`/`)
- Navigation par cartes vers les différentes sections
- Design moderne avec Tailwind CSS
- Responsive sur tous les appareils

#### 2. Dashboard Diagnostic (`/diagnostic`)
- **Métriques système en temps réel:**
  - CPU, Mémoire, Disque (avec barres de progression)
  - État du réseau
  - Uptime système
- **Surveillance des services:**
  - Statut en ligne/hors ligne
  - Indicateurs visuels animés
- **Logs système:**
  - Derniers événements avec niveaux (info, warning, error)
  - Timestamps précis
- **Graphique de performance:**
  - Visualisation sur 24h (QuickChart)
  - Évolution CPU/Mémoire/Disque

#### 3. Console VPN (`/admin/vpn-console`)
- **Statut VPN en direct:**
  - Indicateur actif/inactif avec badges colorés
  - Uptime et dernier ping
- **Carte interactive:**
  - Visualisation géographique des serveurs (Leaflet)
  - Markers avec popups d'information
- **Logs de connexion:**
  - Liste des utilisateurs connectés
  - Pays et timestamps
- **Panneau de contrôle:**
  - Boutons Démarrer/Arrêter/Rafraîchir
  - Synchronisation temps réel avec Firestore

#### 4. API Functions
- **`/vpnList`** - Récupération de la liste des nœuds VPN depuis GitHub

### 🚧 En Développement (PRs Ouvertes)

#### PR #18 - Fonctionnalités Avancées
- Page `/telechargement` avec QR code et vérification IA
- Page `/journal` pour monitoring global des menaces avec analytics
- Page `/admin/logs` pour console avancée des logs
- Navigation unifiée avec Navbar responsive
- Documentation complète (ROADMAP.md, MODULES.md, etc.)

#### PR #19 - Synchronisation Firestore
- Workflow automatique de sync des alertes
- Collection `sentinel_alerts` pour dashboard

#### PR #20 - Live Status Dashboard
- Monitoring en direct (toutes les 10 minutes)
- Dashboard visuel à `/status/`
- Génération automatique de JSON et HTML

## 🔧 Installation Locale

### Prérequis
- Node.js 20.x
- npm ou yarn

### Frontend

```bash
cd frontend
npm install
npm run dev
```

L'application sera accessible à `http://localhost:5173`

### Build de Production

```bash
cd frontend
npm run build
```

Le build sera généré dans `frontend/dist/`

## 🌐 Déploiement

### Déploiement Automatique (Cloudflare Pages)

Le déploiement se fait automatiquement via GitHub Actions lors d'un push sur la branche `main`:

1. Build du frontend
2. Publication sur Cloudflare Pages
3. Disponible sur `sentinelquantumvanguardaipro.pages.dev`

### Déploiement Manuel

```bash
# Installer Wrangler
npm install -g wrangler

# Build
cd frontend && npm run build

# Déployer
wrangler pages publish frontend/dist --project-name=sentinelquantumvanguardaipro
```

## 🔑 Configuration

### Firebase (Requis pour le fonctionnement complet)

Le fichier `frontend/src/firebaseConfig.js` contient actuellement des valeurs placeholder:

```javascript
const firebaseConfig = {
  apiKey: "TA_CLE_API",           // ⚠️ À remplacer
  authDomain: "sentinel-ai.firebaseapp.com",
  projectId: "sentinel-ai",
  storageBucket: "sentinel-ai.appspot.com",
  messagingSenderId: "1234567890",
  appId: "1:1234567890:web:xxxxxx"
};
```

**Pour activer Firebase:**
1. Créer un projet sur [Firebase Console](https://console.firebase.google.com)
2. Activer Firestore Database
3. Créer les collections: `system_diagnostics`, `services_status`, `system_logs`, `vpn_status`, `vpn_servers`, `vpn_logs`, `vpn_control`
4. Récupérer les clés de configuration
5. Mettre à jour `firebaseConfig.js`

### Variables d'Environnement (GitHub Actions)

Secrets nécessaires dans les paramètres du repository:
- `CLOUDFLARE_API_TOKEN` - Pour Cloudflare Pages
- `FIREBASE_API_KEY` - Configuration Firebase
- `FIREBASE_PROJECT_ID` - ID du projet Firebase
- `BOT_TOKEN` - Token Telegram (optionnel, pour notifications)
- `CHAT_ID` - Chat ID Telegram (optionnel)

## 📊 État du Déploiement

Consulter [DEPLOYMENT_STATUS.md](DEPLOYMENT_STATUS.md) pour:
- ✅ Ce qui est déployé
- 🔴 Ce qui reste à déployer
- 📋 Plan de déploiement détaillé
- 🎯 Actions prioritaires

**Résumé rapide:**
- Frontend: ✅ Déployé et fonctionnel
- Firebase: 🔴 Configuration à compléter
- Backend API: 🔴 À déployer
- Oracle VPN Node: 🔴 À provisionner
- Workflows avancés: 🟡 En PR, à merger

## 🛠️ Technologies Utilisées

### Frontend
- **React 18** - Framework UI
- **Vite 5** - Build tool ultra-rapide
- **Tailwind CSS 3.4** - Styling utility-first
- **React Router 6** - Routing côté client
- **Firebase 12** - Backend as a Service (Firestore)
- **Leaflet / React-Leaflet** - Cartes interactives
- **QuickChart** - Graphiques embarqués

### Backend
- **AdonisJS** - Framework Node.js (à déployer)
- **Cloudflare Pages Functions** - Serverless API endpoints

### Infrastructure
- **Cloudflare Pages** - Hébergement frontend
- **GitHub Actions** - CI/CD automatisé
- **Oracle Cloud** - VPN nodes (WireGuard)

### DevOps
- **Wrangler** - CLI Cloudflare
- **PostCSS** - Transformation CSS
- **ESLint** - Linting JavaScript

## 📝 Workflows CI/CD

### 1. Déploiement (`.github/workflows/deploy.yml`)
- **Déclenchement:** Push sur `main` ou manuel
- **Actions:**
  - Checkout du code
  - Installation Node.js 20
  - Build du frontend
  - Publication sur Cloudflare Pages

### 2. Sentinel Audit Matrix (`.github/workflows/sentinel-audit-matrix.yml`)
- **Déclenchement:** Quotidien à 03:10 UTC
- **Actions:**
  - Collecte des données CI/CD (24h)
  - Vérification du statut HTTP du site
  - Génération rapport Markdown
  - Conversion en PDF
  - Publication en Release
  - Notification Telegram (optionnelle)

## 🤝 Contribution

Les contributions sont les bienvenues! Pour contribuer:

1. Fork le projet
2. Créer une branche feature (`git checkout -b feature/AmazingFeature`)
3. Commit les changements (`git commit -m 'Add AmazingFeature'`)
4. Push vers la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## 📄 License

Ce projet est sous licence MIT. Voir le fichier `LICENSE` pour plus de détails.

## 👤 Auteur

**teetee971**

- GitHub: [@teetee971](https://github.com/teetee971)

## 🔗 Liens Utiles

- [Documentation Cloudflare Pages](https://developers.cloudflare.com/pages/)
- [React Documentation](https://react.dev)
- [Firebase Documentation](https://firebase.google.com/docs)
- [Tailwind CSS](https://tailwindcss.com)
- [WireGuard](https://www.wireguard.com)

## 📞 Support

Pour toute question ou problème:
- Ouvrir une [Issue](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues)
- Consulter la [documentation](DEPLOYMENT.md)
- Vérifier l'[état du déploiement](DEPLOYMENT_STATUS.md)

---

**Status:** 🟡 En développement actif | **Version:** 1.0.0 | **Dernière mise à jour:** 2025-11-02
