# Feuille de route réaliste — Sentinel Quantum Vanguard AI Pro

**Révision :** septembre 2026  
**Statut :** feuille de route technique, sans promesse de date

## Règle de preuve

Aucune capacité de sécurité n'est considérée comme validée uniquement parce qu'elle existe dans le code, la documentation ou un scénario de test. Pour les contrôles critiques, la chaîne attendue est : implémentation → test négatif et positif → exécution réelle → résultat observable → revue du résultat → déclaration limitée à ce qui est démontré.

## Priorité 0 — Nettoyage contrôlé et source de vérité

Objectif : réduire la surface inutile avant d'ajouter des fonctions.

- inventorier les arbres, artefacts, scripts, workflows et configurations ;
- classer chaque élément : source de vérité, utilisé, test/fixture, documentation, historique, généré, obsolète ou contaminant ;
- supprimer uniquement les éléments dont l'absence de dépendance opérationnelle est démontrée ;
- ne pas supprimer les fixtures de tests uniquement parce qu'elles contiennent des références à d'autres projets ;
- maintenir une seule source Android : `native-android-app/` ;
- maintenir une séparation stricte avec les autres projets ;
- éliminer les duplications de documentation et de pipelines lorsque leur remplacement est vérifié.

## Priorité 1 — Stabilisation et preuve

Objectif : disposer d'un dépôt cohérent et vérifiable avant toute nouvelle capacité.

- conserver le pinning SHA des GitHub Actions ;
- aligner les versions Node entre `.node-version`, `package.json`, CI et documentation ;
- exécuter les tests de gouvernance, d'isolation et de fuzzing ;
- rétablir l'exécution normale des runners GitHub Actions ;
- distinguer `PASS`, `FAIL_CODE`, `FAIL_TEST`, `FAIL_SECURITY`, `FAIL_INFRA`, `SKIPPED` et `NOT_EXECUTED` ;
- examiner les résultats CI avant toute déclaration de validation.

## Priorité 2 — Convergence du Decision Plane

Objectif : supprimer les divergences entre classification, politique et exécution.

- utiliser un catalogue central pour les actions et décisions ;
- refuser par défaut toute action ou décision inconnue ;
- interdire les booléens comme preuve suffisante pour une action sensible ;
- utiliser des enregistrements structurés pour autorisation, approbation humaine et simulation ;
- lier systématiquement action, `action_id`, cible, version de politique et résultat de simulation ;
- séparer vérification des préconditions et vérification des postconditions ;
- conserver l'IA dans les rôles proposition/analyse/simulation, jamais comme autorité finale ;
- empêcher tout chemin direct IA → exécution privilégiée.

## Priorité 3 — Authenticité des preuves

Objectif : passer de la simple validation structurelle à une preuve réellement attribuable.

- identifier les producteurs réels des `AuthorizationRecord`, `HumanApprovalRecord` et `SimulationRecord` ;
- définir une liste explicite d'émetteurs autorisés et leur capacité ;
- séparer structure, provenance, intégrité et autorité ;
- ajouter un mécanisme d'intégrité adapté au modèle de déploiement ;
- protéger contre rejeu, substitution, modification et confusion de contexte ;
- ajouter une politique de fraîcheur et de révocation ;
- tester les scénarios TOCTOU et changement de politique/cible entre validation et exécution.

## Priorité 4 — Sécurité produit

- poursuivre le fuzzing synthétique autorisé ;
- tester les entrées adversariales, valeurs positives forgées, types inattendus, champs hérités, duplications et incohérences temporelles ;
- conserver les garde-fous sur les actions critiques ;
- vérifier les permissions Android, composants exportés, réseau et stockage ;
- vérifier CSP, navigation, stockage local et surfaces XSS côté web/PWA ;
- renforcer les contrôles de supply chain : lockfile, audit de dépendances, SBOM, secret scanning et revue des workflows.

## Priorité 5 — Audit et forensic

- conserver séparation observation / inférence / hypothèse ;
- séparer confiance du modèle, qualité de preuve et fiabilité de la source ;
- conserver les contradictions au lieu de les écraser ;
- contrôler `event_time`, `ingestion_time`, `processing_time` et `decision_time` ;
- rendre les journaux d'audit altérables uniquement selon une politique contrôlée et détecter toute rupture d'intégrité ;
- journaliser les transitions des états d'action et les décisions de refus.

## Priorité 6 — Qualité du produit

- vérifier le build web/PWA réel ;
- vérifier les références et ressources chargées par l'interface ;
- éliminer les chemins morts et configurations orphelines ;
- vérifier l'interface mobile et les comportements responsive ;
- maintenir une documentation courte, factuelle et synchronisée avec le dépôt.

## Priorité 7 — Android

- maintenir exclusivement `native-android-app/` ;
- vérifier manifeste, permissions, configuration réseau et composants exportés ;
- produire un build de validation reproductible ;
- vérifier signature et checksum des releases ;
- ne jamais stocker de keystore ou de secret dans Git ;
- distinguer explicitement source présente, build réussi, installation réussie, runtime vérifié et release vérifiée.

## Blocage connu

La validation CI complète reste conditionnée au rétablissement des runners GitHub-hosted. Des exécutions précédentes ont échoué avant l'exécution des étapes. Ce phénomène est classé comme blocage d'infrastructure et ne doit pas être contourné en affaiblissant les contrôles. Tant qu'un nouveau résultat exécutable n'est pas observé, aucune réussite CI ne doit être déclarée.

## Hors périmètre

Ne pas réintroduire :

- anciens arbres Android ou frontend ;
- anciens pipelines de release supprimés ;
- artefacts APK committés comme preuve de build ;
- secrets, keystores ou configurations privées ;
- dépendances opérationnelles provenant d'un autre projet ;
- affirmations de conformité, de sécurité absolue, de zéro-downtime ou d'autonomie totale sans preuve actuelle ;
- vocabulaire « quantique » présenté comme une propriété cryptographique ou physique réelle sans implémentation démontrée.

## Critère de progression

Une étape est considérée comme terminée uniquement lorsque le code correspondant existe, que les contrôles pertinents ont été exécutés et que les résultats sont disponibles et examinés.

**Principe :** nettoyer → stabiliser → tester → observer les preuves → corriger → retester → seulement ensuite étendre le périmètre.
