# Sentinel Quantum Vanguard AI Pro

Sentinel Quantum Vanguard AI Pro est une plateforme de cybersécurité défensive consacrée à la veille, à l'analyse, à la simulation contrôlée et à l'aide à la décision. Le dépôt est autonome : il ne doit dépendre d'aucune application, infrastructure, configuration ni identité appartenant à un autre projet.

**Site web / PWA :** https://sentinelquantumvanguardaipro.pages.dev/

**Annuaire téléphonique gratuit :** https://sentinelquantumvanguardaipro.pages.dev/public/phone-intelligence.html

**API Wangiri / spoofing :** https://sentinel-moteur-api.onrender.com/

- Santé processus : https://sentinel-moteur-api.onrender.com/health/live
- Santé Redis : https://sentinel-moteur-api.onrender.com/health/ready
- Documentation OpenAPI : https://sentinel-moteur-api.onrender.com/docs

![Soldat Sentinel tenant un bouclier lumineux dans un centre de supervision](assets/images/sentinel-command-center.webp)

> État vérifié le 15 septembre 2026 : le site, l’annuaire ARCEP et l’API Render sont déployés ; `/health/live`, `/health/ready`, OpenAPI et une évaluation valide ont répondu HTTP 200, avec Redis `connected` et `community_intelligence: available`. Le paiement, l’activation réelle des organisations, l’ouverture publique des signalements communautaires et la distribution publique de l’APK ne sont pas encore activés. Une CI réussie ne constitue pas à elle seule une certification de sécurité opérationnelle.

## Engagement gratuit et frontière commerciale

Le socle téléphone doit rester gratuit sur le Web et Android :

- recherche par numéro et préfixe dans l’index officiel ARCEP ;
- recherche autrichienne dans un index local RTR de 73 749 plages : attribution publiée, catégorie, zone et statuts, sans identification de l’appelant ni garantie de l’opérateur actuel ;
- analyse locale de texte SMS, sans ouverture automatique des liens ;
- listes personnelles avec priorité à la liste blanche ;
- filtrage d’appels Android et historique local privé lorsque l’application sera distribuée.

Les fonctions payantes sont des add-ons séparés : Threat Brief Pro, Sentinel Investigations, FIMI / DISARM-FR, Purple Team Workspace, Organization Pack, connecteurs validés, exports professionnels et support contractualisé. Aucun paiement réel n’est actuellement traité par le dépôt.

L’import autrichien exploite les CSV fournis le 15 septembre 2026 et distingue date d’import et date de publication inconnue. Les codes courts, codes étoilés, paramètres réseau, numéros raccourcis et extensions ne sont pas couverts. Voir [provenance et reconstruction RTR](docs/RTR_AUSTRIA_IMPORT.md) et [couverture internationale réelle](docs/INTERNATIONAL_NUMBERING_SOURCES.md). La synchronisation de cet index avec Android reste à construire.

Le futur add-on **NDR supervisé** est au stade de conception : détecteurs multi-échelles, mouvements latéraux, dossiers SOC explicables et réponse approuvée par un humain. Aucun capteur ou blocage NDR opérationnel n’est annoncé. Voir [architecture et critères du pilote](docs/NDR_DESIGN.md).

