# Sentinel Quantum Vanguard AI Pro — Index

Ce document est la référence de navigation technique du projet. Les anciennes descriptions historiques ne font pas foi.

## Architecture actuelle

- Interface web/PWA : racine du projet et `frontend/`.
- Android natif : `native-android-app/` uniquement.
- Sécurité et gouvernance : `decision-plane/`, `security/`, `scripts/`.
- CI/CD et contrôles : `.github/workflows/`.
- Déploiement web cible : Cloudflare Pages.

## Sécurité et séparation de projet

Sentinel Quantum Vanguard AI Pro est un projet autonome. Il ne doit contenir ni dépendance opérationnelle, ni configuration, ni secret, ni import provenant d'un autre projet.

Le contrôle d'isolation est automatisé par `scripts/check-sentinel-isolation.js` et son test associé. Les références Firebase conservées dans les contrôles négatifs sont des fixtures de détection et non des dépendances d'exécution.

## Workflows actifs

- `ai-governance-validation.yml` — validation de gouvernance IA.
- `android-release.yml` — release Android signée sur tags de version.
- `build-native-android.yml` — build Android de validation, sans publication.
- `codeql-analysis.yml` — analyse CodeQL.
- `frontend-validation.yml` — build et validation frontend.
- `integrity-check.yml` — intégrité, secrets évidents et isolation.
- `osint-validation.yml` — validation OSINT défensive.
- `security-fuzz.yml` — fuzzing de sécurité autorisé.
- `security-governance-validation.yml` — régression de gouvernance sécurité et fuzzing.
- `security-validation.yml` — validation des scénarios de sécurité.
- `sentinel-isolation.yml` — contrôle d'isolation dédié.

Le workflow Microsoft Defender for DevOps historique a été supprimé et ne fait plus partie de la chaîne opérationnelle.

## Android

Le projet Android canonique est `native-android-app/`. Aucun APK signé précompilé n'est annoncé tant qu'un artefact réel, signé et vérifiable n'est pas publié.

Le build de validation produit un APK de test. La release signée est contrôlée par `.github/workflows/android-release.yml` et doit être déclenchée par un tag conforme et rattaché à `main`.

## Validation

Un correctif n'est pas considéré comme validé uniquement parce qu'il est commité. La chaîne de preuve est : correctif appliqué → test exécuté → CI exécutée → résultats examinés → validation de sécurité.

À la date de cette révision, certains jobs GitHub Actions ont échoué avant l'exécution de leurs étapes. Cette situation est traitée comme un blocage CI/infrastructure et non comme une preuve de réussite ou d'échec du code.

## Documentation prioritaire

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `RELEASE_STATUS.md`
- `docs/WORKFLOWS.md`
- `docs/RELEASE_BUILD_GUIDE.md`
- `SECURITY.md`

## Principes

1. Défensif uniquement.
2. Moindre privilège.
3. Actions critiques soumises aux garde-fous et à la validation humaine.
4. Preuves et provenance vérifiables.
5. Aucun secret dans le dépôt.
6. Aucune capacité offensive ou de contournement non autorisée.
7. Séparation stricte avec tout autre projet.

**Statut : documentation technique de référence, sans prétention de validation CI tant que les runners concernés ne sont pas opérationnels.**
