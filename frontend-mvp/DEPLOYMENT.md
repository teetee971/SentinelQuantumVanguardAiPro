# Guide de Déploiement - Frontend MVP

## Déploiement sur Cloudflare Pages

### Prérequis
- Compte Cloudflare avec accès à Cloudflare Pages
- Repository GitHub connecté
- Backend API fonctionnel

### Étapes de Configuration

#### 1. Connexion du Repository
1. Se connecter au dashboard Cloudflare Pages
2. Cliquer sur "Create a project"
3. Connecter votre compte GitHub
4. Sélectionner le repository `SentinelQuantumVanguardAiPro`
5. Choisir la branche de production

#### 2. Configuration du Build

**Framework preset**: None (ou Vite)

**Build settings**:
```
Build command: npm run build
Build output directory: dist
Root directory: frontend-mvp
Node version: 18
```

#### 3. Variables d'Environnement

Ajouter dans "Environment variables":

```
VITE_API_BASE_URL=https://votre-api.com/api
```

**Important**: Remplacer `https://votre-api.com/api` par l'URL réelle de votre API backend.

#### 4. Déploiement

1. Cliquer sur "Save and Deploy"
2. Cloudflare Pages va automatiquement:
   - Installer les dépendances (`npm install`)
   - Construire l'application (`npm run build`)
   - Déployer le contenu du dossier `dist`

#### 5. Configuration Personnalisée (Optionnel)

**Custom domain**: Configurer un domaine personnalisé dans Settings > Custom domains

**Preview deployments**: Chaque push sur une branche crée automatiquement un environnement de preview

### Post-Déploiement

#### Vérification
- [ ] L'application est accessible via l'URL Cloudflare Pages
- [ ] La navigation fonctionne correctement
- [ ] Les appels API utilisent la bonne URL
- [ ] Les formulaires sont fonctionnels
- [ ] Le design est responsive (tester sur mobile)

#### Configuration DNS (si domaine personnalisé)
1. Ajouter les enregistrements DNS fournis par Cloudflare
2. Attendre la propagation DNS (jusqu'à 48h)
3. Activer HTTPS automatique

### Workflow de Déploiement Continu

```yaml
# Le workflow de déploiement est automatique:
1. Push vers la branche configurée
2. Cloudflare Pages détecte le changement
3. Build automatique
4. Déploiement automatique
5. Notification de succès/échec
```

### URLs Importantes

- **Dashboard**: https://dash.cloudflare.com/
- **Pages Settings**: Dashboard > Pages > [Votre projet] > Settings
- **Deployments**: Dashboard > Pages > [Votre projet] > Deployments
- **Analytics**: Dashboard > Pages > [Votre projet] > Analytics

### Rollback en cas de Problème

1. Aller dans "Deployments"
2. Trouver le déploiement stable précédent
3. Cliquer sur "Rollback to this deployment"

### Support et Debug

**Logs de build**:
- Disponibles dans la section "Deployments"
- Chaque déploiement a ses logs détaillés

**Problèmes communs**:

1. **Build fail**: Vérifier les logs, souvent dû à:
   - Dépendances manquantes
   - Erreurs de syntaxe
   - Variables d'environnement manquantes

2. **API errors**: Vérifier:
   - `VITE_API_BASE_URL` est correctement définie
   - L'API backend est accessible
   - CORS est configuré sur le backend

3. **404 sur les routes**: Cloudflare Pages gère automatiquement les SPA, mais vérifier la configuration

### Performance

Cloudflare Pages offre automatiquement:
- ✅ CDN global
- ✅ Cache intelligent
- ✅ Compression Brotli/Gzip
- ✅ HTTP/2 et HTTP/3
- ✅ HTTPS automatique

### Coûts

- **Gratuit** pour la plupart des projets
- Limites généreuses du plan gratuit
- Voir: https://pages.cloudflare.com/pricing

---

## Déploiement Local pour Tests

```bash
# Build local
cd frontend-mvp
npm run build
npm run preview

# L'application sera disponible sur http://localhost:4173
```

---

**Note**: Ce guide suppose que le backend API est déjà déployé et accessible. Si ce n'est pas le cas, déployez d'abord le backend avant le frontend.
