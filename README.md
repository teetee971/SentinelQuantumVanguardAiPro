# Sentinel Quantum Vanguard AI Pro

![Production Ready](https://img.shields.io/badge/Production-Ready-00e5ff?style=for-the-badge&logo=checkmarx&logoColor=white)
![Android APK](https://img.shields.io/badge/Android-APK_Available-00e5ff?style=for-the-badge&logo=android&logoColor=white)
![Cloudflare Secured](https://img.shields.io/badge/Cloudflare-Secured-00e5ff?style=for-the-badge&logo=cloudflare&logoColor=white)
![Security Documented](https://img.shields.io/badge/Security-Documented-00e5ff?style=for-the-badge&logo=security&logoColor=white)

Plateforme de cybersécurité multi-plateforme avec application Android fonctionnelle et interface web de visualisation.

## Positionnement

Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité complète comprenant :

1. **Application Android Native (Nouvelle)** : Application Kotlin + Jetpack Compose pour la consultation de flux OSINT publics (CERT-FR, ANSSI, CVE)
2. **Application Android React Native (Existante)** : Protection mobile avec modules actifs
3. **Interface Web** : Dashboard de visualisation et analyse (frontend statique sécurisé)

## 📱 Application Android Native - Flux OSINT (NOUVEAU)

**Application minimaliste et fonctionnelle en Kotlin + Jetpack Compose**

### Caractéristiques principales
- ✅ **Kotlin + Jetpack Compose** - Interface moderne et déclarative
- ✅ **Aucune authentification** - Accès direct
- ✅ **Aucune collecte de données** - Respect total de la vie privée
- ✅ **Lecture seule** - Consultation uniquement des flux OSINT
- ✅ **Sources OSINT publiques** - CERT-FR, ANSSI, CVE/NVD
- ✅ **Design sombre institutionnel** - Interface sobre et militaire
- ✅ **Aucun backend** - Application autonome
- ✅ **Honnêteté totale** - Pas de promesses de cybersécurité active

### Documentation
📖 **[Documentation complète](native-android-app/APK_README.md)**  
🔧 **[Guide de build](native-android-app/BUILD_GUIDE.md)**  
📱 **[Code source](native-android-app/)**

### Installation locale
```bash
cd native-android-app
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

⚠️ **Note**: Le build CI est actuellement bloqué par des restrictions d'accès aux repositories Maven de Google. Le build local fonctionne parfaitement avec Android Studio.

---

## 📱 Application Android V1 (React Native)

**APK Production Disponible sur GitHub Releases**

### Téléchargement Production
👉 **[Télécharger APK v1.0.0 (Release)](https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest)**

📥 **Lien direct**: https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/SentinelQuantumVanguardAIPro-v1.0.0.apk

### Modules Actifs
- ✅ **Module Téléphone** : Journal d'appels, caller ID, détection spam
- ✅ **Gestion Permissions** : Runtime permissions Android conformes
- ✅ **SOC Dashboard** : Interface de supervision sécurité
- ⚙️ **Monitoring Réseau** : En développement

### Installation
```bash
# 1. Télécharger depuis GitHub Releases
# 2. Activer "Sources inconnues" sur Android
# 3. Installer l'APK
# 4. Accorder les permissions
```

📖 **[Guide de Release](RELEASE_GUIDE.md)** | **[Documentation Android](ANDROID_README.md)** | **[Guide de Test](APK_TEST_GUIDE.md)**

📦 **[Manifeste de Livraison APK](APK_DELIVERY_MANIFEST.md)** - Confirmation factuelle et vérifiable des 6 critères de production

✅ **[Confirmation Livraison APK](CONFIRMATION_LIVRAISON_APK.md)** - Réponse concise aux 6 critères (Français)

### Caractéristiques
- ✅ **Fonctionnel** : Vraie application, pas une démo
- ✅ **Sans root** : Fonctionne sur Android standard (6.0+)
- ✅ **Conforme** : Respecte les politiques Google Play
- ✅ **Transparent** : AUCUNE collecte de données, AUCUN spyware
- ✅ **Open Source** : Code auditable

## 🌐 Interface Web

- **Frontend statique** (HTML / CSS / JavaScript)
- **Hébergement** Cloudflare Pages
- **Déploiement** Edge global
- **Aucun backend**
- **Aucune collecte de données**

### Modes Visuels

L'interface web propose deux modes visuels :

- **Mode Institutionnel (par défaut)** : Interface sobre et professionnelle pour usage opérationnel
- **Mode Cinématique (optionnel)** : Présentation visuelle améliorée avec hero section et imagerie professionnelle

**Caractéristiques du mode cinématique :**
- ✅ Activation via toggle dans l'interface
- ✅ Chargement paresseux (lazy loading) des assets
- ✅ Respect de `prefers-reduced-motion` pour l'accessibilité
- ✅ Préférence sauvegardée en localStorage
- ✅ Design government/defense compliant (pas d'emojis, couleurs sobres)
- ✅ Aucun impact sur le build Android ou les pipelines CI

Les assets visuels (vidéo de fond, imagerie) ne sont chargés **que** lorsque le mode cinématique est activé.

## Sécurité

- ✅ Frontend statique : surface d'attaque minimale
- ✅ Dépendances contrôlées
- ✅ Documentation sécurité fournie ([SECURITY.md](SECURITY.md))
- ✅ Aucune exécution serveur

## Statut

✔️ **Production Ready**  
✔️ **Déployé**  
✔️ **Documenté**  
✔️ **Conforme Cloudflare Pages**

## 🔄 CI/CD Pipeline

Le projet utilise GitHub Actions avec une architecture propre : **1 objectif = 1 workflow**.

### Workflows Essentiels

| Workflow | Rôle | Déclencheur | Sortie |
|----------|------|-------------|--------|
| `build-android.yml` | Build Android APK debug | Push sur `main`, manuel | Artifact APK |
| `release-apk.yml` | Build & Release APK signé | Tag `v*.*.*`, manuel | GitHub Release + APK + SHA-256 |
| `codeql-analysis.yml` | Analyse sécurité CodeQL | Push/PR sur `main`, hebdomadaire | Alertes sécurité |
| `integrity-check.yml` | Vérification intégrité | Push/PR sur `main`, manuel | Rapport intégrité |
| `frontend-validation.yml` | Validation frontend statique | Push (paths: public/*), PR | Rapport validation |
| `pages-deploy.yml` | Déploiement GitHub Pages | Push (paths: public/*), manuel | Site déployé |
| `release.yml` | Création GitHub Release | Tag `v*.*.*`, manuel | Release notes |

### Standards Techniques

- **JDK**: 17 (Temurin) — uniforme sur tous les workflows Android
- **Gradle**: Version wrapper avec cache activé
- **Node.js**: 18 (LTS)
- **CodeQL**: Analyse Java/Kotlin avec build Gradle réel
- **Sécurité**: Seules les alertes High/Critical bloquent les releases

### Vérification d'Intégrité APK

Chaque release APK inclut un fichier `.sha256` pour vérification :

```bash
# Vérifier l'intégrité de l'APK téléchargé
sha256sum -c SentinelQuantumVanguardAIPro-v1.0.0.apk.sha256
```

## Distribution & Vérification APK

### Téléchargement Officiel

| Ressource | Lien |
|-----------|------|
| Page de téléchargement | [/public/telecharger.html](public/telecharger.html) |
| GitHub Releases | [Releases](https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases) |
| APK Direct | [Télécharger APK](https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/SentinelQuantumVanguardAIPro-v1.0.0.apk) |
| SHA-256 | [Télécharger checksum](https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/SentinelQuantumVanguardAIPro-v1.0.0.apk.sha256) |

### Vérification de l'Intégrité (SHA-256)

```bash
# Étape 1 : Télécharger l'APK et le fichier SHA-256
wget https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/SentinelQuantumVanguardAIPro-v1.0.0.apk
wget https://github.com/teetee971/SentinelQuantumVanguardAiPro/releases/latest/download/SentinelQuantumVanguardAIPro-v1.0.0.apk.sha256

# Étape 2 : Vérifier le checksum
sha256sum -c SentinelQuantumVanguardAIPro-v1.0.0.apk.sha256

# Résultat attendu :
# SentinelQuantumVanguardAIPro-v1.0.0.apk: OK
```

### Vérification de la Signature APK

```bash
# Avec apksigner (Android SDK Build Tools)
apksigner verify --verbose --print-certs SentinelQuantumVanguardAIPro-v1.0.0.apk

# Résultat attendu :
# Verified using v2 scheme (APK Signature Scheme v2): true
# Verified using v3 scheme (APK Signature Scheme v3): true

# Alternative avec jarsigner (JDK)
jarsigner -verify -verbose -certs SentinelQuantumVanguardAIPro-v1.0.0.apk
```

### Pourquoi Distribution Directe ?

| Avantage | Explication |
|----------|-------------|
| Souveraineté | Aucune dépendance aux stores tiers |
| Rapidité | Mises à jour instantanées |
| Transparence | Code source et build 100% publics |
| Vérifiabilité | SHA-256 + signature cryptographique |

## Public cible

### Application Android
- Utilisateurs mobiles soucieux de leur sécurité
- Protection contre spam et scam téléphoniques
- Monitoring sécurité en temps réel sur smartphone
- Professionnels de la cybersécurité mobile

### Interface Web

- Experts cybersécurité
- Démonstration SOC
- Présentation institutionnelle
- Vitrine technologique IA

## Déploiement

Le site est automatiquement déployé sur Cloudflare Pages à chaque push sur la branche `main`.

**URL de production** : https://sentinelquantumvanguardaipro.pages.dev

## Développement Local

### Application Android
```bash
cd android-app
npm install

# Test sur émulateur/appareil
npm run android

# Build APK
cd android
./gradlew assembleDebug
```

### Frontend Web

```bash
npm install
npm run dev
```

Le site sera accessible sur `http://localhost:5173`

### Assets Cinématiques (Optionnel)

Pour activer complètement le mode cinématique avec vidéo de fond et imagerie professionnelle :

1. Placer les assets dans `assets/cinematic/`
2. Consulter `assets/cinematic/README.md` pour les spécifications

**Contraintes de design :**
- Pas d'emojis
- Pas de couleurs saturées
- Imagerie réaliste professionnelle uniquement
- Conforme aux normes gouvernementales/défense

Les assets ne sont **jamais** chargés en mode institutionnel (mode par défaut).

## Build

```bash
npm run build
```

Le build génère un site statique dans le répertoire `dist/`.

## Licence

© 2024 – Système de Défense Avancée

---

**Positionnement World Leader**

Sentinel Quantum Vanguard AI Pro est une plateforme de visualisation et d'analyse IA orientée sécurité, conçue selon des standards professionnels, avec une architecture volontairement statique garantissant une surface d'attaque minimale et une transparence totale.

> Aucun superlatif marketing • Aucune promesse d'interception mondiale • Discours crédible, auditable, professionnel
