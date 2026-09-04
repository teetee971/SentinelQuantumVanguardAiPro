# Sentinel Autonomous Engineering

## Objectif

Construire progressivement une maintenance 24/7 sans Codex et sans modification autonome non contrôlée du code source.

La première couche est volontairement déterministe : elle observe, teste, produit des preuves et bloque lorsqu'un contrôle échoue. Elle ne modifie ni le dépôt, ni les secrets, ni les branches, ni les releases.

## Niveaux d'autonomie

### Niveau 0 — Observation

Surveillance périodique de la santé du dépôt et des contrôles critiques.

### Niveau 1 — Validation automatique

Exécution des contrôles d'isolation, supply chain, liens, claims publics, sécurité client, manifeste Android, inventaire de sécurité et build.

### Niveau 2 — Remédiations déterministes

À construire après validation du Niveau 1. Une remédiation doit avoir des préconditions, une action bornée, une vérification post-action, une preuve et un mécanisme de rollback.

### Niveau 3 — Diagnostic avancé

À construire pour classer les incidents, éviter les boucles de retry et distinguer panne d'infrastructure, défaut de code, régression et problème externe.

### Niveau 4 — Agent d'ingénierie futur

Codex ou un autre agent pourra ultérieurement préparer des correctifs complexes. Il ne doit pas pouvoir contourner les gates déterministes.

### Niveau 5 — Release contrôlée

Déploiement automatique uniquement lorsque les critères de release sont satisfaits. Les environnements sensibles restent protégés par des règles d'accès et d'approbation adaptées.

## Règles de sécurité

- Aucun auto-commit arbitraire en production.
- Aucun accès automatique aux secrets pour un diagnostic non nécessaire.
- Aucun contournement des tests ou gates.
- Aucun changement de permissions sans contrôle explicite.
- Aucun changement de périmètre de données sans vérification de conformité.
- Aucun passage automatique d'une simulation à une action réelle.
- Aucun mélange avec A KI PRI SA YÉ.
- Les échecs sont conservés comme preuves et ne sont pas masqués par des retries illimités.

## Budget et anti-boucle

La couche déterministe doit privilégier les scripts locaux et les tests CI. Les futurs agents IA ne seront déclenchés que pour les problèmes qui nécessitent réellement une analyse de code complexe. Des limites de fréquence, de durée et de tentatives devront empêcher les boucles coûteuses.

## État initial

Le workflow `.github/workflows/autonomous-maintenance.yml` exécute cette première couche toutes les six heures, ainsi que sur les changements pertinents et à la demande. Il produit un rapport JSON et un artefact CI.

Cette automatisation ne constitue pas encore une auto-réparation complète. Elle constitue le socle vérifiable sur lequel les remédiations pourront être ajoutées progressivement.
