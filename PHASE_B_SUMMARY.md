# Phase B — résumé historique

Ce document décrit un ancien chantier et est conservé uniquement comme archive. Il ne décrit pas l'architecture Android actuelle et ne constitue pas une preuve de conformité, de sécurité ou de production readiness.

Les anciennes références à `android-app/`, à des modules React Native/Kotlin non maintenus, à des permissions téléphoniques sensibles, à des flavors, à Google Play, à des résultats CodeQL « propres » ou à une conformité juridique vérifiée sont historiques et ne doivent pas être utilisées comme état actuel.

## Référence actuelle

- Projet Android maintenu : `native-android-app/`.
- Build Android : `.github/workflows/build-native-android.yml`.
- Release Android signée : `.github/workflows/android-release.yml`.
- Contrôles sécurité : workflows et scripts actuellement présents dans le dépôt.
- Séparation stricte : Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ.

## Validation

Toute capacité décrite dans cette archive doit être considérée comme non disponible dans l'architecture actuelle tant qu'elle n'est pas démontrée par le code source actuel et par les tests correspondants.

Le principe de validation est : correction → test → CI réussie → artefact vérifié → validation de sécurité. Une ancienne exécution ne remplace pas une preuve actuelle.

## CI

L'issue #195 documente un blocage d'exécution des runners GitHub Actions. Tant que les contrôles concernés échouent avant l'exécution de leurs étapes, aucune validation CI globale ne doit être déclarée réussie.
