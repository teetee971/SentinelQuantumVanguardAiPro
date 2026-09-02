# Sentinel Quantum Vanguard AI Pro — Audit technique et structurel

> Rapport révisé. Les anciennes métriques et affirmations de disponibilité sont historiques ; l’état du dépôt doit être déterminé à partir des fichiers et workflows présents sur `main`.

## 1. Référentiel actuel

- Web/PWA : racine du projet et `frontend/` selon l’arborescence actuelle.
- Android : `native-android-app/` uniquement.
- Sécurité/gouvernance : `decision-plane/`, `security/`, `scripts/`.
- CI/CD : `.github/workflows/`.
- Déploiement web cible : Cloudflare Pages.

Les anciennes références à `android-app/` et `android-app/android/` sont historiques et ne doivent pas être utilisées pour construire l’application.

## 2. Contrôles de sécurité

Le dépôt contient notamment des contrôles d’isolation, de pinning SHA des actions GitHub, de gouvernance des modèles et données, d’intégrité/provenance des preuves, de simulation avant action, de garde-fous des actions critiques, de fuzzing déterministe autorisé et d’intégrité/CodeQL.

Ces contrôles doivent être distingués de leur exécution effective. Un fichier présent n’est pas une preuve de réussite.

## 3. CI

Les workflows actifs doivent être lus directement sous `.github/workflows/`. Les anciens workflows supprimés ou historiques ne doivent pas être comptabilisés comme actifs.

À la dernière vérification, plusieurs jobs GitHub Actions échouaient avant l’exécution des étapes. Cette situation est classée comme blocage CI/infrastructure. Elle empêche de déclarer une validation CI globale.

## 4. Android

Le build de validation utilise `native-android-app/` et le workflow `.github/workflows/build-native-android.yml`.

La release signée utilise `.github/workflows/android-release.yml`, avec déclenchement par tag de version et contrôle du rattachement du tag à `main`.

Aucun secret de signature ni keystore ne doit être présent dans le dépôt.

## 5. Séparation de projet

Sentinel Quantum Vanguard AI Pro doit rester totalement séparé de A KI PRI SA YÉ. Aucun import, secret, configuration, dépendance ou couplage opérationnel croisé n’est autorisé. Le contrôle automatisé d’isolation est une barrière de sécurité et non une simple convention documentaire.

## 6. Règle de validation

La chaîne de preuve est : correctif appliqué → tests exécutés → CI exécutée → résultats examinés → validation de sécurité.

Les anciens termes « Production Ready », « 100 % testé », « zéro vulnérabilité », « conformité garantie » ou équivalents ne doivent pas être repris sans preuve actuelle et contextualisée.

## 7. Références

Pour l’état opérationnel, consulter `README.md`, `ARCHITECTURE_REFERENCE.md`, `AUDIT.md`, `VALIDATION_FINALE.md`, `RELEASE_STATUS.md`, `docs/WORKFLOWS.md`, `docs/RELEASE_BUILD_GUIDE.md` et `SECURITY.md`.

**Conclusion :** ce rapport décrit le cadre actuel de contrôle et remplace les anciennes affirmations de disponibilité. Il ne constitue ni une certification externe ni une preuve de CI verte.
