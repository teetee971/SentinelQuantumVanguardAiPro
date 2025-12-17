# GitHub Actions Workflows Documentation

## Vue d'ensemble

Ce document décrit tous les workflows GitHub Actions configurés pour le repository **SentinelQuantumVanguardAiPro**.

## Table des matières

1. [Workflows de Build et Release](#workflows-de-build-et-release)
2. [Workflows de Sécurité](#workflows-de-sécurité)
3. [Workflows de Validation](#workflows-de-validation)
4. [Workflows de Déploiement](#workflows-de-déploiement)
5. [Configuration et Permissions](#configuration-et-permissions)

---

## Workflows de Build et Release

### 1. Build Android APK (`build-android.yml`)

**Statut**: ✅ Actif  
**Déclencheurs**: 
- Push sur `main`
- Déclenchement manuel (`workflow_dispatch`)

**Description**:  
Construit l'APK Android en mode debug pour tester l'application mobile.

**Étapes principales**:
1. Checkout du code
2. Configuration Java 17 (Temurin)
3. Configuration Gradle avec cache
4. Compilation de l'APK debug
5. Upload de l'artifact

**Permissions requises**:
- `contents: read` - Lecture du repository
- `actions: read` - Lecture des artifacts

**Outputs**:
- Artifact: `Sentinel-APK` contenant l'APK debug

**Utilisation**:
```bash
# Déclenchement manuel via GitHub UI
Actions → Build Android APK → Run workflow
```

---

### 2. Build and Release Android APK (`release-apk.yml`)

**Statut**: ✅ Actif (nécessite secrets)  
**Déclencheurs**:
- Push de tags `v*.*.*`
- Déclenchement manuel avec input version

**Description**:  
Construit et publie une version signée de l'APK Android avec création automatique d'une release GitHub.

**Secrets requis**:
- `RELEASE_KEYSTORE_BASE64` - Keystore encodé en base64
- `RELEASE_KEYSTORE_PASSWORD` - Mot de passe du keystore
- `RELEASE_KEY_ALIAS` - Alias de la clé de signature
- `RELEASE_KEY_PASSWORD` - Mot de passe de la clé

**Étapes principales**:
1. Validation des secrets
2. Configuration Node.js et JDK
3. Décodage du keystore de release
4. Build de l'APK signé
5. Génération du checksum SHA-256
6. Création de la release GitHub avec notes

**Permissions requises**:
- `contents: write` - Création de releases et tags

**Outputs**:
- APK signé: `SentinelQuantumVanguardAIPro-v{VERSION}.apk`
- Checksum: `SentinelQuantumVanguardAIPro-v{VERSION}.apk.sha256`
- Release GitHub avec notes complètes

**Utilisation**:
```bash
# Via tag Git
git tag v1.0.0
git push origin v1.0.0

# Ou déclenchement manuel
Actions → Build and Release Android APK → Run workflow → Version: 1.0.0
```

**Documentation liée**:
- `RELEASE_KEYSTORE_SETUP.md` - Configuration du keystore
- `QUICKSTART_PRODUCTION_APK.md` - Guide rapide de production

---

### 3. GitHub Release (`release.yml`)

**Statut**: ✅ Actif  
**Déclencheurs**:
- Push de tags `v*.*.*`
- Déclenchement manuel avec input tag

**Description**:  
Crée une release GitHub générique avec changelog automatique.

**Étapes principales**:
1. Détermination de la version
2. Génération du changelog depuis les commits
3. Création de la release

**Permissions requises**:
- `contents: write` - Création de releases

**Utilisation**:
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## Workflows de Sécurité

### 4. CodeQL Web Analysis (`codeql-analysis.yml`)

**Statut**: ✅ Actif (workflow principal CodeQL)  
**Déclencheurs**:
- Push sur `main`
- Pull requests sur `main`
- Schedule: Lundi 03:33 UTC (hebdomadaire)

**Description**:  
Analyse de sécurité CodeQL pour le code frontend (JavaScript/TypeScript) et les workflows GitHub Actions.

**Langages analysés**:
- `javascript-typescript` - Code web (HTML, CSS, JS, TS)
- `actions` - Workflows GitHub Actions

**Langages EXCLUS**:
- ❌ Java/Kotlin - Non applicable (pas de code Java/Kotlin à analyser)

**Permissions requises**:
- `actions: read` - Lecture des workflows
- `contents: read` - Lecture du code
- `security-events: write` - Upload des résultats de sécurité

**Rationale**:
Le repository est un projet frontend avec une app mobile React Native. L'analyse Java/Kotlin n'est pas pertinente car il n'y a pas de code source Java/Kotlin à compiler (seulement un wrapper Android pré-compilé).

**Notes importantes**:
- ⚠️ Ce workflow est compatible avec GitHub Default setup
- ⚠️ Un seul workflow CodeQL doit être actif à la fois
- ⚠️ `codeql.yml` est DÉSACTIVÉ pour éviter les conflits

---

### 5. CodeQL Advanced (`codeql.yml`)

**Statut**: ❌ DÉSACTIVÉ  
**Raison**: Conflit avec Default setup et `codeql-analysis.yml`

**Description**:  
Ancien workflow CodeQL Advanced désactivé pour éviter les conflits. Conservé pour référence.

**Problèmes identifiés**:
1. Conflit avec `codeql-analysis.yml` (duplication)
2. Tentative d'analyse Java/Kotlin non applicable
3. Incompatible avec GitHub Default setup

**Action recommandée**:
- ✅ Utiliser `codeql-analysis.yml` uniquement
- ❌ Ne PAS réactiver ce workflow sans désactiver `codeql-analysis.yml` d'abord

---

### 6. Microsoft Defender for DevOps (`defender-for-devops.yml`)

**Statut**: ✅ Actif  
**Déclencheurs**:
- Push sur `main`
- Pull requests sur `main`
- Schedule: Mercredi 06:24 UTC (hebdomadaire)

**Description**:  
Intégration de Microsoft Security DevOps pour analyse statique supplémentaire.

**Étapes principales**:
1. Checkout du code
2. Configuration .NET 5.0 et 6.0
3. Exécution de Microsoft Security DevOps
4. Upload des résultats SARIF vers Security tab

**Permissions requises**:
- `contents: read` - Lecture du code
- `security-events: write` - Upload des résultats
- `actions: read` - Lecture des workflows

**Runner**: Windows (obligatoire pour cet outil)

**Notes**:
- Nécessite configuration Azure pour intégration complète (optionnelle)
- Résultats visibles dans l'onglet Security → Code scanning

---

### 7. Integrity Check (`integrity-check.yml`)

**Statut**: ✅ Actif  
**Déclencheurs**:
- Push sur `main`
- Pull requests sur `main`
- Déclenchement manuel

**Description**:  
Vérifie l'intégrité du repository et détecte d'éventuels secrets exposés.

**Vérifications effectuées**:
1. Présence des fichiers critiques (README, package.json, workflows, etc.)
2. Scan de patterns de secrets (AWS keys, API keys, tokens, etc.)

**Permissions requises**:
- `contents: read` - Lecture du repository

**Patterns de secrets détectés**:
- AWS Access Keys (`AKIA...`)
- OpenAI API Keys (`sk-...`)
- GitHub PAT (`ghp_...`)
- GitLab PAT (`glpat-...`)
- Clés privées SSH/SSL
- Connection strings (MongoDB, PostgreSQL)

---

## Workflows de Validation

### 8. Frontend Validation (`frontend-validation.yml`)

**Statut**: ✅ Actif  
**Déclencheurs**:
- Push sur `main`, `develop`, `copilot/**` (fichiers frontend)
- Pull requests sur `main`
- Déclenchement manuel

**Description**:  
Valide que le projet reste un site statique sans backend et vérifie la conformité.

**Validations effectuées**:
1. ✅ Structure HTML valide
2. ✅ Absence de code backend (Express, Koa, etc.)
3. ✅ Absence de scripts de tracking
4. ✅ Validation du fichier `modules.status.json`
5. ✅ Présence de la documentation requise
6. ✅ Présence des pages légales
7. ✅ Pas d'endpoints API hardcodés
8. ✅ Pas de secrets dans le code
9. ✅ Workflows Android désactivés (si applicable)

**Permissions requises**:
- `contents: read` - Lecture du repository

**Fichiers surveillés**:
- `public/**`
- `index.html`
- `*.md`
- `config/**`

---

## Workflows de Déploiement

### 9. Deploy to GitHub Pages (`pages-deploy.yml`)

**Statut**: ✅ Actif  
**Déclencheurs**:
- Push sur `main` (fichiers frontend)
- Déclenchement manuel

**Description**:  
Déploie le site statique sur GitHub Pages.

**Étapes principales**:
1. Checkout du code
2. Configuration GitHub Pages
3. Préparation des fichiers statiques (`_site/`)
4. Upload et déploiement

**Permissions requises**:
- `contents: read` - Lecture du repository
- `pages: write` - Écriture sur GitHub Pages
- `id-token: write` - Authentification

**Environment**: `github-pages`

**Fichiers déployés**:
- `public/*`
- `index.html`
- `assets/*`
- `*.css`, `*.js`

**URL de production**:
Disponible dans l'output `page_url` du job

---

## Configuration et Permissions

### Permissions GitHub Token

Chaque workflow déclare explicitement ses permissions minimales requises:

| Workflow | contents | actions | pages | id-token | security-events |
|----------|----------|---------|-------|----------|-----------------|
| build-android | read | read | - | - | - |
| release-apk | write | - | - | - | - |
| release | write | - | - | - | - |
| codeql-analysis | read | read | - | - | write |
| defender-for-devops | read | read | - | - | write |
| integrity-check | read | - | - | - | - |
| frontend-validation | read | - | - | - | - |
| pages-deploy | read | - | write | write | - |

### Best Practices

#### ✅ À faire:
1. **Un seul workflow CodeQL actif** - Éviter les doublons
2. **Permissions minimales** - Principe du moindre privilège
3. **Secrets sécurisés** - Utiliser GitHub Secrets, jamais de hardcode
4. **Cache Gradle** - Accélérer les builds Android
5. **Checksum APK** - Toujours générer SHA-256 pour les releases
6. **Documentation à jour** - Maintenir ce fichier synchronisé

#### ❌ À éviter:
1. ❌ Multiples workflows CodeQL actifs simultanément
2. ❌ Permissions `write` non nécessaires
3. ❌ Secrets dans le code ou les logs
4. ❌ Build sans validation préalable
5. ❌ Release sans tests
6. ❌ Workflows sans documentation

### Dépannage

#### Build Android échoue:
1. Vérifier que `android-app/android/gradlew` est exécutable
2. Vérifier la configuration Gradle (build.gradle, settings.gradle)
3. Consulter les logs du workflow

#### Release APK échoue:
1. Vérifier que tous les secrets sont configurés
2. Valider le format du keystore (base64)
3. Tester le keystore localement d'abord

#### CodeQL échoue:
1. Vérifier qu'un seul workflow CodeQL est actif
2. Confirmer les langages configurés correspondent au code
3. Désactiver `codeql.yml` si problèmes persistent

#### Pages Deploy échoue:
1. Vérifier que GitHub Pages est activé dans Settings
2. Confirmer que `index.html` existe
3. Vérifier les permissions du workflow

---

## Références

### Documentation officielle:
- [GitHub Actions](https://docs.github.com/en/actions)
- [CodeQL](https://codeql.github.com/docs/)
- [GitHub Pages](https://docs.github.com/en/pages)

### Documentation interne:
- `README.md` - Vue d'ensemble du projet
- `RELEASE_KEYSTORE_SETUP.md` - Configuration keystore Android
- `QUICKSTART_PRODUCTION_APK.md` - Guide rapide production APK
- `SECURITY.md` - Politique de sécurité

---

**Dernière mise à jour**: 2025-12-17  
**Maintenu par**: Équipe DevOps SentinelQuantumVanguardAiPro
