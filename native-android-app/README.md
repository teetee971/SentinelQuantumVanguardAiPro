# Sentinel Quantum Vanguard — Native Android App

Application Android native en Kotlin avec Jetpack Compose pour la consultation de flux OSINT publics.

## Caractéristiques

- Kotlin + Jetpack Compose
- Accès sans authentification
- Lecture seule des sources OSINT publiques
- Sources : CERT-FR, ANSSI, CVE/NVD
- Interface sombre, sobre et institutionnelle
- Aucun backend propriétaire
- Aucune promesse de cybersécurité active : l'application sert à la veille et à la consultation

## Prérequis

- Android Studio compatible avec AGP 9.4
- JDK 17
- Android SDK Platform 37 pour la compilation
- Gradle 9.6 via le wrapper fourni
- Android 6.0 (API 23) minimum pour l'exécution

## Installation

1. Cloner le dépôt.
2. Ouvrir `native-android-app/` dans Android Studio.
3. Synchroniser les dépendances Gradle.
4. Lancer sur un émulateur ou un appareil physique.

## Build APK Debug

```bash
cd native-android-app
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/`.

## Build APK Release

```bash
./gradlew assembleRelease
```

La release de production doit être signée avec le certificat prévu pour la distribution. Les secrets de signature ne doivent jamais être stockés dans le dépôt.

## Structure du projet

```text
native-android-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/sentinel/quantum/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   ├── navigation/
│   │   │   └── ui/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## Sécurité et permissions

L'application utilise uniquement :

- `INTERNET` pour récupérer les flux OSINT publics ;
- `ACCESS_NETWORK_STATE` pour connaître l'état de la connectivité.

Aucune permission téléphonique, contact, caméra, microphone ou localisation n'est requise par l'application actuelle.

Le manifeste interdit le trafic HTTP en clair (`usesCleartextTraffic=false`) et désactive la sauvegarde Android (`allowBackup=false`). Le build release active également R8/ProGuard.

## Dépendances principales

- AndroidX Core KTX
- AndroidX Lifecycle
- Activity Compose
- Jetpack Compose / Material 3
- Navigation Compose
- Rome Tools pour RSS/Atom
- OkHttp pour les flux HTTP
- Kotlin Coroutines

Les versions sont maintenues dans `app/build.gradle` et alignées sur les versions Android/Compose actuellement retenues.

## Validation

Le workflow `.github/workflows/build-native-android.yml` constitue le build Android de validation. Le workflow `.github/workflows/android-release.yml` est réservé aux releases signées sur tags de version.

Une modification du code ou des dépendances ne vaut pas validation CI tant que le runner GitHub n'a pas effectivement exécuté les étapes et produit des résultats exploitables.

## Version actuelle

- Version code : 1
- Version nom : 1.0.0
- Package : `com.sentinel.quantum`
- `minSdk` : 23
- `targetSdk` : 36
- `compileSdk` : 37
