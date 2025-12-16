# Security Policy — Sentinel Quantum Vanguard AI Pro

## Table des Matières

1. [Portée](#portée)
2. [Architecture de Sécurité](#architecture-de-sécurité)
3. [Gestion des Secrets](#gestion-des-secrets)
4. [Signature APK](#signature-apk)
5. [Intégrité des Releases](#intégrité-des-releases)
6. [Signalement Responsable](#signalement-responsable)
7. [Vulnérabilités Connues](#vulnérabilités-connues)
8. [Bonnes Pratiques](#bonnes-pratiques)

---

## Portée

Application frontend statique + APK Android, distribués via :
- **Web** : Cloudflare Pages (CDN global)
- **Mobile** : GitHub Releases (téléchargement direct)

---

## Architecture de Sécurité

### Principes Fondamentaux

| Principe | Implémentation |
|----------|----------------|
| Surface d'attaque minimale | Frontend statique, aucun backend |
| Zéro collecte de données | Aucune API, aucun tracking |
| Transparence totale | Code source 100% public |
| Vérifiabilité | SHA-256 + signature APK |

### Composants

```
┌─────────────────────────────────────────────────────────────┐
│                    ARCHITECTURE SÉCURISÉE                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │   Frontend Web  │    │   APK Android   │                │
│  │   (Statique)    │    │   (Signé)       │                │
│  └────────┬────────┘    └────────┬────────┘                │
│           │                      │                          │
│           ▼                      ▼                          │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │ Cloudflare CDN  │    │ GitHub Releases │                │
│  │ (Edge Network)  │    │ (Direct DL)     │                │
│  └─────────────────┘    └─────────────────┘                │
│                                                             │
│  ❌ Aucun serveur backend                                  │
│  ❌ Aucune base de données                                 │
│  ❌ Aucune API exposée                                     │
│  ❌ Aucune collecte de données                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Gestion des Secrets

### Secrets GitHub Actions

| Secret | Usage | Rotation |
|--------|-------|----------|
| `RELEASE_KEYSTORE_BASE64` | Keystore de signature APK | Annuelle |
| `RELEASE_KEYSTORE_PASSWORD` | Mot de passe keystore | Annuelle |
| `RELEASE_KEY_ALIAS` | Alias de la clé | Fixe |
| `RELEASE_KEY_PASSWORD` | Mot de passe clé | Annuelle |

### Politique de Gestion

1. **Aucun secret en clair** dans le code source
2. **Validation obligatoire** : le workflow échoue si un secret est manquant
3. **Nettoyage automatique** : le keystore est détruit après usage (`shred` ou `rm`)
4. **Logs protégés** : les secrets utilisent des variables d'environnement (pas d'interpolation directe)

### Vérification CI/CD

```bash
# Le workflow release-apk.yml vérifie :
# 1. Présence de tous les secrets
# 2. Validité du keystore (taille minimale)
# 3. Signature APK réussie
# 4. Destruction du keystore temporaire
```

---

## Signature APK

### Algorithme

| Paramètre | Valeur |
|-----------|--------|
| Algorithme | RSA |
| Taille de clé | 4096 bits |
| Format keystore | PKCS12 |
| Validité | 10 000 jours (~27 ans) |
| Schéma de signature | APK Signature Scheme v2/v3 |

### Vérification de la Signature

```bash
# Avec apksigner (Android SDK)
apksigner verify --verbose --print-certs SentinelQuantumVanguardAIPro-v1.0.0.apk

# Avec jarsigner (JDK)
jarsigner -verify -verbose -certs SentinelQuantumVanguardAIPro-v1.0.0.apk
```

### Sortie Attendue

```
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Number of signers: 1
```

---

## Intégrité des Releases

### SHA-256 Checksum

Chaque release APK est accompagnée d'un fichier `.sha256` contenant le hash SHA-256.

**Vérification** :

```bash
# Linux / macOS
sha256sum -c SentinelQuantumVanguardAIPro-v1.0.0.apk.sha256

# Windows (PowerShell)
$expected = (Get-Content SentinelQuantumVanguardAIPro-v1.0.0.apk.sha256).Split()[0]
$computed = (Get-FileHash SentinelQuantumVanguardAIPro-v1.0.0.apk -Algorithm SHA256).Hash
if ($computed -eq $expected.ToUpper()) { "OK" } else { "ERREUR" }
```

### Publication

Les fichiers suivants sont publiés ensemble sur GitHub Releases :
- `SentinelQuantumVanguardAIPro-vX.Y.Z.apk`
- `SentinelQuantumVanguardAIPro-vX.Y.Z.apk.sha256`

---

## Signalement Responsable

### Comment Signaler une Vulnérabilité

1. **Vulnérabilités non-critiques** :
   - Ouvrir une [Issue GitHub](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues)
   - Utiliser le label `security`

2. **Vulnérabilités critiques** :
   - Ne pas publier publiquement
   - Contacter via les canaux privés GitHub
   - Délai de réponse : 48 heures ouvrées

### Informations à Fournir

- Description de la vulnérabilité
- Étapes de reproduction
- Impact potentiel
- Version concernée
- Proposition de correction (optionnel)

### Périmètre

| Composant | Dans le scope |
|-----------|---------------|
| Code source GitHub | ✅ Oui |
| APK Android | ✅ Oui |
| Frontend web | ✅ Oui |
| Infrastructure Cloudflare | ❌ Non (contacter Cloudflare) |
| GitHub Actions | ❌ Non (contacter GitHub) |

---

## Vulnérabilités Connues

### CVE-2024-29415 (Dependabot)

**Statut** : IGNORÉ (justifié)

| Détail | Valeur |
|--------|--------|
| CVE | CVE-2024-29415 |
| CWE | CWE-918 (SSRF) |
| Package | `ip` <= 2.0.1 |
| Dépendance | Transitive via `vite` (devDependency) |
| Sévérité | Moderate |

**Justification** :
- Package utilisé uniquement au build (jamais en production)
- Application 100% statique (aucun runtime Node.js)
- Aucun vecteur d'exploitation possible

---

## Bonnes Pratiques

### Développement

- ✅ Aucun secret dans le code source
- ✅ Dépendances auditées régulièrement (`npm audit`)
- ✅ CodeQL activé sur chaque PR
- ✅ Build reproductible

### Déploiement

- ✅ HTTPS uniquement (Cloudflare)
- ✅ Headers de sécurité (CSP, X-Frame-Options)
- ✅ Protection DDoS native (Cloudflare)

### Distribution APK

- ✅ Signature cryptographique RSA 4096
- ✅ Checksum SHA-256 publié
- ✅ Téléchargement direct (aucun intermédiaire)

---

## Contact Sécurité

**Équipe** : Sentinel Security Team  
**Réponse** : 48 heures ouvrées  
**Canal** : [GitHub Issues](https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues) (label `security`)

---

**Dernière mise à jour** : Décembre 2025  
**Version** : 2.0
