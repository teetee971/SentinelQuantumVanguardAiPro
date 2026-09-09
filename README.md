# Sentinel Quantum Vanguard AI Pro

Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité défensive consacrée à la veille, à l'analyse, à la simulation contrôlée et à l'aide à la décision. Le dépôt est autonome : il ne doit dépendre d'aucune application, infrastructure, configuration ni identité appartenant à un autre projet.

**Site web / PWA :** https://sentinelquantumvanguardaipro.pages.dev/

> État du projet : développement avancé et validation continue. La présence du site, d'un build ou d'un contrôle CI réussi ne constitue pas à elle seule une preuve de sécurité opérationnelle ni une certification de production.

## Vue d'ensemble

Le dépôt regroupe quatre surfaces principales :

- **Web / PWA** — interface statique construite depuis la racine et déployée sur Cloudflare Pages ;
- **Android natif** — application canonique dans `native-android-app/` ;
- **Decision Plane** — simulation, autorisation, preuve, anti-rejeu et transitions d'état bornées ;
- **Gouvernance et sécurité** — gouvernance IA, threat intelligence, fuzzing, isolation, contrôles de supply chain et preuves de validation.

```text
Sources publiques / Threat Intelligence
                │
                ▼
     Gouvernance + Validation
                │
                ▼
   Simulation / Decision Plane
                │
     preuve + autorisation
                │
                ▼
      Surfaces Web / Android
```

Cette représentation décrit l'organisation du code. Elle ne signifie pas qu'un exécuteur privilégié autonome est actif en production.

## Capacités implémentées dans le dépôt

### Gouvernance IA

Le dépôt contient notamment :

- registre et politique de modèles ;
- liaison explicite modèle / version ;
- évaluation déterministe ;
- moteur de confiance avec gestion de l'incertitude ;
- provenance et chaîne de preuves ;
- tests red-team synthétiques ;
- orchestration de sélection et d'évaluation auditable.

Les validations associées se trouvent principalement dans `ai-governance/` et dans les workflows de gouvernance.

### Decision Plane et preuves

Les contrôles de sécurité présents côté repository couvrent notamment :

- signatures Ed25519 des preuves structurées ;
- allowlists d'émetteurs ;
- résolution de clés publiques ;
- révocation configurée ;
- fraîcheur des preuves ;
- anti-rejeu ;
- liaison action / cible / politique ;
- liaison de simulation ;
- digest d'opération ;
- transitions d'état bornées avant exécution.

Ces propriétés sont testables dans le dépôt. L'identité réelle des producteurs de preuves, la garde des clés privées, le provisioning, la rotation et le chargement de confiance dans un runtime déployé restent des sujets opérationnels distincts.

### Threat Intelligence

Le dépôt comprend une ingestion défensive du catalogue CISA KEV avec validation stricte du format, des CVE, des dates, des doublons et de la taille, ainsi qu'un workflow de rafraîchissement review-gated.

Il comprend également un noyau borné de synthèse quotidienne capable de regrouper les sujets redondants entre sources, d’extraire des IoC publics et d’accepter un résumé LLM optionnel. Ce noyau n’autorise jamais d’action autonome ; la collecte RSS et la diffusion planifiée restent à connecter à des sources approuvées.

Aucun changement de threat intelligence ne doit conduire à une remédiation autonome ou à une écriture directe non revue sur `main`.

### Fuzzing et tests adversariaux

Les suites de sécurité couvrent notamment :

- entrées invalides ou malformées ;
- rejeu ;
- preuves forgées ;
- substitutions de simulation ou d'autorisation ;
- incohérences de binding ;
- indisponibilité du replay store ;
- tests déterministes de gouvernance.

Le fuzzing du dépôt est défensif et destiné à tester les garde-fous de Sentinel.

## Web / PWA

Le build canonique est :

```bash
npm ci --ignore-scripts
npm run build
```

La sortie attendue est :

```text
frontend/dist
```

Le build est produit par `scripts/build-for-cloudflare.js` et inclut la surface web publique ainsi que `_headers` pour Cloudflare Pages.

Le frontend dispose notamment de contrôles automatisés pour :

- liens et ressources locales ;
- navigation partagée ;
- accessibilité statique ciblée ;
- continuité et interfaces des modules ;
- cycles et inventaire ;
- affirmations publiques ;
- isolation du build généré ;
- absence de scripts de tracking interdits ;
- rapport déterministe de taille brute et gzip.

Le rapport de taille est produit dans `artifacts/frontend/size-report.json` en CI et conservé comme artefact GitHub Actions.

## Android

Le seul projet Android maintenu est :

```text
native-android-app/
```

La CI Android exécute actuellement :

- validation du manifeste ;
- tests unitaires debug ;
- lint Android ;
- build APK debug ;
- publication de l'APK de validation comme artefact CI.

Un APK de validation CI n'est pas présenté comme une release publique signée.

