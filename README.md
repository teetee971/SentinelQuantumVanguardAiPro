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

La règle de séparation est appliquée automatiquement. Les références Firebase présentes dans les contrôles négatifs sont des fixtures destinées à vérifier que les dépendances interdites sont détectées ; elles ne constituent pas une dépendance opérationnelle de Sentinel.

## Sécurité

Les contrôles principaux comprennent :

- politique de registre des modèles et liaison exacte modèle/version ;
- évaluation déterministe des modèles ;
- moteur de confiance et gestion explicite de l'incertitude ;
- provenance et intégrité des preuves avec hachage ;
- red-team synthétique contre injection, fabrication de preuves et abus d'outils ;
- simulation d'impact avant action ;
- garde d'action avec autorisation de cible et validation humaine pour les actions critiques ;
- journal d'audit ;
- fuzzing de gouvernance ;
- contrôle d'isolation du projet ;
- contrôle de pinning des GitHub Actions.

Une correction de code n'est jamais considérée comme une preuve de sécurité à elle seule : corrigé, testé localement, validé par CI et validé en sécurité sont des états distincts.

## GitHub Actions actifs

| Workflow | Fonction |
|---|---|
| `ai-governance-validation.yml` | Validation de gouvernance IA |
| `security-governance-validation.yml` | Suite de gouvernance sécurité et fuzzing |
| `security-validation.yml` | Validation sécurité |
| `security-fuzz.yml` | Fuzzing de sécurité autorisé |
| `sentinel-isolation.yml` | Garde d'isolation du projet |
| `codeql-analysis.yml` | Analyse CodeQL |
| `integrity-check.yml` | Contrôles d'intégrité |
| `osint-validation.yml` | Validation OSINT défensive |
| `frontend-validation.yml` | Validation frontend |
| `build-native-android.yml` | Build Android de validation, sans publication |
| `android-release.yml` | Release Android signée sur tag |

L'ancien workflow Windows/.NET hors périmètre a été supprimé. Il ne fait plus partie de l'architecture ni de la chaîne de validation.

Les actions externes des workflows conservés sont épinglées sur des SHA immuables. Les workflows ordinaires utilisent des permissions minimales.

## Android

Le projet Android maintenu est `native-android-app/`. Aucun APK signé précompilé n'est annoncé tant qu'un artefact réel, signé et vérifiable n'est pas publié.

## Build web

Le build canonique est `npm run build`. La sortie attendue pour Cloudflare Pages est `frontend/dist`. Le runtime de build est Node.js 22.16.0, épinglé par `.node-version`.

## Validation CI

Les résultats de CI doivent être interprétés uniquement à partir des exécutions réelles. Un job GitHub Actions qui échoue avant sa première étape est un problème d'exécution du runner et ne constitue pas un résultat de test du code.

Cloudflare Pages fournit également un statut de déploiement indépendant. La configuration de production utilise `main`, `npm run build` et `frontend/dist`.

## Documentation

Voir `ARCHITECTURE_REFERENCE.md`, `SECURITY.md`, `AUDIT.md` et les guides de build présents dans le dépôt pour les règles opérationnelles et de sécurité.

## Licence

Voir `LICENSE`.
