# Android — Build de production

## Référence actuelle

Le projet Android canonique est `native-android-app/`. Les anciennes références à `android-app/` et `android-app/android/` sont historiques.

La configuration actuelle utilise une seule application `com.sentinel.quantum`, `minSdk 23`, `targetSdk 34`, `versionCode 1` et `versionName 1.0.0`. Elle ne définit pas actuellement de flavors Public/Institutional.

## Build de validation

```text
cd native-android-app
./gradlew assembleDebug
```

Le workflow `.github/workflows/build-native-android.yml` effectue le build de validation et publie un artefact CI.

## Release signée

La release de production est gérée exclusivement par `.github/workflows/android-release.yml`.

Déclenchement : tag `v*` uniquement.

Contrôles avant publication : format du tag et rattachement du commit du tag à `main`, présence des secrets de signature, build `assembleRelease`, présence des APK, génération des SHA-256.

Secrets attendus : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Le keystore est temporaire dans le runner et supprimé après traitement. Il ne doit jamais être commité.

## Validation d'une release

Ne pas déclarer une release « production ready » sans preuves actuelles. Vérifier :

- CI terminée avec succès ;
- APK présent et non vide ;
- checksum SHA-256 vérifié ;
- signature vérifiée ;
- installation et lancement sur appareil de test ;
- absence de secrets dans le dépôt et l'artefact.

Règle : `correctif appliqué ≠ testé ≠ CI réussie ≠ release validée ≠ sécurité prouvée`.

## Blocage CI

L'issue #195 documente des échecs de certains jobs GitHub Actions avant l'exécution de leurs étapes. Tant que ce blocage persiste, aucune validation CI globale ne doit être affirmée et aucun contrôle ne doit être affaibli pour le contourner.

## Séparation

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel avec cet autre projet n'est autorisé.
