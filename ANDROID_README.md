# Sentinel Quantum Vanguard AI Pro - Android V1

## 📱 APK Android Fonctionnel

Application Android de sécurité mobile réelle et fonctionnelle.

### ✅ Modules Actifs

#### 1. Module Téléphone (ACTIF)
- ✅ **Accès au journal d'appels** : Lecture de l'historique des appels
- ✅ **Accès aux contacts** : Enrichissement caller ID
- ✅ **État du téléphone** : Informations opérateur, pays, type de réseau
- ✅ **Gestion des permissions** : Demande runtime conforme Android
- ⚙️ **Détection spam** : En développement (analyse locale)
- ⚙️ **Blocage d'appels** : Framework prêt (nécessite API Telecom)

#### 2. Module Sécurité Réseau (EN DÉVELOPPEMENT)
- ⚙️ Surveillance connexions actives
- ⚙️ Analyse domaines/IP
- ⚙️ Alertes sécurité réseau

#### 3. SOC Dashboard (ACTIF)
- ✅ Interface de supervision sécurité
- ✅ Tableau de bord cybersécurité

### 📥 Télécharger l'APK

#### Via GitHub Actions (Recommandé)
1. Allez sur https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions
2. Cliquez sur le workflow "Build Android Debug APK"
3. Sélectionnez la dernière exécution réussie (✅)
4. Descendez jusqu'à "Artifacts"
5. Téléchargez `sentinel-quantum-vanguard-debug-apk`
6. Extrayez le fichier ZIP
7. Installez `app-debug.apk` sur votre appareil Android

#### Installation sur Android
```bash
# Via adb (si développeur)
adb install app-debug.apk

# Ou manuellement :
# 1. Transférez l'APK sur votre téléphone
# 2. Ouvrez le fichier
# 3. Autorisez l'installation depuis des sources inconnues si demandé
# 4. Installez l'application
```

### 🔐 Permissions Requises

L'application demande les permissions suivantes **uniquement quand nécessaire** :

#### Permissions Téléphone
- `READ_CALL_LOG` : Lecture de l'historique d'appels pour détection spam
- `READ_CONTACTS` : Enrichissement caller ID avec vos contacts
- `READ_PHONE_STATE` : Informations opérateur et réseau
- `READ_SMS` : Lecture SMS pour détection phishing (optionnel)

#### Permissions Réseau
- `INTERNET` : Accès réseau pour threat intelligence
- `ACCESS_NETWORK_STATE` : État de la connexion réseau

### ⚠️ Informations Importantes

#### Sécurité et Confidentialité
- ✅ **AUCUNE collecte de données** : Toutes les données restent sur votre appareil
- ✅ **AUCUN spyware** : Code source ouvert, auditable
- ✅ **AUCUNE interception illégale** : Respecte les lois en vigueur
- ✅ **Conforme Google Play** : Toutes les fonctionnalités sont légales et transparentes

#### Fonctionnalités Réalistes
- ✅ **Pas de promesses mensongères** : Seules les fonctionnalités réalisables sont implémentées
- ✅ **Pas de root requis** : Fonctionne sur Android standard (API 23+)
- ✅ **Transparent** : Chaque permission est expliquée et justifiée

### 🔧 Build Local

#### Prérequis
```bash
# Node.js 18+
node --version

# JDK 17
java -version

# Android SDK
```

#### Installation
```bash
cd android-app

# Installer les dépendances Node
npm install

# Build Debug APK
cd android
./gradlew assembleDebug

# APK généré dans :
# android/app/build/outputs/apk/debug/app-debug.apk
```

#### Build Release APK
```bash
cd android-app/android
./gradlew assembleRelease

# APK dans :
# app/build/outputs/apk/release/app-release.apk
```

### 📊 Spécifications Techniques

#### Compatibilité
- **Android minimum** : 6.0 (API 23)
- **Android cible** : 14 (API 34)
- **Architecture** : ARM, ARM64, x86, x86_64

#### Technologies
- **Framework** : React Native 0.73.2
- **Language** : TypeScript 5.3.3
- **Build** : Gradle 8.1.4
- **JDK** : 17 (Temurin)

#### Taille APK
- **Debug** : ~25-30 MB
- **Release** : ~15-20 MB (avec ProGuard)

