# 📱 GUIDE: Construire l'APK Sentinel Téléphone

**Sentinel Quantum Vanguard AI Pro - Module Téléphone**  
**Date**: Décembre 2024

---

## 🎯 Objectif

Ce guide explique comment construire l'APK Android du module téléphone Sentinel localement sur votre machine.

**Note**: Le build CI GitHub Actions est actuellement bloqué par des restrictions réseau (accès Google Maven). Le build local fonctionne parfaitement avec Android Studio.

---

## ✅ Prérequis

### Logiciels Requis

1. **Java Development Kit (JDK) 17 ou supérieur**
   ```bash
   java -version
   # Doit afficher: openjdk version "17.0" ou supérieur
   ```

2. **Android Studio** (dernière version stable)
   - Télécharger: https://developer.android.com/studio
   - Installer avec SDK Android 33 (API 33)

3. **Node.js 18 ou supérieur**
   ```bash
   node --version
   # Doit afficher: v18.0.0 ou supérieur
   ```

4. **Git**
   ```bash
   git --version
   ```

### Configuration Android SDK

Dans Android Studio:
1. Ouvrir **Settings/Preferences** → **Appearance & Behavior** → **System Settings** → **Android SDK**
2. Installer:
   - Android 13 (API 33) - **requis**
   - Android 12 (API 31) - minimum supporté
   - Android SDK Build-Tools 33.0.0
   - Android SDK Platform-Tools
   - Android SDK Tools

---

## 📥 Étape 1: Cloner le Projet

```bash
git clone https://github.com/teetee971/SentinelQuantumVanguardAiPro.git
cd SentinelQuantumVanguardAiPro/android-app
```

---

## 📦 Étape 2: Installer les Dépendances

```bash
# Installer les dépendances Node.js
npm install

# Nettoyer le cache (optionnel mais recommandé)
npm run clean
```

---

## 🔧 Étape 3: Configuration Locale

### A) Créer local.properties

Créer le fichier `android/local.properties` avec le chemin vers votre SDK:

```properties
# Remplacer par votre chemin SDK
sdk.dir=/Users/VOTRE_NOM/Library/Android/sdk

# Ou sur Windows:
# sdk.dir=C:\\Users\\VOTRE_NOM\\AppData\\Local\\Android\\sdk

# Ou sur Linux:
# sdk.dir=/home/VOTRE_NOM/Android/Sdk
```

**Pour trouver votre SDK path**:
- Dans Android Studio: **Settings** → **Appearance & Behavior** → **System Settings** → **Android SDK**
- Le chemin est affiché en haut: "Android SDK Location"

### B) Vérifier gradle.properties

Le fichier `android/gradle.properties` doit contenir:

```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
android.useAndroidX=true
android.enableJetifier=true
```

---

## 🏗️ Étape 4: Build APK Debug

### Option A: Via Ligne de Commande (Recommandé)

```bash
cd android

# Build debug APK
./gradlew assembleDebug

# L'APK sera dans:
# app/build/outputs/apk/debug/app-debug.apk
```

### Option B: Via Android Studio

1. Ouvrir Android Studio
2. **File** → **Open** → Sélectionner `SentinelQuantumVanguardAiPro/android-app/android`
3. Attendre la synchronisation Gradle
4. **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
5. Cliquer sur **Locate** dans la notification pour trouver l'APK

---

## 🚀 Étape 5: Build APK Release (Production)

### A) Créer un Keystore (Première Fois Seulement)

```bash
keytool -genkey -v -keystore sentinel-release.keystore \
  -alias sentinel-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Répondre aux questions:
# - Mot de passe du keystore: [CHOISIR UN MOT DE PASSE FORT]
# - Prénom et nom: Sentinel Team
# - Nom de l'organisation: Sentinel Quantum Vanguard
# - etc.
```

**IMPORTANT**: Sauvegarder ce keystore et les mots de passe dans un lieu sûr!

### B) Configurer le Signing

Créer `android/keystore.properties`:

```properties
storePassword=VOTRE_STORE_PASSWORD
keyPassword=VOTRE_KEY_PASSWORD
keyAlias=sentinel-key
storeFile=../sentinel-release.keystore
```

⚠️ **Ne JAMAIS commit ce fichier dans Git!** (déjà dans .gitignore)

### C) Build Release

```bash
cd android
./gradlew assembleRelease

# L'APK sera dans:
# app/build/outputs/apk/release/app-release.apk
```

---

## 📦 Étape 6: Variants de Build

Le projet supporte 2 variantes:

### 1. Public (Grand Public)

```bash
./gradlew assemblePublicDebug
# ou
./gradlew assemblePublicRelease

# APK: app/build/outputs/apk/public/debug/app-public-debug.apk
```

**Caractéristiques**:
- ApplicationId: `com.sentinel.quantum.public`
- Accès journal d'appels: désactivé par défaut
- Pour utilisateurs finaux

### 2. Institutional (Entreprise/Institution)

```bash
./gradlew assembleInstitutionalDebug
# ou  
./gradlew assembleInstitutionalRelease

# APK: app/build/outputs/apk/institutional/debug/app-institutional-debug.apk
```

