# Security Policy — Sentinel Quantum Vanguard AI Pro

## Portée

Application frontend statique hébergée sur Cloudflare Pages.

## Architecture

- **Aucun backend**
- **Aucun serveur Node.js**
- **Aucune API exposée**
- **Aucun stockage utilisateur**

## CVE-2024-29415 (Dependabot)

**Statut** : IGNORÉ (justifié)

### Détails de la vulnérabilité

- **CVE** : CVE-2024-29415
- **CWE** : CWE-918 (Server-Side Request Forgery)
- **Package** : `ip` <= 2.0.1
- **Dépendance** : Transitive via `vite` (devDependency uniquement)
- **Sévérité** : Moderate

### Justification

Cette vulnérabilité SSRF n'est **pas exploitable** dans le contexte de Sentinel Quantum Vanguard AI Pro pour les raisons suivantes :

1. **Dépendance utilisée uniquement au build**
   - Le package `ip` n'est jamais exécuté en production
   - Utilisé uniquement lors de la compilation en environnement CI/CD isolé via `vite`

2. **Aucun serveur d'exécution**
   - Application frontend statique
   - Déployée sur Cloudflare Pages (CDN uniquement)
   - Aucun runtime Node.js en production

3. **Aucune requête réseau sortante**
   - Pas de code serveur
   - Pas d'API backend
   - Pas de traitement côté serveur

4. **Aucun vecteur exploitable en production**
   - La vulnérabilité SSRF requiert une exécution côté serveur
   - L'application est 100% statique (HTML/CSS/JS)

### Décision

✅ **IGNORER avec justification documentée**

Cette vulnérabilité ne présente aucun risque réel pour la sécurité de l'application en production.

## Surveillance recommandée

- ✅ Maintenir Cloudflare Pages à jour
- ✅ Vérifier alertes Dependabot périodiquement
- ✅ Audit visuel périodique du code
- ✅ Surveillance des dépendances npm

## Bonnes pratiques appliquées

- **Surface d'attaque minimale** : Frontend statique uniquement
- **Pas de collecte de données** : Aucune donnée utilisateur stockée
- **Déploiement sécurisé** : Cloudflare Edge Network avec protection DDoS native
- **Dépendances contrôlées** : Audit régulier des dépendances npm

## Contact

Les rapports de sécurité peuvent être ouverts via [GitHub Issues](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues).

Pour les vulnérabilités critiques, veuillez contacter directement l'équipe via les canaux privés.

---

**Dernière mise à jour** : 2024-12-14  
**Responsable sécurité** : Sentinel Security Team
