# Quick Start - Configuration Production APK

## 🎯 Objectif

Configurer le système de build automatisé pour générer des APK de production signées via GitHub Actions.

## ⏱️ Temps Estimé

- **Première configuration** : 15-20 minutes
- **Releases suivantes** : < 1 minute (automatique)

---

## Étape 1 : Générer le Keystore (5 min)

### 1.1 Ouvrir un Terminal

```bash
cd /home/runner/work/SentinelQuantumVanguardAiPro/SentinelQuantumVanguardAiPro/android-app/android/app
```

### 1.2 Générer le Keystore

```bash
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore sentinel-release.keystore \
  -alias sentinel-key \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=Sentinel Quantum Vanguard AI Pro, OU=Cybersecurity, O=Sentinel Project, L=Paris, ST=Ile-de-France, C=FR"
```

### 1.3 Saisir les Mots de Passe

Lorsque demandé :
- **Enter keystore password** : Choisir un mot de passe FORT (20+ caractères recommandés)
- **Re-enter new password** : Confirmer le mot de passe
- **Enter key password (RETURN if same)** : Appuyer sur ENTRÉE (même mot de passe)

**💡 Exemple de mot de passe fort** : `Sentinel2024!SecureKey$Random#4096`

### 1.4 Vérifier la Création

```bash
ls -lh sentinel-release.keystore
# Doit afficher un fichier de ~3-5 KB

keytool -list -v -keystore sentinel-release.keystore
# Entrer le mot de passe pour voir les détails
```

### 1.5 SAUVEGARDER le Keystore

⚠️ **CRITIQUE** : Sauvegardez immédiatement :

```bash
# Copier vers un emplacement sécurisé
cp sentinel-release.keystore ~/Documents/sentinel-keystore-backup.keystore

# Ou vers une clé USB, cloud chiffré, coffre-fort digital, etc.
```

**Si vous perdez le keystore, vous ne pourrez JAMAIS mettre à jour l'app sur les appareils existants !**

---

## Étape 2 : Encoder le Keystore en Base64 (2 min)

### 2.1 Encoder

```bash
# Linux / macOS
base64 -i sentinel-release.keystore > keystore.base64.txt

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sentinel-release.keystore")) > keystore.base64.txt
```

### 2.2 Copier le Contenu

```bash
# Afficher et copier TOUT le contenu (sera long)
cat keystore.base64.txt

# Ou ouvrir dans un éditeur de texte
nano keystore.base64.txt  # Linux/macOS
notepad keystore.base64.txt  # Windows
```

**⚠️ Attention** : Le texte Base64 doit être copié en UNE SEULE LIGNE (pas de retours à la ligne).

---

## Étape 3 : Configurer GitHub Secrets (5 min)

### 3.1 Aller sur GitHub

1. Ouvrir https://github.com/teetee971/SentinelQuantumVanguardAiPro
2. Cliquer sur **Settings** (Paramètres)
3. Dans le menu latéral : **Secrets and variables** → **Actions**
4. Cliquer sur **New repository secret**

### 3.2 Créer les 4 Secrets

**Secret 1 : RELEASE_KEYSTORE_BASE64**
- Name : `RELEASE_KEYSTORE_BASE64`
- Secret : Coller tout le contenu de `keystore.base64.txt`
- Cliquer **Add secret**

