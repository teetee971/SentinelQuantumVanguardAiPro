# Sentinel Quantum Vanguard AI Pro

Plateforme de cybersécurité défensive. Le dépôt est autonome et doit rester totalement séparé de tout autre projet, notamment A KI PRI SA YÉ.

## Architecture

- Web/PWA à la racine, déployé sur Cloudflare Pages.
- Application Android native canonique dans `native-android-app/`.
- Modules de gouvernance IA, preuve/provenance, confiance, simulation et décision dans leurs répertoires dédiés.
- Validation de l'isolation Sentinel dans `scripts/check-sentinel-isolation.js`.
- Validation de l'intégrité de la supply chain GitHub Actions dans `scripts/check-github-actions-pinning.js`.

## Séparation de projets

Sentinel ne doit contenir aucune dépendance opérationnelle vers A KI PRI SA YÉ, Firebase ou une infrastructure appartenant à un autre projet.

La règle de séparation est appliquée automatiquement par le contrôle d'isolation. Les noms de fichiers interdits, références Firebase, dépendances Firebase, références A KI PRI SA YÉ et certaines intégrations Android incompatibles sont bloqués.

## Sécurité

Les contrôles principaux comprennent :

- politique de registre des modèles et liaison exacte modèle/version ;
- évaluation déterministe des modèles ;
- moteur de confiance et gestion explicite de l'incertitude ;
- chaîne de provenance des preuves avec hachage ;
- red-team synthétique contre injection, fabrication de preuves et abus d'outils ;
- simulation d'impact avant action ;
- garde d'action avec autorisation de cible et validation humaine pour les actions critiques ;
- journal d'audit immuable ;
- fuzzing de gouvernance ;
- contrôle d'isolation de projet ;
- contrôle de pinning des GitHub Actions.

Une correction de code n'est jamais considérée comme une preuve de sécurité à elle seule : corrigé, testé localement, validé par CI et validé en sécurité sont des états distincts.

## GitHub Actions

Les workflows actifs comprennent notamment :

| Workflow | Fonction |
|---|---|
| `project-isolation.yml` | Séparation Sentinel / autres projets |
| `security-governance-validation.yml` | Suite de gouvernance sécurité |
| `security-fuzz.yml` | Fuzzing de sécurité |
| `codeql-analysis.yml` | Analyse CodeQL |
| `integrity-check.yml` | Intégrité du dépôt |
| `frontend-validation.yml` | Validation frontend |
| `build-native-android.yml` | Build Android non publié |
| `android-release.yml` | Release Android signée sur tag |

Les actions tierces des workflows actifs sont épinglées sur des SHA immuables et les workflows ordinaires utilisent des permissions minimales.

La release Android est strictement contrôlée par tag et vérifie que le commit du tag est atteignable depuis `main`. Les secrets de signature ne sont utilisés que par le workflow de release.

## Android

Le projet Android de production est `native-android-app/`. Les anciens workflows ou chemins documentés ailleurs ne constituent pas la source de vérité.

## Développement

Le projet web utilise Node.js `>=20.19.0` et Vite. Les dépendances doivent être installées avec le lockfile correspondant. Pour les validations de sécurité, le script principal est `npm run test:security-governance`.

## Validation CI actuelle

Un blocage d'infrastructure GitHub Actions a été observé : certains jobs échouent avant l'exécution de leur première étape. Ce comportement a persisté après un essai avec une image Ubuntu explicitement versionnée. Il ne doit pas être contourné en affaiblissant les contrôles de sécurité.

Tant qu'un runner n'exécute pas effectivement les étapes et que les suites ne passent pas, la validation CI est considérée comme **en attente**, et non comme réussie.

## Documentation

Voir `ARCHITECTURE_REFERENCE.md`, `SECURITY.md` et `docs/RELEASE_BUILD_GUIDE.md` pour les règles opérationnelles et de sécurité.

## Licence

Voir `LICENSE`.
