# Guide de build — Sentinel Quantum Vanguard Android

Ce document décrit le build de la source Android canonique située dans `native-android-app/`. Il ne constitue pas la preuve qu'un build a déjà réussi sur l'infrastructure CI.

## Environnement de référence

- Android Gradle Plugin : 9.4.0
- Gradle Wrapper : 9.6
- Kotlin : 2.3.21
- JDK : 17
- compileSdk : 37
- targetSdk : 36
- minSdk : 23
- Source Android : `native-android-app/`

Les versions de bibliothèques doivent rester alignées sur `native-android-app/app/build.gradle` ; ne pas recopier une ancienne liste de dépendances depuis ce guide.

## Build local

Depuis la racine du dépôt :

```bash
cd native-android-app
./gradlew clean
./gradlew assembleDebug
```

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

## Sécurité

- Ne pas stocker de secrets dans Git.
- Ne pas désactiver les contrôles de sécurité pour obtenir un build vert.
- Conserver les permissions Android minimales nécessaires au code réellement présent.
- Toute nouvelle capacité réseau, stockage, VPN ou surveillance doit être auditée avant d'être présentée comme opérationnelle.

## Référence

Pour l'état actuel du dépôt et des workflows, consulter `README.md`, `docs/AUDIT_WORKFLOWS.md`, `.github/workflows/` et les fichiers Gradle de `native-android-app/`.