### 🧪 Tests

#### Test sur Émulateur
```bash
cd android-app

# Démarrer l'émulateur Android
# Puis :
npm run android
```

#### Test sur Appareil Physique
```bash
# Activer le mode développeur et le débogage USB
# Connecter l'appareil
adb devices

cd android-app
npm run android
```

### 📱 Modules Détaillés

#### Module Téléphone
**Fonctionnalités Actives:**
- Lecture du journal d'appels (50 derniers appels)
- Accès aux contacts (500 contacts max)
- Informations opérateur et réseau
- Détection pays d'origine du numéro

**Fonctionnalités Framework (Prêt pour implémentation):**
- Détection spam/scam basée sur patterns
- Blocage d'appels (nécessite API Telecom Android)
- Enregistrement d'appels (selon législation locale)

#### Module Sécurité
**En Développement:**
- Monitoring réseau local
- Analyse connexions actives
- Détection comportements anormaux

### 🔍 Architecture Modulaire

```
android-app/
├── android/                          # Code Android natif
│   └── app/src/main/java/com/sentinel/
│       ├── MainActivity.java         # Activité principale
│       ├── MainApplication.java      # Application React Native
│       └── phonemodule/              # Module natif téléphone
│           ├── PhoneSecurityModule.java    # Implémentation native
│           └── PhoneSecurityPackage.java   # Package React Native
├── src/
│   ├── modules/phone/
│   │   ├── PhoneModule.ts            # Module TypeScript
│   │   ├── NativePhoneModule.ts      # Bridge vers natif
│   │   └── CallIdentification.ts     # Service identification
│   └── screens/
│       ├── HomeScreen.tsx            # Écran d'accueil
│       ├── PhoneScreen.tsx           # Module téléphone
│       └── CallHistoryScreen.tsx     # Historique appels
```

### 📖 Documentation Complémentaire

- [SECURITY.md](../SECURITY.md) : Politique de sécurité
- [MODULE_TELEPHONE_DOCUMENTATION.pdf](../MODULE_TELEPHONE_DOCUMENTATION.pdf) : Documentation module téléphone
- [PHONE_MODULE_SUMMARY.md](../PHONE_MODULE_SUMMARY.md) : Résumé module téléphone

### 🚀 Roadmap

#### Version 1.1 (Q1 2025)
- [ ] Détection spam avancée avec machine learning local
- [ ] Blocage d'appels via API Telecom
- [ ] Base de données locale de numéros spam

#### Version 1.2 (Q2 2025)
- [ ] Monitoring réseau complet
- [ ] Threat intelligence en temps réel
- [ ] Export sécurisé des logs

#### Version 2.0 (Q3 2025)
- [ ] Mode institution/entreprise
- [ ] Conformité RGPD complète
- [ ] Audit trail chiffré

### 🆘 Support

#### Problèmes Courants

**L'APK ne s'installe pas**
- Vérifiez que "Sources inconnues" est activé
- Assurez-vous d'avoir Android 6.0+
- Vérifiez l'espace disque disponible

**Permissions refusées**
- Les permissions sont facultatives
- L'app fonctionne avec permissions minimales
- Accordez les permissions dans Paramètres > Apps

**Build échoue**
- Vérifiez Node.js 18+
- Vérifiez JDK 17
- Supprimez `node_modules` et réinstallez

### 📄 Licence

© 2024 Sentinel Quantum Vanguard AI Pro

**Usage:**
- ✅ Utilisation personnelle
- ✅ Tests et développement
- ✅ Audit de sécurité

**Restrictions:**
- ❌ Pas d'usage illégal
- ❌ Pas de redistribution commerciale sans autorisation
- ❌ Pas de modification des avis de sécurité

### 🔗 Liens

- **Repository** : https://github.com/teetee971/SentinelQuantumVanguardAiPro
- **Actions/APK** : https://github.com/teetee971/SentinelQuantumVanguardAiPro/actions
- **Issues** : https://github.com/teetee971/SentinelQuantumVanguardAiPro/issues

---

**Status** : ✅ Production Ready - APK V1 Fonctionnel

**Build automatique** : Les APK sont générés automatiquement via GitHub Actions à chaque push sur les branches principales.
