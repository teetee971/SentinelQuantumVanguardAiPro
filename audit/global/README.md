# Audit global — phase 1

Lecture seule du commit **`6de5a272e0302ad1eccc6c2215157b13f97d485c`** (2026-11-18, message « docs(v1): refresh the copyable Play report and align stale drafts »). Date de lecture : 2026-11-19. Les documents ont été déposés le 2026-11-25 après une interruption avant le premier push.

**Statut V1 : COMMERCIAL NO-GO.** Aucune capacité n’est déclarée commercialisable. Aucun test, build Gradle, flux réseau, workflow GitHub Actions ou scénario sur appareil n’a été exécuté. Un fichier, une classe, un écran ou un test présent ne vaut pas un fonctionnement.

La branche Git de ces documents part de `integration/sentinel-v1-commercial-20261118` (`113862500`), pas de `6de5a272`. `6de5a272` est douze commits devant cette base et ces commits modifient du code (catalogues, sauvegarde, brouillons Play). Les rejouer ici aurait mis autre chose que les fichiers d’audit dans la PR. La lecture, elle, reste celle de `6de5a272`. Les fichiers OSINT comparés sont identiques entre les deux commits.

## Documents

1. [FOCUS_OSINT_THREAT_INTEL.md](FOCUS_OSINT_THREAT_INTEL.md) — OSINT, threat intelligence, DISARM-FR, FIMI, flux CERT-FR.
2. [INVENTAIRE_BRANCHES_PR.md](INVENTAIRE_BRANCHES_PR.md) — branches locales et distantes, PR, worktree.
3. [INVENTAIRE_MODULES.md](INVENTAIRE_MODULES.md) — modules, langages, raccordement, profil commercial.
4. [MATRICE_VERITE_PARTIELLE.md](MATRICE_VERITE_PARTIELLE.md) — capacités classées dans les dix catégories.
5. [CARTOGRAPHIE_ARCHITECTURE.md](CARTOGRAPHIE_ARCHITECTURE.md) — schéma et écart attendu / présent.

## Méthode

- `git fetch origin` puis `git for-each-ref` sur les branches distantes.
- Ancêtre commun testé avec `git merge-base`. L’avance et le retard ne sont donnés que lorsqu’un ancêtre existe. `git rev-list A...B` sur deux histoires disjointes compte tous les commits de chaque côté : ces nombres ne sont pas publiés comme une divergence.
- Compare GitHub `main...integration/sentinel-v1-commercial-20261118` : HTTP 404, « No common ancestor ».
- PR ouvertes : API REST. PR fermées : les 100 mises à jour le plus récemment.
- Code : lecture ciblée des graphes de navigation, du manifeste principal et de `src/v1Commercial/AndroidManifest.xml`, de `app/build.gradle`, des registres `config/product-capabilities.json` (date interne 2026-10-05), `config/integration-capabilities.json`, `config/module-continuity-inventory.json`, `audit/v1-commercial/FEATURE_MATRIX.md`, `docs/PRODUCT_CAPABILITY_STATUS.md`.
- Comptage de lignes par extension, sans `node_modules` ni `build`. Approximation : nombre de sauts de ligne.
- Worktrees : `git worktree list`. Une seule copie.

Les dix catégories :

1. réellement développé — un appel effectif existe (navigation, manifeste, worker ou import de page), et le code appelé fait le travail nommé, pas un texte mort ;
2. présent mais désactivé — le chemin existe et un flag, un défaut ou le profil V1 l’empêche de partir ;
3. documentation ou feuille de route seulement ;
4. non raccordé à l’application Android ni au site ;
5. simulation seulement ;
6. absent du dépôt lu ;
7. cassé ou incomplet au vu du code (chemin qui ne fait pas ce que le nom annonce) ;
8. impossible sans privilège Android supplémentaire ;
9. bloqué par une dépendance, une autorisation, une configuration ou un service externe ;
10. réparable immédiatement sans régression.

La catégorie 10 reste vide : aucune modification n’a été prouvée par une exécution. Une capacité peut porter plusieurs catégories. Le décompte principal est exclusif ; le décompte multiple est séparé dans la matrice.

## Limites

- Histoire de `main` (4 034 commits, racine `d777c0a7`) et histoire de la ligne integration (racine `07392da4`, 42 commits jusqu’à `6de5a272`) disjointes. Le diff d’arbres `main` / `6de5a272` est de 783 fichiers. Il n’a pas été relu fichier par fichier.
- CI : aucune requête check-runs, pour ne pas lancer de workflow. Colonne CI = non consultée.
- Clone local utilisateur `integration/d1-visual-phonecore-20261029` inaccessible. Fichiers non suivis signalés et non lus : `app/schemas/`, `ui/design/SentinelEvidenceGraph.kt`, `SentinelHolographicScene.kt`, `SentinelQuantumCore.kt`, `SentinelReferencePanels.kt`, `SentinelVoiceVisualizer.kt`. Non classés absents.
- `fix/v1-phone-core-p1-20261119` absente du remote après fetch.
- Branches des PR #1767, #1768, #1769 et #1771 non extraites.
- Pas de phases suivantes (défauts cachés, corrections, validation).

## Chiffres de lecture

Les totaux exacts de branches et de PR sont dans l’inventaire, figés au moment de sa génération. Catégorie 10 : 0.
