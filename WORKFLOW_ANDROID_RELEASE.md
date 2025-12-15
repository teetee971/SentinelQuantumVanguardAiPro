# 📱 Workflow Automatique : Android Release APK

## 🎯 Objectif

Ce workflow GitHub Actions génère automatiquement un **APK Android signé** et l'attache à une release GitHub existante, permettant le téléchargement direct sans aucune action manuelle.

## ✅ Configuration Requise

### Secrets GitHub (déjà configurés)

Les secrets suivants doivent être définis dans les paramètres du repository :

- `RELEASE_KEYSTORE_BASE64` : Keystore de signature encodé en base64
- `RELEASE_KEYSTORE_PASSWORD` : Mot de passe du keystore
- `RELEASE_KEY_ALIAS` : Alias de la clé de signature
- `RELEASE_KEY_PASSWORD` : Mot de passe de la clé

> ✅ Ces secrets sont déjà configurés et opérationnels.

## 🚀 Utilisation

### Méthode 1 : Via Publication de Release GitHub (recommandé pour mobile)

1. **Créer une nouvelle release sur GitHub :**
   - Aller sur : `https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/new`
   - Tag version : `v1.0.0-release` (ou `v1.0.1`, `v1.1.0`, etc.)
   - Titre : `Sentinel Vanguard – Version officielle v1.0.0`
   - Description : Ajouter les notes de version
   - ✅ Cocher "Set as the latest release"
   - ❌ Ne PAS cocher "Set as a pre-release"
   - Cliquer sur "Publish release"

2. **Le workflow se déclenche automatiquement :**
   - Build de l'APK signé (~5-10 minutes)
   - Upload automatique de l'APK dans la release
   - Upload du fichier SHA256 pour vérification

3. **Téléchargement :**
   - L'APK apparaît dans la section "Assets" de la release
   - Téléchargement direct : `SentinelQuantumVanguardAIPro-v1.0.0-release.apk`
   - Checksum : `SentinelQuantumVanguardAIPro-v1.0.0-release.apk.sha256`

### Méthode 2 : Via Push de Tag

1. **Créer et pusher un tag :**
   ```bash
   git tag v1.0.0-release
   git push origin v1.0.0-release
   ```

2. **Le workflow se déclenche automatiquement :**
   - Build de l'APK signé
   - Création d'une release si elle n'existe pas
   - Upload de l'APK et du SHA256

## 📦 Assets Générés

Chaque release contiendra automatiquement :

### 1. APK Signé
- **Nom :** `SentinelQuantumVanguardAIPro-v{VERSION}.apk`
- **Exemple :** `SentinelQuantumVanguardAIPro-v1.0.0-release.apk`
- **Type :** APK Android signé avec keystore de production
- **Build :** Institutional (permissions avancées)
- **Taille :** ~25-30 MB
- **Compatible :** Android 6.0+ (API 23)

### 2. Checksum SHA-256
- **Nom :** `SentinelQuantumVanguardAIPro-v{VERSION}.apk.sha256`
- **Exemple :** `SentinelQuantumVanguardAIPro-v1.0.0-release.apk.sha256`
- **Utilité :** Vérifier l'intégrité du téléchargement

## 🔐 Vérification du Téléchargement

### Sur Linux/Mac :
```bash
# Télécharger l'APK et le checksum
wget https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.0-release/SentinelQuantumVanguardAIPro-v1.0.0-release.apk
wget https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.0-release/SentinelQuantumVanguardAIPro-v1.0.0-release.apk.sha256

# Vérifier l'intégrité
sha256sum -c SentinelQuantumVanguardAIPro-v1.0.0-release.apk.sha256
```

### Sur Windows (PowerShell) :
```powershell
# Calculer le SHA256
Get-FileHash .\SentinelQuantumVanguardAIPro-v1.0.0-release.apk -Algorithm SHA256

# Comparer avec le fichier .sha256
```

### Sur Android (Termux) :
```bash
sha256sum SentinelQuantumVanguardAIPro-v1.0.0-release.apk
```

## 🏗️ Pipeline de Build

### Étapes Automatiques

