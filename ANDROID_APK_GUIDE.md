# Sentinel Quantum Vanguard AI Pro — Guide Android

## Source canonique

Le seul projet Android maintenu est `native-android-app/`.

Les anciens chemins Android et les anciens workflows de release ne sont plus des instructions d'exécution.

## État réel du projet Android

Le module Android actuel utilise l'application `com.sentinel.quantum`, compile avec SDK 37, cible SDK 36 et utilise `minSdk 23`. Il n'existe pas de flavors Public/Institutional dans la configuration canonique.

Ne pas documenter ou utiliser des variantes qui n'existent pas dans `native-android-app/app/build.gradle`.

## Build de validation

Le workflow actif est `.github/workflows/build-native-android.yml`. Il construit l'APK de validation depuis `native-android-app/` et publie un artefact CI.

Une exécution CI réussie doit être observée avant de considérer le build comme validé. La présence d'un workflow ou d'un fichier APK ne constitue pas une preuve de réussite.

## Release signée

Le workflow actif est `.github/workflows/android-release.yml`.

Il est déclenché uniquement par un tag `v*`. Le workflow :

1. vérifie le format du tag ;
2. vérifie que le commit du tag est accessible depuis `main` ;
3. vérifie la présence des secrets de signature ;
4. décode temporairement le keystore dans `/tmp` avec des permissions restrictives ;
5. exécute `assembleRelease` ;
6. génère les SHA-256 des APK ;
7. publie les APK et leurs checksums dans la GitHub Release ;
8. supprime le keystore temporaire, y compris en cas d'échec.

Secrets attendus : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Aucun keystore, mot de passe ou clé privée ne doit être commité.

## Build local

Prérequis : JDK 17, Android SDK Platform 37 et accès aux dépendances Gradle.

Depuis la racine du projet Android :

```text
cd native-android-app
./gradlew assembleDebug
```

Pour une release locale, utiliser uniquement un keystore de test ou une configuration de signature sécurisée hors dépôt. Ne jamais placer des mots de passe en clair dans `build.gradle`.

## Vérifications de sécurité Android

Avant toute distribution, vérifier au minimum :

- installation et lancement sur un appareil de test ;
- navigation et comportement réseau attendus ;
- absence de trafic HTTP en clair lorsque la politique de l'application l'interdit ;
- permissions réellement déclarées dans le manifeste ;
- signature de l'APK ;
- checksum SHA-256 ;
- absence de secret ou keystore dans l'artefact et le dépôt.

Les protections Android doivent être décrites à partir du code actuellement présent, et non à partir d'anciennes versions documentaires.

## Règle de validation

`correctif appliqué ≠ testé ≠ CI réussie ≠ release validée ≠ sécurité prouvée`.

Le blocage actuel des runners GitHub Actions est suivi séparément dans l'issue #195. Il ne doit pas être contourné en supprimant ou en affaiblissant les contrôles.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement indépendant de tout autre projet. Aucun import, secret, configuration, dépendance ou couplage opérationnel externe n'est autorisé.