Le filtrage d’appels utilise `CallScreeningService`. Sa décision est renvoyée avant toute journalisation. L’historique local repose sur Room, est limité à 500 décisions et ne conserve ni numéro brut ni numéro masqué, uniquement une empreinte HMAC liée au Keystore lorsque le numéro est disponible. Le scanner SMS fonctionne sur le texte collé par l’utilisateur et n’ouvre aucun lien.

## Attribution téléphonique ARCEP

La surface web contient un index généré depuis les exports officiels `MAJNUM.csv` et `identifiants_CE.csv`. La recherche accepte un numéro complet ou un préfixe français de 4 à 10 chiffres et affiche l’opérateur attributaire, la tranche, le territoire, la date d’attribution et les informations publiques de l’opérateur. Un enrichissement SIRENE facultatif, déclenché explicitement par l’utilisateur, complète la fiche avec code NAF/APE, état administratif et nombre d’établissements. Elle ne permet pas de connaître l’opérateur actuel après portabilité et ne constitue pas un score de réputation. Un workflow hebdomadaire propose les mises à jour sous forme de pull request révisable.

## Sécurité du navigateur

Le fichier `_headers` définit une politique de sécurité conservatrice pour Cloudflare Pages, incluant notamment :

- `X-Content-Type-Options: nosniff` ;
- `Referrer-Policy: strict-origin-when-cross-origin` ;
- `X-Frame-Options: DENY` ;
- une `Permissions-Policy` restrictive ;
- une CSP explicite.

La CSP conserve actuellement des autorisations inline pour compatibilité avec la surface existante. Leur suppression nécessite une migration contrôlée vers des scripts/styles externes ou des nonces/hashes.

## Isolation stricte des projets

Sentinel ne doit contenir aucune dépendance opérationnelle vers un autre projet.

Le contrôle principal est :

```text
scripts/check-sentinel-isolation.js
```

Les références à Firebase ou à d'autres identifiants externes présentes dans les tests négatifs sont des fixtures de détection. Elles ne constituent pas des dépendances opérationnelles.

## CI et supply chain

Les workflows principaux sont sous `.github/workflows/` et couvrent notamment :

| Domaine | Workflow / contrôle |
|---|---|
| Gouvernance IA | `ai-governance-validation.yml` |
| Gouvernance sécurité | `security-governance-validation.yml` |
| Fuzzing | `security-fuzz.yml` |
| Isolation | `sentinel-isolation.yml` |
| Code scanning | `codeql-analysis.yml` |
| Intégrité repository | `integrity-check.yml` |
| Frontend | `frontend-validation.yml` |
| Android | `build-native-android.yml` |
| CISA KEV | `cisa-kev-refresh.yml` |
| Numérotation ARCEP | `arcep-numbering-refresh.yml` |

Les actions externes conservées sont épinglées par SHA et contrôlées par `scripts/check-github-actions-pinning.js`.

Les résultats doivent toujours être interprétés sur le **HEAD exact** concerné. Un ancien run vert ne constitue pas une validation d'un commit plus récent.

## Niveaux de preuve

Sentinel distingue volontairement plusieurs niveaux :

| Niveau | Signification |
|---|---|
| Code présent | Le mécanisme existe dans le dépôt |
| Test réussi | Un comportement défini a été vérifié par un test |
| CI réussie | Les contrôles applicables ont réussi sur un SHA précis |
| Runtime vérifié | Le comportement a été observé dans l'environnement cible |
| Production | Nécessite en plus les contrôles opérationnels, secrets, clés, déploiement et gouvernance appropriés |

Une correction de code n'est jamais considérée comme une preuve de sécurité à elle seule.

## Commandes de validation utiles

```bash
npm run test:syntax
npm run test:static-links
npm run test:frontend-accessibility
npm run test:frontend-size-report
npm run test:security-governance
npm run test:security-fuzz
npm run test:isolation
npm run test:ci-supply-chain
npm run build
npm run report:frontend-size
```

Certaines validations d'intégration nécessitent des dépendances d'environnement supplémentaires, par exemple PostgreSQL ou le SDK Android.

## Documentation technique

Documents de référence :

- `ARCHITECTURE_REFERENCE.md` — architecture et frontières canoniques ;
- `SECURITY.md` — politique et principes de sécurité ;
- `AUDIT.md` — historique et éléments d'audit ;
- `docs/DESIGN_SYSTEM.md` — identité visuelle, composants, responsive et garde-fous de présentation ;
- `docs/PROOF_TRUST_PRODUCTION.md` — limites entre preuve repository et confiance opérationnelle ;
- `CLOUDFLARE_PAGES_CONFIG.md` — chaîne de déploiement web ;
- `CI_VALIDATION_CHECKLIST.md` — contrôles CI documentés.

## Release readiness

La préparation d'une release est évaluée séparément de l'état du développement. Avant un GO final, les points de sécurité et d'exploitation encore ouverts doivent être vérifiés sur leurs preuves réelles : configuration GitHub, secrets/credentials, confiance opérationnelle, scans de sécurité et déploiement cible.

Consulter les issues ouvertes du dépôt pour l'état courant des blocages et travaux restant à démontrer.

## Licence

Voir `LICENSE`.
