# Guide de Build - Sentinel Quantum Vanguard Android

## Prérequis pour le build

⚠️ **Important**: Le build nécessite l'accès aux repositories Google Maven et Maven Central.

### Environnement requis

1. **Android Studio** Arctic Fox ou supérieur
2. **JDK 8+** (OpenJDK 11 ou 17 recommandé)
3. **Android SDK** avec les composants suivants :
   - Android SDK Platform 34
   - Android SDK Build-Tools 34.0.0
   - Android SDK Platform-Tools
4. **Connexion Internet** avec accès à :
   - https://dl.google.com/dl/android/maven2/ (Google Maven)
   - https://repo1.maven.org/maven2/ (Maven Central)

## Instructions de Build

### Option 1 : Via Android Studio (Recommandé)

1. Ouvrir Android Studio
2. File → Open → Sélectionner le dossier `native-android-app`
3. Attendre la synchronisation Gradle (première fois peut prendre 5-10 min)
4. Build → Make Project
5. Build → Build Bundle(s) / APK(s) → Build APK(s)

L'APK sera dans : `native-android-app/app/build/outputs/apk/debug/app-debug.apk`

### Option 2 : Ligne de commande

```bash
cd native-android-app

# Build debug APK
./gradlew assembleDebug

# Build release APK (nécessite signing config)
./gradlew assembleRelease

# Nettoyer le projet
./gradlew clean
```

## Résolution des problèmes courants

### Erreur "Could not resolve com.android.tools.build:gradle"

**Cause**: Pas d'accès au Google Maven Repository

**Solution**:
1. Vérifier la connexion Internet
2. Vérifier qu'aucun firewall ne bloque dl.google.com
3. Si derrière un proxy d'entreprise, configurer gradle.properties :

```properties
systemProp.http.proxyHost=votre.proxy.com
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=votre.proxy.com
systemProp.https.proxyPort=8080
```

### Erreur de version Gradle

Si vous avez une erreur liée à la version de Gradle :

```bash
# Utiliser le wrapper Gradle fourni
./gradlew wrapper --gradle-version=8.2

# Ou spécifier la distribution
./gradlew wrapper --gradle-version=8.2 --distribution-type=bin
```

### Erreur Android SDK non trouvé

Définir la variable d'environnement ANDROID_HOME :

```bash
# Linux/Mac
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

# Windows
set ANDROID_HOME=C:\Users\<username>\AppData\Local\Android\Sdk
set PATH=%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools
```

## Installation de l'APK

### Sur émulateur

```bash
# Lancer l'émulateur depuis Android Studio
# Puis installer l'APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Sur appareil physique

1. Activer "Options pour les développeurs" sur l'appareil :
   - Paramètres → À propos → Appuyer 7 fois sur "Numéro de build"
2. Activer "Débogage USB" dans les options développeur
3. Connecter l'appareil via USB
4. Installer :

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Installation manuelle (sans ADB)

1. Copier l'APK sur l'appareil
2. Ouvrir le fichier APK avec le gestionnaire de fichiers
3. Accepter l'installation depuis des sources inconnues si demandé

## Structure de l'APK

L'APK construit contient :

- **Classes compilées** (Kotlin → DEX)
- **Ressources Android** (layouts, strings, images)
- **Bibliothèques natives** (si nécessaire)
- **AndroidManifest.xml**
- **Signature de debug** (pour debug APK)

### Taille approximative

- Debug APK : ~5-8 MB
- Release APK (avec ProGuard) : ~3-5 MB

## Configuration de signature pour Release

Pour créer un APK de release signé :

1. Créer un keystore :

```bash
keytool -genkey -v -keystore sentinel-release.keystore \
  -alias sentinel-release -keyalg RSA -keysize 2048 \
  -validity 10000
```

2. Créer `keystore.properties` dans le dossier racine :

```properties
storeFile=sentinel-release.keystore
storePassword=<votre_password>
keyAlias=sentinel-release
keyPassword=<votre_password>
```

3. Ajouter au `.gitignore` :

```
*.keystore
keystore.properties
```

4. Build release :

```bash
./gradlew assembleRelease
```

## Tests

### Vérifier l'APK

```bash
# Lister le contenu de l'APK
unzip -l app/build/outputs/apk/debug/app-debug.apk

# Vérifier les permissions
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk

# Voir le manifest
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```

### Tests sur émulateur

```bash
# Lancer l'app
adb shell am start -n com.sentinel.quantum/.MainActivity

# Voir les logs
adb logcat | grep "SentinelQuantum"
```

## Dépendances du projet

Les dépendances suivantes seront téléchargées lors du build :

- AndroidX Core KTX 1.12.0
- AndroidX Lifecycle 2.6.2
- Jetpack Compose BOM 2023.10.01
- Material 3
- Navigation Compose 2.7.5
- Rome Tools RSS 2.1.0
- OkHttp 4.12.0
- Kotlin Coroutines 1.7.3

**Taille totale des dépendances** : ~50-80 MB

## Compatibilité

- **Minimum** : Android 6.0 (API 23) - Marshmallow
- **Target** : Android 14 (API 34)
- **Architectures** : armeabi-v7a, arm64-v8a, x86, x86_64

## Support

Pour des problèmes de build :

1. Nettoyer le projet : `./gradlew clean`
2. Supprimer `.gradle` et `build` folders
3. Invalider les caches dans Android Studio
4. Vérifier que Gradle Daemon n'est pas en conflit : `./gradlew --stop`

## Environnement de build CI/CD

Pour GitHub Actions ou autre CI :

```yaml
- name: Setup Android SDK
  uses: android-actions/setup-android@v2

- name: Build APK
  run: |
    cd native-android-app
    chmod +x gradlew
    ./gradlew assembleDebug

- name: Upload APK
  uses: actions/upload-artifact@v3
  with:
    name: app-debug
    path: native-android-app/app/build/outputs/apk/debug/app-debug.apk
```

## License

Open source - Voir LICENSE dans le répertoire racine du projet.
