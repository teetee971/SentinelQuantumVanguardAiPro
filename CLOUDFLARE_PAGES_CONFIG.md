# Cloudflare Pages - Configuration de Déploiement

## Configuration Recommandée (Site Statique)

### Option 1: Site Statique Pure (RECOMMANDÉ)

Cette configuration est la plus simple et la plus fiable pour Cloudflare Pages.

**Configuration dans l'interface Cloudflare Pages:**

```
Project name: sentinel-quantum-vanguard-ai-pro
Production branch: main
Build command: (laisser vide)
Build output directory: /
Root directory: (laisser vide)
Environment variables: (aucune requise)
```

**Avantages:**
- ✅ Déploiement immédiat sans build
- ✅ Zéro erreur de build possible
- ✅ Performance maximale
- ✅ Maintenance minimale

### Option 2: Avec Build Vite (OPTIONNEL)

Si vous souhaitez utiliser Vite pour optimiser les assets:

**Configuration dans l'interface Cloudflare Pages:**

```
Project name: sentinel-quantum-vanguard-ai-pro
Production branch: main
Build command: npm install && npm run build
Build output directory: dist
Root directory: (laisser vide)
Node.js version: 18.x
Environment variables: (aucune requise)
```

**Prérequis:**
- ✅ `package.json` présent (déjà créé)
- ✅ `vite.config.js` présent (déjà créé)
- ✅ Tests de build local avant déploiement

---

## Procédure de Configuration (Interface Web)

### Étape 1: Accéder à Cloudflare Pages

1. Connexion à votre compte Cloudflare
2. Naviger vers **Pages** dans le menu latéral
3. Cliquer sur **Create a project**

### Étape 2: Connecter le Repository GitHub

1. Sélectionner **Connect to Git**
2. Autoriser Cloudflare à accéder à GitHub
3. Sélectionner le repository: `teetee971/SentinelQuantumVanguardAiPro`
4. Cliquer sur **Begin setup**

### Étape 3: Configurer le Projet

**Pour Option 1 (Statique - Recommandé):**
```
Build settings:
  Framework preset: None
  Build command: (vide)
  Build output directory: /
```

**Pour Option 2 (Vite - Optionnel):**
```
Build settings:
  Framework preset: Vite
  Build command: npm install && npm run build
  Build output directory: dist
```

### Étape 4: Variables d'Environnement

Aucune variable d'environnement n'est requise pour ce projet.

### Étape 5: Déployer

1. Cliquer sur **Save and Deploy**
2. Cloudflare va:
   - Cloner le repository
   - Exécuter le build (si configuré)
   - Déployer le site
   - Fournir une URL de production

---

## Vérification du Déploiement

### Checklist Post-Déploiement

Après le déploiement, vérifier:

- [ ] Homepage accessible (`index.html`)
- [ ] Navigation fonctionnelle (8 pages)
- [ ] Styles CSS chargés correctement
- [ ] JavaScript fonctionnel
- [ ] Responsive design (mobile)
- [ ] Aucune erreur console
- [ ] Feature flags accessibles
- [ ] Pages publiques accessibles (`/public/*.html`)

### URLs à Tester

```
https://[votre-projet].pages.dev/
https://[votre-projet].pages.dev/public/about.html
https://[votre-projet].pages.dev/public/security-audit.html
https://[votre-projet].pages.dev/public/demo-phase-f.html
https://[votre-projet].pages.dev/public/system-status.html
https://[votre-projet].pages.dev/public/roadmap.html
https://[votre-projet].pages.dev/public/legal.html
https://[votre-projet].pages.dev/public/changelog.html
```

---

## Déploiements Automatiques

### Configuration Git

Cloudflare Pages déploiera automatiquement:

- **Branch `main`:** Déploiement en production
- **Pull Requests:** Preview deployments automatiques
- **Autres branches:** Preview deployments (optionnel)

### Triggers de Déploiement

Chaque `git push` sur `main` déclenchera:
1. Clone du repository
2. Build (si configuré)
3. Déploiement automatique
4. Invalidation cache CDN
5. URL de production mise à jour

---

## Domaine Personnalisé (Optionnel)

### Configurer un Domaine Custom

1. Dans Cloudflare Pages, aller dans l'onglet **Custom domains**
2. Cliquer sur **Set up a custom domain**
3. Entrer votre domaine
4. Suivre les instructions DNS

**Exemple:**
```
sentinel-vanguard.com → Pages deployment
www.sentinel-vanguard.com → Pages deployment
```

---

## Troubleshooting

### Erreur: "Build failed"

**Solution pour Option 1 (Statique):**
- Vérifier que Build command est vide
- Vérifier que Output directory est `/`

**Solution pour Option 2 (Vite):**
- Tester build local: `npm install && npm run build`
- Vérifier que `package.json` et `vite.config.js` sont présents
- Vérifier logs de build dans Cloudflare

### Erreur: "404 Not Found"

**Solution:**
- Vérifier que `index.html` est à la racine
- Vérifier que Output directory est correct
- Tester les URLs avec `/public/` prefix pour pages secondaires

### Erreur: "Assets not loading"

**Solution:**
- Vérifier chemins CSS/JS (relatifs recommandés)
- Vérifier que `/public/` directory est présent
- Clear cache navigateur

---

## Performance & Optimisation

### Cloudflare Pages fournit automatiquement:

- ✅ **CDN Global:** Distribution mondiale rapide
- ✅ **Cache Automatique:** Assets mis en cache
- ✅ **Compression:** Gzip/Brotli automatique
- ✅ **HTTPS:** Certificat SSL automatique
- ✅ **HTTP/2:** Support automatique
- ✅ **IPv6:** Support automatique

### Aucune configuration additionnelle requise!

---

## Monitoring

### Métriques Disponibles dans Cloudflare

- 📊 **Analytics:** Visites, pages vues, géolocalisation
- 🌍 **Performance:** Temps de chargement par région
- 🔒 **Security:** Requêtes bloquées, menaces
- 📈 **Bandwidth:** Utilisation bande passante

---

## Rollback

### En cas de problème avec un déploiement:

**Option 1: Rollback via Cloudflare UI**
1. Aller dans **Deployments**
2. Trouver le déploiement précédent fonctionnel
3. Cliquer sur **Rollback to this deployment**

**Option 2: Git Revert**
```bash
git revert HEAD
git push origin main
```

**Option 3: Redéployer une branche spécifique**
1. Merger la branche stable vers `main`
2. Push automatiquement redéploie

---

## Résumé des Commandes

### Build Local (Test avant déploiement)

**Option 1 (Statique):**
```bash
# Aucun build requis
# Ouvrir index.html directement dans navigateur
```

**Option 2 (Vite):**
```bash
npm install
npm run build
npm run preview
# Tester sur http://localhost:4173
```

### Déploiement

```bash
git add .
git commit -m "Update frontend"
git push origin main
# Cloudflare déploie automatiquement
```

---

## Support & Documentation

**Documentation Cloudflare Pages:**
- https://developers.cloudflare.com/pages/

**Support Cloudflare:**
- https://community.cloudflare.com/
- https://support.cloudflare.com/

**Documentation Projet:**
- `/AUDIT_TECHNIQUE_COMPLET.md` - Audit complet
- `/PROJECT_STATUS.md` - Statut projet
- `/README.md` - Documentation principale

---

**Date de création:** 2025-12-13  
**Configuration:** Site Statique (recommandé) ou Vite (optionnel)  
**Statut:** ✅ Prêt pour déploiement immédiat
