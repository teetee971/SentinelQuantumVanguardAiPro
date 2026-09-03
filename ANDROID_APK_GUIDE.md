# Sentinel Quantum Vanguard AI Pro — Guide Android

## Source canonique

Le seul projet Android maintenu est `native-android-app/`.

Les anciens chemins Android et les anciennes procédures de distribution ne sont plus des sources de vérité.

## État réel du projet Android

Le module Android actuel utilise `com.sentinel.quantum`, compile avec SDK 37, cible SDK 36 et utilise `minSdk 23`. Il n'existe pas de flavors Public/Institutional dans la configuration canonique.

Ne pas documenter ou utiliser des variantes qui n'existent pas dans `native-android-app/app/build.gradle`.

## Build de validation

Le workflow `.github/workflows/build-native-android.yml` constitue le workflow de validation Android. Il construit l'APK de validation depuis `native-android-app/` et peut publier un artefact CI lorsqu'il s'exécute correctement.

Une exécution CI réussie doit être observée avant de considérer le build comme validé. La présence d'un workflow ou d'un fichier APK ne constitue pas une preuve de réussite.

## Release signée

Le workflow `.github/workflows/android-release.yml` est réservé aux releases signées sur tags `v*`.

Il doit être considéré comme une procédure de publication, pas comme la preuve qu'une release existe. Un APK n'est officiellement distribuable qu'après production effective de l'artefact, vérification de sa signature et publication confirmée.

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

Les échecs de démarrage de certains runners GitHub Actions doivent être traités comme des problèmes d'infrastructure CI tant qu'aucune étape du job n'a effectivement démarré. Ils ne doivent pas être contournés en supprimant ou en affaiblissant les contrôles.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement indépendant de tout autre projet. Aucun import, secret, configuration, dépendance ou couplage opérationnel externe n'est autorisé.

Les références Firebase présentes dans les scanners d'isolation et leurs tests sont des motifs interdits utilisés pour vérifier cette séparation ; elles ne constituent pas des dépendances opérationnelles.
