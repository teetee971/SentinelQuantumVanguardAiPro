# Guide de build — Sentinel Quantum Vanguard Android

Ce document décrit le build de la source Android canonique située dans `native-android-app/`. Il ne constitue pas la preuve qu'un build a déjà réussi sur l'infrastructure CI.

## Environnement de référence

- Android Gradle Plugin : 9.4.0 (déclaré dans `native-android-app/build.gradle`)
- Gradle Wrapper : 9.7.1 (`gradle/wrapper/gradle-wrapper.properties`)
- Plugin Compose Kotlin : 2.4.10 (le support Kotlin est intégré à AGP 9, aucun plugin `org.jetbrains.kotlin.android` séparé n'est appliqué)
- JDK : 17
- compileSdk : 37
- targetSdk : 36
- minSdk : 24
- Source Android : `native-android-app/`

Ces valeurs doivent rester alignées avec les fichiers Gradle du projet ; en cas de divergence, les fichiers Gradle font foi.

Les versions de bibliothèques doivent rester alignées sur `native-android-app/app/build.gradle` ; ne pas recopier une ancienne liste de dépendances depuis ce guide.

## Build local

Depuis la racine du dépôt :

```bash
cd native-android-app
./gradlew clean
./gradlew assembleDebug
```

Les mêmes vérifications que la CI :

```bash
./gradlew testDebugUnitTest --stacktrace --no-daemon
./gradlew lintDebug --stacktrace --no-daemon
./gradlew assembleDebug --stacktrace --no-daemon
```

Ces commandes nécessitent un accès réseau au dépôt Maven de Google (voir « Dépannage »).

Pour une release :

```bash
./gradlew assembleRelease
```

Le build release dépend de la configuration de signature prévue par le projet. Ne jamais committer un keystore, un mot de passe ou une propriété de signature contenant des secrets.

## Vérifications après build

Pour un APK réellement produit :

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
unzip -t app/build/outputs/apk/debug/app-debug.apk
```

Pour une release signée, vérifier la signature et calculer une empreinte SHA-256 avec les outils Android/JDK disponibles dans l'environnement de build.

## CI/CD

Le workflow de validation Android est `.github/workflows/build-native-android.yml`. Le workflow de release est `.github/workflows/android-release.yml`.

Les workflows actuels sont la source de vérité pour les actions GitHub et leurs versions. Les exemples historiques utilisant `android-actions/setup-android@v2` ou `actions/upload-artifact@v3` ne doivent pas être réintroduits.

## APK distribué

Aucun APK précompilé et signé n'est annoncé comme distribué par le dépôt tant qu'un artefact réel, signé et vérifiable n'a pas été produit. Un chemin de sortie de build ne constitue pas un lien de téléchargement public.

## Tests et preuve

Un résultat « validé » doit correspondre à une exécution réellement observée. En particulier :

1. le workflow doit démarrer et exécuter ses étapes ;
2. le build doit terminer avec succès ;
3. l'artefact attendu doit exister ;
4. pour une release, la signature et le SHA-256 doivent être vérifiés ;
5. les résultats doivent être rattachés au commit testé.

Un échec GitHub avant la première étape est un problème d'exécution de l'infrastructure CI, pas une preuve d'échec du code Android.

## Dépannage

Utiliser en priorité le wrapper fourni :

```bash
./gradlew --version
./gradlew clean
./gradlew assembleDebug --stacktrace
```

Vérifier également que JDK 17 et les composants SDK requis sont disponibles. Ne pas modifier le wrapper ou les versions du projet uniquement pour contourner un échec CI sans identifier sa cause.

### Erreur « plugin not found » sur `com.android.application`

```
com.android.application:com.android.application.gradle.plugin:9.4.0 not found
```

Ce message signifie que le dépôt Maven de Google (`google()`, servi par `dl.google.com`) n'est pas joignable depuis l'environnement de build. Il ne signifie pas que la version d'AGP est invalide : la même configuration est construite avec succès par `.github/workflows/build-native-android.yml` sur des runners GitHub Actions standards.

Causes typiques :

1. environnement isolé / pare-feu sortant bloquant `dl.google.com` et `maven.google.com` (c'est le cas par défaut dans un environnement d'agent automatisé) ;
2. proxy d'entreprise sans exception pour le dépôt Maven de Google ;
3. absence de cache Gradle local préalablement rempli.

Remèdes :

- autoriser `dl.google.com` et `maven.google.com` en sortie ;
- ou remplir le cache Gradle avant l'isolation réseau, puis construire hors ligne :

```bash
cd native-android-app
./gradlew --offline assembleDebug
```

Pour les sessions d'agent Copilot, `.github/workflows/copilot-setup-steps.yml` installe JDK 17 et le SDK Android puis exécute `testDebugUnitTest`, `lintDebug` et `assembleDebug` avant l'activation du pare-feu, afin que la distribution Gradle et les artefacts AGP soient déjà en cache. Ce fichier doit être présent sur la branche par défaut pour être pris en compte.

## Sécurité

- Ne pas stocker de secrets dans Git.
- Ne pas désactiver les contrôles de sécurité pour obtenir un build vert.
- Conserver les permissions Android minimales nécessaires au code réellement présent.
- Toute nouvelle capacité réseau, stockage, VPN ou surveillance doit être auditée avant d'être présentée comme opérationnelle.

## Android App Bundle (AAB)

Pour produire un App Bundle destiné au Play Console, utiliser :

```bash
cd native-android-app
./gradlew bundleReleaseUnsigned
```

Cette variante `releaseUnsigned` ne définit jamais de `signingConfig` : elle prouve que l'empaquetage AAB compile, mais l'artefact produit n'est ni signé ni publiable tel quel. Le build signé existant (`assembleRelease`/`bundleRelease` avec `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) reste inchangé et continue de bloquer toute tâche `*Release` non signée.

Le workflow `.github/workflows/build-aab-playconsole.yml` exécute cette même commande en CI, valide `applicationId`, `targetSdk`, `versionCode` et `versionName` par rapport aux règles d'empaquetage Google Play, puis publie l'AAB en artefact GitHub Actions (rétention 14 jours).

## Fonctionnalités locales de consultation

L'écran « Analyseur de permissions » liste les applications visibles via les API publiques de `PackageManager` et classe leurs permissions déclarées par niveau de risque. Il n'ajoute aucune permission Android et reste une fonction de consultation locale, sans VPN, pare-feu, antivirus actif ni action de contrôle sur l'appareil.

## Référence

Pour l'état actuel du dépôt et des workflows, consulter `README.md`, `docs/AUDIT_WORKFLOWS.md`, `.github/workflows/` et les fichiers Gradle de `native-android-app/`.
