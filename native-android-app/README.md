# Sentinel Quantum Vanguard — Native Android App

Application Android native en Kotlin avec Jetpack Compose pour la consultation de flux OSINT publics.

## Caractéristiques

- Kotlin + Jetpack Compose
- Accès sans authentification
- Lecture seule des sources OSINT publiques
- Sources : CERT-FR, ANSSI, CVE/NVD
- Interface sombre, sobre et institutionnelle
- Aucun backend propriétaire
- Vérification manuelle locale de numéros avec validation bornée et statistiques de session
- Filtrage Android via le rôle système Call Screening : blocage par règles utilisateur et silencieux uniquement pour les préfixes issus d'un paquet signé, frais et anti-rollback
- Analyse locale bornée d'un email brut : en-têtes, Authentication-Results observé, domaines et liens
- Cache local hors-ligne des flux OSINT (bannière « données locales du … »), recherche texte et filtre par source, marquage lu/non lu — tout est stocké localement, sans analytique
- Journal local : recherche par mot-clé/tag et filtre par niveau, export sanitisé partageable via le sélecteur de partage Android (les données exportées passent par le même filtrage anti-secrets que le journal affiché)
- Analyse d'un email partagé depuis une autre application (feuille de partage Android, `ACTION_SEND` texte brut) directement vers l'analyseur local, sans nouvelle permission
- Vérification manuelle et optionnelle de mises à jour de vigilance signées pour le filtrage d'appels (interface prête, désactivée par défaut tant qu'aucun émetteur/clé de production n'est provisionné)
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

Aucune permission de journal d'appels, état téléphonique, SMS, contact, caméra, microphone ou localisation n'est requise. Le service de filtrage fonctionne uniquement après attribution explicite du rôle Android `ROLE_CALL_SCREENING`; l'analyse email n'accède à aucune boîte mail.

Le manifeste interdit le trafic HTTP en clair (`usesCleartextTraffic=false`) et désactive la sauvegarde Android (`allowBackup=false`). Le build release active également R8/ProGuard.

Un `FileProvider` (`androidx.core.content.FileProvider`, non exporté) est déclaré uniquement pour l'export du journal de sécurité local, et n'expose que le sous-répertoire de cache dédié `sentinel_log_export/` (voir `res/xml/file_paths.xml`) ; aucun autre fichier de l'application n'est accessible par ce biais. Le cache OSINT hors-ligne et l'état lu/non lu sont stockés localement (SharedPreferences), sans transmission réseau ni analytique.

La normalisation est France-first : les formes nationales, `+33` et `0033`
sont rapprochées. Aucun préfixe de réputation non signé n'est activé par défaut.
L'infrastructure vérifie des paquets P-256 bornés, expirables et à séquence
croissante, mais aucune source commerciale ou communautaire n'est fournie.

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