**Secret 2 : RELEASE_KEYSTORE_PASSWORD**
- Name : `RELEASE_KEYSTORE_PASSWORD`
- Secret : Le mot de passe du keystore (celui saisi à l'étape 1.3)
- Cliquer **Add secret**

**Secret 3 : RELEASE_KEY_ALIAS**
- Name : `RELEASE_KEY_ALIAS`
- Secret : `sentinel-key`
- Cliquer **Add secret**

**Secret 4 : RELEASE_KEY_PASSWORD**
- Name : `RELEASE_KEY_PASSWORD`  
- Secret : Le mot de passe de la clé (même que keystore si vous avez appuyé sur ENTRÉE)
- Cliquer **Add secret**

### 3.3 Vérifier

Vous devez voir 4 secrets dans la liste :
- ✅ RELEASE_KEYSTORE_BASE64
- ✅ RELEASE_KEYSTORE_PASSWORD
- ✅ RELEASE_KEY_ALIAS
- ✅ RELEASE_KEY_PASSWORD

---

## Étape 4 : Préparer la Première Release (3 min)

### 4.1 Mettre à Jour les Versions

Éditer `android-app/android/app/build.gradle` :

```gradle
defaultConfig {
    applicationId "com.sentinel"
    minSdkVersion 23
    targetSdkVersion 34
    versionCode 1        // Première version
    versionName "1.0.0"  // Version affichée
}
```

**Important** :
- `versionCode` : Entier qui doit augmenter à chaque release (1, 2, 3...)
- `versionName` : Version sémantique (1.0.0, 1.0.1, 1.1.0, 2.0.0...)

### 4.2 Commiter les Changements

```bash
cd /home/runner/work/SentinelQuantumVanguardAiPro/SentinelQuantumVanguardAiPro

git add android-app/android/app/build.gradle
git commit -m "Set version 1.0.0 for first production release"
git push origin copilot/create-secure-webview-app
```

---

## Étape 5 : Déclencher le Build Release (2 min)

### Option A : Via Tag Git (Recommandé)

```bash
# Créer le tag de version
git tag -a v1.0.0 -m "Release 1.0.0 - First production release"

# Pousser le tag
git push origin v1.0.0
```

### Option B : Via GitHub UI

1. Aller sur GitHub → **Actions**
2. Sélectionner **Release Android APK**
3. Cliquer sur **Run workflow**
4. Entrer : `1.0.0`
5. Cliquer **Run workflow**

---

## Étape 6 : Vérifier le Build (5 min)

### 6.1 Suivre le Build

1. Aller sur **Actions** dans GitHub
2. Cliquer sur le workflow "Release Android APK"
3. Voir la progression (prend 5-10 minutes)

### 6.2 Étapes du Workflow

- ✅ Checkout repository
- ✅ Setup Java 17
- ✅ Decode keystore
- ✅ Build Public APK
- ✅ Build Institutional APK
- ✅ Generate checksums
- ✅ Create GitHub Release
- ✅ Upload artifacts

### 6.3 Vérifier la Release

1. Aller sur **Releases** dans GitHub
2. Vous devez voir **Release v1.0.0**
3. Assets téléchargeables :
   - ✅ Sentinel-Public-v1.0.0.apk
   - ✅ Sentinel-Institutional-v1.0.0.apk
   - ✅ checksums.txt
   - ✅ Source code (zip)
   - ✅ Source code (tar.gz)

---

## Étape 7 : Tester l'APK (5 min)

### 7.1 Télécharger l'APK

```bash
# Depuis GitHub Release
curl -L -O https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.0/Sentinel-Public-v1.0.0.apk
```

### 7.2 Vérifier le Checksum

```bash
# Télécharger les checksums
curl -L -O https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.0/checksums.txt

# Vérifier
sha256sum Sentinel-Public-v1.0.0.apk
cat checksums.txt

# Les valeurs doivent correspondre
```

### 7.3 Installer sur Android

```bash
# Via ADB (si appareil connecté)
adb install Sentinel-Public-v1.0.0.apk

# Ou transférer sur téléphone et installer manuellement
```

### 7.4 Vérifier la Signature

```bash
# Extraire le certificat
keytool -printcert -jarfile Sentinel-Public-v1.0.0.apk

# Vérifier que Owner correspond à votre DN
# Vérifier que SHA256 est présent
```

---

## ✅ Configuration Terminée !

Votre infrastructure de release est maintenant configurée.

### Prochaines Releases (< 1 min)

```bash
# 1. Mettre à jour les versions dans build.gradle
# versionCode = 2
# versionName = "1.0.1"

# 2. Commiter
git add android-app/android/app/build.gradle
git commit -m "Bump version to 1.0.1"
git push origin main

# 3. Créer et pousser le tag
git tag -a v1.0.1 -m "Release 1.0.1"
git push origin v1.0.1

# 4. GitHub Actions fait tout automatiquement !
```

---

## 📋 Checklist Finale

Configuration One-Time :
- [x] Keystore généré
- [x] Keystore sauvegardé (3+ emplacements)
- [x] Keystore encodé en Base64
- [x] 4 GitHub Secrets configurés
- [x] Version 1.0.0 définie
- [x] Tag v1.0.0 poussé
- [x] Workflow exécuté avec succès
- [x] GitHub Release créée
- [x] APKs téléchargées et vérifiées
- [x] Installation testée sur appareil

---

## 🆘 En Cas de Problème

### Le workflow échoue

**Vérifier** :
1. Les 4 secrets GitHub sont bien configurés
2. Le Base64 du keystore est correct (pas de retours à la ligne)
3. Les mots de passe correspondent au keystore
4. Le fichier `build.gradle` est correct

**Solution** : Consulter les logs du workflow dans GitHub Actions.

### APK non installable

**Vérifier** :
1. Checksum SHA-256 correspond
2. Android 6.0+ sur l'appareil
3. "Sources inconnues" activé
4. Désinstaller version précédente si signature différente

### J'ai perdu le keystore

❌ **Impossible de récupérer**

**Solutions** :
1. Restaurer depuis sauvegarde
2. Si aucune sauvegarde : Générer nouveau keystore (utilisateurs devront réinstaller)

---

## 📚 Documentation Complète

- [Guide de Production Complet](./PRODUCTION_RELEASE_GUIDE.md)
- [Guide de Téléchargement Utilisateur](./DOWNLOAD_INSTALL_GUIDE.md)
- [Guide de Build Android](./ANDROID_APK_GUIDE.md)

---

**Dernière mise à jour** : Décembre 2024  
**Pour** : Sentinel Quantum Vanguard AI Pro v1.0.0+