**Caractéristiques**:
- ApplicationId: `com.sentinel.quantum.institutional`
- Accès journal d'appels: activé
- Mode audit et conformité
- Pour déploiements entreprise

---

## 📱 Étape 7: Installer l'APK

### Sur Émulateur Android Studio

```bash
# Démarrer un émulateur
# Puis installer:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Sur Appareil Physique

1. Activer **Options pour les développeurs** sur Android:
   - **Paramètres** → **À propos du téléphone**
   - Appuyer 7 fois sur **Numéro de build**

2. Activer **Débogage USB**:
   - **Paramètres** → **Options pour les développeurs**
   - Activer **Débogage USB**

3. Connecter l'appareil en USB

4. Installer:
   ```bash
   adb devices  # Vérifier que l'appareil est détecté
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Installation Manuelle (Sans ADB)

1. Copier l'APK sur le téléphone (email, USB, etc.)
2. Dans **Paramètres** → **Sécurité**
3. Activer **Sources inconnues** ou **Installer des applications inconnues**
4. Ouvrir le fichier APK avec le gestionnaire de fichiers
5. Suivre les instructions d'installation

---

## 🔍 Étape 8: Test et Vérification

### Vérifier les Permissions

Au premier lancement, l'app doit demander:
- ✅ Accès téléphone (READ_PHONE_STATE)
- ✅ Journal d'appels (READ_CALL_LOG)
- ✅ Contacts (READ_CONTACTS) - optionnel
- ✅ Notifications (POST_NOTIFICATIONS)

### Tester le Module Téléphone

1. Ouvrir l'application
2. Naviguer vers **Module Téléphone**
3. Accorder les permissions
4. Faire un appel test avec un numéro ARCEP (ex: 01 62 XX XX XX)
5. Vérifier l'analyse et le score de risque

### Logs de Débogage

```bash
# Voir les logs en temps réel
adb logcat | grep Sentinel

# Filtrer uniquement le module téléphone
adb logcat | grep SentinelCallScreening
```

---

## 🐛 Dépannage

### Problème: Gradle sync failed

**Solution**:
```bash
cd android
./gradlew clean
./gradlew --refresh-dependencies
```

### Problème: SDK not found

**Solution**:
- Vérifier que `local.properties` existe avec le bon chemin SDK
- Relancer Android Studio et laisser télécharger les SDKs manquants

### Problème: Build failed - OutOfMemoryError

**Solution**: Augmenter la mémoire Gradle dans `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m
```

### Problème: Unable to install APK

**Erreurs courantes**:
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: Désinstaller l'ancienne version
- `INSTALL_FAILED_INSUFFICIENT_STORAGE`: Libérer de l'espace
- `INSTALL_PARSE_FAILED_NO_CERTIFICATES`: Re-build l'APK

---

## 📊 Informations APK

### Taille Approximative

- **Debug APK**: ~20-25 MB (non optimisé)
- **Release APK**: ~15-18 MB (optimisé, ProGuard)

### Compatibilité

- **Minimum**: Android 12 (API 31)
- **Target**: Android 13 (API 33)
- **Architectures**: arm64-v8a, armeabi-v7a, x86_64, x86

### Permissions Requises

```xml
<!-- Essentielles -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Optionnelles -->
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />
```

---

## 🔐 Sécurité du Build

### Vérifier la Signature de l'APK

```bash
# Pour debug APK
jarsigner -verify -verbose -certs app-debug.apk

# Pour release APK
jarsigner -verify -verbose -certs app-release.apk
```

### Hash SHA-256 du Keystore

```bash
keytool -list -v -keystore sentinel-release.keystore
```

Sauvegarder l'empreinte SHA-256 pour Google Play Console et Firebase.

---

## 📤 Distribution

### Via GitHub Releases

1. Tag la version:
   ```bash
   git tag -a v1.0.0 -m "Release 1.0.0 - Phone Module"
   git push origin v1.0.0
   ```

2. Créer une release sur GitHub
3. Uploader l'APK release
4. Ajouter notes de version

### Via Google Play Store (Futur)

- Utiliser **Android App Bundle** (.aab) au lieu d'APK:
  ```bash
  ./gradlew bundleRelease
  ```
- L'AAB sera dans: `app/build/outputs/bundle/release/app-release.aab`

---

## 📋 Checklist Avant Release

- [ ] Version code et version name à jour dans `build.gradle`
- [ ] Tests sur plusieurs appareils (Android 12, 13, 14)
- [ ] Vérification permissions (aucune permission excessive)
- [ ] ProGuard/R8 configuré et testé
- [ ] APK signé avec keystore production
- [ ] Changelog rédigé
- [ ] Screenshots à jour
- [ ] Documentation utilisateur complète

---

## 🆘 Support

### Documentation

- **README Principal**: [README.md](../README.md)
- **Conformité Légale**: [PHONE_MODULE_LEGAL_COMPLIANCE.md](../docs/PHONE_MODULE_LEGAL_COMPLIANCE.md)
- **Architecture**: [ANDROID_README.md](../ANDROID_README.md)

### Issues

Pour signaler un problème de build:
https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues

---

**Sentinel Quantum Vanguard AI Pro**  
*Build local réussi = APK fonctionnel garanti*

