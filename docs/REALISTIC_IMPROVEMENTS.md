# Sentinel — améliorations réalistes

Ce document privilégie les contrôles vérifiables, la réduction de complexité et la traçabilité. Il ne constitue pas une déclaration de sécurité absolue.

## Priorité P0 — bloquer les faux positifs de confiance

1. Centraliser la classification des décisions et actions dans `decision-plane/policy/action-catalog.js`.
2. Interdire les booléens de confiance pour les actions sensibles (`authorization: true`, `human_validation: true`, `simulation.safe: true`, etc.) lorsqu'ils sont utilisés comme preuve opérationnelle.
3. Exiger des preuves structurées liées à l'action, à la cible et à la version de politique.
4. Appliquer la fraîcheur temporelle à toutes les preuves qui possèdent une fenêtre de validité.
5. Ajouter une consommation atomique des preuves sensibles afin d'empêcher leur réutilisation après autorisation.
6. Vérifier l'identité et l'intégrité du producteur au point d'exécution. Une structure valide ne doit jamais être assimilée à une preuve authentique.

## Priorité P1 — rendre la chaîne d'exécution explicite

La chaîne cible est :

`proposition IA → décision structurée → vérification des preuves → politique → autorisation → approbation humaine → simulation → Action Gate → adaptateur d'exécution → audit`.

L'IA ne doit pas produire directement une autorisation, une approbation humaine ou une preuve d'exécution.

Mettre en place une machine d'état explicite :

`PROPOSED → VALIDATED → AUTHORIZED → APPROVED → READY → EXECUTING → COMPLETED`

avec sorties `DENIED`, `EXPIRED`, `FAILED` et `ROLLED_BACK`. Chaque transition doit être contrôlée et journalisée.

## Priorité P1 — anti-TOCTOU

Avant toute action sensible, figer au minimum :

- `action_id` ;
- action canonique ;
- `target_id` ;
- `policy_version` ;
- identifiant de simulation ;
- identifiants d'autorisation et d'approbation ;
- empreinte des entrées pertinentes.

L'exécution doit refuser toute divergence entre ce qui a été validé et ce qui est effectivement exécuté.

## Priorité P1 — anti-rejeu

Introduire un registre de consommation des identifiants de preuve et d'action. Une preuve sensible valide une fois ne doit pas automatiquement rester réutilisable. La consommation doit être atomique et liée au périmètre de l'action.

## Priorité P1 — audit forensique

Chaque décision et transition importante doit pouvoir être corrélée par un identifiant commun. Les événements doivent permettre de reconstruire : qui a proposé, quelle preuve a été utilisée, quelle politique a été appliquée, qui a autorisé, qui a approuvé, quelle simulation a été exécutée et quel adaptateur a exécuté l'action.

## Priorité P2 — réduire la complexité

Supprimer ou isoler progressivement les arbres historiques, artefacts générés, archives, doublons et anciennes implémentations uniquement après vérification de leurs références. Les fixtures négatives de sécurité et d'isolation ne doivent pas être supprimées simplement parce qu'elles sont peu référencées.

Conserver une seule source de vérité pour chaque domaine critique : politique, actions, preuves, exécution, audit et état de sécurité.

## Priorité P2 — qualité CI/CD

Le pipeline de sécurité doit distinguer explicitement :

- `PASS` : contrôle réellement exécuté et réussi ;
- `FAIL` : contrôle exécuté et échoué ;
- `NOT_EXECUTED` : contrôle non exécuté ;
- `UNKNOWN` : preuve insuffisante.

Un échec d'infrastructure GitHub Actions ne doit jamais être présenté comme un échec du code ni comme une validation du code.

## Priorité P2 — dépendances

Maintenir `package.json`, `.node-version` et `package-lock.json` strictement cohérents. Toute modification manuelle du lockfile doit être évitée lorsqu'une régénération par npm est possible. Une divergence du lockfile doit bloquer la release jusqu'à régénération et validation réelle.

## Priorité P3 — sécurité client

Vérifier séparément CSP, XSS, navigation externe, stockage local, service worker, portée du cache, manifest PWA et comportement mobile. Les contrôles statiques ne remplacent pas les tests dans un navigateur réel.

## Priorité P3 — Android

Vérifier le manifeste, les permissions, les composants exportés, le trafic en clair, les WebView, le stockage, la signature, la reproductibilité du build et l'absence de secrets dans Git. La présence des sources n'est pas une preuve de build ou d'installation réussie.

## Priorité P3 — intelligence artificielle

Limiter les fonctionnalités déclaratives non mesurées. Chaque capacité IA exposée publiquement doit avoir une définition opérationnelle, une méthode de test et un résultat observable. Les termes tels que « autonome », « auto-réparateur », « quantique », « zéro interruption » ou « sécurité absolue » ne doivent pas être utilisés comme garanties sans mécanisme mesurable et preuve actuelle.

## Critère de sortie

Aucune étape ne doit être déclarée terminée sur la seule base de la présence du code. Une étape est considérée comme validée uniquement lorsque le contrôle correspondant a été exécuté, son résultat conservé et les éventuels échecs compris.
