# 🚀 RELEASE APK - GUIDE PRODUCTION

> 📦 **[Manifeste de Livraison APK](APK_DELIVERY_MANIFEST.md)** - Voir la confirmation factuelle et vérifiable des 6 critères de production

## 📦 GÉNÉRER ET PUBLIER UN APK RELEASE

### Méthode 1: Via GitHub Actions (RECOMMANDÉE)

#### Étape 1: Déclencher le Workflow
```bash
# Option A: Via l'interface GitHub
1. Va sur https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions
2. Clique sur "Build and Release Android APK"
3. Clique "Run workflow"
4. Entre la version (ex: 1.0.0)
5. Clique "Run workflow"

# Option B: Via Git Tag
git tag v1.0.0
git push origin v1.0.0
```

Le workflow va automatiquement :
- ✅ Installer les dépendances
- ✅ Générer le keystore de signature
- ✅ Compiler l'APK release
- ✅ Vérifier la taille (minimum 10 MB)
- ✅ Créer une GitHub Release
- ✅ Publier l'APK avec notes de version

#### Étape 2: Vérifier la Release
```bash
# URL de la release
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/tag/v1.0.0

# URL de téléchargement direct
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/download/v1.0.0/SentinelQuantumVanguardAIPro-v1.0.0.apk
```

#### Étape 3: Le Site Web Est Déjà Configuré
Le bouton "Télécharger APK" sur le site pointe automatiquement vers :
```
https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/SentinelQuantumVanguardAIPro-v1.0.0.apk
```

---

### Méthode 2: Build Local (Si nécessaire)

```bash
cd android-app

# Installer les dépendances
npm install

# Générer le keystore (si absent)
cd android/app
keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore debug.keystore \
  -alias androiddebugkey \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass android \
  -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"

# Build Release APK
cd ..
./gradlew assembleRelease

# APK généré dans:
# app/build/outputs/apk/release/app-release.apk
```

---

## ✅ VALIDATION APK

### Vérifications Automatiques
Le workflow vérifie automatiquement :
- ✅ APK existe
- ✅ Taille > 10 MB
- ✅ Build réussi sans erreurs

### Vérifications Manuelles
```bash
# Vérifier la taille
ls -lh SentinelQuantumVanguardAIPro-v1.0.0.apk

# Vérifier la signature
jarsigner -verify -verbose -certs SentinelQuantumVanguardAIPro-v1.0.0.apk

# Obtenir les infos
aapt dump badging SentinelQuantumVanguardAIPro-v1.0.0.apk
```

---

## 📱 TEST SUR APPAREIL

### Installation
```bash
# Via adb
adb install SentinelQuantumVanguardAIPro-v1.0.0.apk

# OU manuellement
1. Télécharge depuis GitHub Releases
2. Active "Sources inconnues"
3. Installe l'APK
```

### Validation
- [ ] App se lance
- [ ] Permissions fonctionnent
- [ ] Détection appel fonctionne
- [ ] Journal persiste
- [ ] Pas de crash

---

## 🔄 WORKFLOW DE RELEASE

### Versions
```
v1.0.0 - Production initiale
v1.1.0 - Ajout features (minor)
v1.0.1 - Corrections bugs (patch)
v2.0.0 - Breaking changes (major)
```

### Process
```
1. Code prêt → Merge vers main
2. Tag version: git tag v1.0.0
3. Push tag: git push origin v1.0.0
4. Workflow build automatique
5. Release publiée sur GitHub
6. APK disponible en téléchargement
7. Site web mis à jour automatiquement
```

---

## 🔐 SIGNATURE APK

### Debug Keystore (Actuel)
```
Store: debug.keystore
Alias: androiddebugkey
Password: android
Validity: 10000 days
```

⚠️ **Pour production réelle, générer un keystore sécurisé :**

```bash
keytool -genkeypair \
  -v \
  -storetype PKCS12 \
  -keystore release.keystore \
  -alias release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <PASSWORD_SECURE> \
  -keypass <PASSWORD_SECURE> \
  -dname "CN=Sentinel,O=SentinelSecurity,C=FR"
```

Puis configurer dans `android/app/build.gradle`:
```gradle
signingConfigs {
    release {
        storeFile file('release.keystore')
        storePassword System.getenv("KEYSTORE_PASSWORD")
        keyAlias 'release'
        keyPassword System.getenv("KEY_PASSWORD")
    }
}
```

---

## 📊 STATISTIQUES BUILD

### Taille APK Attendue
- **Debug APK**: ~30-35 MB
- **Release APK**: ~15-20 MB (avec ProGuard)
- **Minimum acceptable**: 10 MB

### Temps de Build
- **Local**: 5-10 minutes
- **GitHub Actions**: 8-12 minutes

---

## 🐛 DÉPANNAGE

### APK trop petit (< 10 MB)
```bash
# Vérifier les erreurs de build
./gradlew assembleRelease --stacktrace

# Nettoyer et rebuilder
./gradlew clean
./gradlew assembleRelease
```

### Erreur de signature
```bash
# Régénérer le keystore
rm debug.keystore
# Puis relancer la génération
```

### Build échoue
```bash
# Vérifier JDK
java -version  # Doit être 17

# Vérifier Node
node --version  # Doit être 18+

# Nettoyer
cd android-app
rm -rf node_modules
npm install
```

---

## 🔗 LIENS UTILES

- **Workflow**: `.github/workflows/release-apk.yml`
- **Releases**: https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases
- **Actions**: https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions
- **Site Web**: https://sentinelquantumvanguardaipro.pages.dev

---

## 📋 CHECKLIST RELEASE

Avant de publier une nouvelle version :

- [ ] Code testé localement
- [ ] Tests passent
- [ ] Version incrémentée dans `package.json`
- [ ] CHANGELOG mis à jour
- [ ] Documentation à jour
- [ ] Tag créé
- [ ] Workflow lancé
- [ ] Release vérifiée sur GitHub
- [ ] APK testé sur appareil réel
- [ ] Site web vérifié

---

**🎯 OBJECTIF : APK PRODUCTION RÉEL ET SIGNÉ**

**🚀 DISPONIBLE SUR GITHUB RELEASES**
