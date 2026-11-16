# Sentinel — Plan d'amélioration réaliste

## Objectif

Faire progresser Sentinel Quantum Vanguard AI Pro par réduction de complexité, augmentation de la preuve technique et durcissement des frontières. Le plan exclut les fonctionnalités fictives, les agents « autonomes » ajoutés sans infrastructure réelle et toute promesse qui ne peut pas être démontrée.

## Priorité 0 — Restaurer une validation CI réellement observable

Le dépôt présente actuellement un problème d'exécution GitHub Actions : plusieurs jobs récents échouent avant la première étape, avec aucun step exécuté. Tant que ce problème persiste, un résultat rouge ne doit pas être interprété comme une régression du code.

Actions :

1. Diagnostiquer la disponibilité/attribution des runners GitHub.
2. Relancer un workflow minimal après rétablissement du runner.
3. Rejouer ensuite, dans l'ordre, isolation, gouvernance, fuzzing, frontend et Android.
4. Conserver la distinction `code failure` / `runner failure` dans la documentation.
5. Ne jamais affaiblir les contrôles pour obtenir du vert.

## Priorité 1 — Rendre le frontend plus sûr sans le casser

État actuel : des pages statiques contiennent encore des scripts et gestionnaires d'événements inline. Le CSP doit donc rester compatible tant que cette migration n'est pas terminée.

Actions :

- remplacer progressivement les `onclick`/handlers inline par `addEventListener` ;
- supprimer les interpolations non nécessaires dans `innerHTML` ;
- préférer `textContent`, `createElement` et attributs DOM explicites pour les données ;
- ajouter un contrôle statique spécifique aux handlers inline et aux constructions HTML dynamiques ;
- après migration, retirer `unsafe-inline` du CSP et passer à des scripts/styles strictement autorisés.

## Priorité 2 — Durcir le moteur de simulation

Les scénarios doivent rester synthétiques et non exécutables.

Actions :

- imposer des limites de taille, profondeur, nombre d'événements et durée ;
- séparer explicitement les données `UNTRUSTED`, `NORMALIZED` et `TRUSTED` ;
- valider le schéma avant traitement ;
- refuser les commandes ou charges utiles exécutables dans les fixtures ;
- tester les entrées malformées, énormes, récursives et ambiguës ;
- vérifier que l'export ne transforme pas des données en HTML ou script actif.

## Priorité 3 — Renforcer OSINT

Le code actuel possède déjà une allowlist HTTPS et des limites réseau. La prochaine étape est la robustesse du contenu reçu.

Actions :

- contrôler le type MIME avant parsing ;
- limiter strictement la taille avant allocation/parsing ;
- sécuriser le parsing XML contre les entités externes et les constructions dangereuses ;
- désactiver les redirections vers des destinations non autorisées ;
- dédupliquer les événements ;
- imposer une fenêtre de rétention/cache bornée ;
- conserver la source, l'horodatage de collecte et le niveau de confiance ;
- traiter toute donnée distante comme non fiable jusqu'à normalisation.

## Priorité 4 — Decision Plane

Le decision plane doit rester fail-closed et borné.

Actions :

- budget maximal par action, cible, condition et rollback ;
- budget temporel global ;
- nombre maximal de tentatives ;
- taille maximale des sorties ;
- tests property-based/fuzz supplémentaires ;
- refus explicite des actions sans preuve ou sans politique applicable ;
- journal d'audit déterministe `INPUT → RULES → OBSERVATIONS → RESULT → LIMITATIONS`.

## Priorité 5 — Android

Actions :

- exécuter réellement le build dès que le runner est disponible ;
- vérifier exported components et intents ;
- auditer les deep links ;
- confirmer l'absence de cleartext ;
- vérifier le stockage local et les logs ;
- vérifier backup/data extraction ;
- contrôler R8/minification ;
- figer et auditer les dépendances ;
- produire un APK uniquement après build, signature, checksum et vérification observés.

Aucun APK ne doit être présenté comme officiel avant cette chaîne de preuve.

## Priorité 6 — Supply chain et release

Mettre en place une chaîne reproductible :

`source → lockfile → tests → fuzzing → analyse statique → dépendances → build → signature → checksum → provenance → artefact → release`

Ajouter ensuite un SBOM généré par l'outil de build, sans fabriquer manuellement d'intégrités ou de métadonnées.

## Priorité 7 — Réduction de dette technique

Actions :

- supprimer les doublons réellement identifiés comme orphelins ;
- conserver les fixtures de sécurité même lorsqu'elles ne sont pas référencées par l'interface ;
- éviter d'ajouter de nouveaux frameworks sans besoin démontré ;
- maintenir une seule source de vérité pour la navigation, les flags et les chemins de build ;
- documenter les composants réellement utilisés ;
- reconsidérer les anciens modules uniquement après vérification de leurs références.

## Dépendances et mises à jour

Les mises à jour doivent être effectuées avec le gestionnaire de paquets et le lockfile, puis validées par build et tests. Une version majeure de Vite ou d'une autre dépendance ne doit pas être déclarée adoptée avant une mise à jour cohérente du lockfile et une exécution réussie.

## Ce qui n'est pas recommandé maintenant

- ajouter un SOC réel sans backend, collecte, stockage et exploitation opérationnelle ;
- ajouter des agents autonomes simplement pour augmenter le nombre de composants ;
- promettre une protection active sans canal d'enforcement ;
- annoncer une surveillance 24/7 sans service effectivement opéré ;
- annoncer une certification sans organisme ou rapport correspondant ;
- publier un APK non signé ou non vérifié ;
- masquer un échec CI en modifiant les règles de sécurité.

## Critère de sortie

Une amélioration est considérée comme terminée uniquement lorsque son état peut être exprimé par une chaîne vérifiable :

`implémenté → testé → exécuté → résultat observé → documenté`

Si une étape est impossible à observer, elle reste explicitement marquée comme non validée.