**Sentinel Investigations — carnet local expérimental :** [ouvrir le carnet](https://sentinelquantumvanguardaipro.pages.dev/public/investigations.html). Saisie d’entités et relations sourcées, graphe, chronologie UTC et import/export JSON borné. Les données restent en mémoire dans l’onglet ; l’export est en clair. Sources déclarées non vérifiées, sans collecte automatique ni identification de personnes. L’[import local Maigret](docs/MAIGRET_IMPORT.md) accepte le JSON simple avec aperçu, sélection et SHA-256 ; aucune recherche distante n’est lancée. Les workflows Flowsint, la carte de veille inspirée de World Monitor et l’API aérienne ne sont pas intégrés. Voir [périmètre, licences et étapes suivantes](docs/INVESTIGATIONS_WORKSPACE.md).

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

Les références EUvsDisinfo et ISD Authoritarian Interference Tracker sont documentées comme sources humaines à qualifier. DISARM-FR est utilisé comme taxonomie française sous CC BY 4.0, pas comme flux d’IoC ni comme preuve d’attribution.

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

## Moteur Wangiri FastAPI / Upstash

Le socle backend est dans `backend/wangiri-api/` : score multi-signal explicable, réputation Upstash sous empreinte HMAC, signalements authentifiés/dédupliqués, mode dégradé et conteneur Render non-root. Les tests Python et le build Docker sont exécutés par `Wangiri API Validation`.

État : **runtime Render vérifié le 15 septembre 2026 sur le commit `61d5dee`**. Les endpoints `/health/live` et `/health/ready` répondent HTTP 200 et Redis annonce `connected`. Le Blueprint `render.yaml` demande `REDIS_URL` sans l’enregistrer dans Git et génère les secrets de hachage/signalement. Le cloud reste un enrichissement facultatif ; Android ne doit jamais attendre cette API sur le chemin critique de `CallScreeningService`.

Voir [la procédure de déploiement](backend/wangiri-api/README.md).

## Android

Le seul projet Android maintenu est :

```text
native-android-app/
```

Au lancement, l’application affiche l’identité visuelle du soldat Sentinel pendant l’initialisation locale. L’image embarquée est optimisée en WebP et ne déclenche aucun téléchargement réseau.

La CI Android exécute actuellement :

- validation du manifeste ;
- tests unitaires debug ;
- lint Android ;
- build APK debug ;
- publication de l'APK de validation comme artefact CI.

Un APK de validation CI n'est pas présenté comme une release publique signée.

Le site montre le produit Android avec un bouton désactivé. Il ne contient aucun lien APK/AAB public tant qu’un artefact signé, son certificat public, son checksum, la CI du SHA exact et un test sur appareil réel ne sont pas réunis.

Phone Core dispose désormais d’un centre d’activation et de test sur appareil. Il vérifie séparément les rôles Android Téléphone, filtrage d’appels et SMS ainsi que les permissions nécessaires, puis renvoie vers le composeur et la messagerie Sentinel. Aucun rôle ni permission n’est accordé silencieusement : chaque changement passe par l’interface système Android.

Le composeur Sentinel est éligible au rôle `ROLE_DIALER`, utilise `TelecomManager.placeCall()` pour les appels sortants et fournit un `InCallService` avec interface Sentinel pour les appels entrants/sortants, décrocher, refuser, raccrocher, mise en attente et DTMF. Le comportement réel reste à confirmer sur appareil physique et selon la version Android/opérateur.

Le filtrage d’appels utilise `CallScreeningService`. Sa décision est renvoyée avant toute journalisation et avant l’ouverture de la fiche Caller ID. Cette fiche affiche les faits locaux disponibles : numéro normalisé, pays/drapeau, type indicatif, vérification réseau, motif Sentinel et, lorsque l’utilisateur l’a explicitement activé, des signaux de réputation distants. L’utilisateur peut autoriser séparément la lecture locale du répertoire pour afficher le nom et la société du contact ; cette permission est révocable et aucun contact n’est envoyé par ce composant. L’historique Room reste limité à 500 décisions et ne conserve ni numéro brut ni numéro masqué, uniquement une empreinte HMAC liée au Keystore lorsque le numéro est disponible.

Le client SMS dispose des primitives nécessaires au test réel : demande explicite du rôle `ROLE_SMS`, envoi via `SmsManager`, réception `SMS_DELIVER`, écriture dans le provider SMS Android, lecture locale des conversations, suivi envoyé/livré, sélection multi-SIM, `ACTION_SENDTO`, `RESPOND_VIA_MESSAGE` et analyse locale anti-fraude. Le centre Phone Core permet maintenant de demander le rôle et les permissions correspondantes dans le bon ordre. Les MMS entrants sont indexés localement, mais le décodage complet et sûr des pièces jointes MMS reste en validation ; cette partie ne doit pas être présentée comme finalisée.

Le client VPN Android WireGuard est intégré avec consentement système, contrôleur de tunnel fail-closed et validation full-tunnel IPv4/IPv6. En revanche, aucune passerelle Sentinel de sortie ni control plane de provisionnement n’est encore déployé : le service VPN public n’est donc pas opérationnel. Voir `docs/security/SENTINEL-VPN-ARCHITECTURE.md` et `docs/security/SENTINEL-VPN-MULTI-REGION.md`.

Le module Android inclut désormais un contrôle manuel de mot de passe exposé via l’API gratuite Pwned Passwords en k-anonymat : le SHA-1 est calculé localement et seul son préfixe de 5 caractères est transmis. La surveillance continue d’adresses e-mail ou domaines reste planifiée dans `docs/DIGITAL-EXPOSURE-MONITORING.md` et exige une API/licence autorisée côté serveur.

## Attribution téléphonique ARCEP

La surface web contient un index généré depuis les exports officiels `MAJNUM.csv` et `identifiants_CE.csv`. La recherche accepte un numéro complet ou un préfixe français de 4 à 10 chiffres et affiche l’opérateur attributaire, la tranche, le territoire, la date d’attribution et les informations publiques de l’opérateur. Un enrichissement SIRENE facultatif, déclenché explicitement par l’utilisateur, complète la fiche avec code NAF/APE, état administratif et nombre d’établissements. Elle ne permet pas de connaître l’opérateur actuel après portabilité et ne constitue pas un score de réputation. Un workflow hebdomadaire propose les mises à jour sous forme de pull request révisable.

Les listes suivent un ordre de type pare-feu : liste blanche personnelle, liste de blocage personnelle, futures listes communautaires signées, puis sources réglementaires de référence. Une future base de signalements exige un backend durable, de l’anti-abus, une modération humaine, des seuils multi-sources, un recours, une conservation limitée et une publication signée ; elle n’est pas déployée aujourd’hui.

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

Le dernier audit détaillé est conservé dans `docs/PRODUCTION_READINESS_2026-09-09.md`. Le verdict est séparé par surface : site public, Android installable, paiement/activation et plateforme opérationnelle ne partagent pas automatiquement le même statut.

## Licence

Voir `LICENSE`.
