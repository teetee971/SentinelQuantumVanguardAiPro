# Android Gradle Build Configuration Audit

## Snapshot vérifié — 1 octobre 2026

Ce document décrit la configuration réellement présente dans `native-android-app/` à cette date. Il ne remplace pas les fichiers Gradle : la CI et les fichiers source restent la vérité exécutable.

## Versions canoniques

| Élément | Valeur | Source |
|---|---:|---|
| Android Gradle Plugin | 9.4.1 | `native-android-app/build.gradle` |
| Kotlin / Compose plugin | 2.4.20 | `native-android-app/build.gradle` |
| KSP | 2.3.12 | `native-android-app/build.gradle` |
| Gradle wrapper | 9.8.0 | `gradle/wrapper/gradle-wrapper.properties` |
| JDK | 17 | `app/build.gradle` / CI |
| compileSdk | 37 | `app/build.gradle` |
| targetSdk | 36 | `app/build.gradle` |
| minSdk | 24 | `app/build.gradle` |
| versionCode | 7 | `app/build.gradle` |
| versionName | 1.0.6 | `app/build.gradle` |

L’application canonique est `com.sentinel.quantum`.

## Modules

`settings.gradle` déclare trois modules :

- `:app`
- `:wearable-contract`
- `:wearable-security`

Le module `:app` dépend de `:wearable-security`. Il ne faut donc plus décrire le projet comme un build mono-module.

## Dépôts de dépendances

La résolution utilise `RepositoriesMode.PREFER_SETTINGS` avec :

- Google Maven ;
- Maven Central ;
- JitPack limité explicitement au groupe `com.github.davidliu`, nécessaire à une dépendance transitive du transport média.

Cette exception JitPack n’autorise pas l’ajout arbitraire d’autres groupes.

## Dépendances Android principales observées

Le module app déclare notamment :

- AndroidX Core, Lifecycle, Activity Compose, Navigation et WorkManager ;
- Compose BOM `2026.09.00` et Material 3 ;
- Room 3.0.2 / SQLite Framework 2.6.2 ;
- OkHttp 5.5.0 ;
- Kotlin Coroutines Android 1.11.0 ;
- LiveKit Android 2.29.0 ;
- WireGuard tunnel 1.0.20260102 ;
- Rome 2.1.0.

Cette liste est informative. La preuve de release ne doit pas dépendre de cette liste manuscrite : le workflow exporte le graphe résolu `releaseRuntimeClasspath` dans `release-dependencies.json` et le lie cryptographiquement à `release-evidence.json`.

## Signature et variantes

- `debug` : non minifié, destiné au développement et à la validation.
- `releaseUnsigned` : copie non signée du profil release pour vérifier le packaging/AAB sans secret de production ; elle n’est pas publiable.
- `release` : R8/ProGuard activé et signature exigée via `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Le keystore et les mots de passe ne doivent jamais être committés.

## Garde-fous CI

La CI Android vérifie notamment :

- tests unitaires ;
- lint ;
- vérité produit et manifeste ;
- export non vide du graphe de dépendances natif ;
- build APK debug ;
- package, alignement et signature du debug ;
- absence de secrets statiques interdits dans l’APK ;
- installation et lancement sur émulateur Android 10.

La release signée ajoute APK + AAB, checksums, rapports de certificats, SBOM, inventaire natif et vérification de `release-evidence.json`.

## Limites

Un build CI vert n’est pas une preuve d’exploitation sur appareil/opérateur réel. La release publique reste bloquée tant que les conditions externes de `docs/PRODUCTION_RELEASE_GUIDE.md` ne sont pas satisfaites.

Règle : **configuration cohérente ≠ release signée ≠ validation physique ≠ publication Play**.
