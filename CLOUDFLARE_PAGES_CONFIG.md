# Cloudflare Pages — Configuration actuelle

## Source de vérité

Le site web/PWA de Sentinel Quantum Vanguard AI Pro est déployé sur Cloudflare Pages depuis la branche `main`.

- Projet : `sentinel-quantum-vanguard-ai-pro`
- Sortie de build : `frontend/dist`
- Script de build : `npm run build`
- Runtime Node.js : respecter l'engin défini dans `package.json` (Node.js >= 20.19.0)
- URL publique connue : `https://sentinelquantumvanguardaipro.pages.dev/`

Cette documentation décrit uniquement la configuration actuelle. Les anciennes variantes statiques/Vite et les anciens chemins de sortie ne sont plus des configurations supportées.

## Fonctionnement du build

1. Cloudflare récupère la branche `main`.
2. Les dépendances sont installées conformément au lockfile.
3. `npm run build` exécute le build du projet.
4. Le script de build prépare `frontend/dist`.
5. Cloudflare Pages publie `frontend/dist`.

Le déploiement ne doit pas être déclaré réussi sur la seule base de la présence d'une configuration : il faut vérifier le résultat réel du déploiement.

## Vérification post-déploiement

Contrôler au minimum :

- page d'accueil accessible ;
- ressources CSS/JavaScript chargées ;
- navigation interne fonctionnelle ;
- affichage mobile ;
- absence d'erreurs bloquantes dans la console ;
- pages publiques réellement présentes dans le build ;
- cohérence entre le contenu de `main` et le contenu publié.

## CI/CD et sécurité

Les workflows GitHub Actions restent soumis aux contrôles de sécurité du dépôt, notamment le pinning des actions et les contrôles d'isolation.

Un échec GitHub Actions avant l'exécution des premières étapes est un problème d'infrastructure/exécution CI ; il ne doit pas être présenté comme un échec fonctionnel du code ni contourné par un affaiblissement des contrôles.

## Domaine personnalisé

Un domaine personnalisé peut être configuré directement dans Cloudflare Pages. Aucun domaine personnalisé n'est considéré comme actif dans cette documentation sans preuve de configuration et de résolution DNS.

## Rollback

En cas de régression, utiliser l'historique des déploiements Cloudflare Pages ou revenir explicitement à un commit Git vérifié. Tout rollback doit être suivi d'une vérification du contenu effectivement servi.

## Références du dépôt

- `README.md` : présentation générale.
- `AUDIT.md` : état d'audit courant.
- `SECURITY.md` : exigences de sécurité.
- `internal/OFFICIAL_BASELINE.md` : baseline officielle.

## Séparation des projets

Sentinel Quantum Vanguard AI Pro est strictement indépendant de **A KI PRI SA YÉ**. Aucun déploiement, secret, dépendance, configuration ou flux de données de A KI PRI SA YÉ ne doit être introduit dans ce dépôt.

**Statut documentaire : configuration actuelle, sans certification de production.**
