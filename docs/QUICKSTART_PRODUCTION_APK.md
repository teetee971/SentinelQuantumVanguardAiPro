# Quick Start — Release Android Sentinel

Ce guide décrit uniquement le flux Android actuellement maintenu.

## 1. Projet canonique

Utiliser exclusivement `native-android-app/`.

Les anciens chemins `android-app/android/` et les anciens workflows de release sont obsolètes.

## 2. Build de validation

Le workflow `.github/workflows/build-native-android.yml` construit l'APK de validation et l'archive comme artefact CI.

Pour un test local :

```text
cd native-android-app
./gradlew assembleDebug
```

## 3. Signature de production

La release signée est gérée exclusivement par `.github/workflows/android-release.yml`.

Secrets requis :

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Le keystore doit rester hors du dépôt. Le workflow le décode temporairement et le supprime après le build.

## 4. Déclenchement de release

La release est déclenchée uniquement par un tag correspondant à `vMAJOR.MINOR.PATCH` avec suffixe de prérelease/build éventuellement autorisé.

Le workflow vérifie que le commit du tag est rattaché à `main` avant de signer et publier.

Il n'existe pas de déclenchement manuel documenté pour ce workflow.

## 5. Artefacts

Le workflow publie l'APK de release et un fichier SHA-256 associé. Le nom exact de l'APK doit être lu dans l'artefact ou la release produite ; ne pas supposer plusieurs flavors.

La configuration Android actuelle définit une seule application `com.sentinel.quantum` et une seule tâche `assembleRelease`.

## 6. Validation obligatoire

Avant de déclarer une release valide :

1. vérifier le résultat CI ;
2. vérifier l'artefact ;
3. vérifier le checksum ;
4. vérifier la signature ;
5. tester l'installation et le lancement sur appareil ;
6. conserver les preuves de validation.

Un build lancé ou un fichier APK présent ne suffit pas.

## 7. Blocage CI actuel

Le dépôt fait actuellement l'objet d'un blocage GitHub Actions documenté dans l'issue #195 : certains jobs échouent avant l'exécution de leurs étapes. Ce phénomène n'est pas traité comme un échec du code et ne doit pas être contourné par l'affaiblissement des contrôles.

## 8. Séparation

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun secret, import, dépendance, configuration ou couplage opérationnel avec cet autre projet n'est autorisé.
