# Histoires Git disjointes — stratégie, sans fusion

Aucune branche n’est supprimée. Aucun tag n’est créé ici. Aucun `merge` et aucun `reset --hard`.

## Constat

`origin/main` (`935344423b2af2a4a57c58123f2c5c178b46ec03`, racine `d777c0a7`, 4 034 commits visibles) et `origin/integration/sentinel-v1-commercial-20261118` (`113862500b9579e2ce476c190ad3195f676ffdda`, racine `07392da4`) n’ont pas d’ancêtre commun. L’API GitHub Compare répond 404 « No common ancestor ». Le diff d’arbres `main` / `6de5a272` porte sur 783 fichiers. Un `git merge` des deux pointes serait une fusion d’histoires sans rapport : il ne « rattrape » pas un retard, il juxtapose deux projets et peut écraser des fichiers des deux côtés.

Lecture du 2026-11-19, encore valable le 2026-11-25 pour ces deux pointes : 1 150 branches distantes alors recensées, dont 747 divergentes de `main`, 233 déjà contenues dans `main`, 122 qui contiennent `main`, 48 d’histoire disjointe. Ces comptes n’ont pas été recalculés après les poussées du 2026-11-25.

## Règle

1. Garder `main` et la ligne `integration/sentinel-v1-commercial-20261118` comme deux pointes nommées. Ne pas fast-forward l’une sur l’autre.
2. Toute consolidation future est un nouvel historique explicite, relu fichier par fichier, avec les deux pointes citées dans le message de commit. Pas dans cette PR.
3. Avant cette consolidation, poser des tags annotés sur les pointes à figer. Les noms proposés ne sont pas créés :
   - `archive/main-20261125` sur `935344423`
   - `archive/integration-v1-20261118` sur `113862500`
   - `archive/v1-read-6de5a272` sur `6de5a272e0302ad1eccc6c2215157b13f97d485c`
4. Ne pas réécrire les branches des PR ouvertes. Ne pas récupérer une branche locale inconnue avec `reset --hard`.
5. Le clone local `integration/d1-visual-phonecore-20261029` et ses fichiers non suivis (`app/schemas/`, `ui/design/SentinelEvidenceGraph.kt`, `SentinelHolographicScene.kt`, `SentinelQuantumCore.kt`, `SentinelReferencePanels.kt`, `SentinelVoiceVisualizer.kt`) ne sont pas dans ce remote. Les archiver chez le propriétaire avant toute opération qui remplacerait son travail.

## À préserver

| Référence | Pourquoi |
| --- | --- |
| `main` | Histoire longue, PR historiques, default branch. |
| `integration/sentinel-v1-commercial-20261118` | Ligne du profil Phone Core V1. |
| `6de5a272` et `cursor/v1-p0-privacy-catalog-c39e` | Lecture d’audit et PR #1769. |
| `cursor/v1-play-compliance-20261118-c39e` | PR #1768. |
| `cursor/v1-call-screening-audit-536d` | PR #1767. |
| `cursor/v1-phone-core-full-audit-20261118` | PR #1771. |
| `fix/v1-phone-core-p1-20261119` | PR #1772. Ne pas la réécrire. |
| `docs/audit-global-phase1-20261119` | PR #1774, documents seulement. |
| `cursor/v1-product-truth-20261125-d8fe` | Cette correction de vérité. |
| `fix/v1-p0-v25-v26-20261125` | Absente du remote au moment de ce document. À préserver dès qu’elle est poussée. Ne pas la merger ici. |
| `transfer/sentinel-cycle9-validation-artifacts-20261117` et `transfer/sentinel-cycle10-validation-20261117` | Preuves de cycles. Histoire disjointe. Ne pas les écraser. |

## À figer par tag plus tard, pas à supprimer

- Pointes `automation/cisa-kev-refresh-*` et `automation/vulnerability-watch-*` : instantanés d’automation. Un tag par famille sur la pointe la plus récente suffit à les retrouver. La suppression reste une décision humaine après le tag.
- Branches dont la pointe est déjà contenue dans `main` (233 au recensement) : redondantes pour le contenu, utiles comme noms. Tag puis conservation. Pas de suppression dans cette PR.

## Redondantes au sens du contenu, toujours conservées

Une branche contenue dans `main` (`ahead = 0`) ne porte pas de commit propre. Elle peut quand même être la tête d’une discussion. Elle reste. Exemples de familles nombreuses : anciens `fix/` rejoués sur `main`, `phone-core/*` déjà ancestraux de `main`.

Les branches `copilot/*` de septembre-novembre 2025 sont une autre racine. Elles ne sont pas un doublon de `main`. Les supprimer perdrait cette racine. Les conserver. Ne pas les merger dans la ligne V1.

## Consolidation ultérieure, non faite

Ordre possible, plus tard, avec revue :

1. Taguer les pointes ci-dessus.
2. Copier le travail local non suivi du clone D1.
3. Créer une branche neuve depuis `main`.
4. Y importer uniquement les fichiers du profil V1 relus, par commits petits, sans `merge -s ours` et sans remplacer `main` en bloc.
5. Comparer le résultat au tip integration avec `git diff`, pas avec un merge automatique.

Tant que cette revue n’existe pas, la ligne d’intégration reste la base des PR V1, et `main` reste la base historique. Les deux NO-GO de consolidation : fusion aveugle, et reset qui écraserait le clone local.
