# Sentinel Quantum Vanguard AI Pro — Checklist de validation

> Checklist révisée. Les cases historiques ne valent pas preuve de validation actuelle.

## Règle de validation

Aucun statut « production-ready », « 100 % validé » ou équivalent ne doit être déduit de l’existence d’un fichier ou d’un ancien rapport.

Chaîne de preuve obligatoire : correctif appliqué → tests exécutés → CI exécutée → résultats examinés → validation de sécurité.

## Architecture de référence

- Android : `native-android-app/` uniquement.
- Sécurité/gouvernance : `decision-plane/`, `security/`, `scripts/`.
- CI/CD : `.github/workflows/`.
- Web : surface actuelle du dépôt, déploiement cible Cloudflare Pages.

## Contrôles à vérifier

- [ ] Build Android exécuté avec succès depuis `native-android-app/`.
- [ ] Tests de sécurité exécutés avec succès.
- [ ] Test d’isolation Sentinel exécuté avec succès.
- [ ] Contrôle de pinning SHA exécuté avec succès.
- [ ] Fuzzing de sécurité exécuté avec succès.
- [ ] CodeQL exécuté avec succès.
- [ ] Artefact Android vérifié et checksum contrôlé si une release est produite.
- [ ] Aucun secret ou keystore dans le dépôt.
- [ ] Séparation stricte avec A KI PRI SA YÉ vérifiée.
- [ ] Résultats CI examinés après exécution complète des étapes.

## Blocage connu

À la dernière vérification, des jobs GitHub Actions échouaient avant l’exécution de leurs étapes. Les cases CI correspondantes restent donc non validées. Ce comportement est traité comme un blocage CI/infrastructure, pas comme un échec fonctionnel des tests.

## Séparation de projet

Sentinel Quantum Vanguard AI Pro reste totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel croisé n’est autorisé.

## Références

Pour les procédures actuelles, consulter `README.md`, `ARCHITECTURE_REFERENCE.md`, `AUDIT.md`, `VALIDATION_FINALE.md`, `RELEASE_STATUS.md`, `ANDROID_README.md` et `docs/RELEASE_BUILD_GUIDE.md`.
