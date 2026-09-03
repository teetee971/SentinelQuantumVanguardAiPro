# Roadmap — Sentinel Quantum Vanguard AI Pro

**Dernière mise à jour : 3 septembre 2026**

Cette feuille de route distingue strictement ce qui existe dans le dépôt de ce qui reste à construire. Une case cochée signifie que le code ou la documentation correspondante existe ; elle ne signifie pas qu'une validation de production a été obtenue.

## État actuel

### Déjà présent

- Architecture défensive et gouvernance de sécurité dans le dépôt.
- Contrôle d'isolation automatique empêchant les dépendances opérationnelles non autorisées.
- Validation des workflows GitHub Actions et de leur épinglage.
- Contrôle automatique des claims de la surface publique.
- Validation des liens statiques du site.
- Contrôle des permissions et paramètres de sécurité du manifeste Android.
- Tests de gouvernance IA, validation des plans d'action et fuzzing de gouvernance.
- Application Android native sous `native-android-app/`.
- Surface web statique construite vers `frontend/dist`.
- Veille OSINT limitée aux sources publiques prévues par les modules effectivement conservés.

### Non démontré actuellement

- Aucun statut « production ready » global.
- Aucun antivirus/EDR/SOC de production démontré.
- Aucun APK signé officiellement distribué par le dépôt.
- Aucun taux de détection ou de disponibilité garanti.
- Aucune certification réglementaire obtenue n'est revendiquée.
- La réussite globale de CI reste à confirmer tant que GitHub Actions échoue avant l'exécution de ses étapes.

## Priorité 1 — Restaurer une validation CI réellement exécutable

1. Diagnostiquer le problème des runners GitHub Actions.
2. Obtenir au moins une exécution complète de chaque workflow critique.
3. Corriger les erreurs révélées par ces exécutions, sans affaiblir les contrôles.
4. Conserver les preuves de run et les artefacts nécessaires à la traçabilité.

**Critère de sortie :** résultats CI observés, reproductibles et associés au commit contrôlé.

## Priorité 2 — Qualité et sécurité du cœur

1. Étendre le fuzzing du `decision-plane` aux structures imbriquées et aux limites de taille.
2. Ajouter des tests de propriétés pour les règles d'autorisation et de rollback.
3. Vérifier systématiquement les entrées non fiables aux frontières JavaScript/Kotlin.
4. Ajouter des tests de régression pour chaque vulnérabilité corrigée.
5. Mesurer les limites mémoire/temps des parseurs et validateurs.

**Critère de sortie :** couverture des chemins critiques documentée et tests réellement exécutés.

## Priorité 3 — Android

1. Faire compiler l'application sur CI lorsque l'infrastructure le permet.
2. Vérifier l'APK produit et son manifeste final.
3. Ajouter une analyse des dépendances Gradle et de leurs versions.
4. Ajouter des tests unitaires sur les composants de sécurité locaux.
5. Ajouter une vérification de l'absence de trafic clair et des composants exportés non nécessaires.
6. Préparer une release uniquement après compilation réelle, signature, checksum et conservation de l'artefact.

## Priorité 4 — Surface web

1. Maintenir une seule source de vérité pour les pages publiques.
2. Supprimer les doublons ou façades non utilisées.
3. Étendre les contrôles de liens aux références JavaScript et CSS détectables statiquement.
4. Maintenir le contrôle automatique des affirmations non démontrées.
5. Vérifier le build généré plutôt que seulement les sources.
6. Tester le rendu mobile sur plusieurs tailles d'écran avant publication.

## Priorité 5 — Données et conformité

1. Documenter chaque traitement réellement exécuté.
2. Vérifier les flux réseau de la version déployée.
3. Ne pas déduire une conformité juridique du seul dépôt.
4. Réviser les notices après chaque changement de permissions, stockage ou backend.
5. Ajouter une procédure de conservation et suppression des données si un traitement persistant est introduit.

## Priorité 6 — Supply chain et release

1. Maintenir les Actions épinglées par SHA.
2. Maintenir `npm ci` et le lockfile comme sources de vérité.
3. Mettre à jour Vite et les dépendances uniquement via une régénération réelle du lockfile et une validation complète.
4. Produire un SBOM lors des releases lorsque la chaîne de build est stabilisée.
5. Vérifier les artefacts avant toute publication.
6. Ajouter provenance et checksum aux releases.

## Évolutions réalistes à moyen terme

- Dashboard de posture basé uniquement sur des données effectivement disponibles.
- Export local des résultats d'audit.
- Rapport de sécurité reproductible à partir d'un commit précis.
- Mode hors ligne vérifié par tests d'intégration.
- Journal d'audit structuré avec rotation et limites documentées.
- Intégration de flux OSINT supplémentaires uniquement après définition de leur disponibilité, licence et politique de cache.
- Politique de mise à jour des dépendances avec seuils de criticité et fenêtre de correction.

## Évolutions volontairement non prioritaires

Les fonctionnalités suivantes ne doivent pas être ajoutées avant la stabilisation du socle : marketplace, réseau communautaire de menaces, blockchain, extension navigateur, versions desktop/iOS, partage automatique de données, backend centralisé et orchestration autonome non vérifiée.

Elles augmenteraient fortement la surface d'attaque et la charge de conformité sans résoudre les problèmes actuels de validation.

## Principes permanents

- Ne jamais transformer une simulation en preuve d'efficacité réelle.
- Ne jamais présenter un composant non testé comme opérationnel.
- Ne jamais inventer de télémétrie, métrique ou incident.
- Ne jamais contourner un contrôle CI pour obtenir un résultat vert.
- Maintenir Sentinel totalement séparé des autres projets.
- Préférer une fonctionnalité plus limitée mais démontrable à une fonctionnalité plus ambitieuse non vérifiée.

## Critère de maturité

Une fonctionnalité est considérée comme validée uniquement lorsque :

`code/configuration → test ciblé → exécution observée → résultat conservé → documentation alignée`.

**Prochaine révision recommandée : après la prochaine exécution CI complète.**
