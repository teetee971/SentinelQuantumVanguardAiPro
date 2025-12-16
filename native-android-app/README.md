# Sentinel Quantum Vanguard - Native Android App

Application Android native en Kotlin avec Jetpack Compose pour la consultation de flux OSINT publics.

## Caractéristiques

- **Kotlin + Jetpack Compose** : Interface moderne et déclarative
- **Aucune authentification** : Accès direct sans compte
- **Aucune collecte de données** : Respect total de la vie privée
- **Lecture seule** : Consultation uniquement des flux OSINT
- **Sources OSINT publiques** : CERT-FR, ANSSI, CVE/NVD
- **Design sombre institutionnel** : Interface sobre et professionnelle
- **Pas d'emoji** : Design professionnel et militaire
- **Aucune promesse de cybersécurité active** : Outil de veille uniquement

## Prérequis

- Android Studio Arctic Fox ou supérieur
- JDK 8 ou supérieur
- Android SDK (API 23-34)
- Gradle 8.2+

## Installation

1. Cloner le dépôt
2. Ouvrir le projet dans Android Studio
3. Synchroniser les dépendances Gradle
4. Lancer sur émulateur ou appareil physique

## Build APK Debug

```bash
cd native-android-app
./gradlew assembleDebug
```

L'APK sera disponible dans : `app/build/outputs/apk/debug/app-debug.apk`

## Build APK Release

```bash
./gradlew assembleRelease
```

L'APK sera disponible dans : `app/build/outputs/apk/release/app-release.apk`

## Installation sur appareil

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Structure du projet

```
native-android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/sentinel/quantum/
│   │   │   ├── MainActivity.kt           # Point d'entrée
│   │   │   ├── data/                     # Modèles de données
│   │   │   │   ├── OsintFeedItem.kt
│   │   │   │   └── OsintRepository.kt
│   │   │   ├── navigation/               # Navigation
│   │   │   │   ├── Screen.kt
│   │   │   │   └── NavGraph.kt
│   │   │   └── ui/                       # Interface utilisateur
│   │   │       ├── theme/                # Thème sombre institutionnel
│   │   │       │   ├── Color.kt
│   │   │       │   ├── Theme.kt
│   │   │       │   └── Type.kt
│   │   │       └── screens/              # Écrans de l'app
│   │   │           ├── HomeScreen.kt
│   │   │           ├── OsintFeedScreen.kt
│   │   │           ├── AboutScreen.kt
│   │   │           └── ComplianceScreen.kt
│   │   ├── res/                          # Ressources
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── themes.xml
│   │   │   └── mipmap-*/                 # Icônes de l'app
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Écrans

### 1. Écran d'accueil
- Présentation de l'application
- Fonctionnalités principales
- Navigation vers les autres écrans

### 2. Écran Flux OSINT
- Affichage des flux RSS de CERT-FR, ANSSI, CVE
- Source visible pour chaque élément
- Date de publication
- Explication pédagogique
- Bouton d'actualisation

### 3. Écran "Ce que Sentinel fait / ne fait pas"
- Liste claire des fonctionnalités
- Liste claire des non-fonctionnalités
- Avertissement sur les limitations

### 4. Écran Conformité & Souveraineté
- Informations RGPD
- Sources de données
- Souveraineté numérique
- Transparence
- Permissions utilisées
- License open source

## Dépendances principales

- **AndroidX Core KTX** : Extensions Kotlin pour Android
- **Jetpack Compose** : UI déclarative moderne
- **Material 3** : Design Material 3
- **Navigation Compose** : Navigation entre écrans
- **Rome Tools** : Parsing RSS/Atom
- **OkHttp** : Client HTTP pour les flux RSS
- **Coroutines** : Programmation asynchrone

## Permissions

L'application utilise uniquement les permissions suivantes :
- `INTERNET` : Pour récupérer les flux OSINT publics
- `ACCESS_NETWORK_STATE` : Pour vérifier la connectivité

**Aucune permission sensible n'est demandée.**

## Sécurité

- Aucune collecte de données personnelles
- Aucun tracking
- Aucun backend propriétaire
- Sources OSINT publiques uniquement
- Code source auditable
- ProGuard activé en mode release

## Conformité

- **RGPD** : Aucune donnée personnelle collectée
- **Transparence** : Code open source
- **Souveraineté** : Application autonome
- **Honnêteté** : Pas de promesses de cybersécurité active

## License

Open source - Voir LICENSE dans le répertoire racine

## Support

- Minimum : Android 6.0 (API 23)
- Cible : Android 14 (API 34)
- Architecture : ARM, ARM64, x86, x86_64

## Version

- Version code : 1
- Version nom : 1.0.0
- Package : com.sentinel.quantum
