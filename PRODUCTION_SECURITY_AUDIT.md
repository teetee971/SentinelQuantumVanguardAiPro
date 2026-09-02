# Sentinel Quantum Vanguard AI Pro — Audit sécurité de production

> Document révisé pour éviter toute confusion entre état historique et preuve actuelle.

## Statut documentaire

Ce fichier conserve l’historique d’un ancien audit Android. Les affirmations de type « PRODUCTION READY », « institutionnel/gouvernemental prêt », conformité garantie, métriques de performance ou tests matériels ne constituent pas des preuves actuelles et sont retirées de la référence opérationnelle.

## Architecture Android actuelle

La seule source Android canonique est `native-android-app/`. Les anciennes instructions relatives à `android-app/android/` sont historiques.

Le build de validation est défini par `.github/workflows/build-native-android.yml`. La release signée est définie par `.github/workflows/android-release.yml`, déclenchée par un tag de version conforme rattaché à `main`.

## Sécurité actuelle

Les contrôles de sécurité maintenus dans le dépôt comprennent notamment :

- isolation stricte du projet ;
- pinning SHA des actions GitHub externes ;
- gouvernance des modèles et des classes de données ;
- provenance et intégrité cryptographique des preuves ;
- simulation avant action ;
- garde-fou des actions critiques et validation humaine ;
- fuzzing déterministe dans un laboratoire autorisé ;
- contrôles d’intégrité et CodeQL.

La présence d’un contrôle dans le dépôt ne signifie pas qu’il a été exécuté avec succès dans la CI courante.

## Permissions et téléphonie

Les permissions et capacités Android doivent être vérifiées directement dans `native-android-app/` et son manifeste actuel. Les anciennes listes de permissions de ce document ne doivent pas être considérées comme une spécification actuelle.

Toute fonctionnalité téléphonique doit respecter les API et politiques Android applicables à la version cible. Aucune capacité d’interception clandestine, d’espionnage ou de contournement n’est autorisée.

## Secrets et signature

Aucun keystore, mot de passe ou secret de signature ne doit être commité. Le workflow actuel de release utilise les secrets documentés dans `docs/RELEASE_BUILD_GUIDE.md`.

## Validation

La chaîne de preuve est : correctif appliqué → tests exécutés → CI exécutée → résultats examinés → validation de sécurité.

À la dernière vérification, des jobs GitHub Actions ont échoué avant l’exécution de leurs étapes. Ce blocage est classé CI/infrastructure. Il ne permet ni de déclarer la CI verte ni d’inférer une défaillance du code.

## Séparation stricte

Sentinel Quantum Vanguard AI Pro est totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel entre les deux projets n’est autorisé. Le contrôle d’isolation automatisé fait partie des garde-fous du dépôt.

## Références opérationnelles

Utiliser en priorité :

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `VALIDATION_FINALE.md`
- `RELEASE_STATUS.md`
- `docs/WORKFLOWS.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `SECURITY.md`

**Conclusion :** ce document est une référence de sécurité documentaire et historique. Il ne constitue pas une certification, un audit externe ni une preuve de validation CI actuelle.