1. **Checkout du code source**
2. **Installation Java 17** (Temurin distribution)
3. **Installation Node.js 18** avec cache npm
4. **Installation des dépendances** (`npm ci`)
5. **Décodage du keystore** depuis `RELEASE_KEYSTORE_BASE64`
6. **Installation Android SDK**
7. **Build APK signé** via `./gradlew assembleInstitutionalRelease`
8. **Vérification de l'APK** (taille minimum 10 MB)
9. **Détermination de la version** depuis le tag
10. **Renommage de l'APK** avec la version
11. **Génération du SHA-256**
12. **Upload vers GitHub Release**
13. **Affichage des informations** de téléchargement

### Temps d'Exécution

- ⏱️ **Durée moyenne :** 5-10 minutes
- 🔄 **Retry automatique :** Non (relancer manuellement si échec)
- 📊 **Logs disponibles :** Actions tab sur GitHub

## 📋 Règles de Nommage des Tags

### ✅ Formats Acceptés

- `v1.0.0` - Version simple
- `v1.0.0-release` - Version release
- `v1.0.1` - Version patch
- `v1.1.0` - Version mineure
- `v2.0.0` - Version majeure
- `v1.0.0-beta` - Pré-release beta
- `v1.0.0-rc1` - Release candidate

### ❌ Formats Interdits

- `1.0.0` - Sans préfixe 'v'
- `v1.0.0-$(date)` - Pas de commandes dynamiques
- `release-1.0.0` - Format non standard

## 🎯 Cas d'Usage

### Scénario 1 : Release Publique (depuis mobile)

```
1. Depuis navigateur mobile → GitHub Releases
2. Créer nouvelle release → Tag: v1.0.0-release
3. Publier → Attendre 5-10 min
4. APK disponible dans Assets
5. Télécharger directement sur Android
6. Installer l'APK
```

### Scénario 2 : Release Incrémentale (depuis PC)

```bash
# Mettre à jour la version
git tag v1.0.1
git push origin v1.0.1

# Créer la release sur GitHub UI
# L'APK sera automatiquement attaché
```

### Scénario 3 : Hotfix Urgent

```bash
# Fix appliqué sur main
git tag v1.0.0-hotfix
git push origin v1.0.0-hotfix

# Release créée automatiquement avec APK
```

## 🔍 Monitoring et Debugging

### Vérifier l'Exécution du Workflow

1. Aller sur : `https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions`
2. Chercher le workflow : "Build & Attach Signed Android APK to Release"
3. Voir les logs détaillés de chaque étape

### Causes Communes d'Échec

| Problème | Cause | Solution |
|----------|-------|----------|
| Keystore decode failed | Secret base64 invalide | Vérifier `RELEASE_KEYSTORE_BASE64` |
| Signature failed | Mauvais mot de passe | Vérifier `RELEASE_KEYSTORE_PASSWORD` |
| APK too small | Build échoué | Voir les logs Gradle |
| Upload failed | Permissions manquantes | Vérifier `contents: write` |
| No release found | Tag sans release | Créer la release manuellement |

## 🏢 Conformité Institutionnelle

### ✅ Avantages Souveraineté Numérique

- 🔐 **Keystore maîtrisé** : Vous contrôlez la signature
- 🏗️ **Build reproductible** : Pipeline transparent
- 📦 **Distribution directe** : Pas de dépendance Google Play
- 🇪🇺 **Hébergement GitHub** : Infrastructure fiable
- 🔒 **Secrets sécurisés** : GitHub Secrets chiffrés

### 🎯 Positionnement Marché

- ✅ **PME / Collectivités** : Distribution autonome
- ✅ **Défense / Sécurité** : Pas de GAFAM obligatoire
- ✅ **Entreprises** : MDM compatible
- ✅ **Développeurs** : Open source transparent

## 📞 Support

### En Cas de Problème

1. **Vérifier les logs du workflow** dans Actions tab
2. **Vérifier les secrets GitHub** dans Settings → Secrets
3. **Relancer le workflow** si erreur temporaire
4. **Contacter l'équipe** si problème persistant

### Liens Utiles

- 📖 [Documentation Android](./ANDROID_README.md)
- 🧪 [Guide de Test APK](./APK_TEST_GUIDE.md)
- 🔒 [Sécurité](./SECURITY.md)
- 📋 [Changelog](./CHANGELOG_SUPERPACK_MAX_E7.md)

---

**Mise à Jour :** 15 décembre 2024  
**Version Workflow :** 1.0  
**Statut :** ✅ Production-ready
