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
- `frontend-validation.yml` — build, liens statiques et hygiène des affirmations publiques.
- `integrity-check.yml` — intégrité, secrets évidents et isolation.
- `osint-validation.yml` — validation OSINT défensive.
- `security-fuzz.yml` — fuzzing de sécurité autorisé.
- `security-governance-validation.yml` — régression de gouvernance sécurité et fuzzing.
- `security-validation.yml` — validation des scénarios de sécurité.
- `sentinel-continuous-security.yml` — boucle horaire de contrôles en lecture seule.
- `sentinel-isolation.yml` — contrôle d'isolation dédié.

## Android

Le projet Android canonique est `native-android-app/`. Aucun APK/AAB signé public n'est annoncé tant que des artefacts réels, signés et vérifiables ne sont pas publiés.

Les builds de validation produisent un APK de test et un AAB non signé. La release de production est contrôlée par `.github/workflows/android-release.yml`, exige un tag conforme pointant exactement sur la tête courante de `main`, et doit produire un APK et un AAB signés.

## Validation

Un correctif n'est pas considéré comme validé uniquement parce qu'il est commité. La chaîne de preuve est : correctif appliqué → test exécuté → CI exécutée → résultats examinés → validation de sécurité.

Au 18 septembre 2026, les workflows Android, CodeQL, sécurité, isolation, intégrité, Pages et Lighthouse ont de nouveau exécuté leurs étapes avec succès sur les commits contrôlés de `main`. Ces validations CI ne remplacent pas l'exécution du workflow de release signé sur tag ni les tests sur appareil physique.

## Documentation prioritaire

- `README.md`
- `ARCHITECTURE_REFERENCE.md`
- `AUDIT.md`
- `RELEASE_STATUS.md`
- `docs/WORKFLOWS.md`
- `docs/AUDIT_WORKFLOWS.md`
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

**Statut : documentation technique de référence. La CI est opérationnelle ; la publication Android signée et les tests physiques restent des validations distinctes.**
