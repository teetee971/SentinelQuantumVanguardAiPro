# Sentinel Quantum Vanguard AI Pro

Plateforme de cybersécurité défensive. Le dépôt est autonome et ne doit dépendre d'aucun autre projet.

## Architecture

- Web/PWA à la racine, déployé sur Cloudflare Pages.
- Application Android native canonique dans `native-android-app/`.
- Modules de gouvernance IA, preuve/provenance, confiance, simulation et décision dans leurs répertoires dédiés.
- Validation de l'isolation du projet dans `scripts/check-sentinel-isolation.js` et les garde-fous associés.
- Validation de l'intégrité de la supply chain GitHub Actions dans `scripts/check-github-actions-pinning.js`.
- Contrôle d'hygiène des affirmations publiques dans `scripts/check-public-claims.js`.

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
- liaison d'exécution par condensat d'opération et vérification finale avant effet de bord ;
- anti-rejeu avec contrat de consommation atomique au point d'exécution ;
- journal d'audit ;
- fuzzing de gouvernance ;
- contrôle d'isolation du projet ;
- contrôle de pinning des GitHub Actions ;
- contrôle des affirmations opérationnelles à risque dans la surface publique.

Une correction de code n'est jamais considérée comme une preuve de sécurité à elle seule : corrigé, testé localement, validé par CI et validé en sécurité sont des états distincts.

## Releases historiques

`v1.0.0-release` est conservée comme référence historique uniquement. Elle ne constitue pas une preuve de sécurité, de validation CI ou de readiness production pour l'architecture actuelle. L'audit associé est documenté dans `docs/RELEASE_V1.0.0_AUDIT.md`.

Aucune nouvelle release ne doit reprendre des affirmations historiques non vérifiées. Une release actuelle doit satisfaire `RELEASE_CHECKLIST.md` et disposer des preuves correspondantes.

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
| `frontend-validation.yml` | Validation frontend et hygiène des affirmations |
| `build-native-android.yml` | Build Android de validation, sans publication |
| `android-release.yml` | Release Android signée sur tag |
| `sentinel-continuous-security.yml` | Boucle horaire de contrôles en lecture seule |

Les actions externes des workflows conservés sont épinglées sur des SHA immuables. Les workflows ordinaires utilisent des permissions minimales.

## Android

Le projet Android maintenu est `native-android-app/`. Aucun APK signé précompilé n'est annoncé tant qu'un artefact réel, signé et vérifiable n'est pas publié.

## Build web

Le build canonique est `npm run build`. La sortie attendue pour Cloudflare Pages est `frontend/dist`. Le runtime de build est Node.js 22.16.0, épinglé par `.node-version`.

## Validation CI

Les résultats de CI doivent être interprétés uniquement à partir des exécutions réelles. Un job GitHub Actions qui échoue avant sa première étape est un problème d'exécution du runner et ne constitue pas un résultat de test du code.

Cloudflare Pages fournit également un statut de déploiement indépendant. La configuration de production utilise `main`, `npm run build` et `frontend/dist`.

## Documentation

Voir `ARCHITECTURE_REFERENCE.md`, `SECURITY.md`, `AUDIT.md`, `RELEASE_CHECKLIST.md` et les guides de build présents dans le dépôt pour les règles opérationnelles et de sécurité.

## Licence

Voir `LICENSE`.
