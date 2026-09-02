# APK Android — état et procédure actuelle

Ce document remplace les anciennes instructions de génération APK. Il ne doit pas être utilisé comme preuve qu'une release a été produite ou validée.

## Source canonique

Le projet Android maintenu est `native-android-app/`.

Les anciens chemins `android-app/android/`, les flavors `Institutional`, les anciens noms de secrets et les anciens workflows de publication ne sont plus valides.

## Build de validation

Le workflow `.github/workflows/build-native-android.yml` construit l'APK de validation depuis `native-android-app/` et le publie comme artefact CI.

## Release signée

Le workflow `.github/workflows/android-release.yml` est la seule procédure automatisée de release signée.

Il se déclenche uniquement sur un tag `v*`. Avant la signature et la publication, il contrôle le format du tag et vérifie que le commit du tag est rattaché à `main`.

Secrets attendus :

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Aucun keystore, mot de passe ou clé privée ne doit être commité.

## Validation d'un APK

Un APK n'est pas considéré comme validé simplement parce qu'il existe. Avant distribution, vérifier :

1. succès du workflow ;
2. présence et intégrité de l'artefact ;
3. checksum SHA-256 ;
4. signature ;
5. installation et lancement sur appareil de test ;
6. absence de secrets dans le dépôt et l'artefact ;
7. conservation des preuves de validation.

## Blocage CI connu

L'issue #195 documente un problème d'exécution GitHub Actions dans lequel certains jobs échouent avant l'exécution de leurs étapes. Tant que ce blocage persiste, aucune réussite CI globale ne doit être affirmée et aucun contrôle de sécurité ne doit être affaibli pour le contourner.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel avec cet autre projet n'est autorisé.

## Règle de preuve

`correctif appliqué ≠ testé ≠ CI réussie ≠ APK validé ≠ release validée ≠ sécurité prouvée`.
