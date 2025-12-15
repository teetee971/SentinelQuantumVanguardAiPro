# Guide de Production - APK Signée Sentinel Quantum Vanguard AI Pro

## Vue d'Ensemble

Ce guide explique comment générer et distribuer une APK de production signée de Sentinel Quantum Vanguard AI Pro, prête pour distribution publique ou institutionnelle.

## Prérequis

- Java Development Kit (JDK) 17+
- Android SDK
- Accès administrateur au repository GitHub
- Terminal avec accès keytool

## Table des Matières

1. [Génération du Keystore](#1-génération-du-keystore)
2. [Configuration Locale](#2-configuration-locale)
3. [Build Local de l'APK Release](#3-build-local-de-lapk-release)
4. [Configuration GitHub Actions](#4-configuration-github-actions)
5. [Build Automatisé et Release](#5-build-automatisé-et-release)
6. [Distribution et Installation](#6-distribution-et-installation)

---

## 1. Génération du Keystore

### 1.1 Créer le Keystore de Production

Le keystore est le fichier contenant la clé privée utilisée pour signer l'APK. **Gardez-le en sécurité et ne le partagez jamais publiquement.**

```bash
# Naviguer vers le répertoire du projet Android
cd android-app/android/app

# Générer le keystore (remplacez les valeurs selon votre organisation)
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore sentinel-release.keystore \
  -alias sentinel-key \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass VOTRE_MOT_DE_PASSE_FORT \
  -keypass VOTRE_MOT_DE_PASSE_FORT \
  -dname "CN=Sentinel Quantum Vanguard AI Pro, OU=Cybersecurity, O=Sentinel Project, L=Paris, ST=Ile-de-France, C=FR"
```

### 1.2 Informations à Fournir

Lors de la génération, vous devrez fournir :

- **Store Password** : Mot de passe du keystore (minimum 6 caractères, recommandé 20+ caractères aléatoires)
- **Key Password** : Mot de passe de la clé (peut être identique au store password)
- **Key Alias** : `sentinel-key` (identifiant de la clé dans le keystore)
- **Validity** : 10000 jours (~27 ans) - durée de validité de la clé

**Distinguished Name (DN) Fields:**
- **CN** (Common Name) : Nom de l'application
- **OU** (Organizational Unit) : Département ou équipe
- **O** (Organization) : Nom de l'organisation
- **L** (Locality) : Ville
- **ST** (State) : Région/État
- **C** (Country) : Code pays (FR pour France)

### 1.3 Vérifier le Keystore

```bash
# Lister les informations du keystore
keytool -list -v -keystore sentinel-release.keystore -storepass VOTRE_MOT_DE_PASSE

# Vérifier l'alias
keytool -list -keystore sentinel-release.keystore -storepass VOTRE_MOT_DE_PASSE
```

### 1.4 Sauvegarder le Keystore

⚠️ **CRITIQUE** : Le keystore est irremplaçable. Si vous le perdez :
- Vous ne pourrez plus mettre à jour l'application sur les appareils existants
- Les utilisateurs devront désinstaller et réinstaller
- Vous perdrez la continuité de l'application

**Sauvegardes recommandées :**
1. **Coffre-fort numérique sécurisé** (1Password, Bitwarden, etc.)
2. **Support physique chiffré** (USB, disque dur externe)
3. **Stockage cloud chiffré** (avec mot de passe séparé)
4. **Coffre physique** (pour organisations)

**NE JAMAIS :**
- ❌ Commiter le keystore dans Git
- ❌ Partager le keystore par email
- ❌ Stocker le keystore non chiffré sur le cloud
- ❌ Utiliser un mot de passe faible

---

## 2. Configuration Locale

### 2.1 Créer le Fichier de Configuration

Créez `android-app/android/keystore.properties` (ce fichier est ignoré par Git) :

```properties
# Keystore de Production Sentinel
storeFile=./app/sentinel-release.keystore
storePassword=VOTRE_MOT_DE_PASSE_KEYSTORE
keyAlias=sentinel-key
keyPassword=VOTRE_MOT_DE_PASSE_CLE
```

### 2.2 Vérifier le .gitignore

Assurez-vous que `android-app/android/.gitignore` contient :

```gitignore
# Fichiers de sécurité - NE JAMAIS COMMITER
*.keystore
keystore.properties
local.properties
```

---

## 3. Build Local de l'APK Release

### 3.1 Build Standard (Public)

```bash
cd android-app/android

# Build APK release publique
./gradlew assemblePublicRelease

# L'APK sera généré ici :
# app/build/outputs/apk/public/release/app-public-release.apk
```

### 3.2 Build Institutionnel

```bash
# Build APK release institutionnelle (avec permissions étendues)
./gradlew assembleInstitutionalRelease

# L'APK sera généré ici :
# app/build/outputs/apk/institutional/release/app-institutional-release.apk
```

### 3.3 Build All Variants

```bash
# Générer toutes les variantes en une commande
./gradlew assembleRelease

# Génère :
# - app-public-release.apk
# - app-institutional-release.apk
```

### 3.4 Vérifier la Signature

```bash
# Extraire et vérifier le certificat de l'APK
keytool -printcert -jarfile app/build/outputs/apk/public/release/app-public-release.apk

# Vérifier que :
# - Owner correspond à votre DN
# - SHA256 fingerprint est présent
# - Validity period couvre au moins 25 ans
```

### 3.5 Analyser l'APK

```bash
# Taille de l'APK
ls -lh app/build/outputs/apk/public/release/*.apk

# Vérifier l'optimisation ProGuard
unzip -l app/build/outputs/apk/public/release/app-public-release.apk | grep -i "classes.dex"

# Extraction complète pour inspection (optionnel)
unzip app/build/outputs/apk/public/release/app-public-release.apk -d /tmp/apk-analysis/
```

---

## 4. Configuration GitHub Actions

### 4.1 Créer les Secrets GitHub

1. Aller sur GitHub → Settings → Secrets and variables → Actions
2. Créer les secrets suivants :

| Secret Name | Value | Description |
|------------|-------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64 du keystore | Fichier keystore encodé |
| `RELEASE_KEYSTORE_PASSWORD` | Mot de passe keystore | Store password |
| `RELEASE_KEY_ALIAS` | `sentinel-key` | Alias de la clé |
| `RELEASE_KEY_PASSWORD` | Mot de passe clé | Key password |

### 4.2 Encoder le Keystore en Base64

```bash
# macOS / Linux
base64 -i sentinel-release.keystore -o keystore.base64.txt

# Ou en une ligne
cat sentinel-release.keystore | base64 > keystore.base64.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sentinel-release.keystore")) > keystore.base64.txt

# Copier le contenu de keystore.base64.txt dans le secret GitHub
cat keystore.base64.txt
```

### 4.3 Configurer le Workflow GitHub Actions

Le workflow `.github/workflows/release-android.yml` est déjà configuré pour :
- Décoder le keystore depuis les secrets
- Builder les APKs release
- Créer une GitHub Release automatique
- Uploader les APKs comme assets

### 4.4 Variables d'Environnement Disponibles

Le workflow utilise automatiquement :
- `RELEASE_KEYSTORE_BASE64` → décodé en `release.keystore`
- `RELEASE_KEYSTORE_PASSWORD` → passé à Gradle
- `RELEASE_KEY_ALIAS` → passé à Gradle
- `RELEASE_KEY_PASSWORD` → passé à Gradle

---

## 5. Build Automatisé et Release

### 5.1 Déclencher un Build Release

**Option 1 : Via Tag Git**

```bash
# Créer et pousser un tag de version
git tag -a v1.0.0 -m "Release version 1.0.0 - Production ready"
git push origin v1.0.0

# GitHub Actions détectera le tag et lancera le build release
```

**Option 2 : Manuellement via GitHub UI**

1. Aller sur Actions → Release Android APK
2. Cliquer sur "Run workflow"
3. Entrer la version (ex: 1.0.0)
4. Confirmer

### 5.2 Processus de Build Automatisé

Quand un tag `v*` est poussé :
1. GitHub Actions télécharge le code
2. Configure Java 17 et Gradle
3. Décode le keystore depuis les secrets
4. Build les APKs release (public + institutional)
5. Crée une GitHub Release automatique
6. Upload les APKs comme assets téléchargeables
7. Génère les checksums SHA-256

### 5.3 Vérifier le Build

1. Aller sur Actions → Release Android APK
2. Vérifier que le workflow s'est terminé avec succès
3. Aller sur Releases → Dernière release
4. Télécharger et vérifier les APKs

---

## 6. Distribution et Installation

### 6.1 URLs de Téléchargement

Une fois la release créée sur GitHub :

```
# APK Publique
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/Sentinel-Public-v1.0.0.apk

# APK Institutionnelle
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/Sentinel-Institutional-v1.0.0.apk

# Checksums
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/checksums.txt
```

### 6.2 Installation sur Android

**Prérequis :**
- Android 6.0 (API 23) ou supérieur
- 50 MB d'espace disque disponible

**Procédure :**

1. **Télécharger l'APK** depuis GitHub Releases
2. **Autoriser l'installation depuis sources inconnues** :
   - Settings → Security → Unknown Sources (Android 7 et inférieur)
   - Settings → Apps → Special Access → Install unknown apps → Sélectionner le navigateur (Android 8+)
3. **Ouvrir le fichier APK téléchargé**
4. **Appuyer sur "Installer"**
5. **Confirmer les permissions** (INTERNET, ACCESS_NETWORK_STATE)
6. **Lancer l'application**

### 6.3 Vérification de l'Installation

```bash
# Via ADB (pour vérification technique)
adb install app-public-release.apk

# Vérifier la signature
adb shell pm list packages -f | grep sentinel

# Voir les permissions
adb shell dumpsys package com.sentinel.quantum.public | grep permission
```

### 6.4 Désinstallation

```
Settings → Apps → Sentinel Quantum Vanguard AI Pro → Uninstall
```

---

## 7. Mises à Jour et Versioning

### 7.1 Schéma de Versioning

Sentinel suit [Semantic Versioning](https://semver.org/) :

```
MAJOR.MINOR.PATCH

1.0.0 → Version initiale publique
1.0.1 → Correctif bug mineur
1.1.0 → Nouvelle fonctionnalité
2.0.0 → Changement majeur / incompatibilité
```

### 7.2 Incrémenter les Versions

**Dans `android-app/android/app/build.gradle` :**

```gradle
defaultConfig {
    applicationId "com.sentinel"
    minSdkVersion 23
    targetSdkVersion 34
    versionCode 2        // Incrémenter à chaque release (2, 3, 4...)
    versionName "1.0.1"  // Version affichée (1.0.1, 1.1.0, etc.)
}
```

**Règles :**
- `versionCode` : Doit TOUJOURS augmenter (entier unique)
- `versionName` : Version sémantique lisible

### 7.3 Workflow de Release

```bash
# 1. Mettre à jour les versions dans build.gradle
nano android-app/android/app/build.gradle

# 2. Commiter les changements
git add android-app/android/app/build.gradle
git commit -m "Bump version to 1.0.1"

# 3. Créer et pousser le tag
git tag -a v1.0.1 -m "Release 1.0.1 - Bug fixes and improvements"
git push origin main
git push origin v1.0.1

# 4. GitHub Actions crée automatiquement la release
```

---

## 8. Sécurité et Conformité

### 8.1 Protection du Keystore

✅ **À FAIRE :**
- Utiliser un mot de passe fort (20+ caractères)
- Sauvegarder dans 3 emplacements sécurisés minimum
- Chiffrer toutes les sauvegardes
- Limiter l'accès au keystore (admin uniquement)
- Documenter l'emplacement des sauvegardes (document séparé, confidentiel)

❌ **NE JAMAIS :**
- Commiter dans Git (vérifier .gitignore)
- Partager par email ou chat
- Stocker en clair sur cloud public
- Utiliser pour plusieurs applications

### 8.2 Vérification d'Intégrité

**Générer les checksums :**

```bash
# SHA-256
sha256sum app-public-release.apk > checksums.txt

# MD5 (compatibilité)
md5sum app-public-release.apk >> checksums.txt
```

**Publier les checksums :**
- Dans la description de la GitHub Release
- Sur la page de téléchargement du site web
- Documentation officielle

### 8.3 Signature de Release

**Vérifier la signature de l'APK publié :**

```bash
# Télécharger l'APK depuis GitHub Release
curl -L -O https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/Sentinel-Public-v1.0.0.apk

# Vérifier le certificat
keytool -printcert -jarfile Sentinel-Public-v1.0.0.apk

# Comparer le fingerprint SHA-256 avec celui documenté
```

---

## 9. Résolution de Problèmes

### 9.1 Erreur "Keystore not found"

**Symptôme :** Build échoue avec "keystore file not found"

**Solution :**
```bash
# Vérifier que le fichier existe
ls -la android-app/android/app/sentinel-release.keystore

# Vérifier le chemin dans keystore.properties
cat android-app/android/keystore.properties

# Le chemin doit être relatif à app/
storeFile=./app/sentinel-release.keystore  # ✅ Correct
storeFile=sentinel-release.keystore        # ❌ Incorrect
```

### 9.2 Erreur "Incorrect password"

**Symptôme :** Build échoue avec "keystore password was incorrect"

**Solution :**
```bash
# Tester le keystore manuellement
keytool -list -v -keystore sentinel-release.keystore

# Vérifier que les mots de passe correspondent
# Si oublié, impossible de récupérer → générer nouveau keystore
```

### 9.3 APK Non Installable

**Symptôme :** "App not installed" sur Android

**Causes possibles :**
1. Version déjà installée avec signature différente → Désinstaller l'ancienne
2. versionCode inférieur à version installée → Incrémenter versionCode
3. APK corrompu → Re-télécharger
4. Architecture incompatible → Vérifier minSdkVersion

### 9.4 GitHub Actions Échoue

**Symptôme :** Workflow "Release Android APK" en erreur

**Vérifications :**
1. Secrets GitHub correctement configurés
2. Keystore Base64 valide (pas de retours à la ligne)
3. Mots de passe corrects (pas d'espaces supplémentaires)
4. build.gradle sans erreurs de syntaxe

**Debug :**
```bash
# Tester localement avec les mêmes paramètres
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=./app/sentinel-release.keystore \
  -Pandroid.injected.signing.store.password=MOT_DE_PASSE \
  -Pandroid.injected.signing.key.alias=sentinel-key \
  -Pandroid.injected.signing.key.password=MOT_DE_PASSE
```

---

## 10. Checklist de Release

Avant chaque release publique :

### Code & Build
- [ ] Tests passent sur Android 6.0 (API 23) minimum
- [ ] Tests passent sur Android 14 (API 34)
- [ ] Pas de logs de debug dans le code
- [ ] ProGuard activé et testé
- [ ] Taille APK optimisée (<10 MB idéalement)
- [ ] versionCode incrémenté
- [ ] versionName mis à jour

### Documentation
- [ ] CHANGELOG.md mis à jour
- [ ] README.md à jour
- [ ] Permissions documentées
- [ ] Instructions d'installation claires

### Sécurité
- [ ] Keystore sauvegardé (3 emplacements)
- [ ] Secrets GitHub vérifiés
- [ ] Pas de secrets hardcodés dans le code
- [ ] APK signée avec release keystore
- [ ] Checksums générés

### Distribution
- [ ] APK testée sur 3+ appareils physiques
- [ ] Installation "sources inconnues" fonctionnelle
- [ ] Permissions demandées clairement
- [ ] GitHub Release créée
- [ ] Download page mise à jour

### Conformité
- [ ] Mentions légales à jour
- [ ] Privacy policy incluse
- [ ] Avertissements clairs (OSINT only)
- [ ] Pas de promesses irréalistes

---

## 11. Support et Contact

### Documentation Complète
- [Build Guide (Android APK)](./ANDROID_APK_GUIDE.md)
- [Release Build Guide](./RELEASE_BUILD_GUIDE.md)
- [Institutional Use Cases](./INSTITUTIONAL_USE_CASES.md)

### Ressources Externes
- [Android Developer - App Signing](https://developer.android.com/studio/publish/app-signing)
- [Semantic Versioning](https://semver.org/)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

### Contact
- GitHub Issues : Questions techniques
- GitHub Discussions : Questions générales

---

**Dernière mise à jour :** Décembre 2024  
**Version du guide :** 1.0.0  
**Cible :** Sentinel Quantum Vanguard AI Pro v1.0.0+
