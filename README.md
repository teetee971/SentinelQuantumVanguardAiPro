# Sentinel Quantum Vanguard AI Pro

Plateforme de cybersécurité défensive. Le dépôt est autonome et ne doit dépendre d'aucun autre projet.

## Architecture

- Web/PWA à la racine, déployé sur Cloudflare Pages.
- Application Android native canonique dans `native-android-app/`.
- Modules de gouvernance IA, preuve/provenance, confiance, simulation et décision dans leurs répertoires dédiés.
- Validation de l'isolation du projet dans `scripts/check-sentinel-isolation.js` et les garde-fous associés.
- Validation de l'intégrité de la supply chain GitHub Actions dans `scripts/check-github-actions-pinning.js`.

## Séparation des projets

Sentinel ne doit contenir aucune dépendance opérationnelle vers une application, une infrastructure, une configuration ou des identifiants appartenant à un autre projet.

La règle de séparation est appliquée automatiquement. Les références Firebase, dépendances Firebase et intégrations incompatibles sont bloquées par les contrôles dédiés.

## Sécurité

Les contrôles principaux comprennent :

- politique de registre des modèles et liaison exacte modèle/version ;
- évaluation déterministe des modèles ;
- moteur de confiance et gestion explicite de l'incertitude ;
- provenance et intégrité des preuves avec hachage ;
- red-team synthétique contre injection, fabrication de preuves et abus d'outils ;
- simulation d'impact avant action ;
- garde d'action avec autorisation de cible et validation humaine pour les actions critiques ;
- journal d'audit immuable ;
- fuzzing de gouvernance ;
- contrôles d'isolation de projet ;
- contrôle de pinning des GitHub Actions.

Une correction de code n'est jamais considérée comme une preuve de sécurité à elle seule : corrigé, testé localement, validé par CI et validé en sécurité sont des états distincts.

## GitHub Actions actifs

| Workflow | Fonction |
|---|---|
| `ai-governance-validation.yml` | Validation de gouvernance IA |
| `security-governance-validation.yml` | Suite de gouvernance sécurité et fuzzing |
| `security-validation.yml` | Validation sécurité |
| `security-fuzz.yml` | Fuzzing de sécurité autorisé |
| `project-isolation.yml` | Garde d'isolation du projet |
| `sentinel-isolation.yml` | Garde d'isolation renforcée |
| `codeql-analysis.yml` | Analyse CodeQL |
| `integrity-check.yml` | Contrôles d'intégrité |
| `defender-for-devops.yml` | Microsoft Defender for DevOps |
| `osint-validation.yml` | Validation OSINT défensive |
| `frontend-validation.yml` | Validation frontend |
| `build-native-android.yml` | Build Android de validation, sans publication |
| `android-release.yml` | Release Android signée sur tag |

Les actions externes des workflows actifs sont épinglées sur des SHA immuables. Les workflows ordinaires utilisent des permissions minimales.

La release Android est strictement contrôlée par tag et vérifie que le commit du tag est atteignable depuis `main`. Les secrets de signature ne sont utilisés que par le workflow de release.

## Android

Le projet Android maintenu est `native-android-app/`. Les anciens workflows ou chemins documentés ailleurs ne constituent pas la source de vérité.

## Développement

Le projet web utilise Node.js `>=20.19.0` et Vite. Les dépendances doivent être installées avec le lockfile correspondant. Pour les validations de sécurité, les suites dédiées doivent être exécutées et leurs résultats examinés.

## Validation CI actuelle

Un blocage d'infrastructure GitHub Actions est actuellement documenté dans l'issue #195 : certains jobs échouent avant l'exécution de leur première étape. Ce comportement a persisté après un essai avec une image Ubuntu explicitement versionnée.

Il s'agit d'un blocage de capacité de validation, pas d'une preuve de réussite ou d'échec du code. Aucun contrôle de sécurité ne doit être supprimé ou affaibli pour contourner ce blocage.

Tant qu'un runner n'exécute pas effectivement les étapes et que les suites ne passent pas, la validation CI globale reste **en attente**.

## Documentation

Voir `ARCHITECTURE_REFERENCE.md`, `SECURITY.md`, `AUDIT.md` et `docs/RELEASE_BUILD_GUIDE.md` pour les règles opérationnelles et de sécurité.

## Licence

Voir `LICENSE`.
