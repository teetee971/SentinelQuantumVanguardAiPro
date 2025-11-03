# ✅ Ce qui reste à déployer - Checklist Rapide

## 🔴 PRIORITÉ HAUTE - Configuration Firebase

**Pourquoi?** Sans Firebase, les pages Diagnostic et VPN Console ne fonctionnent pas.

- [ ] Créer projet Firebase sur [console.firebase.google.com](https://console.firebase.google.com)
- [ ] Activer Firestore Database
- [ ] Créer les collections Firestore:
  - [ ] `system_diagnostics` (pour /diagnostic)
  - [ ] `services_status` (pour /diagnostic)
  - [ ] `system_logs` (pour /diagnostic)
  - [ ] `vpn_status` (pour /admin/vpn-console)
  - [ ] `vpn_servers` (pour /admin/vpn-console)
  - [ ] `vpn_logs` (pour /admin/vpn-console)
  - [ ] `vpn_control` (pour /admin/vpn-console)
- [ ] Récupérer les clés de configuration Firebase
- [ ] Remplacer les valeurs dans `frontend/src/firebaseConfig.js`:
  ```javascript
  const firebaseConfig = {
    apiKey: "VOTRE_VRAIE_CLE",
    authDomain: "votre-projet.firebaseapp.com",
    projectId: "votre-projet",
    storageBucket: "votre-projet.appspot.com",
    messagingSenderId: "VOTRE_ID",
    appId: "VOTRE_APP_ID"
  };
  ```
- [ ] Commit et push les changements
- [ ] Vérifier que les pages se déploient correctement

**Temps estimé:** 2-3 heures

---

## 🔴 PRIORITÉ HAUTE - Backend API

**Pourquoi?** Nécessaire pour mettre à jour les nœuds VPN via l'API.

### Option 1: Cloudflare Workers (Recommandé)

- [ ] Créer un nouveau Worker Cloudflare
- [ ] Convertir `backend/routes/vpn.ts` en Cloudflare Worker
- [ ] Endpoint à créer: `POST /api/vpn/update`
- [ ] Configurer KV storage pour remplacer `vpn_nodes.json`
- [ ] Tester avec curl:
  ```bash
  curl -X POST https://votre-worker.workers.dev/api/vpn/update \
    -H "Content-Type: application/json" \
    -d '{"provider":"Test","ip":"1.2.3.4","port":51820,"public_key":"abc123","country":"France","region":"Paris"}'
  ```
- [ ] Mettre à jour l'URL dans les scripts clients

### Option 2: Autre hébergement (Heroku/Railway/Render)

- [ ] Choisir une plateforme d'hébergement
- [ ] Déployer le backend AdonisJS existant
- [ ] Configurer la base de données (si nécessaire)
- [ ] Configurer CORS pour le frontend
- [ ] Tester l'endpoint

**Temps estimé:** 4-6 heures

---

## 🟡 PRIORITÉ MOYENNE - Oracle VPN Node

**Pourquoi?** Pour avoir un vrai nœud VPN fonctionnel.

- [ ] Créer compte Oracle Cloud (si pas déjà fait)
- [ ] Créer une instance compute ARM (Always Free Tier):
  - Shape: VM.Standard.A1.Flex
  - RAM: 6 GB
  - OS: Ubuntu 22.04
- [ ] Se connecter en SSH à l'instance
- [ ] Copier et exécuter `oracle-vpn-node/setup-oracle-node.sh`
- [ ] Récupérer la clé publique du serveur:
  ```bash
  echo $WG_PUBLIC_KEY
  ```
- [ ] Noter l'IP publique de l'instance
- [ ] Configurer le Security List dans Oracle Cloud:
  - Autoriser UDP port 51820
- [ ] Mettre à jour `backend/vpn_nodes.json`:
  ```json
  {
    "nodes": [
      {
        "provider": "Oracle Cloud Free Tier",
        "ip": "VOTRE_IP_PUBLIQUE",
        "port": 51820,
        "public_key": "VOTRE_CLE_PUBLIQUE",
        "status": "online",
        "country": "France",
        "region": "Paris",
        "updated_at": "2025-11-02T22:00:00Z"
      }
    ]
  }
  ```
- [ ] Commit et push les changements
- [ ] Tester la connectivité WireGuard

**Temps estimé:** 2-3 heures

---

## 🟡 PRIORITÉ MOYENNE - Secrets GitHub Actions

**Pourquoi?** Pour activer toutes les fonctionnalités des workflows.

Aller dans: Settings → Secrets and variables → Actions → New repository secret

- [ ] `CLOUDFLARE_API_TOKEN` (peut déjà exister)
  - Obtenir sur: Cloudflare Dashboard → API Tokens
  - Permissions: Cloudflare Pages
- [ ] `FIREBASE_API_KEY`
  - Copier depuis la config Firebase
- [ ] `FIREBASE_PROJECT_ID`
  - Copier depuis la config Firebase
- [ ] `BOT_TOKEN` (optionnel - pour Telegram)
  - Créer un bot avec @BotFather sur Telegram
- [ ] `CHAT_ID` (optionnel - pour Telegram)
  - Obtenir en envoyant `/start` au bot et en checkant l'API
- [ ] `FIRESTORE_SERVICE_KEY` (pour PR #19)
  - Générer une clé de compte de service Firebase

**Temps estimé:** 1 heure

---

## 🟢 PRIORITÉ BASSE - Merge des PRs

**Pourquoi?** Ajoute des fonctionnalités supplémentaires.

### PR #18 - Documentation & Pages supplémentaires
- [ ] Review le code
- [ ] Tester localement:
  ```bash
  git fetch origin copilot/update-roadmap-progress
  git checkout copilot/update-roadmap-progress
  cd frontend && npm install && npm run dev
  ```
- [ ] Vérifier les nouvelles pages:
  - [ ] `/telechargement` - Page de téléchargement avec QR code
  - [ ] `/journal` - Journal global des menaces
  - [ ] `/admin/logs` - Console des logs
- [ ] Merger dans main si OK
- [ ] Vérifier le déploiement

### PR #19 - Firestore Alert Sync
- [ ] Review le workflow `.github/workflows/firestore-sync.yml`
- [ ] Configurer le secret `FIRESTORE_SERVICE_KEY`
- [ ] Merger dans main
- [ ] Tester le workflow manuellement

### PR #20 - Live Status Dashboard
- [ ] Review le workflow `.github/workflows/live-status-dashboard.yml`
- [ ] Merger dans main
- [ ] Attendre l'exécution automatique (toutes les 10 min)
- [ ] Vérifier `/status/` sur le site

**Temps estimé:** 3-4 heures

---

## 📊 Progression Globale

- ✅ **Déjà fait:** ~40%
  - Frontend construit et déployé
  - Workflow CI/CD en place
  - Structure de base fonctionnelle

- 🔴 **Reste à faire:** ~60%
  - Configuration Firebase: 25%
  - Backend API: 20%
  - Oracle VPN Node: 10%
  - Secrets & PRs: 5%

---

## 🎯 Ordre Recommandé

1. **Jour 1:** Firebase Configuration (CRITIQUE)
   - Sans cela, les pages ne fonctionnent pas vraiment

2. **Jour 2:** Backend API
   - Permet la mise à jour dynamique des nœuds

3. **Jour 3:** Oracle VPN Node
   - Fournit un vrai nœud VPN fonctionnel

4. **Jour 4:** Secrets & PRs
   - Active toutes les fonctionnalités avancées

---

## 🆘 En cas de problème

1. Vérifier les logs GitHub Actions
2. Consulter [DEPLOYMENT_STATUS.md](DEPLOYMENT_STATUS.md) pour les détails
3. Consulter [DEPLOYMENT.md](DEPLOYMENT.md) pour le guide
4. Ouvrir une issue sur GitHub

---

**Dernière mise à jour:** 2025-11-02  
**Document complet:** [DEPLOYMENT_STATUS.md](DEPLOYMENT_STATUS.md)
